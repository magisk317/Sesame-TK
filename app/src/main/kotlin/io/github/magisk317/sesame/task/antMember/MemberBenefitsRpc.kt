package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/** Member points, benefit exchange and delivery-zone RPCs. */
object MemberBenefitsRpc {
    fun queryPointCert(page: Int, pageSize: Int): String = request(
        "alipay.antmember.biz.rpc.member.h5.queryPointCert",
        jsonObject("page" to page, "pageSize" to pageSize),
    )

    fun receivePointByUser(certId: String): String = request(
        "alipay.antmember.biz.rpc.member.h5.receivePointByUser",
        jsonObject("certId" to certId),
    )

    fun queryDeliveryZoneDetail(deliveryIds: List<String>, page: Int, pageSize: Int): String = request(
        "com.alipay.alipaymember.biz.rpc.config.h5.queryDeliveryZoneDetail",
        jsonObject(
            "deliveryIdList" to jsonArray(*deliveryIds.map(::JsonPrimitive).toTypedArray()),
            "lowerPoint" to 0, "pageNum" to page, "pageSize" to pageSize, "queryNoReserve" to true,
            "resourceCardChannel" to "ZERO_EXCHANGE_CHANNEL",
            "sourcePassMap" to jsonObject("innerSource" to "", "source" to "", "unid" to ""),
            "startPageFirstQuery" to false,
            "topIdList" to jsonArray(JsonPrimitive("202412231259661040")),
            "uniqueId" to "17665547901390and99999999INTELLIGENT_SORT92524974${deliveryIds.joinToString(",")}",
            "upperPoint" to 99999999, "withPointRange" to false,
        ),
    )

    fun exchangeBenefit(benefitId: String, itemId: String, userId: String): String = request(
        "com.alipay.alipaymember.biz.rpc.exchange.h5.exchangeBenefit",
        jsonObject(
            "benefitId" to benefitId, "cityCode" to "", "exchangeType" to "POINT_PAY", "itemId" to itemId,
            "miniAppId" to "", "orderSource" to "", "requestId" to "requestId${System.currentTimeMillis()}",
            "requestSourceInfo" to "SID:${userId}${System.currentTimeMillis()}|0",
            "sourcePassMap" to jsonObject(
                "alipayClientVersion" to "10.7.80.8000", "bid" to "", "feedsIndex" to "0",
                "innerSource" to "a159.b52659", "isCpc" to "", "mobileOsType" to "Android", "source" to "",
                "unid" to UUID.randomUUID().toString(), "uniqueId" to "$userId${System.currentTimeMillis()}",
            ),
            "userOutAccount" to "",
        ),
    )

    fun queryExchangeList(page: Int, pageSize: Int): String = request(
        "com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.queryListV2",
        jsonObject("currentPage" to page, "formDelivery" to "false", "pageSize" to pageSize,
            "privilegeSource" to "", "privilegeTab" to "", "tabList" to jsonArray()),
    )

    fun obtainAward(templateId: String): String = request(
        "com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.obtainAward",
        jsonObject("awardTemplateId" to templateId),
    )

    private fun request(method: String, payload: JsonObject): String = RequestManager.requestString(method, jsonArray(payload).toString())
}
