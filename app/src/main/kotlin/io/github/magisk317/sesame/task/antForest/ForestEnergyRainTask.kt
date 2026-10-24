package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonElement
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/**
 * Handles the energy-rain task list independently from the game-report tasks.
 *
 * Only GAME_30s_SLLXX has a live-verified finishTask protocol. GAME_DONE_* tasks
 * use their individual game-report flow and must not be sent to this handler.
 */
object ForestEnergyRainTask {
    internal const val SCENE_CODE = "ANTFOREST_ENERGY_RAIN_TASK"
    internal const val VERIFIED_TASK_TYPE = "GAME_30s_SLLXX"

    private const val TAG = "ForestEnergyRainTask"
    private const val SOURCE = "ANTFOREST"
    private const val MAX_ROUNDS = 3
    private const val MAX_ACTIONS = 6
    private const val STATE_DELAY_MS = 1_000L

    internal enum class Action {
        FINISH,
        RECEIVE,
        SKIP,
    }

    internal data class TaskInfo(
        val taskType: String,
        val status: String,
        val title: String,
    )

    /**
     * Returns whether a verified task changed state during this invocation.
     */
    suspend fun process(): Boolean {
        val attempted = mutableSetOf<String>()
        var actionCount = 0
        var changedAny = false

        try {
            repeat(MAX_ROUNDS) { round ->
                val response = responseOf(
                    ForestDrawRpc.list(SCENE_CODE, SOURCE)
                ) ?: return changedAny
                if (!ResChecker.checkRes(TAG, response)) return changedAny

                val tasks = parseTasks(response)
                if (tasks.isEmpty()) return changedAny

                var changed = false
                for (task in tasks) {
                    if (actionCount >= MAX_ACTIONS) {
                        StructuredLog.record("能量雨任务达到本次动作上限($MAX_ACTIONS), 留待下次执行")
                        return changedAny
                    }

                    when (actionFor(task.taskType, task.status)) {
                        Action.FINISH -> {
                            if (!attempted.add("finish:${task.taskType}")) continue
                            val result = responseOf(
                                ForestTaskRpc.finish(SCENE_CODE, task.taskType)
                            ) ?: continue
                            actionCount++
                            if (isSuccessful(result)) {
                                StructuredLog.record("后台完成能量雨任务: ${task.title} [${task.taskType}]")
                                changed = true
                            } else if (isUnsupported(result)) {
                                StructuredLog.record("能量雨任务不支持后台完成: ${task.title} [${task.taskType}]")
                            } else {
                                StructuredLog.error(
                                    TAG,
                                    "能量雨任务完成失败: ${task.title} [${result.optString("code")} ${result.optString("desc")}]",
                                )
                            }
                        }

                        Action.RECEIVE -> {
                            if (!attempted.add("receive:${task.taskType}")) continue
                            val result = responseOf(
                                ForestTaskRpc.receiveAward(SCENE_CODE, task.taskType)
                            ) ?: continue
                            actionCount++
                            if (isSuccessful(result)) {
                                StructuredLog.forest("能量雨任务奖励[${task.title}]")
                                changed = true
                            } else {
                                StructuredLog.error(
                                    TAG,
                                    "能量雨任务奖励领取失败: ${task.title} [${result.optString("code")} ${result.optString("desc")}]",
                                )
                            }
                        }

                        Action.SKIP -> Unit
                    }
                }

                if (!changed) return changedAny
                changedAny = true
                if (round < MAX_ROUNDS - 1) delay(STATE_DELAY_MS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "处理能量雨任务异常", t)
        }
        return changedAny
    }

    /** Claim-only scan for the session-level award poller. */
    suspend fun claimPendingAwards() {
        try {
            val response = responseOf(
                ForestDrawRpc.list(SCENE_CODE, SOURCE)
            ) ?: return
            if (!ResChecker.checkRes(TAG, response)) return
            parseTasks(response)
                .asSequence()
                .filter { it.status == TaskStatus.FINISHED.name }
                .take(MAX_ACTIONS)
                .forEach { task ->
                    val result = responseOf(
                        ForestTaskRpc.receiveAward(SCENE_CODE, task.taskType)
                    ) ?: return@forEach
                    if (isSuccessful(result)) {
                        StructuredLog.forest("能量雨任务奖励[${task.title}]")
                    } else {
                        StructuredLog.error(
                            TAG,
                            "能量雨奖励轮询失败: ${task.title} [${result.optString("code")} ${result.optString("desc")}]",
                        )
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "能量雨领奖轮询异常", t)
        }
    }

    internal fun actionFor(taskType: String, status: String): Action = when {
        taskType != VERIFIED_TASK_TYPE -> Action.SKIP
        status == TaskStatus.TODO.name -> Action.FINISH
        status == TaskStatus.FINISHED.name -> Action.RECEIVE
        else -> Action.SKIP
    }

    internal fun parseTasks(response: JsonObject): List<TaskInfo> {
        val taskList = response.optJSONArray("taskInfoList") ?: return emptyList()
        return buildList {
            for (index in 0 until taskList.length()) {
                val task = taskList.optJSONObject(index) ?: continue
                val baseInfo = task.optJSONObject("taskBaseInfo") ?: continue
                val taskType = baseInfo.optString("taskType")
                val status = baseInfo.optString("taskStatus")
                if (taskType.isBlank() || status.isBlank()) continue

                val title = runCatching {
                    val bizInfo = baseInfo.optString("bizInfo").toJsonObject()
                    bizInfo.optString("taskTitle")
                        .ifBlank { bizInfo.optString("title") }
                        .ifBlank { taskType }
                }.getOrDefault(taskType)
                add(TaskInfo(taskType, status, title))
            }
        }
    }

    internal fun isSuccessful(response: JsonObject): Boolean =
        response.optString("code") == "100000000" ||
            (response.optBoolean("success", false) &&
                response.optString("resultCode") in setOf("SUCCESS", "100000000"))

    internal fun isUnsupported(response: JsonObject): Boolean =
        response.optString("code") == "400000040" ||
            response.optString("resultCode") == "400000040" ||
            response.optString("code") == "400000057" ||
            response.optString("resultCode") == "400000057"

    private fun responseOf(raw: String): JsonObject? =
        runCatching { raw.toJsonElement() as? JsonObject }.getOrNull()
}
