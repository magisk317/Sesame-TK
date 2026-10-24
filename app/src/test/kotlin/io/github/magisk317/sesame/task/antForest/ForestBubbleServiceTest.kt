package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestBubbleServiceTest {
    @Test
    fun extractsAvailableFriendBubblesWithoutApplyingSelfThreshold() {
        val service = ForestBubbleService(
            isTeam = { false },
            selfId = { "self" },
            userName = { _, _, _ -> "friend" },
            shouldCollectSelfBubble = { _, _ -> false },
        )
        val available = mutableListOf<Long>()

        service.extract(
            home = jsonObject(
                "bubbles" to jsonArray(
                    jsonObject("id" to 11, "collectStatus" to "AVAILABLE", "fullEnergy" to 8),
                    jsonObject("id" to 12, "collectStatus" to "ROBBED", "fullEnergy" to 20),
                ),
            ),
            serverTime = 100,
            availableBubbles = available,
            userId = "friend",
        )

        assertEquals(listOf(11L), available)
    }

    @Test
    fun appliesSelfThresholdToAvailableBubbles() {
        val service = ForestBubbleService(
            isTeam = { false },
            selfId = { "self" },
            userName = { _, _, _ -> "self" },
            shouldCollectSelfBubble = { amount, _ -> amount >= 20 },
        )
        val available = mutableListOf<Long>()

        service.extract(
            home = jsonObject(
                "bubbles" to jsonArray(
                    jsonObject("id" to 21, "collectStatus" to "AVAILABLE", "fullEnergy" to 8),
                    jsonObject("id" to 22, "collectStatus" to "AVAILABLE", "fullEnergy" to 20),
                ),
            ),
            serverTime = 100,
            availableBubbles = available,
            userId = "self",
        )

        assertEquals(listOf(22L), available)
    }
}
