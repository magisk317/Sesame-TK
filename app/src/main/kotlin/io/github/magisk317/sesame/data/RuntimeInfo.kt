package io.github.magisk317.sesame.data

import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import io.github.magisk317.sesame.util.toJsonObject

/** Per-user runtime state persisted separately from task configuration. */
class RuntimeInfo private constructor(
    private val userId: String,
) {
    private val file = Files.runtimeInfoFile(userId)
    private var all: JsonObject = runCatching {
        file?.let { Files.readFromFile(it).toJsonObject() } ?: JsonObject(emptyMap())
    }.getOrDefault(JsonObject(emptyMap()))
    private var current: JsonObject = (all[userId] as? JsonObject) ?: JsonObject(emptyMap())

    enum class RuntimeInfoKey {
        ForestPauseTime,
    }

    @Synchronized
    fun save() {
        file?.let { Files.write2File(all.toString(), it) }
    }

    fun get(key: RuntimeInfoKey): Any? = current[key.name]?.toValue()

    fun getString(key: String): String = current[key]?.toValue()?.toString().orEmpty()

    fun getLong(key: String, default: Long): Long = current[key]?.toValue()?.toString()?.toLongOrNull() ?: default

    fun getBool(key: String, default: Boolean): Boolean = current[key]?.toValue()?.toString()?.toBooleanStrictOrNull() ?: default

    fun getString(key: RuntimeInfoKey): String = getString(key.name)

    fun getLong(key: RuntimeInfoKey): Long = getLong(key.name, 0L)

    fun put(key: RuntimeInfoKey, value: Any?) = put(key.name, value)

    @Synchronized
    fun put(key: String, value: Any?) {
        current = buildJsonObject {
            current.forEach { (existingKey, existingValue) -> put(existingKey, existingValue) }
            put(key, value.toJsonElement())
        }
        all = buildJsonObject {
            all.forEach { (existingKey, existingValue) -> put(existingKey, existingValue) }
            put(userId, current)
        }
        save()
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }

    private fun JsonElement.toValue(): Any? = when (this) {
        JsonNull -> null
        is JsonPrimitive -> content
        is JsonObject -> toString()
        else -> toString()
    }

    companion object {
        private const val TAG = "RuntimeInfo"
        @Volatile
        private var instance: RuntimeInfo? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): RuntimeInfo {
            val currentUserId = UserMap.currentUid.orEmpty()
            return instance?.takeIf { it.userId == currentUserId }
                ?: RuntimeInfo(currentUserId).also { instance = it }
        }
    }
}
