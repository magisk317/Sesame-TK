package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.TimeFormatter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestPropUsagePlannerTest {
    private val now = 10_000 * TimeFormatter.ONE_DAY_MS

    @Test
    fun plansOnlyEnabledAndExpiredProps() {
        val plan = ForestPropUsagePlanner().plan(
            ForestPropUsagePlanner.State(
                now = now,
                doubleEnabled = true,
                doubleEndTime = now + 40 * TimeFormatter.ONE_DAY_MS,
                robExpandEnabled = true,
                robExpandEndTime = now - 1,
                stealthEnabled = false,
                stealthEndTime = 0,
                shieldEnabled = false,
                shieldEndTime = 0,
                bombEnabled = false,
                bombEndTime = 0,
                bubbleBoostEnabled = true,
            ),
        )

        assertFalse(plan.useDouble)
        assertTrue(plan.useRobExpand)
        assertFalse(plan.useStealth)
        assertFalse(plan.useShield)
        assertFalse(plan.useBomb)
        assertTrue(plan.useBubbleBoost)
        assertTrue(plan.hasActions)
    }

    @Test
    fun shieldAndBombAreMutuallyExclusive() {
        val state = ForestPropUsagePlanner.State(
            now = now,
            doubleEnabled = false,
            doubleEndTime = 0,
            robExpandEnabled = false,
            robExpandEndTime = 0,
            stealthEnabled = false,
            stealthEndTime = 0,
            shieldEnabled = true,
            shieldEndTime = 0,
            bombEnabled = true,
            bombEndTime = 0,
            bubbleBoostEnabled = false,
        )

        val plan = ForestPropUsagePlanner().plan(state)

        assertFalse(plan.useShield)
        assertFalse(plan.useBomb)
        assertFalse(plan.hasActions)
    }

    @Test
    fun disabledConfigurationProducesAnEmptyPlan() {
        val plan = ForestPropUsagePlanner().plan(
            ForestPropUsagePlanner.State(
                now = now,
                doubleEnabled = false,
                doubleEndTime = 0,
                robExpandEnabled = false,
                robExpandEndTime = 0,
                stealthEnabled = false,
                stealthEndTime = 0,
                shieldEnabled = false,
                shieldEndTime = 0,
                bombEnabled = false,
                bombEndTime = 0,
                bubbleBoostEnabled = false,
            ),
        )

        assertFalse(plan.hasActions)
    }
}
