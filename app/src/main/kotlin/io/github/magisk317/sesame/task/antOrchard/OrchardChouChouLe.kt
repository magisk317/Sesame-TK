package io.github.magisk317.sesame.task.antOrchard

import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

/**
 * 芭芭农场抽抽乐（阿肥寻宝记·花园世界专场）。
 *
 * 自动化流程：
 * 1. 尝试通过 antiep.finishTask 完成 TODO 任务（逛花园/金豆夺宝可直接完成，
 *    居民订单/充值返回 400000040 但可能隐性完成）
 * 2. 领取所有 FINISHED 任务的奖励（两种接口互补覆盖）
 * 3. 抽掉全部可用次数
 *
 * 不可自动完成的任务（ZH50/充值2等）标记为跳过，不重复尝试。
 */
internal object OrchardChouChouLe {
    private const val TAG = "OrchardChouChouLe"
    private const val MAX_RECEIVE_ROUNDS = 8
    private const val MAX_DRAWS = 40

    /** 已知不可通过 RPC 完成的任务类型，避免重复尝试。 */
    private val KNOWN_UNSKIPPABLE = setOf(
        "CHOUCHOULE_GAME_WDHYSJ_ZH50",
        "CHOUCHOULE_GAME_WDHYSJ_CHARGE2",
    )

    fun run() {
        try {
            val enter = AntOrchardRpcCall.enterOrchardDraw().toJsonObject()
            if (!ResChecker.checkRes(TAG, enter)) return
            val activityId = enter.optJSONObject("drawActivity")?.optString("activityId").orEmpty()
            var balance = enter.optJSONObject("drawAsset")?.optInt("blance") ?: 0

            attemptFinishTasks()
            balance = receiveAllAwards(balance)

            if (balance <= 0) {
                StructuredLog.record(TAG, "抽抽乐 本轮无可抽次数")
                return
            }

            drawAll(activityId, balance)
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "抽抽乐 err", t)
        }
    }

    /**
     * 尝试完成所有 TODO 任务。
     * antiep.finishTask 对部分任务直接返回成功，对另一些返回 400000040 但仍可能
     * 在服务端标记为 FINISHED（由 receiveAllAwards 验证）。
     */
    private fun attemptFinishTasks() {
        val listResponse = AntOrchardRpcCall.listOrchardDrawTask().toJsonObject()
        if (!ResChecker.checkRes(TAG, listResponse)) return
        val tasks = listResponse.optJSONArray("taskInfoList") ?: return

        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            val status = task.optString("taskStatus")
            if (status != "TODO") continue
            val taskType = task.optString("taskType")
            if (taskType.isBlank()) continue
            if (taskType in KNOWN_UNSKIPPABLE) continue

            val result = AntOrchardRpcCall.attemptFinishDrawTask(taskType).toJsonObject()
            val code = result.optString("code")
            when (code) {
                "100000000" -> StructuredLog.farm("抽抽乐任务✓[${taskTitle(task)}] 已完成")
                "400000040" -> {} // 可能隐性完成，后续 receiveTaskAward 验证
                "B000000008" -> {} // 无状态转换处理（已完成）
                else -> {} // 其他错误不记录，避免刷 log
            }
            GlobalThreadPools.sleepCompat(300L)
        }
    }

    /**
     * 领取所有可领奖励，返回更新后的抽奖次数。
     * 同时使用两种接口：receiveOrchardDrawTaskAward（antorchard 场景）和
     * receiveDrawTaskAwardGeneric（通用场景），覆盖隐性完成的任务。
     */
    private fun receiveAllAwards(balance: Int): Int {
        var current = balance
        var rounds = 0
        while (rounds++ < MAX_RECEIVE_ROUNDS) {
            var changed = false
            val listResponse = AntOrchardRpcCall.listOrchardDrawTask().toJsonObject()
            if (!ResChecker.checkRes(TAG, listResponse)) return current
            val tasks = listResponse.optJSONArray("taskInfoList") ?: return current

            for (index in 0 until tasks.length()) {
                val task = tasks.optJSONObject(index) ?: continue
                val status = task.optString("taskStatus")
                if (status != "FINISHED") continue
                val taskType = task.optString("taskType")
                if (taskType.isBlank()) continue

                // 先用 antorchard 专用接口
                var receive = AntOrchardRpcCall.receiveOrchardDrawTaskAward(taskType).toJsonObject()
                // 如果专用接口失败，用通用接口兜底（覆盖隐性完成的任务）
                if (!ResChecker.checkRes(TAG, receive)) {
                    receive = AntOrchardRpcCall.receiveDrawTaskAwardGeneric(taskType).toJsonObject()
                }
                if (ResChecker.checkRes(TAG, receive)) {
                    val inc = receive.optInt("incAwardCount", 1)
                    current += inc
                    StructuredLog.farm("抽抽乐任务🧾[${taskTitle(task)}] 奖励已领取 (+${inc}次机会)")
                    changed = true
                    GlobalThreadPools.sleepCompat(500L)
                }
            }
            if (!changed) break
        }
        return current
    }

    /** 抽掉全部可用次数。 */
    private fun drawAll(activityId: String, initialBalance: Int) {
        var drawn = 0
        var left = initialBalance
        while (left > 0 && drawn < MAX_DRAWS) {
            val draw = AntOrchardRpcCall.drawOrchard(activityId).toJsonObject()
            if (!ResChecker.checkRes(TAG, draw)) break
            drawn++
            left--
            val prize = draw.optJSONObject("drawPrizeVO")
            val name = prize?.optString("prizeName")?.ifBlank {
                prize.optString("title", "未知奖品")
            } ?: "未知奖品"
            val count = prize?.optString("decideAwardCount").orEmpty()
            StructuredLog.farm("抽抽乐🎁[领取: $name]#$count")
            GlobalThreadPools.sleepCompat(1200L)
        }
        if (drawn > 0) {
            StructuredLog.farm("抽抽乐🎁 共抽奖 $drawn 次")
        }
    }

    private fun taskTitle(task: JsonObject): String {
        val base = task.optJSONObject("taskBaseInfo")
        val bizInfo = base?.optString("bizInfo").orEmpty().toJsonObject()
        return bizInfo.optString("title", task.optString("taskType", "未知任务"))
    }
}
