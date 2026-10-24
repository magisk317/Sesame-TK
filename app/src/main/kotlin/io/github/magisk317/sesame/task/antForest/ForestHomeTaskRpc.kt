package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** RPCs used by the Forest home "complete tasks for rewards" panel. */
object ForestHomeTaskRpc {
    private const val QUERY_METHOD = "com.alipay.charitygamecenter.queryOptionalPlay"
    private const val RECEIVE_METHOD = "com.alipay.antieptask.receiveTaskAwardopengreen"
    private const val SOURCE = "chInfo_ch_appcenter__chsub_9patch"

    fun queryOptionalPlay(): String = request(
        QUERY_METHOD,
        jsonArray(
            jsonObject(
                "bizType" to "ANTFOREST",
                "commonDegradeFilterRequest" to jsonObject(
                    "appMode" to "normal",
                    "deviceLevel" to "high",
                    "platform" to "Android",
                    "unityDeviceLevel" to "high",
                ),
                "playTypeList" to buildJsonArray {
                    add(JsonPrimitive("TASK_TRIGGER"))
                    add(JsonPrimitive("TOP_UP_COUPON"))
                },
                "recentAppRecordList" to jsonArray(),
                "requestType" to "RPC",
                "sceneCode" to "ANTFOREST_COMMON",
                "source" to SOURCE,
                "version" to "12.12.1.8000",
            ),
        ).toString(),
    )

    /**
     * The award request contains the task object plus transport fields added by
     * the Forest home client. They are not present in queryOptionalPlay's task
     * entries; omitting them returns code 100000001 (missing parameter).
     */
    fun receiveTaskAward(task: JsonObject): String = request(
        RECEIVE_METHOD,
            jsonArray(
                buildJsonObject {
                task.forEach { (key, value) -> put(key, value) }
                put("ignoreLimit", JsonPrimitive(true))
                put("requestType", JsonPrimitive("RPC"))
                put("source", JsonPrimitive("ch_appcenter__chsub_9patch"))
            },
        ).toString(),
    )

    private fun request(method: String, payload: String): String =
        RequestManager.requestString(method, payload)
}
