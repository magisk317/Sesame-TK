package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** Blue-bean sign-in, account and exchange RPCs. */
object BlueBeanRpc {
    fun querySignInProcess(appletId: String, scene: String): String = request("com.alipay.insmarketingbff.bean.querySignInProcess", jsonObject("appletId" to appletId, "scene" to scene))
    fun signIn(appletId: String, scene: String): String = request("com.alipay.insmarketingbff.bean.signInTrigger", jsonObject("appletId" to appletId, "scene" to scene))
    fun exchangeDetail(itemId: String): String = request("com.alipay.insmarketingbff.onestop.planTrigger", jsonObject("extParams" to jsonObject("itemId" to itemId), "planCode" to "bluebean_onestop", "planOperateCode" to "exchangeDetail"))
    fun exchange(itemId: String, pointAmount: Int): String = request("com.alipay.insmarketingbff.onestop.planTrigger", jsonObject("extParams" to jsonObject("itemId" to itemId, "pointAmount" to pointAmount.toString()), "planCode" to "bluebean_onestop", "planOperateCode" to "exchange"))
    fun queryAccount(pointProdCode: String): String = request("com.alipay.insmarketingbff.point.queryUserAccountInfo", jsonObject("channel" to "HiChat", "pointProdCode" to pointProdCode, "pointUnitType" to "COUNT"))

    private fun request(method: String, payload: JsonObject): String = RequestManager.requestString(method, jsonArray(payload).toString())
}
