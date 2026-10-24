package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject

/** RPC gateway for the Sesame Credit alchemy activity. */
object AlchemyRpc {
    private const val VERSION = "2025-10-22"
    const val SCENE_ZML = "zml"
    const val SCENE_ALCHEMY = "alchemy"

    fun alchemyQueryHome(): String = request("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryHome", AlchemyRpcPayloads.empty())
    fun alchemyExecute(): String = request("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.alchemy", AlchemyRpcPayloads.empty())
    fun alchemyQueryCheckIn(sceneCode: String): String = request(
        "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists",
        AlchemyRpcPayloads.queryCheckIn(sceneCode, VERSION),
    )

    /** Uses the scene code observed in the real Alipay request. */
    fun alchemyQueryCheckIn(): String = alchemyQueryCheckIn(SCENE_ALCHEMY)
    fun alchemyQueryTimeLimitedTask(): String = request(
        "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.queryTask", AlchemyRpcPayloads.empty(),
    )
    fun alchemyCompleteTimeLimitedTask(templateId: String): String = request(
        "com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.completeTask",
        AlchemyRpcPayloads.completeTimeLimitedTask(templateId),
    )
    fun alchemyQueryListV3(): String = request(
        "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3",
        AlchemyRpcPayloads.queryListV3(),
    )
    /** 主页面任务列表（不带 version=alchemy，返回 zml_* 模板池） */
    fun mainPageQueryListV3(): String = request(
        "com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3",
        AlchemyRpcPayloads.queryListV3NoVersion(),
    )
    fun claimAward(): String = request("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.claimAward", AlchemyRpcPayloads.empty())

    private fun request(method: String, payload: String): String = RequestManager.requestString(method, payload)
}

object AlchemyRpcPayloads {
    fun empty(): String = request(jsonObject())
    fun queryCheckIn(sceneCode: String, version: String): String = request(jsonObject("sceneCode" to sceneCode, "version" to version))
    fun completeTimeLimitedTask(templateId: String): String = request(jsonObject("templateId" to templateId))
    fun queryListV3(): String = request(
        jsonObject(
            "chInfo" to "", "deliverStatus" to "", "deliveryTemplateId" to "",
            "searchSubscribeTask" to true, "version" to "alchemy",
        ),
    )
    fun queryListV3NoVersion(): String = request(
        jsonObject(
            "chInfo" to "", "deliverStatus" to "", "deliveryTemplateId" to "",
            "searchSubscribeTask" to true,
        ),
    )
    private fun request(payload: kotlinx.serialization.json.JsonObject): String = jsonArray(payload).toString()
}
