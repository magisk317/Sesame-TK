package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** Gateway for the forest vitality item and exchange protocol. */
object ForestVitalityRpc {
    fun itemList(labelType: String): String = request(
        "com.alipay.antiep.itemList",
        ForestVitalityRpcPayloads.itemList(labelType),
    )

    fun itemDetail(spuId: String): String = request(
        "com.alipay.antiep.itemDetail",
        ForestVitalityRpcPayloads.itemDetail(spuId),
    )

    fun exchangeBenefit(spuId: String, skuId: String): String = request(
        "com.alipay.antcommonweal.exchange.h5.exchangeBenefit",
        ForestVitalityRpcPayloads.exchangeBenefit(spuId, skuId),
    )

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())

    private const val SCENE = "ANTFOREST_VITALITY"
}

object ForestVitalityRpcPayloads {
    fun itemList(labelType: String): JsonObject = jsonObject(
        "extendInfo" to "{}", "labelType" to labelType, "pageSize" to 20,
        "requestType" to "rpc", "sceneCode" to SCENE, "source" to "afEntry", "startIndex" to 0,
    )

    fun itemDetail(spuId: String): JsonObject = jsonObject(
        "requestType" to "rpc", "sceneCode" to SCENE, "source" to "afEntry", "spuId" to spuId,
    )

    fun exchangeBenefit(spuId: String, skuId: String): JsonObject = jsonObject(
        "sceneCode" to SCENE,
        "requestId" to "${System.currentTimeMillis()}_${RandomUtil.getRandomInt(17)}",
        "spuId" to spuId, "skuId" to skuId, "source" to "GOOD_DETAIL",
    )

    private const val SCENE = "ANTFOREST_VITALITY"
}
