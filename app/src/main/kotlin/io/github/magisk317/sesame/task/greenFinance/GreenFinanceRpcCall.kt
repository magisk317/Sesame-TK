package io.github.magisk317.sesame.task.greenFinance

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** RPC boundary for green finance. Payload construction is structured at the boundary. */
object GreenFinanceRpcCall {
    private val uid: String get() = UserMap.currentUid ?: ""

    @JvmStatic fun taskQuery(appletId: String): String = request(
        "com.alipay.loanpromoweb.promo.task.taskQuery", jsonObject("appletId" to appletId, "completedBottom" to true),
    )

    @JvmStatic fun taskTrigger(appletId: String, stageCode: String, taskCenId: String): String = request(
        "com.alipay.loanpromoweb.promo.task.taskTrigger",
        jsonObject("appletId" to appletId, "stageCode" to stageCode, "taskCenId" to taskCenId),
    )

    @JvmStatic fun signInTrigger(sceneId: String): String = request(
        "com.alipay.loanpromoweb.promo.signin.trigger", jsonObject("extInfo" to jsonObject(), "sceneId" to sceneId),
    )

    @JvmStatic fun greenFinanceIndex(): String = request(
        "com.alipay.mcaplatformunit.common.mobile.newservice.GreenFinancePageQueryService.indexV2",
        jsonObject("clientVersion" to "VERSION2", "custType" to "MERCHANT"),
    )

    @JvmStatic fun batchSelfCollect(bsnIds: JsonArray): String = request(
        "com.alipay.mcaplatformunit.common.mobile.service.GreenFinancePointCollectService.batchSelfCollect",
        jsonObject("bsnIds" to bsnIds, "clientVersion" to "VERSION2", "custType" to "MERCHANT", "uid" to uid),
    )

    @JvmStatic fun signInQuery(sceneId: String): String = request(
        "com.alipay.loanpromoweb.promo.signin.query",
        jsonObject("cycleCount" to 7, "cycleType" to "d", "extInfo" to jsonObject(), "needContinuous" to 1, "sceneId" to sceneId),
    )

    @JvmStatic fun queryUserTickItem(firstBehaviorType: String): String = request(
        "com.alipay.mcaplatformunit.common.mobile.newservice.GreenFinanceTickService.queryUserTickItem",
        jsonObject("custType" to "MERCHANT", "firstBehaviorType" to firstBehaviorType, "uid" to uid),
    )

    @JvmStatic fun submitTick(firstBehaviorType: String, behaviorCode: String): String = request(
        "com.alipay.mcaplatformunit.common.mobile.newservice.GreenFinanceTickService.submitTick",
        jsonObject("custType" to "MERCHANT", "firstBehaviorType" to firstBehaviorType, "uid" to uid, "behaviorCode" to behaviorCode),
    )

    @JvmStatic fun queryExpireMcaPoint(day: Long): String = request(
        "com.alipay.mcaplatformunit.common.mobile.newservice.GreenFinancePageQueryService.queryExpireMcaPoint",
        jsonObject("custType" to "MERCHANT", "profitType" to "MYBK_LOAN_DISCOUNT", "uid" to uid,
            "expireDate" to System.currentTimeMillis() + day * 24 * 60 * 60 * 1000),
    )

    @JvmStatic fun queryAllDonationProjectNew(): String = request(
        "com.alipay.mcaplatformunit.common.mobile.newservice.GreenFinanceDonationService.queryAllDonationProjectNew",
        jsonObject("custType" to "MERCHANT", "subjectType" to "ALL_DONATION", "uid" to uid),
    )

    @JvmStatic fun donation(projectId: String, amount: String): String = request(
        "com.alipay.mcaplatformunit.common.mobile.newservice.GreenFinanceDonationService.donation",
        jsonObject("custType" to "MERCHANT", "donationGold" to amount, "uid" to uid,
            "outbizNo" to System.currentTimeMillis(), "projectId" to projectId),
    )

    @JvmStatic fun consultProveTaskList(): String = request(
        "com.alipay.mcaplatformunit.common.mobile.newservice.GreenFinanceProveTaskService.consultProveTaskList",
        jsonObject("custType" to "MERCHANT", "uid" to uid),
    )

    @JvmStatic fun queryPrizes(campId: String): String = request(
        "com.alipay.loanpromoweb.promo.camp.queryPrizes", jsonObject("campIds" to jsonArray(kotlinx.serialization.json.JsonPrimitive(campId))),
    )

    @JvmStatic fun campTrigger(campId: String): String = request(
        "com.alipay.loanpromoweb.promo.camp.trigger", jsonObject("campId" to campId),
    )

    @JvmStatic fun proveTask(bizType: String, imageUrl: String): String = request(
        "com.alipay.mcaplatformunit.common.mobile.newservice.GreenFinanceProveTaskService.proveTask",
        jsonObject("bizType" to bizType, "custType" to "MERCHANT", "imageUrl" to imageUrl, "uid" to uid),
    )

    @JvmStatic fun queryProveTaskStatus(taskId: String): String = request(
        "com.alipay.mcaplatformunit.common.mobile.newservice.GreenFinanceProveTaskService.queryProveTaskStatus",
        jsonObject("taskId" to taskId, "custType" to "MERCHANT", "uid" to uid),
    )

    @JvmStatic fun queryRankingList(startIndex: Int): String = request(
        "com.alipay.mcaplatformunit.common.mobile.service.GreenFinanceUserInteractionQueryService.queryRankingList",
        jsonObject("clientVersion" to "VERSION2", "custType" to "MERCHANT", "includeMe" to true,
            "onlyRealFriend" to true, "pageLimit" to 10, "rankingScene" to "FRIEND",
            "rankingType" to "OVERALL", "startIndex" to startIndex, "uid" to uid),
    )

    @JvmStatic fun queryGuestIndexPoints(guestId: String): String = request(
        "com.alipay.mcaplatformunit.common.mobile.service.GreenFinanceUserInteractionQueryService.queryGuestIndexPoints",
        jsonObject("clientVersion" to "VERSION2", "custType" to "MERCHANT",
            "guestCustType" to "MERCHANT", "guestUid" to guestId, "uid" to uid),
    )

    @JvmStatic fun batchSteal(bsnIds: JsonArray, collectedUid: String): String = request(
        "com.alipay.mcaplatformunit.common.mobile.service.GreenFinancePointCollectService.batchSteal",
        jsonObject("bsnIds" to bsnIds, "clientVersion" to "VERSION2", "collectedCustType" to "MERCHANT",
            "collectedUid" to collectedUid, "custType" to "MERCHANT", "uid" to uid),
    )

    private fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())
}
