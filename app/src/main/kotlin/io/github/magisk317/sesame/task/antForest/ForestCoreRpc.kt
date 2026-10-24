package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.entity.RpcEntity
import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/** Core forest home, friend and energy-collection RPC gateway. */
object ForestCoreRpc {
    fun queryFriendsEnergyRanking(): String = RequestManager.requestString(
        "alipay.antmember.forest.h5.queryEnergyRanking",
        requestArray(jsonObject("source" to SOURCE, "periodType" to "total", "rankType" to "energyRank", "version" to ForestApiVersion.value)),
        requestArray(jsonObject("pathList" to path("friendRanking", "myself", "totalDatas"))),
    )

    fun queryTopEnergyChallengeRanking(): String = request(
        "alipay.antforest.forest.h5.queryTopEnergyChallengeRanking", jsonObject("source" to SOURCE),
    )

    fun fillUserRobFlag(userIds: List<String?>): String = RequestManager.requestString(
        "alipay.antforest.forest.h5.fillUserRobFlag",
        requestArray(jsonObject("source" to SOURCE, "userIdList" to jsonStringArray(userIds))),
        requestArray(jsonObject("pathList" to path("friendRanking"))),
    )

    fun fillUserRobFlag(userIds: List<String?>, needFillUserInfo: Boolean): String = request(
        "alipay.antforest.forest.h5.fillUserRobFlag",
        jsonObject("source" to SOURCE, "userIdList" to jsonStringArray(userIds), "needFillUserInfo" to needFillUserInfo),
    )

    fun queryHomePage(): String = RequestManager.requestString(
        "alipay.antforest.forest.h5.queryHomePage", homePayload(), 3, 1000,
    )

    /**
     * Friend home is requested by the ranking pass, watering, and waiting-task
     * validation. Coalesce concurrent requests and reuse a very short-lived
     * response so those paths do not issue the same RPC repeatedly.
     */
    fun queryFriendHomePage(userId: String, fromAct: String? = null): String {
        val normalizedFromAct = fromAct ?: DEFAULT_FRIEND_FROM_ACT
        val key = "$userId\u0000$normalizedFromAct"
        val lock = friendQueryLocks.computeIfAbsent(key) { Any() }
        synchronized(lock) {
            val now = System.currentTimeMillis()
            friendHomeCache[key]?.takeIf { now - it.createdAt < FRIEND_HOME_CACHE_TTL_MS }?.let {
                return it.response
            }

            val response = RequestManager.requestString(
                "alipay.antforest.forest.h5.queryFriendHomePage",
                friendPayload(userId, normalizedFromAct), 3, 1000,
            )
            if (response.isNotBlank()) {
                if (friendHomeCache.size >= MAX_FRIEND_HOME_CACHE_ENTRIES) {
                    friendHomeCache.entries
                        .minByOrNull { it.value.createdAt }
                        ?.let {
                            friendHomeCache.remove(it.key, it.value)
                            friendQueryLocks.remove(it.key)
                        }
                }
                // 防止 lock map 无限增长（cache miss 场景也会创建 lock）
                if (friendQueryLocks.size > MAX_FRIEND_HOME_CACHE_ENTRIES * 2) {
                    friendQueryLocks.keys.removeIf { !friendHomeCache.containsKey(it) }
                }
                friendHomeCache[key] = CachedFriendHome(now, response)
            }
            return response
        }
    }

    fun invalidateFriendHome(userId: String) {
        friendHomeCache.keys.removeIf { it.startsWith("$userId\u0000") }
    }

    fun takeLook(skipUsers: JsonObject): String = request(
        "alipay.antforest.forest.h5.takeLook",
        jsonObject("contactsStatus" to "N", "exposedUserId" to "", "skipUsers" to skipUsers,
            "source" to SOURCE, "takeLookEnd" to false, "takeLookStart" to true,
            "version" to ForestApiVersion.value),
    )

    fun energyEntity(bizType: String, userId: String, bubbleId: Long): RpcEntity = RpcEntity(
        "alipay.antmember.forest.h5.collectEnergy",
        requestArray(jsonObject("bizType" to bizType, "bubbleIds" to jsonArray(JsonPrimitive(bubbleId)),
            "source" to SOURCE, "userId" to userId, "version" to ForestApiVersion.value)),
        null,
    )

    fun batchEnergyEntity(bizType: String, userId: String, bubbleIds: List<Long>): RpcEntity = RpcEntity(
        "alipay.antmember.forest.h5.collectEnergy",
        requestArray(jsonObject("bizType" to bizType, "bubbleIds" to jsonArray(*bubbleIds.map(::JsonPrimitive).toTypedArray()),
            "fromAct" to "BATCH_ROB_ENERGY", "source" to SOURCE, "userId" to userId,
            "version" to ForestApiVersion.value)),
    )

    @JvmStatic
    fun collectEnergy(bizType: String, userId: String, bubbleId: Long): String =
        RequestManager.requestString(energyEntity(bizType, userId, bubbleId))

    fun collectRebornEnergy(): String = request(
        "alipay.antforest.forest.h5.collectRebornEnergy", jsonObject("source" to SOURCE),
    )

    fun transferEnergy(targetUser: String, bizNo: String, energyId: Int, notifyFriend: Boolean): String = request(
        "alipay.antmember.forest.h5.transferEnergy",
        jsonObject("bizNo" to (bizNo + UUID.randomUUID()), "energyId" to energyId,
            "extInfo" to jsonObject("sendChat" to if (notifyFriend) "Y" else "N"),
            "from" to "friendIndex", "source" to SOURCE, "targetUser" to targetUser,
            "transferType" to "WATERING", "version" to ForestApiVersion.value),
    )

    private fun homePayload(): String = requestArray(jsonObject(
        "activityParam" to jsonObject(), "configVersionMap" to jsonObject("wateringBubbleConfig" to "0"),
        "skipWhackMole" to false, "source" to SOURCE, "version" to ForestApiVersion.value,
    ))

    private fun friendPayload(userId: String, fromAct: String): String = requestArray(jsonObject(
        "canRobFlags" to "T,F,F,F,F", "configVersionMap" to jsonObject("wateringBubbleConfig" to "0"),
        "source" to SOURCE, "userId" to userId, "fromAct" to fromAct, "version" to ForestApiVersion.value,
    ))

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, requestArray(payload))

    private fun requestArray(payload: JsonObject): String = jsonArray(payload).toString()
    private fun path(vararg values: String) = jsonArray(*values.map(::JsonPrimitive).toTypedArray())
    private fun jsonStringArray(values: List<String?>) = buildJsonArray {
        values.forEach { add(it?.let(::JsonPrimitive) ?: JsonNull) }
    }
    private data class CachedFriendHome(val createdAt: Long, val response: String)

    private const val DEFAULT_FRIEND_FROM_ACT = "TAKE_LOOK_FRIEND"
    private const val FRIEND_HOME_CACHE_TTL_MS = 15_000L
    private const val MAX_FRIEND_HOME_CACHE_ENTRIES = 512
    private val friendHomeCache = ConcurrentHashMap<String, CachedFriendHome>()
    private val friendQueryLocks = ConcurrentHashMap<String, Any>()
    private const val SOURCE = "chInfo_ch_appcenter__chsub_9patch"
}
