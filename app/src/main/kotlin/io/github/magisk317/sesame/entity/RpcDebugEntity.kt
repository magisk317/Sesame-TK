package io.github.magisk317.sesame.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Persisted RPC debug configuration. Dynamic payloads remain JSON elements. */
@Serializable
data class RpcDebugEntity(
    var name: String = "",

    // 🔥 关键：同时支持 "method" (默认), "methodName", "Method"
    var method: String = "",

    var requestData: JsonElement? = null,

    var id: String = "",

    var description: String = ""
) {
    init {
        if (id.isEmpty()) {
            id = System.currentTimeMillis().toString()
        }
    }

    fun getDisplayName(): String {
        return name.ifEmpty { method }
    }

    fun getRequestDataString(): String {
        val payload = requestData
        return when (payload) {
            is JsonPrimitive -> payload.content
            is JsonElement -> payload.toString()
            else -> "[{}]"
        }
    }
}
