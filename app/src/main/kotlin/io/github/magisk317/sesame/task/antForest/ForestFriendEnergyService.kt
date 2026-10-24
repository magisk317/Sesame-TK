package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonObject

/** Coordinates one friend/PK energy pass and its optional friend-side effects. */
class ForestFriendEnergyService(
    private val currentUserId: () -> String?,
    private val errorWaiting: () -> Boolean,
    private val isEmptyForest: (String) -> Boolean,
    private val collectEnabled: () -> Boolean,
    private val pkCollectEnabled: () -> Boolean,
    private val excluded: (String) -> Boolean,
    private val shouldProtect: (JsonObject) -> Boolean,
    private val shouldCollectGift: (JsonObject) -> Boolean,
    private val isProtected: (String) -> Boolean,
    private val queryHome: (String?, String?) -> JsonObject?,
    private val collectEnergy: (String?, JsonObject?, String?) -> JsonObject?,
    private val protect: (JsonObject) -> Unit,
    private val collectGift: (JsonObject) -> Unit,
) {
    fun process(obj: JsonObject, flag: String?) {
        if (errorWaiting()) return
        val userId = obj.getString("userId")
        if (userId == currentUserId()) return
        if (ForestUtil.isUserInFrequencyCooldown(userId)) return

        var userName = obj.optString("displayName", UserMap.getMaskName(userId).orEmpty())
        if (isEmptyForest(userId)) return
        val isPk = flag == "pk"
        if (isPk) userName = "PK榜好友|$userName"

        if (isPk) {
            if (!collectEnabled() || !pkCollectEnabled()) {
                StructuredLog.record(TAG, "PK好友: [$userName$userId], 不满足收取条件，跳过")
                return
            }
            StructuredLog.record(TAG, "正在查询PK好友 [$userName$userId] 的主页...")
            collectEnergy(userId, queryHome(userId, "PKContest"), "pk")
            return
        }

        val needCollect = collectEnabled() && !excluded(userId)
        val needProtect = shouldProtect(obj) && Status.canProtectBubbleToday(currentUserId())
        val needGift = shouldCollectGift(obj)
        if (!needCollect && !needProtect && !needGift) return

        var home: JsonObject? = null
        if (needCollect) {
            StructuredLog.record(TAG, "正在查询好友 [$userName$userId] 的主页...")
            home = collectEnergy(userId, queryHome(userId, null), "friend")
        }
        if (needProtect && isProtected(userId)) {
            if (home == null) home = queryHome(userId, null)
            home?.let(protect)
        }
        if (needGift) {
            if (home == null) home = queryHome(userId, null)
            home?.let(collectGift)
        }
    }

    private companion object {
        const val TAG = "ForestFriendEnergyService"
    }
}
