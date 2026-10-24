package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlin.random.Random

/** Farm game-center, mall and fitment exchange RPC boundary. */
object FarmGameMallRpc {
    fun flyGameListFarmTask() = request("com.alipay.antfarm.listFarmTask", json("bizKey" to "SHANGYEHUA_GAME_TIMES", "gameType" to "flyGame", "requestType" to "RPC", "sceneCode" to "FLAYGAME", "signSceneCode" to "", "source" to "ANTFARM", "taskSceneCode" to "ANTFARM_GAME_TIMES_TASK", "version" to ""))
    fun hitGameListFarmTask() = request("com.alipay.antfarm.listFarmTask", json("bizKey" to "SHANGYEHUA_HIT_ANIMAL", "gameType" to "hitGame", "requestType" to "RPC", "sceneCode" to "HITGAME", "signSceneCode" to "", "source" to "ANTFARM", "taskSceneCode" to "ANTFARM_GAME_TIMES_TASK", "version" to ""))
    fun drawGameCenterAward(drawTimes: Int) = request("com.alipay.antfarm.drawGameCenterAward", json("drawTimes" to drawTimes, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to "1.8.2302070202.46"))
    fun queryGameList() = request("com.alipay.charitygamecenter.queryGameList", json("bizType" to "ANTFARM", "commonDegradeFilterRequest" to json("deviceLevel" to "high", "platform" to "Android", "unityDeviceLevel" to "high"), "recentAppRecordList" to jsonArray(), "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to "1.8.2302070202.46"))
    fun getMallHome() = request("com.alipay.charitygamecenter.getMallHome", json("bizType" to "ANTFARM_GAME_CENTER", "pageSize" to 10, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "startIndex" to 0))
    fun getMallItemDetail(spuId: String?) = request("com.alipay.charitygamecenter.getMallItemDetail", json("bizType" to "ANTFARM_GAME_CENTER", "itemId" to spuId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5"))
    fun buyMallItem(spuId: String?, skuId: String?) = request("com.alipay.charitygamecenter.buyMallItem", json("bizType" to "ANTFARM_GAME_CENTER", "ignoreHoldLimit" to false, "itemId" to spuId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "subItemId" to skuId))
    fun getItemList(activityId: String?, pageSize: Int, startIndex: Int) = request("com.alipay.antiep.itemList", json("activityId" to activityId, "pageSize" to pageSize, "requestType" to "RPC", "sceneCode" to "ANTFARM_IP_DRAW_MALL", "source" to "antfarm.villa", "startIndex" to startIndex))
    fun getFitmentItemList(activityId: String?, pageSize: Int, labelType: String?, startIndex: Int) = request("com.alipay.antiep.itemList", json("activityId" to activityId, "labelType" to labelType, "pageSize" to pageSize, "requestType" to "NORMAL", "sceneCode" to "ANTFARM_FITMENT_MALL", "source" to "antfarm", "startIndex" to startIndex))
    fun getItemDetail(spuId: String?) = request("com.alipay.antiep.itemDetail", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM_FITMENT_MALL", "source" to "antfarm", "spuId" to spuId))
    fun exchangeBenefit(spuId: String?, skuId: String?, activityId: String?) = exchangeBenefit(spuId, skuId, activityId, "ANTFARM_FITMENT_MALL", "H5")
    fun exchangeBenefit(spuId: String?, skuId: String?, activityId: String?, sceneCode: String?, source: String?) = request("com.alipay.antcommonweal.exchange.h5.exchangeBenefit", json("context" to json("activityId" to activityId), "requestId" to "${System.currentTimeMillis()}_${Random.nextLong(1000000000000000L, 9999999999999999L)}", "requestType" to "RPC", "sceneCode" to sceneCode, "skuId" to skuId, "source" to source, "spuId" to spuId))
    private fun json(vararg values: Pair<String, Any?>) = jsonObject(*values)
    private fun request(method: String, payload: kotlinx.serialization.json.JsonObject) =
        RequestManager.requestString(method, jsonArray(payload).toString())
}
