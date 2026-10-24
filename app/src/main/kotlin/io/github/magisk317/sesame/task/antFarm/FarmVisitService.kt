package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Owns the daily friend-visit and grain-gifting workflow. */
internal class FarmVisitService(
    private val friends: () -> Map<String?, Int?>,
    private val foodStock: () -> Int,
    private val setFoodStock: (Int) -> Unit,
) {
    suspend fun run() {
        try {
            val currentUid = UserMap.currentUid
            for ((userId, configuredCount) in friends()) {
                val target = userId ?: continue
                val count = configuredCount ?: continue
                if (target == currentUid || count <= 0) continue
                val visitCount = count.coerceAtMost(3)
                if (Status.canVisitFriendToday(target, visitCount)) {
                    val remaining = visitFriend(target, visitCount)
                    if (remaining > 0) Status.visitFriendToday(target, remaining)
                }
            }
        } catch (error: CancellationException) {
            StructuredLog.record(TAG, "visit 协程被取消")
            throw error
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "visit err:", error)
        }
    }

    private suspend fun visitFriend(userId: String, count: Int): Int {
        var visited = 0
        try {
            var response = FarmCoreRpc.enterFarm(userId, userId).toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return 0
            val farm = response.getJSONObject("farmVO")
            setFoodStock(farm.getInt("foodStock"))
            val subFarm = farm.getJSONObject("subFarmVO")
            if (subFarm.optBoolean("visitedToday", true)) return 3
            val farmId = subFarm.getString("farmId")
            repeat(count) {
                if (foodStock() < 10) return visited
                response = FarmCoreRpc.visitFriend(farmId).toJsonObject()
                if (!ResChecker.checkRes(TAG, response)) return@repeat
                setFoodStock(response.getInt("foodStock"))
                StructuredLog.farm(
                    "赠送麦子🌾[${UserMap.getMaskName(userId)}]#${response.getInt("giveFoodNum")}g",
                )
                visited++
                if (response.optBoolean("isReachLimit")) {
                    StructuredLog.record(TAG, "今日给[${UserMap.getMaskName(userId)}]送麦子已达上限")
                    visited = 3
                    return@repeat
                }
                delay(800L)
            }
        } catch (error: CancellationException) {
            StructuredLog.record(TAG, "visitFriend 协程被取消")
            throw error
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "visitFriend err:", error)
        }
        return visited
    }

    private companion object {
        const val TAG = "FarmVisitService"
    }
}
