package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Insurance-gold query and claim RPCs. */
object InsuredGoldRpc {
    fun queryAvailable(): String = RequestManager.requestString(
        "com.alipay.insgiftbff.insgiftMain.queryMultiSceneWaitToGainList",
        jsonArray(jsonObject(
            "entrance" to "wealth_entry",
            "eventToWaitParamDTO" to params("GIFT_UNIVERSAL_COVERAGE"),
            "helpChildParamDTO" to params("GIFT_HEALTH_GOLD_CHILD"),
            "priorityChannelParamDTO" to params("GIFT_UNIVERSAL_COVERAGE"),
            "signInParamDTO" to params("GIFT_UNIVERSAL_COVERAGE"),
        )).toString(),
        "insgiftbff", "queryMultiSceneWaitToGainList", "insgiftMain",
    )

    fun collect(ball: JsonObject): String = RequestManager.requestString(
        "com.alipay.insgiftbff.insgiftMain.gainMyAndFamilySumInsured",
        ball.toString(), "insgiftbff", "gainMyAndFamilySumInsured", "insgiftMain",
    )

    private fun params(product: String): JsonObject = jsonObject(
        "giftProdCode" to product,
        "rightNoList" to jsonArray(*listOf(
            "UNIVERSAL_ACCIDENT", "UNIVERSAL_HOSPITAL", "UNIVERSAL_OUTPATIENT", "UNIVERSAL_SERIOUSNESS",
            "UNIVERSAL_WEALTH", "UNIVERSAL_TRANS", "UNIVERSAL_FRAUD_LIABILITY",
        ).map(::JsonPrimitive).toTypedArray()),
    )
}
