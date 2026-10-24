package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** Gateway for the forest patrol/protected-land protocol. */
object ForestPatrolRpc {
    fun queryUserPatrol(): String = request("alipay.antforest.forest.h5.queryUserPatrol", ForestPatrolRpcPayloads.base())

    fun queryMyPatrolRecord(): String = request("alipay.antforest.forest.h5.queryMyPatrolRecord", ForestPatrolRpcPayloads.base())

    fun switchUserPatrol(targetPatrolId: String): String = request(
        "alipay.antforest.forest.h5.switchUserPatrol",
        ForestPatrolRpcPayloads.with(ForestPatrolRpcPayloads.base(), "targetPatrolId" to targetPatrolId),
    )

    fun patrolGo(nodeIndex: Int, patrolId: Int): String = request(
        "alipay.antforest.forest.h5.patrolGo",
        ForestPatrolRpcPayloads.patrolGo(nodeIndex, patrolId),
    )

    fun patrolKeepGoing(nodeIndex: Int, patrolId: Int, eventType: String): String {
        return request(
            "alipay.antforest.forest.h5.patrolKeepGoing",
            ForestPatrolRpcPayloads.patrolKeepGoing(nodeIndex, patrolId, eventType),
        )
    }

    fun exchangePatrolChance(costStep: Int): String = request(
        "alipay.antforest.forest.h5.exchangePatrolChance",
        ForestPatrolRpcPayloads.exchangePatrolChance(costStep),
    )

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())
}

object ForestPatrolRpcPayloads {
    fun base(): JsonObject = jsonObject("source" to SOURCE, "timezoneId" to TIMEZONE)
    fun patrolGo(nodeIndex: Int, patrolId: Int): JsonObject = with(base(), "nodeIndex" to nodeIndex, "patrolId" to patrolId)
    fun patrolKeepGoing(nodeIndex: Int, patrolId: Int, eventType: String): JsonObject {
        val reaction = when (eventType) {
            "video" -> jsonObject("viewed" to "Y")
            "chase" -> jsonObject("sendChat" to "Y")
            "quiz" -> jsonObject("answer" to "correct")
            else -> jsonObject()
        }
        return with(base(), "nodeIndex" to nodeIndex, "patrolId" to patrolId, "reactParam" to reaction)
    }
    fun exchangePatrolChance(costStep: Int): JsonObject = with(base(), "costStep" to costStep)

    fun with(base: JsonObject, vararg values: Pair<String, Any?>): JsonObject = jsonObject(*base.entries.map { it.key to it.value }.toTypedArray(), *values)

    private const val SOURCE = "ant_forest"
    private const val TIMEZONE = "Asia/Shanghai"
}
