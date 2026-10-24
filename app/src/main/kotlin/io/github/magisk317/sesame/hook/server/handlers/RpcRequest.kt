package io.github.magisk317.sesame.hook.server.handlers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class RpcRequest(
    val methodName: String = "",
    // 允许接收 String 或 JSON Object
    val requestData: JsonElement? = null,
    // 可选：antiep 等 facade RPC 需要的调用元数据
    val appName: String? = null,
    val operation: String? = null,
    val facadeName: String? = null
) {
    /**
     * 将 requestData 安全转换为字符串
     */
    fun getRequestDataString(): String {
        return when (requestData) {
            is JsonPrimitive -> requestData.content
            null -> ""
            else -> requestData.toString()
        }
    }
}
