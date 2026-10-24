package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestEnergyRainRpcTest {
    @Test
    fun `energy rain payloads preserve captured protocol`() {
        val home = ForestEnergyRainRpcPayloads.queryHome()
        assertEquals("senlinguangchuangrukou", home.getString("source"))
        assertEquals("20250813", home.getString("version"))
        assertTrue(ForestEnergyRainRpcPayloads.queryCanGrantList().isEmpty())
        assertEquals(123L, ForestEnergyRainRpcPayloads.grantChance("123").getString("targetUserId").toLong())
        assertEquals("USER", ForestEnergyRainRpcPayloads.grantChance("USER").getString("targetUserId"))
    }

    @Test
    fun `settlement and end list payloads keep required fields`() {
        val previousVersion = ForestApiVersion.value
        ForestApiVersion.value = "test-version"
        try {
            val settle = ForestEnergyRainRpcPayloads.settle(42, "TOKEN")
            assertEquals(0, settle.getInt("activityPropNums"))
            assertEquals(42, settle.getInt("saveEnergy"))
            assertEquals("TOKEN", settle.getString("token"))
            assertEquals("test-version", settle.getString("version"))
        } finally {
            ForestApiVersion.value = previousVersion
        }
        assertTrue(ForestEnergyRainRpcPayloads.queryEndGameList().isEmpty())
    }
}
