package io.github.magisk317.sesame.entity

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RpcDebugItemRaw(
    val name: String,
    val method: String,
    val requestData: JsonElement?,
    val description: String,
)

object RpcDebugCodec {
    private fun json(pretty: Boolean = false) = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        prettyPrint = pretty
    }
    private val listSerializer = ListSerializer(RpcDebugEntity.serializer())

    @JvmStatic
    fun decodeList(value: String): List<RpcDebugEntity> = json().decodeFromString(listSerializer, value)

    @JvmStatic
    fun encodeList(value: List<RpcDebugEntity>, pretty: Boolean = false): String =
        json(pretty).encodeToString(listSerializer, value)

    @JvmStatic
    fun parseFields(value: String): RpcDebugItemRaw {
        val root = json().decodeFromString(JsonElement.serializer(), value) as? JsonObject
            ?: return RpcDebugItemRaw("", "", null, "")
        fun text(vararg names: String): String = names.firstNotNullOfOrNull { root[it]?.jsonPrimitive?.content }?.trim().orEmpty()
        return RpcDebugItemRaw(
            name = text("name", "Name"),
            method = text("method", "methodName", "Method"),
            requestData = root["requestData"] ?: root["RequestData"],
            description = text("description", "Description", "desc", "Desc"),
        )
    }

    @JvmStatic
    fun format(value: String): String = json(true).encodeToString(
        JsonElement.serializer(),
        json().decodeFromString(JsonElement.serializer(), value),
    )
}
