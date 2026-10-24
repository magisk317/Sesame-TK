package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.RandomUtil
import kotlinx.serialization.json.JsonObject

/** RPC gateway for AI-fish state and task operations. */
object AntAiFishRpcCall {
    private const val SCENE_CODE = "ANTAIFISH"
    private const val TASK_SOURCE = "task_entry"
    private const val RESCUE_SCENE_CODE = "ANTAIFISH_RESCUE_AND_RESTORE"
    private const val RESCUE_SOURCE = "ANTAIFISH"

    @JvmStatic
    fun status(): String = request(
        "alipay.antaifish.h5.status",
        jsonObject("source" to "ANT_FOREST"),
    )

    @JvmStatic
    fun homepage(): String = request(
        "alipay.antaifish.h5.homepage",
        jsonObject("source" to "ANT_OCEAN", "uniqueId" to uniqueId()),
    )

    @JvmStatic
    fun touchFish(): String = request(
        "alipay.antaifish.h5.touchfish",
        jsonObject("source" to "ANT_OCEAN", "uniqueId" to uniqueId()),
    )

    @JvmStatic
    fun rescueFish(): String = request(
        "alipay.antaifish.h5.rescueFish",
        rescueArgs(),
    )

    internal fun rescueArgs(): JsonObject =
        jsonObject("source" to "ANT_OCEAN", "uniqueId" to uniqueId())

    @JvmStatic
    fun taskList(): String = request(
        "com.alipay.antieptask.listTaskopengreen",
        jsonObject("requestType" to "RPC", "sceneCode" to SCENE_CODE, "source" to TASK_SOURCE),
    )

    @JvmStatic
    fun rescueTaskList(): String = request(
        "com.alipay.antieptask.listTaskopengreen",
        jsonObject("requestType" to "RPC", "sceneCode" to RESCUE_SCENE_CODE, "source" to RESCUE_SOURCE),
    )

    @JvmStatic
    fun finishTask(taskType: String): String = request(
        "com.alipay.antieptask.finishTaskopengreen",
        taskArgs(taskType, includeOutBizNo = true, ignoreLimit = null),
    )

    @JvmStatic
    fun receiveTaskAward(taskType: String): String = request(
        "com.alipay.antieptask.receiveTaskAwardopengreen",
        // AI-fish requires ignoreLimit=true for the open-green award endpoint.
        taskArgs(taskType, includeOutBizNo = false, ignoreLimit = true),
    )

    @JvmStatic
    fun finishRescueTask(taskType: String): String = request(
        "com.alipay.antiep.finishTask",
        rescueTaskArgs(taskType),
    )

    internal fun taskArgs(taskType: String, includeOutBizNo: Boolean, ignoreLimit: Boolean?): JsonObject = jsonObject(
        "requestType" to "RPC",
        "sceneCode" to SCENE_CODE,
        "source" to TASK_SOURCE,
        "taskType" to taskType,
        "ignoreLimit" to ignoreLimit,
        "outBizNo" to if (includeOutBizNo) "${taskType}_${uniqueId()}" else null,
    )

    internal fun rescueTaskArgs(taskType: String): JsonObject = jsonObject(
        "outBizNo" to "${taskType}_${RandomUtil.nextDouble()}",
        "requestType" to "H5",
        "sceneCode" to RESCUE_SCENE_CODE,
        "source" to RESCUE_SOURCE,
        "taskType" to taskType,
    )

    private fun uniqueId(): String = System.currentTimeMillis().toString() + RandomUtil.nextLong()

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())
}
