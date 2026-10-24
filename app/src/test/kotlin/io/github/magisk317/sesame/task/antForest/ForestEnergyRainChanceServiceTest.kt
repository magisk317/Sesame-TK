package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestEnergyRainChanceServiceTest {
    @Test
    fun consumesCardsAndExchangedCardBeforeMarkingTheDay() = runBlocking {
        val cards = ArrayDeque(listOf("LIMIT_TIME_ENERGY_RAIN_CHANCE", "ENERGY_RAIN_CHANCE"))
        val used = mutableListOf<String>()
        var exchanged = false
        var marked = false
        val service = ForestEnergyRainChanceService(
            hasRunToday = { marked },
            findProp = { _, type ->
                when {
                    type == "LIMIT_TIME_ENERGY_RAIN_CHANCE" && !exchanged && cards.firstOrNull() == type ->
                        jsonObject("propType" to type)
                    type == "LIMIT_TIME_ENERGY_RAIN_CHANCE" && exchanged && !used.contains("exchanged") ->
                        jsonObject("propType" to "exchanged")
                    type == "ENERGY_RAIN_CHANCE" && cards.firstOrNull() == type ->
                        jsonObject("propType" to type)
                    else -> null
                }
            },
            queryBag = { null },
            useProp = {
                val type = it["propType"]?.toString()?.trim('"') ?: error("missing prop type")
                used += type
                if (cards.firstOrNull() == type) cards.removeFirst()
                true
            },
            exchangeChance = { exchanged = true; true },
            markRunToday = { marked = true },
            pause = {},
        )

        service.use()

        assertEquals(listOf("LIMIT_TIME_ENERGY_RAIN_CHANCE", "exchanged", "ENERGY_RAIN_CHANCE"), used)
        assertTrue(exchanged)
        assertTrue(marked)
    }

    @Test
    fun skipsTheFlowWhenAlreadyProcessed() = runBlocking {
        var used = false
        ForestEnergyRainChanceService(
            hasRunToday = { true },
            findProp = { _, _ -> error("must not query") },
            queryBag = { error("must not query") },
            useProp = { used = true; true },
            exchangeChance = { error("must not exchange") },
            markRunToday = { error("must not mark") },
            pause = {},
        ).use()

        assertTrue(!used)
    }
}
