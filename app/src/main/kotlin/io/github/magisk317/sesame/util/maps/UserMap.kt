package io.github.magisk317.sesame.util.maps

import io.github.magisk317.sesame.entity.UserEntity
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.service.RemotePrefsStore
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * 用于管理和操作用户数据的映射关系。
 * 转换为 Kotlin object 单例模式。
 */
object UserMap {
    private val TAG = UserMap::class.java.simpleName
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }
    private val userDtoMapSerializer = MapSerializer(String.serializer(), UserEntity.UserDto.serializer())

    // 存储用户信息的线程安全映射
    // ConcurrentHashMap 不允许 key 或 value 为 null
    private val userMap = ConcurrentHashMap<String, UserEntity>()

    /**
     * 当前用户ID
     * 使用 @JvmStatic 和 @JvmField 保持 Java 互操作性
     */
    var currentUid: String? = null
        private set // 外部只能通过 setCurrentUserId 修改

    /**
     * 获取只读的用户信息映射
     */
    @JvmStatic
    fun getUserMap(): Map<String, UserEntity> {
        return Collections.unmodifiableMap(userMap)
    }

    /**
     * 获取所有用户ID的集合
     */
    @JvmStatic
    fun getUserIdSet(): Set<String> {
        return userMap.keys
    }

    /**
     * 设置当前用户ID
     */
    @JvmStatic
    @Synchronized
    fun setCurrentUserId(userId: String?) {
        currentUid = if (userId.isNullOrEmpty()) null else userId
    }

    /**
     * 获取当前用户的掩码名称
     * 修复：如果 currentUid 为 null，直接返回 null，避免 ConcurrentHashMap 崩溃
     */
    @JvmStatic
    fun getCurrentMaskName(): String? {
        return getMaskName(currentUid)
    }

    /**
     * 获取指定用户的掩码名称
     * 修复：增加了 userId 判空检查
     */
    @JvmStatic
    fun getMaskName(userId: String?): String? {
        if (userId == null) {
            StructuredLog.record(TAG, "getMaskName: userId is null")
            return null
        } // 关键修复：防止 userMap.get(null) 崩溃
        return userMap[userId]?.maskName
    }

    /**
     * 获取指定用户实体
     */
    @JvmStatic
    fun get(userId: String?): UserEntity? {
        if (userId == null) return null // 关键修复
        return userMap[userId]
    }

    /**
     * 添加用户到映射
     */
    @JvmStatic
    @Synchronized
    fun add(userEntity: UserEntity?) {
        if (userEntity == null) return
        val uid = userEntity.userId
        if (!uid.isNullOrEmpty()) {
            userMap[uid] = userEntity
        }
    }

    /**
     * 从映射中移除指定用户
     */
    @JvmStatic
    @Synchronized
    fun remove(userId: String?) {
        if (userId != null) {
            userMap.remove(userId)
        }
    }

    /**
     * 加载用户数据
     */
    @JvmStatic
    @Synchronized
    fun load(userId: String?) {
        userMap.clear()
        if (userId.isNullOrEmpty()) {
            StructuredLog.error(TAG, "Skip loading user map for empty userId")
            return
        }
        try {
            val friendIdMapFile = Files.getFriendIdMapFile(userId)
            if (friendIdMapFile == null) {
                StructuredLog.error(TAG, "Friend ID map file is null for userId: $userId")
                return
            }
            val body = Files.readFromFile(friendIdMapFile)
            if (body.isNotEmpty()) {
                val dtoMap = json.decodeFromString(userDtoMapSerializer, body)
                dtoMap.values.forEach { dto ->
                    // 再次确保 Key 和 Value 不为 null
                    val uid = dto.userId
                    val entity = dto.toEntity()
                    if (!uid.isNullOrEmpty()) {
                        userMap[uid] = entity
                    }
                }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }

    /**
     * 卸载用户数据
     */
    @JvmStatic
    @Synchronized
    fun unload() {
        userMap.clear()
    }

    /**
     * 保存用户数据到文件
     */
    @JvmStatic
    @Synchronized
    fun save(userId: String?): Boolean {
        if (userId.isNullOrEmpty()) return false
        val dtoMap = userMap.mapValues { (_, entity) -> UserEntity.UserDto.fromEntity(entity) }
        val body = json.encodeToString(userDtoMapSerializer, dtoMap)
        return Files.write2File(body, Files.getFriendIdMapFile(userId)!!)
    }

    /**
     * 加载当前用户的数据
     */
    @JvmStatic
    @Synchronized
    fun loadSelf(userId: String?) {
        userMap.clear()
        if (userId.isNullOrEmpty()) return

        try {
            readSelf(userId)?.let { entity ->
                entity.userId?.let { uid -> userMap[uid] = entity }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }

    /** Reads one account profile without mutating the process-wide friend map. */
    @JvmStatic
    fun readSelf(userId: String?): UserEntity? {
        if (userId.isNullOrEmpty()) return null
        return runCatching {
            val body = Files.readFromFile(Files.getSelfIdFile(userId)!!)
            if (body.isEmpty()) return@runCatching null
            json.decodeFromString(UserEntity.UserDto.serializer(), body).toEntity()
        }.onFailure { StructuredLog.printStackTrace(TAG, "读取用户档案失败", it) }.getOrNull()
    }

    /**
     * 保存当前用户数据到文件
     */
    @JvmStatic
    @Synchronized
    fun saveSelf(userEntity: UserEntity?) {
        if (userEntity == null) return
        RemotePrefsStore.putActiveUserId(userEntity.userId)
        StructuredLog.record(TAG, "update now active user: $userEntity")
        val body = json.encodeToString(
            UserEntity.UserDto.serializer(),
            UserEntity.UserDto.fromEntity(userEntity),
        )
        Files.write2File(body, Files.getSelfIdFile(userEntity.userId)!!)
    }
}
