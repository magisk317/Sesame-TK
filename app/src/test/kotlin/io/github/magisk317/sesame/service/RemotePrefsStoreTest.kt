package io.github.magisk317.sesame.service

import android.content.SharedPreferences
import java.util.Collections
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RemotePrefsStoreTest {
    private lateinit var preferences: InMemoryPreferences

    @BeforeEach
    fun setUp() {
        RemotePrefsStore.resetForTests()
        preferences = InMemoryPreferences()
    }

    @AfterEach
    fun tearDown() {
        RemotePrefsStore.resetForTests()
    }

    @Test
    fun `pending clear preserves entitlement and applies writes made after clear`() {
        preferences.putString("config:alice", "old")
        preferences.putLong("config_updated_at:alice", 1L)
        preferences.putString("active_user_id", "old-user")
        preferences.putString("entitlement_token", "keep-token")
        preferences.putBoolean("mobile_entitlement_automation_allowed", true)

        val clear = RemotePrefsStore.clearConfigurations()
        assertEquals(RemotePrefsStore.ConfigurationClearState.QUEUED, clear.state)
        assertTrue(clear.generation > 0L)

        assertEquals(false, RemotePrefsStore.putConfig("alice", "new", clear.generation + 1L))
        assertEquals(false, RemotePrefsStore.putActiveUserId("alice"))

        RemotePrefsStore.installProvider { preferences }

        assertEquals("new", preferences.getString("config:alice", null))
        assertEquals(clear.generation + 1L, preferences.getLong("config_updated_at:alice", 0L))
        assertEquals("alice", preferences.getString("active_user_id", null))
        assertEquals("keep-token", preferences.getString("entitlement_token", null))
        assertTrue(preferences.getBoolean("mobile_entitlement_automation_allowed", false))
        assertTrue(
            preferences.getLong("configuration_reset_generation", 0L) >= clear.generation,
        )
    }

    private class InMemoryPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        fun putString(key: String, value: String) {
            values[key] = value
        }

        fun putLong(key: String, value: Long) {
            values[key] = value
        }

        fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }

        override fun getAll(): Map<String, *> = Collections.unmodifiableMap(values)

        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            values[key] as? Set<String> ?: defValues

        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val updates = linkedMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = put(key, value)

            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = put(key, values)

            override fun putInt(key: String, value: Int): SharedPreferences.Editor = put(key, value)

            override fun putLong(key: String, value: Long): SharedPreferences.Editor = put(key, value)

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = put(key, value)

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = put(key, value)

            override fun remove(key: String): SharedPreferences.Editor {
                removals += key
                updates.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                updates.clear()
                removals.clear()
                return this
            }

            override fun commit(): Boolean {
                if (clear) values.clear()
                removals.forEach(values::remove)
                updates.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                return true
            }

            override fun apply() {
                commit()
            }

            private fun put(key: String, value: Any?): SharedPreferences.Editor {
                removals.remove(key)
                updates[key] = value
                return this
            }
        }
    }
}
