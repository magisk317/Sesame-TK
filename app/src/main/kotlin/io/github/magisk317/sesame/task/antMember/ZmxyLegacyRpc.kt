package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** Remaining legacy Zhima Credit task and sesame-grain RPCs. */
object ZmxyLegacyRpc {
    fun queryHome(): String = request("com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV7RpcManager.queryHome", jsonObject("invokeSource" to "zmHome", "miniZmGrayInside" to "", "version" to "week"))
    fun completeCheckIn(checkInDate: String, sceneCode: String): String = request("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask", jsonObject("checkInDate" to checkInDate, "sceneCode" to sceneCode))
    fun queryAvailableTask(): String = request("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", jsonObject())
    fun joinTask(templateId: String): String = request("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.joinActivity", jsonObject("chInfo" to "seasameList", "joinFromOuter" to false, "templateId" to templateId))
    fun feedbackTask(templateId: String): String = RequestManager.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback", jsonArray(jsonObject("actionType" to "TO_COMPLETE", "templateId" to templateId)).toString(), "zmmemberop", "taskFeedback", "CreditAccumulateStrategyRpcManager")
    fun finishTask(recordId: String): String = request("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.pushActivity", jsonObject("recordId" to recordId))
    fun queryCreditFeedback(): String = request("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.queryCreditFeedback", jsonObject("queryPotential" to false, "size" to 20, "status" to "UNCLAIMED"))
    fun collectAllCreditFeedback(): String = request("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback", jsonObject("collectAll" to true, "status" to "UNCLAIMED"))
    fun collectCreditFeedback(id: String): String = request("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback", jsonObject("collectAll" to false, "creditFeedbackId" to id, "status" to "UNCLAIMED"))

    private fun request(method: String, payload: JsonObject): String = RequestManager.requestString(method, jsonArray(payload).toString())
}
