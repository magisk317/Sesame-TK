package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** RPC gateway for the forest charity game center. */
object ForestGameCenterRpc {
    fun queryList(): String = request(
        "com.alipay.charitygamecenter.queryGameList",
        jsonObject(
            "bizType" to "ANTFOREST",
            "commonDegradeFilterRequest" to jsonObject(
                "deviceLevel" to "high", "platform" to "Android", "unityDeviceLevel" to "high",
            ),
            "requestType" to "RPC", "sceneCode" to "ANTFOREST",
            "source" to "chInfo_ch_appcenter__chsub_9patch", "version" to ForestApiVersion.value,
        ),
    )

    fun drawAward(batchDrawCount: Int): String = request(
        "com.alipay.charitygamecenter.drawGameCenterAward",
        jsonObject(
            "batchDrawCount" to batchDrawCount, "bizType" to "ANTFOREST", "requestType" to "RPC",
            "sceneCode" to "ANTFOREST", "source" to "leyuan", "version" to ForestApiVersion.value,
        ),
    )

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())
}
