package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.TimeFormatter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestPropRenewalPolicyTest {
    private val policy = ForestPropRenewalPolicy()
    private val now = 10_000 * TimeFormatter.ONE_DAY_MS

    @Test
    fun renewsMissingAndExpiredProps() {
        assertTrue(policy.shouldRenewShield(0, now))
        assertTrue(policy.shouldRenewShield(now - 1, now))
        assertTrue(policy.shouldRenewDoubleCard(0, now))
        assertTrue(policy.shouldRenewEnergyBomb(now - 1, now))
    }

    @Test
    fun ignoresClearlyInvalidHistoricalTimestamps() {
        val invalid = now - 366 * TimeFormatter.ONE_DAY_MS

        assertFalse(policy.shouldRenewShield(invalid, now))
        assertFalse(policy.shouldRenewDoubleCard(invalid, now))
        assertFalse(policy.shouldRenewEnergyBomb(invalid, now))
    }

    @Test
    fun usesConfiguredRenewalWindows() {
        assertTrue(policy.shouldRenewShield(now + 23 * TimeFormatter.ONE_HOUR_MS + 59 * TimeFormatter.ONE_MINUTE_MS, now))
        assertFalse(policy.shouldRenewShield(now + 24 * TimeFormatter.ONE_HOUR_MS, now))
        assertTrue(policy.shouldRenewDoubleCard(now + 31 * TimeFormatter.ONE_DAY_MS, now))
        assertFalse(policy.shouldRenewDoubleCard(now + 31 * TimeFormatter.ONE_DAY_MS + 1, now))
    }

    @Test
    fun renewsBombOnlyWhenTheAdditionalDayFitsTheMaximumDuration() {
        assertTrue(policy.shouldRenewEnergyBomb(now + 3 * TimeFormatter.ONE_DAY_MS, now))
        assertFalse(policy.shouldRenewEnergyBomb(now + 3 * TimeFormatter.ONE_DAY_MS + 1, now))
    }
}
