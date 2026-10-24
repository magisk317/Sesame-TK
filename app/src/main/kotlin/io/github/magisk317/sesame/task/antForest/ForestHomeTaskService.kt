package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject

/** Claims rewards exposed by the Forest home task panel. */
object ForestHomeTaskService {
    private const val TAG = "ForestHomeTaskService"
    private const val TASK_LIST_PATH = "taskTriggerPlayInfo"

    suspend fun claimPendingAwards() {
        val root = runCatching { ForestHomeTaskRpc.queryOptionalPlay().toJsonObject() }
            .onFailure { StructuredLog.printStackTrace(TAG, "森林乐园🎁 任务列表查询失败", it) }
            .getOrNull() ?: return
        if (!ResChecker.checkRes("森林乐园🎁 任务列表查询失败:", root)) return

        val taskList = root.optJSONObject(TASK_LIST_PATH)?.optJSONArray("taskList") ?: run {
            StructuredLog.record(TAG, "森林乐园🎁 任务列表为空")
            return
        }
        StructuredLog.record(TAG, "森林乐园🎁 任务列表: ${taskList.length()} 项")
        val unsupportedTodoTypes = linkedSetOf<String>()
        var claimed = 0
        for (index in 0 until taskList.length()) {
            val task = taskList.optJSONObject(index) ?: continue
            if (task.optString("taskStatus") == TaskStatus.TODO.name) {
                unsupportedTodoTypes += task.optString("taskType").ifBlank { "<unknown>" }
                continue
            }
            if (task.optString("taskStatus") != TaskStatus.FINISHED.name) continue
            val received = task.optInt("alreadyReceiveAwardCount", 0)
            val award = task.optInt("awardCount", 0)
            if (award > 0 && received >= award) continue

            val title = task.optJSONObject("bizInfo")?.optString("title", task.optString("taskType"))
                ?: task.optString("taskType")
            StructuredLog.record(TAG, "森林乐园🎁 准备领奖: $title (${task.optString("taskType")})")
            val response = runCatching {
                ForestHomeTaskRpc.receiveTaskAward(task).toJsonObject()
            }.onFailure {
                StructuredLog.printStackTrace(TAG, "森林乐园🎁 领奖异常: $title", it)
            }.getOrNull() ?: continue
            if (ResChecker.checkRes("森林乐园🎁 领奖失败: $title", response)) {
                claimed++
                StructuredLog.forest("森林乐园🎁 奖励领取成功: $title +${response.optInt("incAwardCount", award)}")
            }
        }
        if (unsupportedTodoTypes.isNotEmpty()) {
            StructuredLog.record(
                TAG,
                "森林乐园🎁 TODO 暂无已验证完成 RPC，保持跳过: ${unsupportedTodoTypes.joinToString()}",
            )
        }
        if (claimed > 0) StructuredLog.record(TAG, "森林乐园🎁 本轮领取奖励 $claimed 项")
    }
}
