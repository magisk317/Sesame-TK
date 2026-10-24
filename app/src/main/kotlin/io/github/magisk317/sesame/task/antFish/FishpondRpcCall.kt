package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** RPC gateway for the regular fishpond page. */
object FishpondRpcCall {
    private const val APP_NAME = "antfishpond"
    private const val SOURCE = "ch_appcenter__chsub_commonapp"
    private const val VERSION = "20260211.01"
    private const val SCENE = "GameCenter"
    private const val FISHPOND_FACADE = "FishpondRpc"
    private const val TASK_FACADE = "FishpondTaskRpc"
    private const val ANTIEP_FACADE = "AntiepWebRpc"

    fun sync(vararg types: String): String = request(
        "com.alipay.antfishpond.fishpondSyncIndex",
        "fishpondSyncIndex",
        syncPayload(types.toList()),
    )

    fun listTask(): String = request(
        "com.alipay.antfishpond.listTask",
        "listTask",
        commonPayload(),
        facadeName = TASK_FACADE,
    )

    fun sign(signKey: String): String = request(
        "com.alipay.antfishpond.sign",
        "sign",
        signPayload(signKey),
        facadeName = TASK_FACADE,
    )

    fun exchangeManureForRod(taskId: String, manurePaid: Int, rodExchangeCount: Int): String = request(
        "com.alipay.antfishpond.exchangeManureForRod",
        "exchangeManureForRod",
        exchangePayload(taskId, manurePaid, rodExchangeCount),
        facadeName = TASK_FACADE,
    )

    fun finishTask(sceneCode: String, taskType: String, outBizNo: String): String = request(
        "com.alipay.antiep.finishTask",
        "finishTask",
        finishPayload(sceneCode, taskType, outBizNo),
        appName = "antiep",
        facadeName = ANTIEP_FACADE,
    )

    fun receiveTaskAward(sceneCode: String, taskType: String): String = request(
        "com.alipay.antiep.receiveTaskAward",
        "receiveTaskAward",
        receivePayload(sceneCode, taskType),
        appName = "antiep",
        facadeName = ANTIEP_FACADE,
    )

    fun triggerSubplotsActivity(activityType: String, actionType: String): String = request(
        "com.alipay.antfishpond.triggerSubplotsActivity",
        "triggerSubplotsActivity",
        triggerSubplotsPayload(activityType, actionType),
    )

    fun angle(): String = request(
        "com.alipay.antfishpond.fishpondAngle",
        "fishpondAngle",
        anglePayload(),
    )

    internal fun syncPayload(types: List<String>): JsonObject = jsonObject(
        "appMode" to "normal",
        "requestType" to "NORMAL",
        "sceneCode" to SCENE,
        "source" to SOURCE,
        "syncTypeList" to jsonArray(*types.map(::JsonPrimitive).toTypedArray()),
        "version" to VERSION,
    )

    internal fun anglePayload(): JsonObject = commonPayload("bizNo" to "")

    internal fun signPayload(signKey: String): JsonObject = jsonObject("signKey" to signKey)

    internal fun exchangePayload(taskId: String, manurePaid: Int, rodExchangeCount: Int): JsonObject = jsonObject(
        "taskId" to taskId,
        "manurePaid" to manurePaid,
        "rodExchangeCount" to rodExchangeCount,
    )

    internal fun finishPayload(sceneCode: String, taskType: String, outBizNo: String): JsonObject = jsonObject(
        "outBizNo" to outBizNo,
        "requestType" to "H5",
        "sceneCode" to sceneCode,
        "source" to "ANTAIFISH",
        "taskType" to taskType,
    )

    internal fun receivePayload(sceneCode: String, taskType: String): JsonObject = jsonObject(
        "ignoreLimit" to false,
        "requestType" to "H5",
        "sceneCode" to sceneCode,
        "source" to "ANTAIFISH",
        "taskType" to taskType,
    )

    internal fun triggerSubplotsPayload(activityType: String, actionType: String): JsonObject = commonPayload(
        "actionType" to actionType,
        "activityType" to activityType,
    )

    internal fun commonPayload(vararg extra: Pair<String, Any?>): JsonObject = jsonObject(
        "requestType" to "NORMAL",
        "sceneCode" to SCENE,
        "source" to SOURCE,
        "version" to VERSION,
        *extra,
    )

    private fun request(
        operationType: String,
        operation: String,
        payload: JsonObject,
        appName: String = APP_NAME,
        facadeName: String = FISHPOND_FACADE,
    ): String = RequestManager.requestString(
        operationType,
        jsonArray(payload).toString(),
        appName,
        operation,
        facadeName,
    )
}
