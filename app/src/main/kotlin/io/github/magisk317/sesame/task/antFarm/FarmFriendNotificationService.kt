package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getDouble
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonObject

/** Scans ranking friends and claims the notification reward for active theft. */
internal class FarmFriendNotificationService(
    private val foodStock: () -> Int,
    private val foodStockLimit: () -> Int,
    private val notifyList: () -> Collection<String?>,
    private val notifyMode: () -> Int,
    private val notifyEnabled: () -> Boolean,
    private val ownerFarmId: () -> String?,
    private val onFoodStock: (amount: Int, refresh: Boolean) -> Unit,
) {
    internal fun notify() {
        if (foodStock() >= foodStockLimit()) return
        try {
            var hasNext: Boolean
            var pageStart = 0
            do {
                val raw = FarmCoreRpc.rankingList(pageStart)
                if (raw.isNullOrEmpty()) {
                    StructuredLog.record(TAG, "notifyFriend.rankingList: 收到空响应，终止通知")
                    break
                }
                val result = raw.toJsonObject()
                if (!ResChecker.checkRes(TAG, result)) {
                    StructuredLog.record(TAG, result.toString())
                    break
                }
                hasNext = result.getBoolean("hasNext")
                val ranking = result.getJSONArray("rankingList")
                pageStart += ranking.size
                for (index in ranking.indices) {
                    val friend = ranking.getJSONObject(index)
                    val userId = friend.getString("userId")
                    if (!shouldNotify(userId)) continue
                    val starve = friend.has("actionType") && friend.getString("actionType") == "starve_action"
                    if (!friend.getBoolean("stealingAnimal") || starve) continue

                    val homeRaw = FarmCoreRpc.enterFarm(userId, userId)
                    if (homeRaw.isNullOrEmpty()) continue
                    val home = homeRaw.toJsonObject()
                    if (!ResChecker.checkRes(TAG, home)) continue
                    var notified = notifyEnabled()
                    val userName = UserMap.getMaskName(userId)
                    for (target in FarmStealingAnimalStateService().parse(home, ownerFarmId())) {
                        if (notified) break
                        notified = notifyAnimal(target.status, target.farmId, target.animalId, userName)
                    }
                }
            } while (hasNext)
            StructuredLog.record(TAG, "饲料剩余[${foodStock()}g]")
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "notifyFriend err:", error)
        }
    }

    private fun shouldNotify(userId: String): Boolean {
        var selected = notifyList().contains(userId)
        if (notifyMode() == AntFarm.NotifyFriendType.DONT_NOTIFY) selected = !selected
        return selected && userId != UserMap.currentUid
    }

    private fun notifyAnimal(
        status: JsonObject,
        friendFarmId: String?,
        animalId: String?,
        user: String?,
    ): Boolean {
        return try {
            if (status.getString("animalInteractStatus") != AntFarm.AnimalInteractStatus.STEALING.name ||
                status.getString("animalFeedStatus") != AntFarm.AnimalFeedStatus.EATING.name
            ) return false
            val result = FarmCoreRpc.notifyFriend(animalId, friendFarmId).toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) return false
            val rewardCount = result.getDouble("rewardCount")
            val refresh = result.getBoolean("refreshFoodStock")
            onFoodStock(
                if (refresh) result.getDouble("finalFoodStock").toInt() else rewardCount.toInt(),
                refresh,
            )
            StructuredLog.farm("通知好友📧[$user]被偷吃#奖励${rewardCount}g")
            true
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "notifyFriend err:", error)
            false
        }
    }

    private companion object {
        const val TAG = "FarmFriendNotificationService"
    }
}
