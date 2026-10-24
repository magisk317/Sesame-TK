package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager

/** Transport gateway for open-green draw campaigns. */
object ForestDrawRpc {
    fun enter(activityId: String?, sceneCode: String, source: String): String = request(
        "com.alipay.antiepdrawprod.enterDrawActivityopengreen",
        ForestDrawRpcPayloads.enter(activityId, sceneCode, source),
    )

    fun list(sceneCode: String, source: String, businessSource: String? = null): String = request(
        "com.alipay.antieptask.listTaskopengreen",
        ForestDrawRpcPayloads.list(sceneCode, source, businessSource),
    )

    fun draw(activityId: String, sceneCode: String, source: String, userId: String?): String = request(
        "com.alipay.antiepdrawprod.drawopengreen",
        ForestDrawRpcPayloads.draw(activityId, sceneCode, source, userId ?: "null"),
    )

    fun batchDraw(
        activityId: String,
        sceneCode: String,
        source: String,
        userId: String?,
        times: Int,
    ): String = request(
        "com.alipay.antiepdrawprod.batchDrawopengreen",
        ForestDrawRpcPayloads.batchDraw(activityId, sceneCode, source, userId ?: "null", times),
    )

    fun receive(source: String, sceneCode: String, taskType: String): String = request(
        "com.alipay.antieptask.receiveTaskAwardopengreen",
        ForestDrawRpcPayloads.receive(source, sceneCode, taskType),
    )

    fun exchange(activityId: String, sceneCode: String, source: String, taskSceneCode: String, taskType: String): String = request(
        "com.alipay.antiepdrawprod.exchangeTimesFromTaskopengreen",
        ForestDrawRpcPayloads.exchange(activityId, sceneCode, source, taskSceneCode, taskType),
    )

    fun finishAdTask(taskType: String, sceneCode: String): String = request(
        "com.alipay.antiep.finishTask", ForestDrawRpcPayloads.finishTask4(taskType, sceneCode),
    )

    fun finishTask(taskType: String, sceneCode: String): String = request(
        "com.alipay.antieptask.finishTaskopengreen", ForestDrawRpcPayloads.finishTask(taskType, sceneCode),
    )

    private fun request(method: String, payload: String): String = RequestManager.requestString(method, payload)
}
