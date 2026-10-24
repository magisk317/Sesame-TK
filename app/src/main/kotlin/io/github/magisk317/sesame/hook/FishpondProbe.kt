package io.github.magisk317.sesame.hook

import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Runtime-only diagnostics for the regular fishpond page RPCs. */
object FishpondProbe {
    private const val TAG = "FishpondProbe"
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val sensitiveKeys = setOf("risktoken", "token", "wua", "uid", "userid")

    fun beforeRequest(method: String?, params: Any?) {
        if (method.isNullOrBlank() || !method.startsWith("com.alipay.antfishpond.")) return
        val count = counts.computeIfAbsent(method) { AtomicInteger() }.incrementAndGet()
        val fields = runCatching { fieldShape(params?.toString()) }.getOrDefault("unknown")
        StructuredLog.capture(TAG, "request method=$method count=$count fields=$fields")
    }

    fun afterResponse(method: String?, response: Any?) {
        if (method.isNullOrBlank() || !method.startsWith("com.alipay.antfishpond.")) return
        val summary = runCatching { responseShape(response?.toString()) }.getOrDefault("unknown")
        StructuredLog.capture(TAG, "response method=$method summary=$summary")
    }

    private fun fieldShape(raw: String?): String {
        val element = raw?.let { parse(it) } ?: return "null"
        return shape(element)
    }

    private fun responseShape(raw: String?): String {
        val element = raw?.let { parse(it) } ?: return "null"
        if (element !is JsonObject) return shape(element)
        return element.keys.sorted().joinToString(",")
    }

    private fun shape(element: JsonElement): String = when (element) {
        is JsonObject -> element.keys.sorted().joinToString(",", prefix = "{") { key ->
            if (key.lowercase() in sensitiveKeys) "$key=<redacted>" else "$key:${shape(element.getValue(key))}"
        } + "}"
        else -> when {
            element is JsonPrimitive && element.isString -> "string"
            element.toString().startsWith("[") -> "array"
            else -> "value"
        }
    }

    private fun parse(raw: String): JsonElement? = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(raw)
    }.getOrNull()?.let { root ->
        // requestData is sometimes encoded as a JSON string inside the wrapper.
        if (root is JsonPrimitive && root.isString) {
            runCatching { kotlinx.serialization.json.Json.parseToJsonElement(root.content) }.getOrNull()
        } else root
    }
}
