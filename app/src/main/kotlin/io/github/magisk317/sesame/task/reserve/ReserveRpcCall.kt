package io.github.magisk317.sesame.task.reserve

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

object ReserveRpcCall {
    private const val VERSION = "20230501"
    private const val VERSION2 = "20230522"
    private const val SOURCE = "chInfo_ch_appcenter__chsub_9patch"

    @JvmStatic
    fun queryTreeItemsForExchange(): String = request(
        "alipay.antforest.forest.h5.queryTreeItemsForExchange",
        jsonObject("cityCode" to "370100", "itemTypes" to "", "source" to SOURCE, "version" to VERSION2),
    )

    @JvmStatic
    fun queryTreeForExchange(projectId: String): String = request(
        "alipay.antforest.forest.h5.queryTreeForExchange",
        jsonObject("projectId" to projectId, "version" to VERSION, "source" to SOURCE),
    )

    @JvmStatic
    fun exchangeTree(projectId: String): String = request(
        "alipay.antmember.forest.h5.exchangeTree",
        jsonObject("projectId" to projectId.toInt(), "sToken" to System.currentTimeMillis().toString(), "version" to VERSION, "source" to SOURCE),
    )

    @JvmStatic
    fun queryAreaTrees(): String = request("alipay.antmember.forest.h5.queryAreaTrees", jsonObject())

    @JvmStatic
    fun queryTreeItemsForExchange(applyActions: String, itemTypes: String): String = request(
        "alipay.antforest.forest.h5.queryTreeItemsForExchange",
        jsonObject("applyActions" to applyActions, "itemTypes" to itemTypes),
    )

    private fun request(method: String, value: JsonObject): String =
        RequestManager.requestString(method, jsonArray(value).toString())
}
