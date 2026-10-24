package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/** RPC gateway for the forest whack-a-mole activity. */
object ForestWhackMoleRpc {
    fun start(source: String = DEFAULT_SOURCE): String = request(
        "alipay.antforest.forest.h5.startWhackMole",
        ForestWhackMoleRpcPayloads.start(source),
    )

    fun hit(moleId: Long, token: String, source: String = DEFAULT_SOURCE): String = request(
        "alipay.antforest.forest.h5.whackMole",
        ForestWhackMoleRpcPayloads.hit(moleId, token, source),
    )

    fun settle(
        token: String,
        moleIds: List<String> = (1..15).map(Int::toString),
        source: String = DEFAULT_SOURCE,
    ): String = request(
        "alipay.antforest.forest.h5.settlementWhackMole",
        ForestWhackMoleRpcPayloads.settle(token, moleIds, source),
    )

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())

    private const val DEFAULT_SOURCE = "senlinguangchangdadishu"
}

object ForestWhackMoleRpcPayloads {
    fun start(source: String): JsonObject = jsonObject("source" to source)

    fun hit(moleId: Long, token: String, source: String): JsonObject = jsonObject(
        "moleId" to moleId,
        "source" to source,
        "token" to token,
        "version" to ForestApiVersion.value,
    )

    fun settle(token: String, moleIds: List<String>, source: String): JsonObject = jsonObject(
        "moleIdList" to buildJsonArray {
            moleIds.forEach { add(it.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(it)) }
        },
        "settlementScene" to "NORMAL",
        "source" to source,
        "token" to token,
        "version" to ForestApiVersion.value,
    )
}
