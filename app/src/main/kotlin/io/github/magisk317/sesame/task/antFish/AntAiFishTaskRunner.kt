package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.task.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Executes AI-fish tasks without launching an Alipay page. */
internal object AntAiFishTaskRunner {
    private const val TAG = "AntAiFishTaskRunner"
    private const val MAX_ROUNDS = 8
    private const val MAX_ACTIONS = 48
    private const val STATE_DELAY_MS = 500L

    suspend fun run() {
        var actionCount = 0
        var previousPlanSignature: String? = null
        try {
            runRescueTasks()
            repeat(MAX_ROUNDS) { round ->
                val response = AntAiFishRpcCall.taskList().toJsonObject()
                if (!ResChecker.checkRes(TAG, response)) {
                    StructuredLog.record(TAG, "AI 摸鱼任务列表请求失败: ${response.optString("desc", response.optString("resultDesc"))}")
                    return
                }

                val actions = AntAiFishTaskPlanner.plan(response)
                if (actions.isEmpty()) {
                    StructuredLog.record(TAG, "AI 摸鱼任务列表没有可执行的后台 RPC 动作")
                    return
                }
                val planSignature = AntAiFishTaskPlanner.signature(actions)
                if (planSignature == previousPlanSignature) {
                    StructuredLog.record(TAG, "AI 摸鱼任务连续两轮状态未变化，停止重复 RPC")
                    return
                }
                previousPlanSignature = planSignature
                var changed = false
                for (action in actions) {
                    if (actionCount >= MAX_ACTIONS) {
                        StructuredLog.record(TAG, "AI 摸鱼任务达到本次动作上限($MAX_ACTIONS)，留待下次轮训")
                        return
                    }
                    val result = when (action.type) {
                        AntAiFishTaskActionType.FINISH -> AntAiFishRpcCall.finishTask(action.taskType)
                        AntAiFishTaskActionType.RECEIVE_AWARD -> AntAiFishRpcCall.receiveTaskAward(action.taskType)
                    }.toJsonObject()
                    actionCount++
                    val actionName = when (action.type) {
                        AntAiFishTaskActionType.FINISH -> "FINISH"
                        AntAiFishTaskActionType.RECEIVE_AWARD -> "RECEIVE_AWARD"
                    }

                    if (ResChecker.checkRes(TAG, result)) {
                        changed = true
                        val label = when (action.type) {
                            AntAiFishTaskActionType.FINISH -> "AI 摸鱼任务后台完成"
                            AntAiFishTaskActionType.RECEIVE_AWARD -> "AI 摸鱼任务奖励领取"
                        }
                        StructuredLog.forest("AI摸鱼🐟 $label[${action.title}]# ${action.awardCount}次")
                    } else {
                        StructuredLog.record(
                            TAG,
                            "AI 摸鱼任务 RPC 失败 action=$actionName: ${action.title} [${action.taskType}] " +
                                result.optString("desc", result.optString("resultDesc")),
                        )
                    }
                }

                if (!changed || round == MAX_ROUNDS - 1) return
                delay(STATE_DELAY_MS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "AI 摸鱼任务执行异常", t)
        }
    }

    /** Rescue tasks grant the graffiti-removal chance directly from finishTask. */
    private suspend fun runRescueTasks() {
        val response = AntAiFishRpcCall.rescueTaskList().toJsonObject()
        if (!ResChecker.checkRes(TAG, response)) {
            StructuredLog.record(TAG, "AI 摸鱼去除涂鸦任务列表请求失败: ${response.optString("desc", response.optString("resultDesc"))}")
            return
        }
        val tasks = response.optJSONArray("taskInfoList") ?: return
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            val base = task.optJSONObject("taskBaseInfo") ?: continue
            if (base.optString("taskStatus") != TaskStatus.TODO.name) continue
            val taskType = base.optString("taskType")
            if (taskType.isBlank()) continue
            val title = base.optString("bizInfo").toJsonObject().optString("taskTitle").ifBlank { taskType }
            val result = AntAiFishRpcCall.finishRescueTask(taskType).toJsonObject()
            if (ResChecker.checkRes(TAG, result)) {
                StructuredLog.forest("AI摸鱼🐟 去除涂鸦完成[$title]")
            } else {
                StructuredLog.record(
                    TAG,
                    "AI 摸鱼去除涂鸦 RPC 失败: $title [$taskType] " +
                        result.optString("desc", result.optString("resultDesc")),
                )
            }
        }
    }

    /** Claim-only scan used by the global award poller. */
    suspend fun pollPendingAwards() {
        try {
            val response = AntAiFishRpcCall.taskList().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return
            AntAiFishTaskPlanner.plan(response)
                .asSequence()
                .filter { it.type == AntAiFishTaskActionType.RECEIVE_AWARD }
                .take(MAX_ACTIONS)
                .forEach { action ->
                    val result = AntAiFishRpcCall.receiveTaskAward(action.taskType).toJsonObject()
                    if (ResChecker.checkRes(TAG, result)) {
                        StructuredLog.forest("AI摸鱼🐟 任务奖励轮训[${action.title}]# ${action.awardCount}次")
                    } else {
                        StructuredLog.record(
                            TAG,
                            "AI 摸鱼任务 RPC 失败 action=RECEIVE_AWARD: ${action.title} [${action.taskType}] " +
                                result.optString("desc", result.optString("resultDesc")),
                        )
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "AI 摸鱼领奖轮询异常", t)
        }
    }
}
