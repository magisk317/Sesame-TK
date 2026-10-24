package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestRobExpandCardServiceTest {
    @Test
    fun triesCardsInConfiguredPriorityOrder() {
        val used = mutableListOf<String>()
        var activated = 0
        ForestRobExpandCardService(
            findProp = { _, type -> jsonObject("propType" to type) },
            useProp = { prop ->
                used += prop["propType"].toString().trim('"')
                used.last() == "SHAMO_ROB_EXPAND_CARD_1.5_1DAYS"
            },
            onActivated = { activated++ },
        ).use(null)

        assertEquals(
            listOf(
                "VITALITY_ROB_EXPAND_CARD_1.1_3DAYS",
                "SHAMO_ROB_EXPAND_CARD_1.5_1DAYS",
            ),
            used,
        )
        assertEquals(1, activated)
    }
}
