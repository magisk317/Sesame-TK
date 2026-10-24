package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** RPC gateway for the forest energy-rain activity. */
object ForestEnergyRainRpc {
    fun queryHome(): String = request(
        "alipay.antforest.forest.h5.queryEnergyRainHome",
        ForestEnergyRainRpcPayloads.queryHome(),
    )

    fun queryCanGrantList(): String = request(
        "alipay.antforest.forest.h5.queryEnergyRainCanGrantList",
        ForestEnergyRainRpcPayloads.queryCanGrantList(),
    )

    fun grantChance(targetUserId: String): String = request(
        "alipay.antforest.forest.h5.grantEnergyRainChance",
        ForestEnergyRainRpcPayloads.grantChance(targetUserId),
    )

    fun start(): String = request(
        "alipay.antforest.forest.h5.startEnergyRain",
        ForestEnergyRainRpcPayloads.start(),
    )

    fun settle(saveEnergy: Int, token: String): String = request(
        "alipay.antforest.forest.h5.energyRainSettlement",
        ForestEnergyRainRpcPayloads.settle(saveEnergy, token),
    )

    fun queryEndGameList(): String = request(
        "alipay.antforest.forest.h5.queryEnergyRainEndGameList",
        ForestEnergyRainRpcPayloads.queryEndGameList(),
    )

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())
}

object ForestEnergyRainRpcPayloads {
    fun queryHome(): JsonObject = jsonObject("source" to "senlinguangchuangrukou", "version" to ForestApiVersion.value)

    fun queryCanGrantList(): JsonObject = jsonObject()

    fun grantChance(targetUserId: String): JsonObject = jsonObject(
        "targetUserId" to (targetUserId.toLongOrNull() ?: targetUserId),
    )

    fun start(): JsonObject = jsonObject("version" to ForestApiVersion.value)

    fun settle(saveEnergy: Int, token: String): JsonObject = jsonObject(
        "activityPropNums" to 0, "saveEnergy" to saveEnergy, "token" to token, "version" to ForestApiVersion.value,
    )

    fun queryEndGameList(): JsonObject = jsonObject()
}
