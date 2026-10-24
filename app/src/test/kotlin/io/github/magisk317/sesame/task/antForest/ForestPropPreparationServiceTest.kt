package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestPropPreparationServiceTest {
    private fun state(
        now: Long = 10_000,
        enabled: Boolean = true,
    ) = ForestPropUsagePlanner.State(
        now = now,
        doubleEnabled = enabled,
        doubleEndTime = 0,
        robExpandEnabled = enabled,
        robExpandEndTime = 0,
        stealthEnabled = enabled,
        stealthEndTime = 0,
        shieldEnabled = enabled,
        shieldEndTime = 0,
        bombEnabled = false,
        bombEndTime = 0,
        bubbleBoostEnabled = enabled,
    )

    @Test
    fun executesPlannedActionsInStableOrder() {
        val events = mutableListOf<String>()
        val service = ForestPropPreparationService(
            planner = ForestPropUsagePlanner(),
            queryBag = { jsonObject("success" to true) },
            useDouble = { events += "double" },
            useRobExpand = { events += "expand" },
            useStealth = { events += "stealth" },
            useBubbleBoost = { events += "boost" },
            useShield = { events += "shield" },
            useBomb = { events += "bomb" },
        )

        service.prepare(state(), skip = false)

        assertEquals(listOf("double", "expand", "stealth", "boost", "shield"), events)
    }

    @Test
    fun skipDoesNotQueryOrExecuteProps() {
        val events = mutableListOf<String>()
        val service = ForestPropPreparationService(
            planner = ForestPropUsagePlanner(),
            queryBag = { error("must not query") },
            useDouble = { events += "double" },
            useRobExpand = { events += "expand" },
            useStealth = { events += "stealth" },
            useBubbleBoost = { events += "boost" },
            useShield = { events += "shield" },
            useBomb = { events += "bomb" },
        )

        service.prepare(state(), skip = true)

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun disabledPlanDoesNotQueryBag() {
        val service = ForestPropPreparationService(
            planner = ForestPropUsagePlanner(),
            queryBag = { error("must not query") },
            useDouble = {},
            useRobExpand = {},
            useStealth = {},
            useBubbleBoost = {},
            useShield = {},
            useBomb = {},
        )

        service.prepare(state(enabled = false), skip = false)
    }
}
