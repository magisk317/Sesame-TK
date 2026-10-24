package io.github.magisk317.sesame.util

import android.content.Context
import android.content.SharedPreferences
import io.github.magisk317.sesame.service.RemotePrefsStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Private task state and cache store.
 *
 * This store is deliberately separate from user identity, business configuration, host
 * credentials, logs, and entitlement state. Values are encoded as JSON strings so typed
 * collections keep the same serializer contract as the former runtime store.
 */
object TaskRuntimeStore {
    private const val PREFS_NAME = "sesame_task_runtime"
    private const val RESET_GENERATION_KEY = "internal_reset_generation"
    private const val TAG = "TaskRuntimeStore"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Volatile
    private var preferences: SharedPreferences? = null

    @Volatile
    private var appliedResetGeneration = 0L

    @Synchronized
    fun initialize(context: Context) {
        preferences = (context.applicationContext ?: context)
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        applyRemoteResetBarrier()
    }

    internal fun initializeForTests(value: SharedPreferences) {
        preferences = value
        appliedResetGeneration = value.getLong(RESET_GENERATION_KEY, 0L)
    }

    internal fun resetForTests() {
        preferences = null
        appliedResetGeneration = 0L
    }

    fun applyRemoteResetBarrier(
        generation: Long = RemotePrefsStore.getConfigurationResetGeneration(),
    ): Boolean {
        val prefs = preferences ?: return true
        val target = maxOf(generation, appliedResetGeneration)
        val stored = runCatching { prefs.getLong(RESET_GENERATION_KEY, 0L) }.getOrDefault(0L)
        if (target <= maxOf(appliedResetGeneration, stored)) {
            appliedResetGeneration = maxOf(appliedResetGeneration, stored)
            return true
        }
        val committed = runCatching {
            prefs.edit().clear().putLong(RESET_GENERATION_KEY, target).commit()
        }.onFailure {
            StructuredLog.e(TAG, "清理任务运行态失败", it)
        }.getOrDefault(false)
        if (committed) appliedResetGeneration = target
        return committed
    }

    fun getInt(key: String): Int? = raw(key)?.toIntOrNull()

    fun getBoolean(key: String): Boolean? = raw(key)?.toBooleanStrictOrNull()

    fun getString(key: String): String? {
        val value = raw(key) ?: return null
        return runCatching { json.decodeFromString(String.serializer(), value) }
            .getOrElse { value }
    }

    fun getRaw(key: String): String? = raw(key)

    fun <T> get(key: String, serializer: KSerializer<T>): T? {
        val value = raw(key) ?: return null
        return runCatching { json.decodeFromString(serializer, value) }
            .onFailure { StructuredLog.e(TAG, "读取任务运行态失败: key=$key", it) }
            .getOrNull()
    }

    fun <T : Any> getOrCreate(key: String, default: T, serializer: KSerializer<T>): T {
        return get(key, serializer) ?: default.also { put(key, it, serializer) }
    }

    fun put(key: String, value: String): Boolean = commit { putString(key, json.encodeToString(value)) }

    fun put(key: String, value: Boolean): Boolean = commit { putString(key, value.toString()) }

    fun put(key: String, value: Int): Boolean = commit { putString(key, value.toString()) }

    fun put(key: String, value: Any): Boolean =
        commit { putString(key, JsonUtil.formatJson(value, pretty = false)) }

    fun <T> put(key: String, value: T, serializer: KSerializer<T>): Boolean =
        commit { putString(key, json.encodeToString(serializer, value)) }

    fun remove(key: String): Boolean = commit { remove(key) }

    fun clear(): Boolean {
        val generation = maxOf(appliedResetGeneration, RemotePrefsStore.getConfigurationResetGeneration())
        val prefs = preferences ?: return false
        return runCatching {
            prefs.edit().clear().putLong(RESET_GENERATION_KEY, generation).commit()
        }.onFailure {
            StructuredLog.e(TAG, "清理任务运行态失败", it)
        }.getOrDefault(false)
    }

    private fun raw(key: String): String? = runCatching {
        preferences?.getString(key, null)
    }.onFailure {
        StructuredLog.e(TAG, "读取任务运行态原始值失败: key=$key", it)
    }.getOrNull()

    private fun commit(block: SharedPreferences.Editor.() -> Unit): Boolean {
        val prefs = preferences ?: return false
        return runCatching { prefs.edit().apply(block).commit() }
            .onFailure { StructuredLog.e(TAG, "写入任务运行态失败", it) }
            .getOrDefault(false)
    }
}
