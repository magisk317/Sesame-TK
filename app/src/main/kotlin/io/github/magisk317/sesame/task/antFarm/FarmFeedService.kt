package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.task.antFarm.AntFarm.AnimalFeedStatus
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap

/** Owns the feed-animal RPC and its state/error guards. */
class FarmFeedService(
    private val foodStock: () -> Int,
    private val feedStatus: () -> String?,
    private val scheduleSendBack: (Int) -> Unit,
    private val sendBackEnabled: () -> Boolean,
    private val sendBackDelayMinutes: () -> Int,
    private val checkIntervalMinutes: () -> Int,
) {
    fun feed(farmId: String?): Boolean {
        try {
            if (feedStatus() == AnimalFeedStatus.SLEEPY.name) {
                StructuredLog.record(TAG, "投喂小鸡🥣[小鸡正在睡觉中，跳过投喂]")
                return false
            }
            if (feedStatus() == AnimalFeedStatus.EATING.name) {
                StructuredLog.record(TAG, "投喂小鸡🥣[小鸡正在吃饭中，跳过投喂]")
                return false
            }
            if (foodStock() < 180) {
                StructuredLog.record(TAG, "喂鸡饲料不足，停止本次投喂尝试")
                return false
            }

            val result = FarmCoreRpc.feedAnimal(farmId).toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) {
                val code = result.optString("resultCode", "")
                val memo = result.optString("memo", "")
                if (code == "311") {
                    StructuredLog.record(TAG, "投喂小鸡🥣[$memo]")
                } else {
                    StructuredLog.record(TAG, "投喂小鸡失败: $result")
                }
                return false
            }

            val remainingFood = result.optInt("foodStock", 0).coerceAtLeast(0)
            StructuredLog.farm("${UserMap.getCurrentMaskName()}投喂小鸡🥣[180g]#剩余饲料${remainingFood}g")
            val delay = sendBackDelayMinutes()
            when {
                delay in 10..checkIntervalMinutes() && sendBackEnabled() -> scheduleSendBack(delay)
                delay > checkIntervalMinutes() -> StructuredLog.record(
                    TAG,
                    "设置个合理的喂食后赶鸡时间，建议 30 分钟",
                )
            }
            return true
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "feedAnimal err:", error)
            return false
        }
    }

    private companion object {
        const val TAG = "FarmFeedService"
    }
}
