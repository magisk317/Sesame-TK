package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FarmAnimalVisitServiceTest {
    @Test
    fun plansPrizeClaimsOnlyForFeedNodes() {
        val plan = FarmAnimalVisitService().plan(
            jsonObject(
                "talkConfigs" to jsonArray(
                    jsonObject("farmId" to "farm-1"),
                ),
                "talkNodes" to jsonArray(
                    jsonObject("type" to "OTHER", "consistencyKey" to "skip"),
                    jsonObject("type" to "FEED", "consistencyKey" to "claim-1"),
                    jsonObject("type" to "FEED", "consistencyKey" to "claim-2"),
                ),
            ),
        )

        assertEquals("farm-1", plan?.farmId)
        assertEquals(listOf("claim-1", "claim-2"), plan?.prizeKeys)
    }

    @Test
    fun rejectsMissingVisitStructure() {
        assertNull(FarmAnimalVisitService().plan(jsonObject("talkNodes" to jsonArray())))
    }
}
