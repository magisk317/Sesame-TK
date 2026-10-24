package io.github.magisk317.sesame.util

import android.content.SharedPreferences
import io.github.magisk317.sesame.service.RemotePrefsStore
import java.util.Collections
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TaskRuntimeStoreTest {
    private lateinit var preferences: InMemoryPreferences

    @BeforeEach
    fun setUp() {
        RemotePrefsStore.resetForTests()
        preferences = InMemoryPreferences()
        TaskRuntimeStore.initializeForTests(preferences)
    }

    @AfterEach
    fun tearDown() {
        TaskRuntimeStore.resetForTests()
        RemotePrefsStore.resetForTests()
    }

    @Test
    fun `typed values and reset barrier stay in the task store`() {
        assertTrue(TaskRuntimeStore.put("count", 4))
        assertTrue(TaskRuntimeStore.put("enabled", true))
        assertTrue(TaskRuntimeStore.put("date", "2026-09-02"))
        assertEquals(4, TaskRuntimeStore.getInt("count"))
        assertEquals(true, TaskRuntimeStore.getBoolean("enabled"))
        assertEquals("2026-09-02", TaskRuntimeStore.getString("date"))

        assertTrue(TaskRuntimeStore.applyRemoteResetBarrier(42L))
        assertNull(TaskRuntimeStore.getInt("count"))
        assertNull(TaskRuntimeStore.getBoolean("enabled"))
        assertNull(TaskRuntimeStore.getString("date"))
        assertEquals(42L, preferences.getLong("internal_reset_generation", 0L))
    }

    private class InMemoryPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = Collections.unmodifiableMap(values)

        override fun getString(key: String, defValue: String?): String? =
            values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
            values[key] as? Set<String> ?: defValues

        override fun getInt(key: String, defValue: Int): Int =
            values[key] as? Int ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            values[key] as? Long ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            values[key] as? Float ?: defValue

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

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
