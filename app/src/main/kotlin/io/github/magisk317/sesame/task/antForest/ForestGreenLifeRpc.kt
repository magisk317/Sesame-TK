package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** RPC gateway for forest-market green-life actions. */
object ForestGreenLifeRpc {
    fun consult(sourceType: String): String = request(
        "alipay.bizfmcg.greenlife.consultForSendEnergyByAction",
        jsonObject("sourceType" to sourceType),
    )

    fun send(sourceType: String): String = request(
        "alipay.bizfmcg.greenlife.sendEnergyByAction",
        jsonObject(
            "actionType" to "GOODS_BROWSE",
            "requestId" to io.github.magisk317.sesame.util.RandomUtil.getRandomString(8),
            "sourceType" to sourceType,
        ),
    )

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())
}
