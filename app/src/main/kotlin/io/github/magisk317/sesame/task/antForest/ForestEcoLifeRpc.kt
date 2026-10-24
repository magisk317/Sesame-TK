package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/** RPC gateway for the forest green-life activity. */
object ForestEcoLifeRpc {
    fun queryHome(): String = request("alipay.ecolife.rpc.h5.queryHomePage", ForestEcoLifeRpcPayloads.home())
    fun open(): String = request("alipay.ecolife.rpc.h5.openEcolife", ForestEcoLifeRpcPayloads.open())
    fun tick(actionId: String, dayPoint: String?, source: String): String = request(
        "alipay.ecolife.rpc.h5.tick", ForestEcoLifeRpcPayloads.tick(actionId, dayPoint, source),
    )
    fun queryDish(source: String, dayPoint: String?): String = request(
        "alipay.ecolife.rpc.h5.queryDish", ForestEcoLifeRpcPayloads.queryDish(source, dayPoint),
    )
    fun uploadDishImage(
        operateType: String, imageId: String?, conf1: Double, conf2: Double, conf3: Double, dayPoint: String?,
    ): String = request(
        "alipay.ecolife.rpc.h5.uploadDishImage",
        ForestEcoLifeRpcPayloads.uploadDishImage(operateType, imageId, conf1, conf2, conf3, dayPoint),
    )

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())
}

object ForestEcoLifeRpcPayloads {
    fun home(): JsonObject = jsonObject("channel" to CHANNEL, "source" to "search_brandbox")
    fun open(): JsonObject = jsonObject("channel" to CHANNEL, "source" to "renwuGD")
    fun tick(actionId: String, dayPoint: String?, source: String): JsonObject = jsonObject(
        "actionId" to actionId, "channel" to CHANNEL, "dayPoint" to (dayPoint ?: "null"),
        "generateEnergy" to false, "source" to source,
    )
    fun queryDish(source: String, dayPoint: String?): JsonObject = jsonObject(
        "channel" to CHANNEL, "dayPoint" to (dayPoint ?: "null"), "source" to source,
    )

    fun uploadDishImage(
        operateType: String, imageId: String?, conf1: Double, conf2: Double, conf3: Double, dayPoint: String?,
    ): JsonObject {
        val imagePath = imageId ?: "null"
        val aiResult = buildJsonArray {
            add(aiResultItem(conf1, "other"))
            add(aiResultItem(conf2, "guangpan"))
            add(aiResultItem(conf3, "feiguangpan"))
        }
        return jsonObject(
            "channel" to CHANNEL,
            "dayPoint" to (dayPoint ?: "null"),
            "source" to "photo-comparison",
            "uploadParamMap" to jsonObject(
                "AIResult" to aiResult,
                "existAIResult" to true,
                "imageId" to imagePath,
                "imageUrl" to "https://mdn.alipayobjects.com/afts/img/$imagePath/original?bz=APM_20000067",
                "operateType" to operateType,
            ),
        )
    }

    private fun aiResultItem(conf: Double, label: String): JsonObject = jsonObject(
        "conf" to conf,
        "kvPair" to false,
        "label" to label,
        "pos" to buildJsonArray {
            listOf(1.0002995, 0.22104378, 0.0011976048, 0.77727276).forEach { add(JsonPrimitive(it)) }
        },
        "value" to "",
    )

    private const val CHANNEL = "ALIPAY"
}
