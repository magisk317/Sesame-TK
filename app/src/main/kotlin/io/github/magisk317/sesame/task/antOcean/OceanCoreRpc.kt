package io.github.magisk317.sesame.task.antOcean

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonPrimitive

/** Home, energy and friend interaction RPCs for the ocean domain. */
object OceanCoreRpc {
    @JvmStatic fun queryOceanStatus() = request("alipay.antocean.ocean.h5.queryOceanStatus", json("source" to APP_SOURCE))
    @JvmStatic fun queryHomePage() = request("alipay.antocean.ocean.h5.queryHomePage", json("source" to FOREST_SOURCE, "uniqueId" to id(), "version" to VERSION))
    @JvmStatic fun cleanOcean(userId: String) = request("alipay.antocean.ocean.h5.cleanOcean", json("cleanedUserId" to userId, "source" to FOREST_SOURCE, "uniqueId" to id()))
    @JvmStatic fun ipOpenSurprise() = request("alipay.antocean.ocean.h5.ipOpenSurprise", json("source" to APP_SOURCE, "uniqueId" to id()))
    @JvmStatic fun querySeaAreaDetailList() = request("alipay.antocean.ocean.h5.querySeaAreaDetailList", json("seaAreaCode" to "", "source" to APP_SOURCE, "targetUserId" to "", "uniqueId" to id()))
    @JvmStatic fun queryMiscInfo() = request("alipay.antocean.ocean.h5.queryMiscInfo", json("queryBizTypes" to buildJsonArray { add(JsonPrimitive("HOME_TIPS_REFRESH")) }, "source" to APP_SOURCE, "uniqueId" to id()))
    @JvmStatic fun queryReplicaHome() = request("alipay.antocean.ocean.h5.queryReplicaHome", json("replicaCode" to "avatar", "source" to "senlinzuoshangjiao", "uniqueId" to id()))
    @JvmStatic fun collectReplicaAsset() = request("alipay.antocean.ocean.h5.collectReplicaAsset", json("replicaCode" to "avatar", "source" to "senlinzuoshangjiao", "uniqueId" to id(), "version" to VERSION))
    @JvmStatic fun queryUserRanking() = request("alipay.antocean.ocean.h5.queryUserRanking", json("source" to FOREST_SOURCE, "uniqueId" to id()))
    @JvmStatic fun collectEnergy(bubbleId: String, userId: String) = request("alipay.antmember.forest.h5.collectEnergy", json("bubbleIds" to buildJsonArray { add(bubbleId.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(bubbleId)) }, "channel" to "ocean", "source" to FOREST_SOURCE, "uniqueId" to id(), "userId" to userId, "version" to VERSION))
    @JvmStatic fun cleanFriendOcean(userId: String) = request("alipay.antocean.ocean.h5.cleanFriendOcean", json("cleanedUserId" to userId, "source" to FOREST_SOURCE, "uniqueId" to id()))
    @JvmStatic fun queryFriendPage(userId: String) = request("alipay.antocean.ocean.h5.queryFriendPage", json("friendUserId" to userId, "interactFlags" to "T", "source" to FOREST_SOURCE, "uniqueId" to id(), "version" to VERSION))
    @JvmStatic fun repairSeaArea() = request("alipay.antocean.ocean.h5.repairSeaArea", json("source" to APP_SOURCE, "uniqueId" to id()))
    @JvmStatic fun createSeaAreaExtraCollect() = request("alipay.antocean.ocean.h5.createSeaAreaExtraCollect", json("source" to APP_SOURCE, "uniqueId" to id()))

    private fun request(method: String, payload: kotlinx.serialization.json.JsonObject) = OceanRpcSupport.request(method, payload)
    private fun id() = OceanRpcSupport.id()
    private fun json(vararg values: Pair<String, Any?>) = OceanRpcSupport.json(*values)
    private const val APP_SOURCE = OceanRpcSupport.APP_SOURCE
    private const val FOREST_SOURCE = OceanRpcSupport.FOREST_SOURCE
    private const val VERSION = OceanRpcSupport.VERSION
}
