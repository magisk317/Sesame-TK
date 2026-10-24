package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject

/** Request payloads shared by forest task and vitality flows. */
object ForestTaskRpcPayloads {
    fun queryTaskList(version: String): String = request(
        "extend" to jsonObject(), "fromAct" to "home_task_list",
        "source" to "chInfo_ch_appcenter__chsub_9patch", "version" to version,
    )

    fun queryTaskListV2(firstTaskType: String, version: String): String {
        val values = buildList {
            add("extend" to jsonObject("firstTaskType" to firstTaskType))
            add("fromAct" to "home_task_list")
            add("version" to version)
            when (firstTaskType) {
                "DNHZ_SL_college" -> add("source" to firstTaskType)
                "DXS_BHZ", "DXS_JSQ" -> add("source" to "202212TJBRW")
            }
        }
        return request(*values.toTypedArray())
    }

    fun receiveTaskAward(sceneCode: String, taskType: String): String = request(
        "ignoreLimit" to false, "requestType" to "H5", "sceneCode" to sceneCode,
        "source" to "ANTFOREST", "taskType" to taskType,
    )

    fun receiveTaskAwardV2(taskType: String): String = receiveTaskAward("ANTFOREST_VITALITY_TASK", taskType)

    fun finishTask(sceneCode: String, taskType: String): String = request(
        "outBizNo" to "${taskType}_${RandomUtil.nextDouble()}", "requestType" to "H5",
        "sceneCode" to sceneCode, "source" to "ANTFOREST", "taskType" to taskType,
    )

    fun sign(entityId: String, userId: String, sceneCode: String): String = request(
        "entityId" to entityId, "requestType" to "rpc", "sceneCode" to sceneCode,
        "source" to "ANTFOREST", "userId" to userId,
    )

    fun initTask(taskType: String): String = request(
        "outBizNo" to "${taskType}_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(8)}",
        "requestType" to "H5", "sceneCode" to "ANTFOREST_ENERGY_RAIN_TASK",
        "source" to "ANTFOREST", "taskType" to taskType,
    )

    private fun request(vararg values: Pair<String, Any?>): String = jsonArray(jsonObject(*values)).toString()
}
