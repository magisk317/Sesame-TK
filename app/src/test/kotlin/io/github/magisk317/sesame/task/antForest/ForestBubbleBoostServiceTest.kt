package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestBubbleBoostServiceTest {
    private val service = ForestBubbleBoostService(
        queryHome = { null },
        findProp = { _, _ -> null },
        queryBag = { null },
        useProp = { false },
        collectSelf = {},
    )

    @Test
    fun recognizesOnlyPositiveBubblesThatMatureLater() {
        val home = jsonObject(
            "bubbles" to jsonArray(
                jsonObject("fullEnergy" to 0, "produceTime" to 2_000),
                jsonObject("fullEnergy" to 10, "produceTime" to 900),
                jsonObject("fullEnergy" to 20, "produceTime" to 2_000),
            ),
        )

        assertTrue(service.hasWaitingBubbles(home, 1_000))
        assertFalse(service.hasWaitingBubbles(home, 2_000))
    }
}
