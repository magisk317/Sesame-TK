package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager

/** RPC gateway for the annual-review task component. */
object AnnualReviewRpc {
    val OPERATION_IDENTIFY = AnnualReviewRpcPayloads.OPERATION_IDENTIFY
    val COMPONENT_PREFIX = AnnualReviewRpcPayloads.COMPONENT_PREFIX
    val QUERY_COMPONENT = AnnualReviewRpcPayloads.QUERY_COMPONENT
    val APPLY_COMPONENT = AnnualReviewRpcPayloads.APPLY_COMPONENT
    val PROCESS_COMPONENT = AnnualReviewRpcPayloads.PROCESS_COMPONENT
    val GET_REWARD_COMPONENT = AnnualReviewRpcPayloads.GET_REWARD_COMPONENT

    fun queryTasks(): String? = request(AnnualReviewRpcPayloads.query())
    fun applyTask(code: String): String? = request(AnnualReviewRpcPayloads.apply(code))
    fun processTask(code: String, recordNo: String): String? = request(AnnualReviewRpcPayloads.process(code, recordNo))
    fun getReward(code: String, recordNo: String): String? = request(AnnualReviewRpcPayloads.getReward(code, recordNo))

    private fun request(payload: String): String? = runCatching {
        RequestManager.requestString("alipay.imasp.program.programInvoke", payload)
    }.getOrNull()
}
