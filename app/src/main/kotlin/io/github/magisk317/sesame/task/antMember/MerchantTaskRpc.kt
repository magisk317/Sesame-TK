package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Merchant check-in and merchant-service task RPCs. */
object MerchantTaskRpc {
    fun transcodeCheck(): String = request("alipay.mrchservbase.mrchbusiness.sign.transcode.check", jsonObject())
    fun signIn(activityNo: String): String = request("alipay.merchant.kmdk.signIn", jsonObject("activityNo" to activityNo))
    fun signUp(activityNo: String): String = request("alipay.merchant.kmdk.signUp", jsonObject("activityNo" to activityNo))
    fun merchantSign(): String = request("alipay.mrchservbase.mrchpoint.sqyj.homepage.signin.v1", jsonObject())
    fun taskList(): String = request("alipay.mrchservbase.task.more.query", jsonObject("paramMap" to jsonObject("platform" to "Android"), "taskItemCode" to ""))
    fun queryActivity(): String = request("alipay.merchant.kmdk.query.activity", jsonObject("scene" to "activityCenter"))
    fun finishTask(bizId: String): String = request("com.alipay.adtask.biz.mobilegw.service.task.finish", jsonObject("bizId" to bizId))
    fun receiveTask(taskCode: String): String = request("alipay.mrchservbase.sqyj.task.receive", jsonObject("compId" to "ZTS_TASK_RECEIVE", "extInfo" to jsonObject("taskCode" to taskCode)))
    fun queryAction(actionCode: String): String = request("alipay.mrchservbase.task.query.by.actioncode", jsonObject("actionCode" to actionCode))
    fun produce(actionCode: String): String = request("alipay.mrchservbase.biz.task.action.produce", jsonObject("actionCode" to actionCode))
    fun receiveBall(ballId: String): String = request("alipay.mrchservbase.mrchpoint.ball.receive", jsonObject("ballIds" to jsonArray(JsonPrimitive(ballId)), "channel" to "MRCH_SELF", "outBizNo" to System.currentTimeMillis().toString() + RandomUtil.nextLong()))

    private fun request(method: String, payload: JsonObject): String = RequestManager.requestString(method, jsonArray(payload).toString())
}
