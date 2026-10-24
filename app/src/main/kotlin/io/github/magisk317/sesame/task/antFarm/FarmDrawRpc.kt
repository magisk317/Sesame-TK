package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Draw-machine activities and their task/claim protocol. */
object FarmDrawRpc {
    private const val VERSION = "1.8.2302070202.46"

    fun clickForGiftV2(foodType: String?, giftIndex: Int) = request("com.alipay.antfarm.clickForGiftV2", json("foodType" to foodType, "giftIndex" to giftIndex, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "ANTFOREST", "version" to VERSION))
    fun queryLoveCabin(userId: String?) = request("com.alipay.antfarm.queryLoveCabin", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "ENTERFARM", "userId" to userId, "version" to VERSION))
    fun chouchouleListFarmTask(drawType: String?) = request("com.alipay.antfarm.listFarmTask", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "signSceneCode" to "", "source" to "H5", "taskSceneCode" to taskScene(drawType), "topTask" to ""))
    fun chouchouleDoFarmTask(drawType: String?, bizKey: String?) = request("com.alipay.antfarm.doFarmTask", json("bizKey" to bizKey, "requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "H5", "taskSceneCode" to taskScene(drawType)))
    fun chouchouleReceiveFarmTaskAward(drawType: String?, taskId: String?) = request("com.alipay.antfarm.receiveFarmTaskAward", json("awardType" to if (drawType == "dailyDraw") "DAILY_DRAW_TIMES" else "IP_DRAW_MACHINE_DRAW_TIMES", "requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "antfarm_villa", "taskId" to taskId, "taskSceneCode" to taskScene(drawType)))
    fun queryDrawMachineActivity(scene: String?, otherScene: String?) = request("com.alipay.antfarm.queryDrawMachineActivity", jsonObject("otherScenes" to jsonArray(otherScene?.let(::JsonPrimitive) ?: JsonNull), "requestType" to "RPC", "scene" to scene, "sceneCode" to "ANTFARM", "source" to "antfarm_villa"))
    fun drawMachineIP(batchDrawTimes: Int = 1) = request("com.alipay.antfarm.drawMachine", json("batchDrawTimes" to batchDrawTimes, "requestType" to "RPC", "scene" to "ipDrawMachine", "sceneCode" to "ANTFARM", "source" to "antfarm_villa"))
    fun drawMachineDaily(activityId: String?) = drawMachineDaily(1)
    fun drawMachineDaily(batchDrawTimes: Int) = request("com.alipay.antfarm.drawMachine", json("batchDrawTimes" to batchDrawTimes, "requestType" to "RPC", "scene" to "dailyDrawMachine", "sceneCode" to "ANTFARM", "source" to "antfarm_villa"))
    fun drawPrize() = request("com.alipay.antfarm.DrawPrize", json("requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "chouchoule"))
    fun drawPrize(activityId: String?) = request("com.alipay.antfarm.DrawPrize", json("activityId" to activityId, "requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "icon"))

    fun xlightPlugin(referToken: String?, spaceCode: String?): String {
        val referInfo = json("referToken" to referToken)
        val position = json("referInfo" to referInfo, "spaceCode" to spaceCode)
        val sdk = json("adComponentType" to "GUESS_PRICE", "adComponentVersion" to "4.28.66", "networkType" to "WIFI", "pageFrom" to "ch_url-https://render.alipay.com/p/yuyan/180020380000000182/prizeMachine.html", "pageNo" to 1, "pageUrl" to "https://render.alipay.com/p/yuyan/180020010001256918/antfarm-landing.html?caprMode=sync", "session" to "u_0c09f_b010f", "unionAppId" to "2060090000304921", "xlightRuntimeSDKversion" to "4.28.66", "xlightSDKType" to "h5", "xlightSDKVersion" to "4.28.66")
        return request("com.alipay.adexchange.ad.facade.xlightPlugin", json("positionRequest" to position, "sdkPageInfo" to sdk))
    }

    fun finishAdTask(playBizId: String?, playEventInfo: JsonObject?, iepTaskType: String?, iepTaskSceneCode: String?) = request("com.alipay.adtask.biz.mobilegw.service.interaction.finish", jsonObject("extendInfo" to jsonObject("iepTaskSceneCode" to iepTaskSceneCode, "iepTaskType" to iepTaskType, "playEndingStatus" to "success"), "playBizId" to playBizId, "playEventInfo" to playEventInfo, "source" to "adx"))
    fun finishTask(taskType: String?, sceneCode: String?, outBizNo: String?) = request("com.alipay.antiep.finishTask", json("outBizNo" to outBizNo, "requestType" to "RPC", "sceneCode" to sceneCode, "source" to "ADBASICLIB", "taskType" to taskType))
    fun finishFeedTask(bizKey: String, attempt: Int = 0) = finishTask(bizKey, "ANTFARM_FOOD_TASK", "${bizKey}_${attempt}_${System.currentTimeMillis()}")
    fun receiveFeedTaskAward(bizKey: String) = request("com.alipay.antfarm.receiveFarmTaskAward", json("awardType" to "FOOD_TASK", "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "taskId" to bizKey, "version" to VERSION))
    fun queryTabVideoUrl() = request("com.alipay.antfarm.queryTabVideoUrl", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to VERSION))
    fun videoDeliverModule(bizId: String?) = request("alipay.content.reading.life.deliver.module", json("bizId" to bizId, "bizType" to "CONTENT", "chInfo" to "ch_antFarm", "refer" to "antFarm", "timestamp" to System.currentTimeMillis().toString()))
    fun videoTrigger(bizId: String?) = request("alipay.content.reading.life.prize.trigger", json("bizId" to bizId, "bizType" to "CONTENT", "prizeFlowNum" to "VIDEO_TASK", "prizeType" to "farmFeed"))
    fun drawLotteryPlus() = request("com.alipay.antfarm.drawLotteryPlus", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5 ", "version" to ""))

    private fun taskScene(drawType: String?) = if (drawType == "dailyDraw") "ANTFARM_DAILY_DRAW_TASK" else "ANTFARM_IP_DRAW_TASK"
    private fun json(vararg values: Pair<String, Any?>): JsonObject = jsonObject(*values)
    private fun request(method: String, payload: JsonObject) = RequestManager.requestString(method, jsonArray(payload).toString())
}
