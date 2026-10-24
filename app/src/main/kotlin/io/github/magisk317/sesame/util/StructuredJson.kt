package io.github.magisk317.sesame.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val structuredJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

fun String.toJsonElement(): JsonElement = runCatching {
    structuredJson.decodeFromString(JsonElement.serializer(), this)
}.getOrElse { JsonNull }

fun String.toJsonObject(): JsonObject = toJsonElement() as? JsonObject ?: buildJsonObject { }

fun String.toJsonArray(): JsonArray = toJsonElement() as? JsonArray ?: buildJsonArray { }

fun JsonObject.opt(key: String): JsonElement? = this[key]
fun JsonObject.has(key: String): Boolean = containsKey(key)
fun JsonObject.optString(key: String, default: String = ""): String = this[key]?.asJsonString() ?: default
fun JsonObject.optBoolean(key: String, default: Boolean = false): Boolean = this[key]?.asJsonBoolean() ?: default
fun JsonObject.optInt(key: String, default: Int = 0): Int = this[key]?.asJsonString()?.toIntOrNull() ?: default
fun JsonObject.optLong(key: String, default: Long = 0L): Long = this[key]?.asJsonString()?.toLongOrNull() ?: default
fun JsonObject.optDouble(key: String, default: Double = 0.0): Double = this[key]?.asJsonString()?.toDoubleOrNull() ?: default
fun JsonObject.optJSONObject(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.optJSONArray(key: String): JsonArray? = this[key] as? JsonArray
fun JsonElement.atPath(path: String): JsonElement? {
    var current: JsonElement = this
    for (part in path.split('.').filter(String::isNotBlank)) {
        current = when {
            part.startsWith('[') && part.endsWith(']') -> {
                val index = part.substring(1, part.length - 1).toIntOrNull() ?: return null
                (current as? JsonArray)?.getOrNull(index) ?: return null
            }
            else -> (current as? JsonObject)?.get(part) ?: return null
        }
    }
    return current
}
fun JsonObject.atPath(path: String): JsonElement? = (this as JsonElement).atPath(path)
fun JsonElement.asText(default: String = ""): String =
    (this as? JsonPrimitive)?.contentOrNull ?: default
fun JsonObject.textAt(path: String, default: String = ""): String =
    atPath(path)?.asText(default) ?: default
fun JsonObject.getString(key: String): String = optString(key).also { require(it.isNotEmpty() || containsKey(key)) { "Missing JSON field: $key" } }
fun JsonObject.getBoolean(key: String): Boolean = optBoolean(key)
fun JsonObject.getInt(key: String): Int = optInt(key).also { require(containsKey(key)) { "Missing JSON field: $key" } }
fun JsonObject.getLong(key: String): Long = optLong(key).also { require(containsKey(key)) { "Missing JSON field: $key" } }
fun JsonObject.getDouble(key: String): Double = optDouble(key).also { require(containsKey(key)) { "Missing JSON field: $key" } }
fun JsonObject.getJSONObject(key: String): JsonObject = optJSONObject(key) ?: error("Missing JSON object: $key")
fun JsonObject.getJSONArray(key: String): JsonArray = optJSONArray(key) ?: error("Missing JSON array: $key")

fun JsonArray.length(): Int = size
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun JsonArray.get(index: Int): JsonElement = this[index]
fun JsonArray.opt(index: Int): JsonElement? = getOrNull(index)
fun JsonArray.getJSONObject(index: Int): JsonObject = get(index).jsonObject
fun JsonArray.optJSONObject(index: Int): JsonObject? = getOrNull(index) as? JsonObject
fun JsonArray.getString(index: Int): String = get(index).asJsonString()
fun JsonArray.optString(index: Int, default: String = ""): String = getOrNull(index)?.asJsonString() ?: default
fun JsonArray.toStringList(default: String = ""): List<String> = map { it.asText(default) }

fun jsonObject(vararg values: Pair<String, Any?>): JsonObject = buildJsonObject {
    values.forEach { (key, value) -> if (value != null) put(key, value.toJsonElement()) }
}

fun jsonArray(vararg values: JsonElement): JsonArray = buildJsonArray { values.forEach(::add) }

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}

private fun JsonElement.asJsonString(): String = (this as? JsonPrimitive)?.content ?: ""
private fun JsonElement.asJsonBoolean(): Boolean = (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
