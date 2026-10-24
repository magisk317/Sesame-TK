package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** RPC gateway for Sesame Credit growth tasks. */
object ZmxyRpc {
    private const val GROWTH_BEHAVIOR = "com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager"
    private const val GROWTH_TASK = "com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager"

    fun queryGrowthGuideToDoList(guideBehaviorId: String?, invokeVersion: String?): String = request(
        "$GROWTH_BEHAVIOR.queryToDoList",
        ZmxyRpcPayloads.queryGrowthGuideToDoList(guideBehaviorId, invokeVersion),
    )

    fun openBehaviorCollect(behaviorId: String): String = request(
        "$GROWTH_BEHAVIOR.openBehaviorCollect", ZmxyRpcPayloads.openBehaviorCollect(behaviorId),
    )

    fun queryDailyQuiz(behaviorId: String): String = request(
        "$GROWTH_TASK.queryDailyQuiz", ZmxyRpcPayloads.queryDailyQuiz(behaviorId),
    )

    fun pushDailyTask(behaviorId: String, bizDate: Long, answerId: String, questionId: String, answerStatus: String?): String =
        request("$GROWTH_TASK.pushDailyTask", ZmxyRpcPayloads.pushTask(behaviorId, bizDate, answerId, questionId, answerStatus))

    fun pushVideoQuizTask(bizDate: Long, answerId: String, questionId: String, answerStatus: String?): String =
        request("$GROWTH_TASK.pushDailyTask", ZmxyRpcPayloads.pushTask("shipingwenda", bizDate, answerId, questionId, answerStatus))

    fun queryScoreProgress(): String? = runCatching {
        request(
            "com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryScoreProgress",
            ZmxyRpcPayloads.queryScoreProgress(),
        )
    }.getOrNull()

    fun collectProgressBall(ballIdList: JsonArray): String? = runCatching {
        request("$GROWTH_BEHAVIOR.collectProgressBall", ZmxyRpcPayloads.collectProgressBall(ballIdList))
    }.getOrNull()

    private fun request(method: String, payload: String): String = RequestManager.requestString(method, payload)
}

object ZmxyRpcPayloads {
    fun queryGrowthGuideToDoList(guideBehaviorId: String?, invokeVersion: String?): String = request(
        jsonObject("guideBehaviorId" to guideBehaviorId.orEmpty().ifEmpty { "yuebao_7d" }, "invokeVersion" to invokeVersion.orEmpty().ifEmpty { "1.0.2025.10.27" }, "switchNewPage" to true),
    )

    fun openBehaviorCollect(behaviorId: String): String = request(jsonObject("behaviorId" to behaviorId))
    fun queryDailyQuiz(behaviorId: String): String = request(jsonObject("behaviorId" to behaviorId))

    fun pushTask(behaviorId: String, bizDate: Long, answerId: String, questionId: String, answerStatus: String?): String = request(
        jsonObject("behaviorId" to behaviorId, "bizDate" to bizDate,
            "extInfo" to jsonObject("answerId" to answerId, "answerStatus" to answerStatus.orEmpty().ifEmpty { "RIGHT" }, "questionId" to questionId)),
    )

    fun queryScoreProgress(): String = request(
        jsonObject("needTotalProcess" to "TRUE", "queryGuideInfo" to true, "switchNewPage" to true),
    )

    fun collectProgressBall(ballIdList: JsonArray): String = request(jsonObject("ballIdList" to ballIdList))

    private fun request(payload: JsonObject): String = jsonArray(payload).toString()
}
