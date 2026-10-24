package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import kotlinx.coroutines.delay

/** Applies the feeding-time policy for repeated accelerate-card use. */
internal class FarmAccelerateService(
    private val state: () -> State,
    private val canUse: () -> Boolean,
    private val markLimit: () -> Unit,
    private val useTool: () -> Boolean,
    private val recordUse: () -> Unit,
    private val usedCount: () -> Int,
    private val sync: () -> Unit,
    private val feed: () -> Boolean,
    private val receiveAwards: suspend () -> Unit,
    private val continueEnabled: () -> Boolean,
    private val maxEmotionEnabled: () -> Boolean,
    private val remainingTimeMinutes: () -> Int,
    private val gameFinished: () -> Boolean,
    private val foodStock: () -> Int,
    private val foodStockLimit: () -> Int,
    private val gameRewardMax: () -> Int,
) {
    data class State(
        val countdownSeconds: Double,
        val totalConsumeSpeed: Double,
        val ownFoodConsumePerHour: Double,
        val finalScore: Double,
        val foodStock: Int,
        val foodStockLimit: Int,
        val ownerFeedStatus: String?,
        val ownerBuff: String?,
    )

    suspend fun run(): Boolean {
        if (!canUse()) return false
        if (state().ownerBuff == ACCELERATING && !continueEnabled()) return false

        var current = state()
        if (current.countdownSeconds <= 0 || current.totalConsumeSpeed <= 0) return false
        if (current.ownFoodConsumePerHour <= 0) return false

        var thresholdMinutes = remainingTimeMinutes()
        if (thresholdMinutes !in 1..<60) {
            thresholdMinutes = 60
            StructuredLog.farm("连续使用加速卡加速的剩余时间设置有误，正确值1-59,现不加速剩余时间为1个小时内的饲料")
        }
        var remainingFood = current.countdownSeconds * current.totalConsumeSpeed
        var used = false
        var exitReason = CONDITION_NOT_MET

        while (remainingFood >= thresholdMinutes / 60.0 * current.ownFoodConsumePerHour) {
            if (!canUse()) {
                StructuredLog.record(TAG, "加速卡内部⏩已达到本地使用上限(8次)，停止使用")
                markLimit()
                exitReason = REACHED_LIMIT
                break
            }
            current = state()
            if (maxEmotionEnabled() && current.finalScore != 100.0) {
                exitReason = EMOTION_NOT_MAX
                break
            }
            if (!useTool()) {
                StructuredLog.record(TAG, "加速卡内部⏩useFarmTool 返回失败，终止循环")
                exitReason = TOOL_USE_FAILED
                break
            }

            remainingFood -= current.ownFoodConsumePerHour
            used = true
            recordUse()
            val timeLeft = remainingFood / current.totalConsumeSpeed
            if (timeLeft >= 0.0) {
                StructuredLog.farm("使用了1张加速卡⏩ 预估剩余时间: ${TimeFormatter.formatDuration((timeLeft * 1000).toLong())}")
                StructuredLog.farm("今日已使用${usedCount()}张加速卡")
                delay(1000)
            } else {
                StructuredLog.farm("使用加速卡后小鸡饲料吃完，等待8秒后尝试喂鸡")
                delay(8000)
                sync()
                current = state()
                if (current.ownerFeedStatus == HUNGRY && feed()) {
                    sync()
                    remainingFood = 180.0
                    StructuredLog.farm("加速卡后投喂小鸡成功！")
                    if (!gameFinished() && foodStock() >= foodStockLimit() - gameRewardMax()) {
                        StructuredLog.farm(
                            "今天游戏改分还没有完成，预留${gameRewardMax()}g的饲料剩余空间，目前饲料${foodStock()}g，差${foodStockLimit() - foodStock()}g满饲料",
                        )
                    } else {
                        StructuredLog.farm("加速后已喂食，领取饲料奖励")
                        receiveAwards()
                    }
                } else {
                    current = state()
                    remainingFood = current.countdownSeconds * current.totalConsumeSpeed
                    StructuredLog.farm("使用加速卡使饲料吃完，投喂小鸡失败！")
                }
            }
            if (!continueEnabled()) {
                exitReason = SINGLE_USE_MODE
                break
            }
        }

        when (exitReason) {
            CONDITION_NOT_MET -> StructuredLog.record("剩余可加速的时间少于设置的${thresholdMinutes}分钟，将在下次喂食后再次使用加速卡")
            SINGLE_USE_MODE -> StructuredLog.record("开启了“仅在满状态使用加速卡")
            EMOTION_NOT_MAX -> StructuredLog.record("开启了“仅心情满值时加速”，且当前心情不为 100")
        }
        StructuredLog.record(TAG, "加速卡内部⏩最终 isUseAccelerateTool=$used")
        return used
    }

    private companion object {
        const val TAG = "FarmAccelerateService"
        const val ACCELERATING = "ACCELERATING"
        const val HUNGRY = "HUNGRY"
        const val CONDITION_NOT_MET = "CONDITION_NOT_MET"
        const val REACHED_LIMIT = "REACHED_LIMIT"
        const val EMOTION_NOT_MAX = "EMOTION_NOT_MAX"
        const val TOOL_USE_FAILED = "TOOL_USE_FAILED"
        const val SINGLE_USE_MODE = "SINGLE_USE_MODE"
    }
}
