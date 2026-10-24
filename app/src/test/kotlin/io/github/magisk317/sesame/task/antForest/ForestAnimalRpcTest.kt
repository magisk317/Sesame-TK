package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestAnimalRpcTest {
    @Test
    fun `animal queries distinguish summary and detail requests`() {
        val summary = ForestAnimalRpcPayloads.queryAnimalAndPiece(0)
        assertEquals("N", summary.getString("withDetail"))
        assertTrue(summary.getBoolean("withGift"))

        val detail = ForestAnimalRpcPayloads.queryAnimalAndPiece(12)
        assertEquals(12, detail.getInt("animalId"))
        assertEquals("ant_forest", detail.getString("source"))
    }

    @Test
    fun `friend operations use the expected source and version`() {
        val previousVersion = ForestApiVersion.value
        ForestApiVersion.value = "test-version"
        try {
            val protect = ForestAnimalRpcPayloads.protectBubble("USER")
            assertEquals("ANT_FOREST_H5", protect.getString("source"))
            assertEquals("test-version", protect.getString("version"))
        } finally {
            ForestApiVersion.value = previousVersion
        }

        val gift = ForestAnimalRpcPayloads.collectFriendGiftBox("BOX", "USER")
        assertEquals("BOX", gift.getString("targetId"))
        assertEquals("USER", gift.getString("targetUserId"))
        assertTrue(!gift.has("timezoneId"))

        val robEnergy = ForestAnimalRpcPayloads.collectAnimalRobEnergy("PROP", "TYPE", "2026-08-08")
        assertTrue(!robEnergy.has("timezoneId"))
    }
}
