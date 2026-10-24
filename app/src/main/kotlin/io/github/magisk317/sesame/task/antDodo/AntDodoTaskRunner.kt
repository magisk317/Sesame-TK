package io.github.magisk317.sesame.task.antDodo

import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.TaskRuntimeStore
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/** Application service for Dodo task completion and award claiming. */
object AntDodoTaskRunner {
    private const val TAG = "AntDodo"
    private val blockedSerializer = SetSerializer(String.serializer())

    @JvmStatic
    fun run() {
        try {
            val blocked = TaskRuntimeStore.getOrCreate("badDodoTaskList", emptySet(), blockedSerializer).toMutableSet()
            if (blocked.isEmpty()) {
                blocked.add("HELP_FRIEND_COLLECT")
                TaskRuntimeStore.put("badDodoTaskList", blocked, blockedSerializer)
            }
            while (true) {
                var changed = false
                val response = AntDodoRpcCall.taskList().toJsonObject()
                if (!ResChecker.checkRes(TAG, response)) {
                    StructuredLog.record(TAG, "查询任务列表失败：${response.optString("resultDesc")}")
                    return
                }
                for (action in AntDodoTaskPlanner.plan(response, blocked)) {
                    val result = when (action.type) {
                        AntDodoTaskActionType.RECEIVE_AWARD -> AntDodoRpcCall.receiveTaskAward(action.sceneCode, action.taskType)
                        AntDodoTaskActionType.FINISH -> AntDodoRpcCall.finishTask(action.sceneCode, action.taskType)
                    }
                    val json = result.toJsonObject()
                    if (ResChecker.checkRes(TAG, json)) {
                        changed = true
                        val label = if (action.type == AntDodoTaskActionType.RECEIVE_AWARD) "任务奖励🎖️" else "物种任务🧾️"
                        StructuredLog.forest("$label[${action.title}]#${action.awardCount}个")
                    } else if (action.type == AntDodoTaskActionType.FINISH) {
                        blocked.add(action.taskType)
                        TaskRuntimeStore.put("badDodoTaskList", blocked, blockedSerializer)
                        StructuredLog.record(TAG, "完成任务失败，加入黑名单：${action.title}")
                    }
                    GlobalThreadPools.sleepCompat(500)
                }
                if (!changed) return
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "神奇物种任务执行异常:", t)
        }
    }

    @JvmStatic
    fun pollPendingAwards() {
        try {
            val response = AntDodoRpcCall.taskList().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return
            for (action in AntDodoTaskPlanner.plan(response)) {
                if (action.type != AntDodoTaskActionType.RECEIVE_AWARD) continue
                val award = AntDodoRpcCall.receiveTaskAward(action.sceneCode, action.taskType).toJsonObject()
                if (ResChecker.checkRes(TAG, award)) {
                    StructuredLog.forest("物种任务奖励🎖️[${action.taskType}]")
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "神奇物种领奖轮询异常:", t)
        }
    }
}
