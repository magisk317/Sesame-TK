package io.github.magisk317.sesame.entity

import io.github.magisk317.sesame.hook.internal.SecurityBodyHelper
import io.github.magisk317.sesame.util.jsonObject
import kotlin.concurrent.Volatile

/**
 * 表示一个 RPC（远程过程调用）实体，用于封装请求和响应数据。
 * 提供线程安全的响应和错误标识。
 */
class RpcEntity @JvmOverloads constructor(
    val requestMethod: String? = null,
    val requestData: String? = null,
    val requestRelation: String? = null,
    val appName: String? = null,
    val methodName: String? = "taskFeedback",
    val facadeName: String? = null
) {
    @Volatile
    var hasResult = false
    @Volatile
    var hasError = false
    @Volatile
    var responseObject: Any? = null
    @Volatile
    var responseString: String? = null
    /**
     * 设置响应结果并标记请求已完成。
     *
     * @param result    响应的对象
     * @param resultStr 响应的字符串形式
     */
    fun setResponseObject(result: Any?, resultStr: String?) {
        this.hasResult = true // 标记请求有结果
        this.responseObject = result
        // 确保 responseString 不为 null，避免上层 NPE
        this.responseString = resultStr ?: ""
    }

    /**
     * 标记请求为错误状态。
     */
    fun setError() {
        this.hasError = true // 标记请求发生错误
        // 确保 responseString 不为 null，避免上层 NPE
        if (this.responseString == null) {
            this.responseString = ""
        }
    }

    val rpcFullRequestData: String
        get() {
            return jsonObject(
                "__apiCallStartTime" to System.currentTimeMillis(),
                "apiCallLink" to "XRiverNotFound",
                "appName" to appName,
                "execEngine" to "XRiver",
                "__apiNativeCallId" to "native_" + (100..2000).random(),
                "facadeName" to facadeName,
                "methodName" to methodName,
                "operationType" to requestMethod,
                "requestData" to requestData,
                "relationLocal" to requestRelation,
                "wua" to SecurityBodyHelper.getSecurityBodyData(4).toString(),
                "useWua" to true,
                "disableLimitView" to true,
            ).toString()
        }
}
