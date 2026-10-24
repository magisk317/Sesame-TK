package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.toJsonArray
import kotlinx.serialization.json.JsonObject

/** Gateway for forest animal collection and friend-protection operations. */
object ForestAnimalRpc {
    fun queryAnimalAndPiece(animalId: Int): String = request(
        "alipay.antforest.forest.h5.queryAnimalAndPiece",
        ForestAnimalRpcPayloads.queryAnimalAndPiece(animalId),
    )

    fun combineAnimalPiece(animalId: Int, piecePropIds: String): String = request(
        "alipay.antforest.forest.h5.combineAnimalPiece",
        ForestAnimalRpcPayloads.combineAnimalPiece(animalId, piecePropIds),
    )

    fun consumeProp(propGroup: String, propId: String, propType: String): String = request(
        "alipay.antforest.forest.h5.consumeProp",
        ForestAnimalRpcPayloads.consumeProp(propGroup, propId, propType),
    )

    fun collectAnimalRobEnergy(propId: String, propType: String, shortDay: String): String = request(
        "alipay.antforest.forest.h5.collectAnimalRobEnergy",
        ForestAnimalRpcPayloads.collectAnimalRobEnergy(propId, propType, shortDay),
    )

    fun protectBubble(targetUserId: String): String = request(
        "alipay.antforest.forest.h5.protectBubble",
        ForestAnimalRpcPayloads.protectBubble(targetUserId),
    )

    fun collectFriendGiftBox(targetId: String, targetUserId: String): String = request(
        "alipay.antforest.forest.h5.collectFriendGiftBox",
        ForestAnimalRpcPayloads.collectFriendGiftBox(targetId, targetUserId),
    )

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())
}

object ForestAnimalRpcPayloads {
    fun queryAnimalAndPiece(animalId: Int): JsonObject = if (animalId != 0) {
        with(base(), "animalId" to animalId)
    } else {
        with(base(), "withDetail" to "N", "withGift" to true)
    }

    fun combineAnimalPiece(animalId: Int, piecePropIds: String): JsonObject =
        with(base(), "animalId" to animalId, "piecePropIds" to piecePropIds.toJsonArray())

    fun consumeProp(propGroup: String, propId: String, propType: String): JsonObject =
        with(base(), "propGroup" to propGroup, "propId" to propId, "propType" to propType)

    fun collectAnimalRobEnergy(propId: String, propType: String, shortDay: String): JsonObject =
        with(sourceOnly("chInfo_ch_appcenter__chsub_9patch"), "propId" to propId, "propType" to propType,
            "shortDay" to shortDay, "version" to ForestApiVersion.value)

    fun protectBubble(targetUserId: String): JsonObject = jsonObject(
        "source" to "ANT_FOREST_H5", "targetUserId" to targetUserId, "version" to ForestApiVersion.value,
    )

    fun collectFriendGiftBox(targetId: String, targetUserId: String): JsonObject =
        with(sourceOnly("chInfo_ch_appcenter__chsub_9patch"), "targetId" to targetId, "targetUserId" to targetUserId)

    private fun base(source: String = SOURCE): JsonObject = jsonObject("source" to source, "timezoneId" to TIMEZONE)

    private fun sourceOnly(source: String): JsonObject = jsonObject("source" to source)

    private fun with(base: JsonObject, vararg values: Pair<String, Any?>): JsonObject =
        jsonObject(*base.entries.map { it.key to it.value }.toTypedArray(), *values)

    private const val SOURCE = "ant_forest"
    private const val TIMEZONE = "Asia/Shanghai"
}
