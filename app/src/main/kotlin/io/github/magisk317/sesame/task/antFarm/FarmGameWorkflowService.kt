package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.StructuredLog

/** Decides when the farm game and ChouChouLe actions are allowed to run. */
internal class FarmGameWorkflowService(
    private val gameFinished: () -> Boolean,
    private val accelerateEnabled: () -> Boolean,
    private val accelerateLimitReached: () -> Boolean,
    private val syncFarmState: () -> Unit,
    private val insideGameTimeRange: () -> Boolean,
    private val ignoreAccelerateLimit: () -> Boolean,
    private val accelerateCardCount: () -> Int,
    private val usedAccelerateCardCount: () -> Int,
    private val foodStock: () -> Int,
    private val foodStockLimit: () -> Int,
    private val gameRewardMax: () -> Int,
    private val taskEnabled: () -> Boolean,
    private val taskFinished: () -> Boolean,
    private val receiveAwards: suspend () -> Unit,
    private val playGames: suspend () -> Unit,
    private val chouChouLeFinished: () -> Boolean,
    private val gameEnabledForChouChouLe: () -> Boolean,
    private val chouChouLeTimeReached: () -> Boolean,
    private val playChouChouLe: () -> Unit,
) {
    suspend fun runGames() {
        if (gameFinished()) {
            StructuredLog.record(TAG, "今日庄园游戏改分已完成")
            return
        }
        val timeMode = !accelerateEnabled() || ignoreAccelerateLimit()
        when {
            timeMode -> {
                if (insideGameTimeRange()) {
                    if (taskFinished()) receiveAwards()
                    playGames()
                } else {
                    StructuredLog.record(TAG, "当前处于按时游戏改分模式，未到设定时间，跳过")
                }
            }
            accelerateLimitReached() || accelerateCardCount() <= 0 -> {
                syncFarmState()
                val threshold = foodStockLimit() - gameRewardMax()
                if (foodStock() < threshold) receiveAwards()
                val satisfied = foodStock() >= threshold
                when {
                    satisfied || !taskEnabled() -> {
                        if (!satisfied) {
                            StructuredLog.record(
                                TAG,
                                "未开启饲料任务，虽然尝试领取了奖励，但饲料缺口仍超过${gameRewardMax()}g，直接执行游戏",
                            )
                        }
                        playGames()
                    }
                    taskFinished() -> StructuredLog.record(
                        TAG,
                        "已开启饲料任务且今日已完成，但领取奖励后缺口仍超过${gameRewardMax()}g，暂不执行游戏改分。请确认饲料奖励完成情况，可以关闭设置里的“做饲料任务”选项直接进行游戏改分",
                    )
                    else -> StructuredLog.record(
                        TAG,
                        "已开启饲料任务但尚未完成，现有饲料缺口超过${gameRewardMax()}g，等待任务完成后再执行",
                    )
                }
            }
            else -> StructuredLog.record(
                TAG,
                "加速卡有${accelerateCardCount()}张，已使用${usedAccelerateCardCount()}张，尚未达到今日使用上限，等待加速完成后再改分",
            )
        }
    }

    fun runChouChouLe() {
        if (chouChouLeFinished()) {
            StructuredLog.record(TAG, "今日抽抽乐已完成")
            return
        }
        val timeMode = !gameEnabledForChouChouLe() || ignoreAccelerateLimit()
        when {
            timeMode -> {
                if (chouChouLeTimeReached()) playChouChouLe()
                else StructuredLog.record(TAG, "当前处于按时抽抽乐模式，未到设定时间，跳过")
            }
            gameFinished() -> playChouChouLe()
            else -> StructuredLog.record(TAG, "游戏改分还没有完成，暂不执行抽抽乐")
        }
    }

    private companion object {
        const val TAG = "FarmGameWorkflowService"
    }
}
