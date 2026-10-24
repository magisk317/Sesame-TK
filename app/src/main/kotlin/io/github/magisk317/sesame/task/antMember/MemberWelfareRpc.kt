package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.jsonArray
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/** Welfare-center and gold-bill activity RPCs. */
object MemberWelfareRpc {
    fun queryHome(): String? = request("com.alipay.finaggexpbff.needle.welfareCenter.index", json("isResume" to true))
    fun trigger(type: String): String? = request("com.alipay.finaggexpbff.needle.welfareCenter.trigger", json("type" to type))
    fun queryConsume(): String? = request(
        "com.alipay.wealthgoldtwa.needle.consume.query",
        json("tabBubbleDeliverParam" to jsonObject(), "tabTypeDeliverParam" to jsonObject()),
    )
    fun submitConsume(amount: Int, productId: String, bonusAmount: Int): String? = request(
        "com.alipay.wealthgoldtwa.needle.consume.submit",
        json(
            "exchangeAmount" to amount,
            "exchangeMoney" to String.format(Locale.ROOT, "%.2f", amount / 1000.0),
            "prizeType" to "GOLD",
            "productId" to productId,
            "bonusAmount" to bonusAmount,
        ),
    )

    private fun request(method: String, payload: JsonObject): String? = runCatching {
        RequestManager.requestString(method, jsonArray(payload).toString())
    }.getOrNull()

    private fun json(vararg values: Pair<String, Any?>): JsonObject = jsonObject(*values)
}
