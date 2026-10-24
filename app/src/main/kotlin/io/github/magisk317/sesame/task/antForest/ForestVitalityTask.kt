package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.TaskBlacklist
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonElement
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/**
 * Handles the forest vitality task list exposed by listTaskopengreen.
 *
 * This list contains both ordinary RPC-backed tasks and tasks whose progress
 * is owned by another app or a multi-day activity. The server response is the
 * authority: only a successful finishTask response advances a task.
 */
object ForestVitalityTask {
    internal const val SCENE_CODE = "ANTFOREST_VITALITY_TASK"

    private const val TAG = "ForestVitalityTask"
    private const val LEGACY_SOURCE = "task_entry"
    private const val MAX_ACTIONS = 40
    private const val STATE_DELAY_MS = 1_000L

    private data class TaskListRequest(
        val source: String,
        val businessSource: String,
    )

    // These are the two request shapes emitted by the current Alipay task
    // pages. The legacy request is used only when both page-shaped calls fail.
    private val PAGE_REQUESTS = listOf(
        TaskListRequest(
            source = "afEntry",
            businessSource = "ANTFOREST-vitality_store_task_list",
        ),
        TaskListRequest(
            source = "chInfo_ch_appcenter__chsub_9patch",
            businessSource = "ANTFOREST-home_task_list",
        ),
    )

    /**
     * Task types whose generic finishTask flow was observed to transition
     * TODO -> FINISHED and then accept receiveTaskAward in live RPC probes.
     * New task types stay out of this set until the same two-step evidence is
     * captured; this avoids repeatedly sending known-unsupported requests.
     */
    private val VERIFIED_FINISH_TASK_TYPES = setOf(
        // Live RPC verified: finishTask + receiveTaskAward succeeded.
        "BWXRK_LLRW_LINSHI",
        "CCL_202510",
        "DL_AIMOYU_202607",
        "DL_SCzhibo_202604",
        "DL_MYSLSHIPINHAO_202607",
        "DL_ZHONGSHUGONGYILIN_202607",
        "FKSSJ_LLRW_LINSHI",
        "GYG_baidu_202506",
        "GYG_BAOXIANnew_202605",
        "GYG_BAOXIANyiliao_202606",
        "GYG_diantao",
        "GYG_haoyangmao_2025",
        "GYG_huiyuan10g_1_202511",   // 2026-08-19 probe: finishTask→FINISHED
        "GYG_huiyuan2_202509",
        "GYG_jingxishiji_202602",
        "GYG_jingjingyoux_202603",
        "GYG_MAYITOUJIAO_2025",
        "GYG_mtsp_202505",
        "GYG_pailitao_202603",
        "GYG_shangou_20250621",
        "GYG_shenqiyutang_202510",
        "GYG_taobaoshangou_202509",
        "GYG-TAOCAICAI",
        "GYG_XIANYU_2023",
        "LHS_LLRW_LINSHI",
        "LMCT_LLRW_LINSHI",
        "NONGCHANG_20230818",
        "ONE_CLICK_WATERING_V1",
        "QYJZFM_LLRW_LINSHI",
        "qutoutiao",
        "SGBHSD_LLRW_linshi",
        "SGZHD_LLRW_LINSHI",        // 2026-08-19 probe: finishTask→FINISHED
        "SYH_zupin_202503",
        "SYH_GAME_SDPPX_10g",
        "SYH_GAME_ZGWHX_10g",
        "TEST_LEAF_CONVERT_TASK",
        "YUNDONG-GUSHU_2023",
    )

    internal enum class Action {
        FINISH,
        RECEIVE,
        SKIP,
    }

    internal data class TaskInfo(
        val taskType: String,
        val status: String,
        val title: String,
        val progress: Int,
        val required: Int,
        val parentTaskType: String? = null,
        val taskTag: String = "",
        val jumpUrl: String = "",
    )

    suspend fun process() {
        val attempted = mutableSetOf<String>()
        var actionCount = 0

        try {
            while (actionCount < MAX_ACTIONS) {
                val tasks = loadTasks() ?: return
                if (tasks.isEmpty()) {
                    StructuredLog.record("森林活力任务列表为空")
                    return
                }

                StructuredLog.record("森林活力任务扫描, 共 " + tasks.size + " 项")
                var changed = false

                for (task in tasks) {
                    val blacklisted = TaskBlacklist.isTaskInBlacklist(task.taskType) ||
                        TaskBlacklist.isTaskInBlacklist(task.title)
                    when (actionFor(task.taskType, task.status, blacklisted)) {
                        Action.FINISH -> {
                            if (!attempted.add("finish:" + task.taskType)) continue

                            StructuredLog.record(
                                "后台完成森林活力任务: " + task.title + " [" + task.taskType + "]"
                            )
                            val result = responseOf(
                                ForestTaskRpc.finish(SCENE_CODE, task.taskType)
                            ) ?: continue
                            actionCount++
                            if (isSuccessful(result)) {
                                StructuredLog.forest("森林🌿 活力任务完成[" + task.title + "]")
                                changed = true
                                // A successful finish can publish a new task immediately.
                                // Stop this scan so the next iteration observes the fresh list.
                                break
                            } else if (isUnsupported(result)) {
                                StructuredLog.record(
                                    "森林🌿 活力任务不支持后台完成: " +
                                        task.title + " [" + task.taskType + "]"
                                )
                            } else {
                                StructuredLog.error(
                                    TAG,
                                    "森林活力任务完成失败: " + task.title + " [" +
                                        result.optString("code") + " " +
                                        result.optString("desc") + "]",
                                )
                            }
                        }

                        Action.RECEIVE -> {
                            if (!attempted.add("receive:" + task.taskType)) continue

                            StructuredLog.record("森林🌿 领取活力任务奖励: " + task.title)
                            val result = responseOf(
                                ForestTaskRpc.receiveAward(SCENE_CODE, task.taskType)
                            ) ?: continue
                            actionCount++
                            if (isSuccessful(result)) {
                                StructuredLog.forest("森林🌿 活力奖励[" + task.title + "]")
                                changed = true
                                break
                            } else {
                                StructuredLog.error(
                                    TAG,
                                    "森林活力奖励领取失败: " + task.title + " [" +
                                        result.optString("code") + " " +
                                        result.optString("desc") + "]",
                                )
                            }
                        }

                        Action.SKIP -> {
                            if (!blacklisted && task.status == TaskStatus.TODO.name &&
                                !isVerifiedFinishTask(task.taskType)
                            ) {
                                val taskPath = task.parentTaskType?.let {
                                    "$it -> ${task.taskType}"
                                } ?: task.taskType
                                StructuredLog.record(
                                    "森林活力任务协议未验证, 跳过后台完成: " +
                                        task.title + " [" + taskPath + "]"
                                )
                            }
                        }
                    }
                }

                if (!changed) {
                    StructuredLog.record("森林活力🌿 本轮无状态变更")
                    return
                }
                if (actionCount >= MAX_ACTIONS) {
                    StructuredLog.record("森林活力任务达到本次动作上限(" + MAX_ACTIONS + "), 留待下次执行")
                    return
                }
                delay(STATE_DELAY_MS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "处理森林活力任务异常", t)
        }
    }

    /**
     * Re-check only FINISHED items. This remains independent from the normal
     * task flow and therefore is not blocked by only-once-daily filtering.
     */
    suspend fun claimPendingAwards() {
        try {
            val tasks = loadTasks() ?: return
            tasks.asSequence()
                .filter { it.status == TaskStatus.FINISHED.name }
                .take(MAX_ACTIONS)
                .forEach { task ->
                    val result = responseOf(
                        ForestTaskRpc.receiveAward(SCENE_CODE, task.taskType)
                    ) ?: return@forEach
                    if (isSuccessful(result)) {
                        StructuredLog.forest("森林活力奖励[${task.title}]")
                    } else {
                        StructuredLog.error(
                            TAG,
                            "森林活力奖励轮询失败: ${task.title} [${result.optString("code")} ${result.optString("desc")}]",
                        )
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "森林活力领奖轮询异常", t)
        }
    }

    internal fun actionFor(
        taskType: String,
        status: String,
        blacklisted: Boolean = false,
    ): Action = when {
        blacklisted -> Action.SKIP
        status == TaskStatus.TODO.name && isVerifiedFinishTask(taskType) -> Action.FINISH
        status == TaskStatus.FINISHED.name -> Action.RECEIVE
        else -> Action.SKIP
    }

    internal fun isVerifiedFinishTask(taskType: String): Boolean =
        taskType in VERIFIED_FINISH_TASK_TYPES

    internal fun parseTasks(response: JsonObject): List<TaskInfo> {
        val taskList = response.optJSONArray("taskInfoList") ?: return emptyList()
        val result = ArrayList<TaskInfo>()
        val seen = HashSet<String>()
        for (index in 0 until taskList.length()) {
            appendTask(taskList.optJSONObject(index), null, result, seen)
        }
        return result
    }

    internal fun parseTask(task: JsonObject?, parentTaskType: String? = null): TaskInfo? {
        val baseInfo = task?.optJSONObject("taskBaseInfo") ?: return null
        val taskType = baseInfo.optString("taskType")
        val status = baseInfo.optString("taskStatus")
        if (taskType.isBlank() || status.isBlank()) return null

        val bizInfo = runCatching {
            baseInfo.optString("bizInfo").toJsonObject()
        }.getOrNull()
        val title = bizInfo
            ?.optString("taskTitle")
            ?.ifBlank { bizInfo.optString("title") }
            ?.ifBlank { bizInfo.optString("taskTag") }
            ?.ifBlank { taskType }
            ?: taskType

        return TaskInfo(
            taskType = taskType,
            status = status,
            title = title,
            progress = baseInfo.optInt("taskProgress", 0),
            required = baseInfo.optInt("taskRequire", 0),
            parentTaskType = parentTaskType,
            taskTag = bizInfo?.optString("taskTag").orEmpty(),
            jumpUrl = bizInfo?.optString("taskJumpUrl").orEmpty(),
        )
    }

    private fun appendTask(
        task: JsonObject?,
        parentTaskType: String?,
        result: MutableList<TaskInfo>,
        seen: MutableSet<String>,
    ) {
        val parsed = parseTask(task, parentTaskType)
        val childParentTaskType = parsed?.taskType ?: parentTaskType
        if (parsed != null) {
            val key = parentTaskType?.let { "$it/${parsed.taskType}" } ?: parsed.taskType
            if (seen.add(key)) {
                result += parsed
            }
        }

        val children = task?.optJSONArray("childTaskTypeList") ?: return
        for (index in 0 until children.length()) {
            appendTask(children.optJSONObject(index), childParentTaskType, result, seen)
        }
    }

    private fun loadTasks(): List<TaskInfo>? {
        val tasksByType = LinkedHashMap<String, TaskInfo>()
        var successfulPageRequest = false

        for (request in PAGE_REQUESTS) {
            val response = responseOf(
                                ForestDrawRpc.list(
                    SCENE_CODE,
                    request.source,
                    request.businessSource,
                )
            ) ?: continue
            if (!ResChecker.checkRes(TAG, response)) {
                StructuredLog.record(
                    "森林活力任务列表查询失败: " +
                        response.optString("desc", response.optString("resultDesc"))
                )
                continue
            }
            successfulPageRequest = true
            for (task in parseTasks(response)) {
                tasksByType.putIfAbsent(task.taskType, task)
            }
        }

        if (successfulPageRequest) return tasksByType.values.toList()

        // Older Alipay builds do not understand businessSource. Keep the old
        // request as a compatibility fallback, never as a third normal scan.
        val legacyResponse = responseOf(
                ForestDrawRpc.list(SCENE_CODE, LEGACY_SOURCE)
        ) ?: return null
        if (!ResChecker.checkRes(TAG, legacyResponse)) {
            StructuredLog.record(
                "森林活力任务列表查询失败: " +
                    legacyResponse.optString("desc", legacyResponse.optString("resultDesc"))
            )
            return null
        }
        return parseTasks(legacyResponse)
    }

    internal fun isSuccessful(response: JsonObject): Boolean =
        response.optString("code") == "100000000" ||
            (response.optBoolean("success", false) &&
                response.optString("resultCode") in setOf("SUCCESS", "100000000"))

    internal fun isUnsupported(response: JsonObject): Boolean =
        response.optString("code") == "400000040" ||
            response.optString("resultCode") == "400000040"

    private fun responseOf(raw: String): JsonObject? =
        runCatching { raw.toJsonElement() as? JsonObject }.getOrNull()
}
