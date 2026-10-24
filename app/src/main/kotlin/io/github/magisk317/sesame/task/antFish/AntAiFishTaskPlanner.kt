package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

internal enum class AntAiFishTaskActionType {
    FINISH,
    RECEIVE_AWARD,
}

internal data class AntAiFishTaskAction(
    val type: AntAiFishTaskActionType,
    val taskType: String,
    val title: String,
    val awardCount: Int,
)

/** Turns the server task tree into backend-only actions. */
internal object AntAiFishTaskPlanner {
    private const val SCENE_CODE = "ANTAIFISH"

    fun plan(response: JsonObject): List<AntAiFishTaskAction> {
        val taskList = response.optJSONArray("taskInfoList") ?: return emptyList()
        val actions = mutableListOf<AntAiFishTaskAction>()
        val seen = mutableSetOf<String>()
        for (index in 0 until taskList.length()) {
            append(taskList.optJSONObject(index), actions, seen, index.toString())
        }
        return actions
    }

    /** Stable representation of the pending state used to detect stale RPC responses. */
    fun signature(actions: List<AntAiFishTaskAction>): String = actions
        .map { "${it.type}:${it.taskType}" }
        .sorted()
        .joinToString("|")

    private fun append(
        task: JsonObject?,
        actions: MutableList<AntAiFishTaskAction>,
        seen: MutableSet<String>,
        path: String,
    ) {
        val base = task?.optJSONObject("taskBaseInfo")
        val taskType = base?.optString("taskType").orEmpty()
        val sceneCode = base?.optString("sceneCode").orEmpty()
        val status = base?.optString("taskStatus").orEmpty()
        val identity = base?.let { taskIdentity(it, path) }
        if (base != null && taskType.isNotBlank() && sceneCode == SCENE_CODE &&
            identity != null && seen.add(identity)
        ) {
            val bizInfo = base.optString("bizInfo").toJsonObject()
            val title = bizInfo.optString("taskTitle")
                .ifBlank { bizInfo.optString("title") }
                .ifBlank { taskType }
            val awardCount = task.optInt("taskRights", "awardCount")
            when (status) {
                TaskStatus.TODO.name -> actions += AntAiFishTaskAction(
                    AntAiFishTaskActionType.FINISH,
                    taskType,
                    title,
                    awardCount,
                )

                TaskStatus.FINISHED.name -> actions += AntAiFishTaskAction(
                    AntAiFishTaskActionType.RECEIVE_AWARD,
                    taskType,
                    title,
                    awardCount,
                )
            }
        }

        val children = task?.optJSONArray("childTaskTypeList") ?: return
        for (index in 0 until children.length()) {
            append(children.optJSONObject(index), actions, seen, "$path.$index")
        }
    }

    private fun taskIdentity(base: JsonObject, path: String): String {
        val explicitId = sequenceOf("taskId", "taskInstanceId", "id", "bizId")
            .map { base.optString(it) }
            .firstOrNull { it.isNotBlank() }
        return explicitId?.let { "id:$it" } ?: "path:$path"
    }

    private fun JsonObject.optInt(parentKey: String, key: String): Int =
        optJSONObject(parentKey)?.optInt(key, 0) ?: 0
}
