package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager

/** RPC gateway for the standard forest task protocols. */
object ForestTaskRpc {
    fun queryList(): String = request(
        "alipay.antforest.forest.h5.queryTaskList",
        ForestTaskRpcPayloads.queryTaskList(ForestApiVersion.value),
    )

    fun queryListV2(firstTaskType: String): String = request(
        "alipay.antforest.forest.h5.queryTaskList",
        ForestTaskRpcPayloads.queryTaskListV2(firstTaskType, ForestApiVersion.value),
    )

    fun receiveAward(sceneCode: String, taskType: String): String = request(
        "com.alipay.antiep.receiveTaskAward",
        ForestTaskRpcPayloads.receiveTaskAward(sceneCode, taskType),
    )

    fun receiveAwardV2(taskType: String): String = request(
        "com.alipay.antiep.receiveTaskAward",
        ForestTaskRpcPayloads.receiveTaskAwardV2(taskType),
    )

    fun finish(sceneCode: String, taskType: String): String = request(
        "com.alipay.antiep.finishTask",
        ForestTaskRpcPayloads.finishTask(sceneCode, taskType),
    )

    fun sign(entityId: String, userId: String, sceneCode: String): String = request(
        "com.alipay.antiep.sign",
        ForestTaskRpcPayloads.sign(entityId, userId, sceneCode),
    )

    fun initTask(taskType: String): String = request(
        "com.alipay.antiep.initTask",
        ForestTaskRpcPayloads.initTask(taskType),
    )

    private fun request(method: String, payload: String): String = RequestManager.requestString(method, payload)
}
