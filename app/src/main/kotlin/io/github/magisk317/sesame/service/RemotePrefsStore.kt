package io.github.magisk317.sesame.service

import io.github.magisk317.sesame.util.StructuredLog
import android.content.SharedPreferences
import com.magisk317.mobile.entitlement.MobileEntitlementGate
import com.magisk317.mobile.entitlement.MobileEntitlementKeys
import com.magisk317.mobile.entitlement.MobileEntitlementPublishedState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * The small LibXposed IPC surface shared by the module UI and hooked host processes.
 *
 * Runtime caches, task history, credentials, and business files do not belong here. Values in
 * this group are either scalar control values or signed/validated snapshots required by a hook.
 */
object RemotePrefsStore {
    const val GROUP = "sesame_runtime"

    enum class ConfigurationClearState {
        CLEARED,
        QUEUED,
    }

    data class ConfigurationClearResult(
        val state: ConfigurationClearState,
        val generation: Long,
    )

    private const val KEY_ACTIVE_USER_ID = "active_user_id"
    private const val KEY_ENTITLEMENT_TOKEN = MobileEntitlementKeys.PUBLISHED_TOKEN
    private const val KEY_ENTITLEMENT_ALLOWED = MobileEntitlementKeys.PUBLISHED_ALLOWED
    private const val KEY_CONFIGURATION_RESET_GENERATION = "configuration_reset_generation"
    private const val CONFIG_PREFIX = "config:"
    private const val CONFIG_UPDATED_AT_PREFIX = "config_updated_at:"
    private const val CONFIG_BACKUP_PREFIX = "config_backup:"
    private const val CONFIG_BACKUP_INDEX_PREFIX = "config_backup_index:"
    private const val CONFIG_BACKUP_SLOTS = 3

    data class ConfigSnapshot(
        val json: String,
        val updatedAt: Long,
    )

    private data class PendingConfig(
        val json: String,
        val updatedAt: Long,
    )

    private val provider = AtomicReference<(() -> SharedPreferences?)?>(null)
    private val pendingConfigs = ConcurrentHashMap<String, PendingConfig>()
    private val pendingConfigRemovals = ConcurrentHashMap.newKeySet<String>()
    private val pendingLock = Any()

    @Volatile
    private var pendingConfigurationClear = false

    @Volatile
    private var pendingConfigurationResetGeneration = 0L

    @Volatile
    private var pendingActiveUserSet = false

    @Volatile
    private var pendingActiveUserId: String? = null

    @Volatile
    private var pendingEntitlementSet = false

    @Volatile
    private var pendingEntitlementToken: String? = null

    @Volatile
    private var pendingEntitlementAllowedSet = false

    @Volatile
    private var pendingEntitlementAllowed = false

    fun installProvider(value: (() -> SharedPreferences?)?) {
        provider.set(value)
        if (value != null) flushPending()
    }

    internal fun resetForTests() {
        provider.set(null)
        synchronized(pendingLock) {
            pendingConfigs.clear()
            pendingConfigRemovals.clear()
            pendingConfigurationClear = false
            pendingConfigurationResetGeneration = 0L
            pendingActiveUserSet = false
            pendingActiveUserId = null
            pendingEntitlementSet = false
            pendingEntitlementToken = null
            pendingEntitlementAllowedSet = false
            pendingEntitlementAllowed = false
        }
    }

    fun available(): Boolean = preferences() != null

    fun getBoolean(key: String, defaultValue: Boolean): Boolean = runCatching {
        preferences()?.getBoolean(key, defaultValue) ?: defaultValue
    }.getOrDefault(defaultValue)

    fun getString(key: String, defaultValue: String = ""): String = runCatching {
        preferences()?.getString(key, defaultValue) ?: defaultValue
    }.getOrDefault(defaultValue)

    fun getInt(key: String, defaultValue: Int): Int = runCatching {
        preferences()?.getInt(key, defaultValue) ?: defaultValue
    }.getOrDefault(defaultValue)

    fun getActiveUserId(): String? = runCatching {
        preferences()?.getString(KEY_ACTIVE_USER_ID, null)
    }.getOrNull()

    fun putActiveUserId(userId: String?): Boolean {
        return synchronized(pendingLock) {
            val prefs = preferences()
            if (prefs == null) {
                queueActiveUser(userId)
                return@synchronized false
            }
            if (pendingConfigurationClear) {
                queueActiveUser(userId)
                return@synchronized false
            }
            val committed = edit {
                if (userId.isNullOrBlank()) remove(KEY_ACTIVE_USER_ID)
                else putString(KEY_ACTIVE_USER_ID, userId)
            }
            if (!committed) queueActiveUser(userId)
            committed
        }
    }

    fun getEntitlementToken(): String? = runCatching {
        preferences()?.getString(KEY_ENTITLEMENT_TOKEN, null)?.takeIf(String::isNotBlank)
    }.getOrNull()

    fun getEntitlementAllowed(): Boolean = runCatching {
        preferences()?.getBoolean(KEY_ENTITLEMENT_ALLOWED, false) ?: false
    }.getOrDefault(false)

    fun getEntitlementState(): MobileEntitlementPublishedState? = runCatching {
        MobileEntitlementGate.read(preferences())
    }.getOrNull()

    /** Monotonic signal used to invalidate process-private runtime state after a UI reset. */
    fun getConfigurationResetGeneration(): Long = runCatching {
        preferences()?.getLong(KEY_CONFIGURATION_RESET_GENERATION, 0L) ?: 0L
    }.getOrDefault(0L)

    fun putEntitlementAllowed(allowed: Boolean): Boolean {
        val prefs = preferences()
        if (prefs == null) {
            queueEntitlementAllowed(allowed)
            return false
        }
        val committed = edit { putBoolean(KEY_ENTITLEMENT_ALLOWED, allowed) }
        if (!committed) queueEntitlementAllowed(allowed)
        return committed
    }

    fun putEntitlementState(allowed: Boolean, token: String?): Boolean {
        val prefs = preferences()
        if (prefs == null) {
            queueEntitlementState(allowed, token)
            return false
        }
        val committed = edit {
            putBoolean(KEY_ENTITLEMENT_ALLOWED, allowed)
            if (token.isNullOrBlank()) remove(KEY_ENTITLEMENT_TOKEN)
            else putString(KEY_ENTITLEMENT_TOKEN, token)
        }
        if (!committed) queueEntitlementState(allowed, token)
        return committed
    }

    fun putEntitlementToken(token: String?): Boolean {
        val prefs = preferences()
        if (prefs == null) {
            queueEntitlementToken(token)
            return false
        }
        val committed = edit {
            if (token.isNullOrBlank()) remove(KEY_ENTITLEMENT_TOKEN)
            else putString(KEY_ENTITLEMENT_TOKEN, token)
        }
        if (!committed) queueEntitlementToken(token)
        return committed
    }

    fun getConfig(userId: String?): String? = getConfigSnapshot(userId)?.json

    fun getConfigSnapshot(userId: String?): ConfigSnapshot? = runCatching {
        val prefs = preferences() ?: return null
        val key = configKey(userId)
        val json = prefs.getString(key, null)?.takeIf(String::isNotBlank) ?: return null
        ConfigSnapshot(json, prefs.getLong(configUpdatedAtKey(userId), 0L))
    }.getOrNull()

    fun getConfigUpdatedAt(userId: String?): Long = runCatching {
        preferences()?.getLong(configUpdatedAtKey(userId), 0L) ?: 0L
    }.getOrDefault(0L)

    /**
     * 保存前调用：把 remote 里的当前配置滚动备份到固定槽位（保留最近 3 份）。
     * 任何一次误写入都可以从这里找回上一版用户数据。
     */
    fun backupConfig(userId: String?): Boolean {
        val previous = getConfigSnapshot(userId)?.json ?: return false
        return runCatching {
            val prefs = preferences() ?: return false
            val idx = runCatching {
                prefs.getInt(backupIndexKey(userId), 0)
            }.getOrDefault(0)
            val slot = (idx % CONFIG_BACKUP_SLOTS) + 1
            val committed = edit {
                putString(backupSlotKey(userId, slot), previous)
                putInt(backupIndexKey(userId), slot)
            }
            if (committed) {
                StructuredLog.record(
                    "RemotePrefsStore",
                    "已滚动备份用户配置到槽位 $slot",
                )
            }
            committed
        }.getOrDefault(false)
    }

    fun getConfigBackups(userId: String?): List<String> = runCatching {
        val prefs = preferences() ?: return emptyList()
        (1..CONFIG_BACKUP_SLOTS).mapNotNull { slot ->
            prefs.getString(backupSlotKey(userId, slot), null)?.takeIf(String::isNotBlank)
        }
    }.getOrDefault(emptyList())

    /**
     * Publishes a snapshot only when it is at least as new as the current remote value. This
     * prevents a delayed hook fallback from overwriting a newer UI edit.
     */
    fun putConfig(userId: String?, value: String, updatedAt: Long = System.currentTimeMillis()): Boolean {
        return synchronized(pendingLock) {
            val normalizedUpdatedAt = updatedAt.coerceAtLeast(1L)
            val key = configKey(userId)
            val prefs = preferences()
            if (prefs == null) {
                pendingConfigs.compute(key) { _, previous ->
                    if (previous == null || normalizedUpdatedAt >= previous.updatedAt) {
                        PendingConfig(value, normalizedUpdatedAt)
                    } else {
                        previous
                    }
                }
                pendingConfigRemovals.remove(key)
                return@synchronized false
            }
            if (pendingConfigurationClear) {
                pendingConfigs.compute(key) { _, previous ->
                    if (previous == null || normalizedUpdatedAt >= previous.updatedAt) {
                        PendingConfig(value, normalizedUpdatedAt)
                    } else {
                        previous
                    }
                }
                pendingConfigRemovals.remove(key)
                return@synchronized false
            }

            val currentUpdatedAt = runCatching {
                prefs.getLong(configUpdatedAtKey(userId), 0L)
            }.getOrDefault(0L)
            if (currentUpdatedAt > normalizedUpdatedAt) return@synchronized true

            val committed = edit {
                putString(key, value)
                putLong(configUpdatedAtKey(userId), normalizedUpdatedAt)
            }
            if (!committed) {
                pendingConfigs[key] = PendingConfig(value, normalizedUpdatedAt)
            }
            committed
        }
    }

    fun removeConfig(userId: String?): Boolean {
        return synchronized(pendingLock) {
            val key = configKey(userId)
            val prefs = preferences()
            if (prefs == null) {
                pendingConfigs.remove(key)
                pendingConfigRemovals.add(key)
                return@synchronized false
            }
            if (pendingConfigurationClear) {
                pendingConfigs.remove(key)
                pendingConfigRemovals.add(key)
                return@synchronized false
            }
            edit {
                remove(key)
                remove(configUpdatedAtKey(userId))
            }
        }
    }

    fun clearConfigurations(): ConfigurationClearResult {
        return synchronized(pendingLock) {
            val prefs = preferences()
            val generation = nextResetGeneration(prefs)
            if (prefs == null) {
                pendingConfigurationClear = true
                pendingConfigurationResetGeneration = maxOf(
                    pendingConfigurationResetGeneration,
                    generation,
                )
                pendingConfigs.clear()
                pendingConfigRemovals.clear()
                pendingActiveUserSet = false
                pendingActiveUserId = null
                return@synchronized ConfigurationClearResult(ConfigurationClearState.QUEUED, generation)
            }
            val committed = edit {
                prefs.all.keys
                    .filter { it.startsWith(CONFIG_PREFIX) || it.startsWith(CONFIG_UPDATED_AT_PREFIX) }
                    .forEach { key -> remove(key) }
                remove(KEY_ACTIVE_USER_ID)
                putLong(KEY_CONFIGURATION_RESET_GENERATION, generation)
            }
            if (!committed) {
                pendingConfigurationClear = true
                pendingConfigurationResetGeneration = maxOf(
                    pendingConfigurationResetGeneration,
                    generation,
                )
                pendingConfigs.clear()
                pendingConfigRemovals.clear()
                pendingActiveUserSet = false
                pendingActiveUserId = null
            }
            ConfigurationClearResult(
                state = if (committed) ConfigurationClearState.CLEARED else ConfigurationClearState.QUEUED,
                generation = generation,
            )
        }
    }

    fun edit(block: SharedPreferences.Editor.() -> Unit): Boolean {
        val prefs = preferences() ?: return false
        return runCatching { prefs.edit().apply(block).commit() }.getOrDefault(false)
    }

    private fun preferences(): SharedPreferences? = runCatching {
        provider.get()?.invoke()
    }.getOrNull()

    private fun flushPending() {
        val prefs = preferences() ?: return
        synchronized(pendingLock) {
            runCatching {
                val editor = prefs.edit()
                val clearRequested = pendingConfigurationClear

                if (clearRequested) {
                    prefs.all.keys
                        .filter { it.startsWith(CONFIG_PREFIX) || it.startsWith(CONFIG_UPDATED_AT_PREFIX) }
                        .forEach { key -> editor.remove(key) }
                    editor.remove(KEY_ACTIVE_USER_ID)
                    if (pendingConfigurationResetGeneration > 0L) {
                        editor.putLong(
                            KEY_CONFIGURATION_RESET_GENERATION,
                            pendingConfigurationResetGeneration,
                        )
                    }
                }

                if (pendingActiveUserSet) {
                    if (pendingActiveUserId.isNullOrBlank()) editor.remove(KEY_ACTIVE_USER_ID)
                    else editor.putString(KEY_ACTIVE_USER_ID, pendingActiveUserId)
                }
                if (pendingEntitlementSet) {
                    if (pendingEntitlementToken.isNullOrBlank()) editor.remove(KEY_ENTITLEMENT_TOKEN)
                    else editor.putString(KEY_ENTITLEMENT_TOKEN, pendingEntitlementToken)
                }
                if (pendingEntitlementAllowedSet) {
                    editor.putBoolean(KEY_ENTITLEMENT_ALLOWED, pendingEntitlementAllowed)
                }

                pendingConfigRemovals.forEach { key ->
                    editor.remove(key)
                    editor.remove(configUpdatedAtKeyFromConfigKey(key))
                }

                if (clearRequested || !pendingConfigurationClear) {
                    pendingConfigs.forEach { (key, pending) ->
                        val updatedAtKey = configUpdatedAtKeyFromConfigKey(key)
                        val currentUpdatedAt = runCatching { prefs.getLong(updatedAtKey, 0L) }.getOrDefault(0L)
                        if (currentUpdatedAt <= pending.updatedAt && key !in pendingConfigRemovals) {
                            editor.putString(key, pending.json)
                            editor.putLong(updatedAtKey, pending.updatedAt)
                        }
                    }
                }

                if (!editor.commit()) return
                pendingConfigurationClear = false
                pendingConfigurationResetGeneration = 0L
                pendingActiveUserSet = false
                pendingActiveUserId = null
                pendingEntitlementSet = false
                pendingEntitlementToken = null
                pendingEntitlementAllowedSet = false
                pendingEntitlementAllowed = false
                pendingConfigRemovals.clear()
                pendingConfigs.clear()
            }
        }
    }

    private fun configKey(userId: String?): String = "$CONFIG_PREFIX${userId.orEmpty()}"

    private fun configUpdatedAtKey(userId: String?): String =
        "$CONFIG_UPDATED_AT_PREFIX${userId.orEmpty()}"

    private fun backupSlotKey(userId: String?, slot: Int): String =
        "$CONFIG_BACKUP_PREFIX${configKey(userId)}:$slot"

    private fun backupIndexKey(userId: String?): String =
        "$CONFIG_BACKUP_INDEX_PREFIX${userId.orEmpty()}"

    private fun configUpdatedAtKeyFromConfigKey(key: String): String =
        "$CONFIG_UPDATED_AT_PREFIX${key.removePrefix(CONFIG_PREFIX)}"

    private fun queueActiveUser(userId: String?) = synchronized(pendingLock) {
        pendingActiveUserSet = true
        pendingActiveUserId = userId?.takeIf(String::isNotBlank)
    }

    private fun queueEntitlementAllowed(allowed: Boolean) = synchronized(pendingLock) {
        pendingEntitlementAllowedSet = true
        pendingEntitlementAllowed = allowed
    }

    private fun queueEntitlementToken(token: String?) = synchronized(pendingLock) {
        pendingEntitlementSet = true
        pendingEntitlementToken = token?.takeIf(String::isNotBlank)
    }

    private fun queueEntitlementState(allowed: Boolean, token: String?) = synchronized(pendingLock) {
        pendingEntitlementAllowedSet = true
        pendingEntitlementAllowed = allowed
        pendingEntitlementSet = true
        pendingEntitlementToken = token?.takeIf(String::isNotBlank)
    }

    private fun nextResetGeneration(prefs: SharedPreferences?): Long {
        val current = runCatching {
            prefs?.getLong(KEY_CONFIGURATION_RESET_GENERATION, 0L) ?: 0L
        }.getOrDefault(0L)
        return maxOf(System.currentTimeMillis(), current + 1L)
    }
}
