package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject

/** Structured request payloads for the open-green draw campaigns. */
object ForestDrawRpcPayloads {
    fun enter(activityId: String?, sceneCode: String, source: String): String = request(
        "activityId" to activityId.orEmpty(), "requestType" to "RPC", "sceneCode" to sceneCode, "source" to source,
    )

    fun list(sceneCode: String, source: String, businessSource: String?): String {
        val values = buildList {
            add("requestType" to "RPC")
            add("sceneCode" to sceneCode)
            add("source" to source)
            if (!businessSource.isNullOrEmpty()) {
                add("extend" to jsonObject(
                    "businessSource" to businessSource,
                    "osType" to "android",
                    "version" to "20260109",
                ))
            }
        }
        return request(*values.toTypedArray())
    }

    fun draw(activityId: String, sceneCode: String, source: String, userId: String): String = request(
        "activityId" to activityId, "requestType" to "RPC", "sceneCode" to sceneCode,
        "source" to source, "userId" to userId,
    )

    fun batchDraw(
        activityId: String,
        sceneCode: String,
        source: String,
        userId: String,
        times: Int,
    ): String = request(
        "activityId" to activityId, "requestType" to "RPC", "sceneCode" to sceneCode,
        "source" to source, "times" to times, "userId" to userId,
    )

    fun receive(source: String, sceneCode: String, taskType: String): String = request(
        "ignoreLimit" to true, "requestType" to "RPC", "sceneCode" to sceneCode,
        "source" to source, "taskType" to taskType,
    )

    fun exchange(
        activityId: String,
        sceneCode: String,
        source: String,
        taskSceneCode: String,
        taskType: String,
    ): String = request(
        "activityId" to activityId, "requestType" to "RPC", "sceneCode" to sceneCode,
        "source" to source, "taskSceneCode" to taskSceneCode, "taskType" to taskType,
    )

    fun finishTask4(taskType: String, sceneCode: String): String {
        val source = if (taskType.contains("XLIGHT")) "ADBASICLIB" else "task_entry"
        return request(
            "outBizNo" to (taskType + RandomUtil.getRandomTag()), "requestType" to "RPC",
            "sceneCode" to sceneCode, "source" to source, "taskType" to taskType,
        )
    }

    fun finishTask(taskType: String, sceneCode: String): String = request(
        "outBizNo" to (taskType + RandomUtil.getRandomTag()), "requestType" to "RPC",
        "sceneCode" to sceneCode, "source" to "task_entry", "taskType" to taskType,
    )

    private fun request(vararg values: Pair<String, Any?>): String =
        jsonArray(jsonObject(*values)).toString()
}
