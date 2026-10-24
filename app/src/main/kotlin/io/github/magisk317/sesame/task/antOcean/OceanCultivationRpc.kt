package io.github.magisk317.sesame.task.antOcean

/** Atlas, cultivation and ecological protection RPCs. */
object OceanCultivationRpc {
    @JvmStatic fun queryCultivationList() = request("alipay.antocean.ocean.h5.queryCultivationList", json("source" to FOREST_SOURCE, "version" to "20231031"))
    @JvmStatic fun queryCultivationDetail(cultivationCode: String, projectCode: String) = request("alipay.antocean.ocean.h5.queryCultivationDetail", json("cultivationCode" to cultivationCode, "projectCode" to projectCode, "source" to FOREST_SOURCE, "uniqueId" to id()))
    @JvmStatic fun oceanExchangeTree(cultivationCode: String, projectCode: String) = request("alipay.antocean.ocean.h5.exchangeTree", json("cultivationCode" to cultivationCode, "projectCode" to projectCode, "source" to FOREST_SOURCE, "uniqueId" to id()))
    @JvmStatic fun queryOceanChapterList() = request("alipay.antocean.ocean.h5.queryOceanChapterList", json("source" to SOURCE, "uniqueId" to id()))
    @JvmStatic fun switchOceanChapter(code: String) = request("alipay.antocean.ocean.h5.switchOceanChapter", json("chapterCode" to code, "source" to SOURCE, "uniqueId" to id()))
    @JvmStatic fun combineFish(fishId: String) = request("alipay.antocean.ocean.h5.combineFish", json("fishId" to fishId, "source" to FOREST_SOURCE, "uniqueId" to id()))

    private fun request(method: String, payload: kotlinx.serialization.json.JsonObject) = OceanRpcSupport.request(method, payload)
    private fun id() = OceanRpcSupport.id()
    private fun json(vararg values: Pair<String, Any?>) = OceanRpcSupport.json(*values)
    private const val FOREST_SOURCE = OceanRpcSupport.FOREST_SOURCE
    private const val SOURCE = "chInfo_ch_url-https://2021003115672468.h5app.alipay.com/www/atlasOcean.html"
}
