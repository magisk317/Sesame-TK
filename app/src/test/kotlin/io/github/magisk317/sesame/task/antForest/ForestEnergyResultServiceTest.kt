package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestEnergyResultServiceTest {
    private val service = ForestEnergyResultService()

    @Test
    fun parsesBatchEnergyAndCollectAgainIds() {
        val result = service.parse(
            """
            {
              "resultCode":"SUCCESS",
              "bubbles":[
                {"id":11,"collectedEnergy":3,"canBeRobbedAgain":true},
                {"id":12,"collectedEnergy":7,"canBeRobbedAgain":false}
              ]
            }
            """.trimIndent().toJsonObject(),
        )

        assertTrue(result.isSuccess)
        assertEquals(2, result.bubbleCount)
        assertEquals(10, result.collectedEnergy)
        assertEquals(listOf(11L), result.canCollectAgainIds)
    }

    @Test
    fun preservesFailureCodeAndDescriptionWithoutReadingBubbles() {
        val result = service.parse(
            """{"resultCode":"PARAM_ILLEGAL2","resultDesc":"already collected"}""".toJsonObject(),
        )

        assertFalse(result.isSuccess)
        assertEquals("PARAM_ILLEGAL2", result.resultCode)
        assertEquals("already collected", result.resultDescription)
        assertEquals(0, result.bubbleCount)
        assertTrue(result.canCollectAgainIds.isEmpty())
    }
}
