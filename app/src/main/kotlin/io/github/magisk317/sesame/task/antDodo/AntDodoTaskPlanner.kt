package io.github.magisk317.sesame.task.antDodo

import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

enum class AntDodoTaskActionType {
    FINISH,
    RECEIVE_AWARD,
}

data class AntDodoTaskAction(
    val type: AntDodoTaskActionType,
    val sceneCode: String,
    val taskType: String,
    val title: String,
    val awardCount: String,
)

/** Converts the server task tree into transport-agnostic actions. */
object AntDodoTaskPlanner {
    @JvmStatic
    fun plan(response: JsonObject): List<AntDodoTaskAction> = plan(response, emptySet())

    @JvmStatic
    fun plan(response: JsonObject, blockedTaskTypes: Set<String> = emptySet()): List<AntDodoTaskAction> {
        val groups = response.optJSONObject("data")?.optJSONArray("taskGroupInfoList") ?: return emptyList()
        val actions = mutableListOf<AntDodoTaskAction>()
        for (groupIndex in 0 until groups.length()) {
            val tasks = groups.optJSONObject(groupIndex)?.optJSONArray("taskInfoList") ?: continue
            for (taskIndex in 0 until tasks.length()) {
                val base = tasks.optJSONObject(taskIndex)?.optJSONObject("taskBaseInfo") ?: continue
                val taskType = base.optString("taskType")
                val sceneCode = base.optString("sceneCode")
                if (taskType.isEmpty() || sceneCode.isEmpty()) continue
                val bizInfo = base.optString("bizInfo").toJsonObject()
                val title = bizInfo.optString("taskTitle", taskType)
                val awardCount = bizInfo.optString("awardCount", "1")
                when (base.optString("taskStatus")) {
                    TaskStatus.FINISHED.name -> actions += AntDodoTaskAction(
                        AntDodoTaskActionType.RECEIVE_AWARD, sceneCode, taskType, title, awardCount,
                    )
                    TaskStatus.TODO.name -> if (taskType !in blockedTaskTypes) {
                        actions += AntDodoTaskAction(
                            AntDodoTaskActionType.FINISH, sceneCode, taskType, title, awardCount,
                        )
                    }
                }
            }
        }
        return actions
    }
}
