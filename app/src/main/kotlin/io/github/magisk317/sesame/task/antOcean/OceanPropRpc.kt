package io.github.magisk317.sesame.task.antOcean

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray

/** Ocean prop exchange and fish-completion RPCs. */
object OceanPropRpc {
    @JvmStatic fun queryExchangePropList() = request("alipay.antocean.ocean.h5.queryOceanPropList", json("skipPropId" to false, "source" to FOREST_SOURCE, "uniqueId" to id()))
    @JvmStatic fun exchangeProp() = request("alipay.antocean.ocean.h5.exchangeProp", json("bizNo" to System.currentTimeMillis(), "exchangeNum" to "1", "propCode" to "UNIVERSAL_PIECE", "propType" to "UNIVERSAL_PIECE", "source" to FOREST_SOURCE, "uniqueId" to id()))
    @JvmStatic fun queryUsePropList() = request("alipay.antocean.ocean.h5.queryOceanPropList", json("propTypeList" to "UNIVERSAL_PIECE", "skipPropId" to false, "source" to APP_SOURCE, "uniqueId" to id()))
    @JvmStatic fun queryFishList(pageNum: Int) = request("alipay.antocean.ocean.h5.queryFishList", json("combineStatus" to "UNOBTAINED", "needSummary" to "Y", "pageNum" to pageNum, "source" to APP_SOURCE, "targetUserId" to "", "uniqueId" to id()))
    @JvmStatic fun usePropByType(assets: Int, attachAssetsSet: Set<Int>): String? {
        if (attachAssetsSet.isEmpty()) return null
        val details = buildJsonArray {
            attachAssetsSet.forEach { add(json("assets" to assets, "assetsNum" to 1, "attachAssets" to it, "propCode" to "UNIVERSAL_PIECE")) }
        }
        return request("alipay.antocean.ocean.h5.usePropByType", json("assetsDetails" to details, "propCode" to "UNIVERSAL_PIECE", "propType" to "UNIVERSAL_PIECE", "source" to APP_SOURCE, "uniqueId" to id()))
    }

    private fun request(method: String, payload: JsonObject) = OceanRpcSupport.request(method, payload)
    private fun id() = OceanRpcSupport.id()
    private fun json(vararg values: Pair<String, Any?>) = OceanRpcSupport.json(*values)
    private const val APP_SOURCE = OceanRpcSupport.APP_SOURCE
    private const val FOREST_SOURCE = OceanRpcSupport.FOREST_SOURCE
}
