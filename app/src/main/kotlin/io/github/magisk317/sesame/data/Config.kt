package io.github.magisk317.sesame.data

import io.github.magisk317.sesame.entity.UserEntity
import io.github.magisk317.sesame.model.ModelConfig
import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.task.TaskCommon
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.StringUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.service.RemotePrefsStore
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Runtime configuration state and its file persistence boundary. */
object Config {
    private const val TAG = "Config"

    @Volatile
    var init: Boolean = false
        private set

    /**
     * 上一次 load 是否未能成功读取既有用户配置（存储里有数据但解析/回退失败）。
     * 此标志为 true 时 save() 一律拒绝写入，防止把内存中的默认值覆盖到用户数据上。
     * 全新安装（存储里确实没有配置）不会置位，首次保存不受影响。
     */
    @Volatile
    var lastLoadFailed: Boolean = false
        private set

    val modelFieldsMap: MutableMap<String, ModelFields> = ConcurrentHashMap()

    @JvmStatic
    fun setModelFieldsMap(newModels: Map<String, ModelFields>?) {
        modelFieldsMap.clear()
        val requested = newModels.orEmpty()
        Model.getModelConfigMap().values.forEach { modelConfig ->
            val modelCode = modelConfig.code ?: return@forEach
            val configFields = modelConfig.fields
            val savedFields = requested[modelCode]
            val mergedFields = ModelFields()
            configFields.values.forEach { configField ->
                runCatching {
                    savedFields?.get(configField.code)?.value?.let(configField::setObjectValue)
                }.onFailure { StructuredLog.printStackTrace(it) }
                mergedFields.addField(configField)
            }
            modelFieldsMap[modelCode] = mergedFields
        }
    }

    @JvmStatic
    fun hasModelFields(modelCode: String?): Boolean = modelFieldsMap.containsKey(modelCode)

    @JvmStatic
    fun hasModelField(modelCode: String?, fieldCode: String?): Boolean =
        modelFieldsMap[modelCode]?.containsKey(fieldCode) == true

    @JvmStatic
    fun isModify(userId: String?): Boolean {
        val file = if (StringUtil.isEmpty(userId)) Files.getDefaultConfigFile()
        else Files.getConfigFile(requireNotNull(userId))
        val existing = if (file.exists()) Files.readFromFile(file) else null
        return existing == null || toSaveStr() != existing
    }

    @JvmStatic
    @Synchronized
    fun save(userId: String?, force: Boolean?): Boolean {
        if (lastLoadFailed) {
            StructuredLog.error(
                TAG,
                "配置未能成功加载，已拒绝保存，避免默认值覆盖用户数据；请先排查加载失败原因",
            )
            return false
        }
        if (force != true && !isModify(userId)) return true
        val json = runCatching { toSaveStr() }.getOrElse {
            StructuredLog.printStackTrace(TAG, it)
            StructuredLog.record(TAG, "保存用户配置失败，格式化 JSON 时出错")
            return false
        }
        return try {
            val actualUserId = userId.takeUnless { StringUtil.isEmpty(it) }
            RemotePrefsStore.backupConfig(actualUserId)
            val success = if (actualUserId == null) {
                Files.setDefaultConfigFile(json)
            } else {
                Files.setConfigFile(actualUserId, json)
            }
            if (!success) throw IOException("配置文件保存失败")
            RemotePrefsStore.putConfig(userId, json)
            val userName = if (actualUserId == null) "默认用户"
            else UserMap.get(actualUserId)?.showName ?: "默认"
            StructuredLog.record(TAG, "保存 [$userName] 配置")
            true
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, e)
            StructuredLog.record(TAG, "保存用户配置失败")
            false
        }
    }

    /**
     * Publishes existing UI-owned config files after the remote preference channel becomes
     * available. This is an upgrade/import step only; the hook process never calls it and never
     * falls back to these files.
     */
    @JvmStatic
    fun migrateFilesToRemote(userIds: Iterable<String>): Int {
        if (!RemotePrefsStore.available() || RemotePrefsStore.getConfigurationResetGeneration() > 0L) {
            return 0
        }
        var published = 0
        val targets = buildList {
            add(null)
            userIds.filter(String::isNotBlank).forEach(::add)
        }
        targets.forEach { userId ->
            val file = if (userId == null) {
                Files.getDefaultConfigFile()
            } else {
                Files.getConfigFile(userId)
            }
            if (!file.isFile) return@forEach
            val json = Files.readFromFile(file).takeIf(String::isNotBlank) ?: return@forEach
            if (RemotePrefsStore.putConfig(userId, json, file.lastModified())) {
                published++
            }
        }
        if (published > 0) {
            StructuredLog.record(TAG, "已将 $published 份本地配置迁移到 LibXposed remote prefs")
        }
        return published
    }

    @JvmStatic
    fun isLoaded(): Boolean = init

    @JvmStatic
    @Synchronized
    fun load(userId: String?, allowFileFallback: Boolean = true): Config {
        val remoteJson = RemotePrefsStore.getConfig(userId)
        if (remoteJson != null) {
            val remoteLoaded = runCatching {
                ConfigCodec.apply(this, remoteJson)
                init = true
                TaskCommon.update()
                StructuredLog.record(TAG, "从 LibXposed remote prefs 加载配置")
            }.onFailure { StructuredLog.printStackTrace(TAG, "加载 remote 配置失败", it) }.isSuccess
            if (remoteLoaded) {
                lastLoadFailed = false
                return this
            }
            if (!allowFileFallback) {
                unload()
                lastLoadFailed = true
                StructuredLog.record(TAG, "remote 配置无效，拒绝回退到外部配置文件")
                return this
            }
            lastLoadFailed = true
        }
        if (!allowFileFallback) {
            unload()
            StructuredLog.record(
                TAG,
                if (RemotePrefsStore.available()) {
                    "remote 配置缺失，拒绝回退到外部配置文件"
                } else {
                    "remote 偏好不可用，拒绝读取外部配置文件"
                },
            )
            lastLoadFailed = false
            return this
        }
        val allowLegacyFileFallback = RemotePrefsStore.getConfigurationResetGeneration() == 0L
        var configFile: File? = null
        try {
            val actualUserId = userId.takeUnless { StringUtil.isEmpty(it) }
            configFile = when {
                allowLegacyFileFallback && actualUserId == null -> Files.getDefaultConfigFile()
                allowLegacyFileFallback && actualUserId != null -> Files.getConfigFile(actualUserId)
                actualUserId == null -> File(Files.CONFIG_DIR, "config.json")
                else -> File(Files.CONFIG_DIR, "$actualUserId/config.json")
            }
            val userName = if (actualUserId == null) "默认"
            else UserMap.get(actualUserId)?.showName ?: actualUserId
            StructuredLog.record(TAG, "加载配置: $userName")

            when {
                allowLegacyFileFallback && configFile.exists() -> {
                    applyFile(configFile)
                    lastLoadFailed = false
                    publishRemoteSnapshot(userId)
                }
                allowLegacyFileFallback && actualUserId != null && Files.getDefaultConfigFile().exists() -> {
                    val json = Files.readFromFile(Files.getDefaultConfigFile())
                    ConfigCodec.apply(this, json)
                    StructuredLog.record(TAG, "复制新配置: $userName")
                    Files.write2File(json, configFile)
                    lastLoadFailed = false
                    publishRemoteSnapshot(userId)
                }
                else -> {
                    unload()
                    if (allowLegacyFileFallback) {
                        Files.write2File(toSaveStr(), configFile)
                    }
                    if (!allowLegacyFileFallback) {
                        StructuredLog.record(TAG, "reset barrier 生效，忽略外部配置文件")
                    }
                    lastLoadFailed = false
                    publishRemoteSnapshot(userId)
                }
            }
        } catch (t: Throwable) {
            lastLoadFailed = true
            StructuredLog.printStackTrace(TAG, "配置加载失败，拒绝用默认值覆盖既有配置", t)
            runCatching { unload() }
                .onFailure { StructuredLog.printStackTrace(TAG, "重置配置失败", it) }
        }
        init = true
        TaskCommon.update()
        return this
    }

    private fun publishRemoteSnapshot(userId: String?) {
        if (!RemotePrefsStore.available()) return
        if (!RemotePrefsStore.putConfig(userId, toSaveStr())) {
            StructuredLog.record(TAG, "remote 配置发布已排队")
        }
    }

    @JvmStatic
    @Synchronized
    fun unload() {
        init = false
        modelFieldsMap.values.forEach { fields ->
            fields.values.forEach(ModelField<*>::reset)
        }
    }

    @JvmStatic
    fun toSaveStr(): String = ConfigCodec.encode(this)

    private fun applyFile(file: File) {
        val json = Files.readFromFile(file)
        ConfigCodec.apply(this, json)
        val formatted = toSaveStr()
        if (formatted != json) Files.write2File(formatted, file)
    }
}
