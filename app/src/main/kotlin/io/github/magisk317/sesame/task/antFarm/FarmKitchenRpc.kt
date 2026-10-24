package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** Kitchen inventory and cooking RPC boundary. */
object FarmKitchenRpc {
    fun enterKitchen(userId: String?) = request("com.alipay.antfarm.enterKitchen", json("requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "VILLA", "userId" to userId, "version" to "unknown"))
    fun collectDailyFoodMaterial(amount: Int) = request("com.alipay.antfarm.collectDailyFoodMaterial", json("collectDailyFoodMaterialAmount" to amount, "requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "VILLA", "version" to "unknown"))
    fun queryFoodMaterialPack() = request("com.alipay.antfarm.queryFoodMaterialPack", json("requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "kitchen", "version" to "unknown"))
    fun collectDailyLimitedFoodMaterial(amount: Int) = request("com.alipay.antfarm.collectDailyLimitedFoodMaterial", json("collectDailyLimitedFoodMaterialAmount" to amount, "requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "kitchen", "version" to "unknown"))
    fun farmFoodMaterialCollect() = request("com.alipay.antorchard.farmFoodMaterialCollect", json("collect" to true, "requestType" to "RPC", "sceneCode" to "ORCHARD", "source" to "VILLA", "version" to "unknown"))
    fun cook(userId: String?, source: String?) = request("com.alipay.antfarm.cook", json("requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to source, "userId" to userId, "version" to "unknown"))
    fun useFarmFood(cookbookId: String?, cuisineId: String?) = request("com.alipay.antfarm.useFarmFood", json("cookbookId" to cookbookId, "cuisineId" to cuisineId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "canMock" to true, "source" to "chInfo_ch_appcenter__chsub_9patch", "useCuisine" to true, "version" to "1.8.2302070202.46"))
    fun collectKitchenGarbage() = request("com.alipay.antfarm.collectKitchenGarbage", json("requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "VILLA", "version" to "unknown"))
    private fun json(vararg values: Pair<String, Any?>): JsonObject = jsonObject(*values)
    private fun request(method: String, payload: JsonObject) = RequestManager.requestString(method, jsonArray(payload).toString())
}
