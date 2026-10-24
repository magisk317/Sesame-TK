package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.TimeFormatter

/** Decides whether a time-limited forest prop should be renewed. */
class ForestPropRenewalPolicy(
    private val shieldThresholdHours: Int = 23,
    private val shieldThresholdMinutes: Int = 59,
) {
    fun shouldRenewShield(endTime: Long, now: Long): Boolean {
        if (isInvalidPastTime(endTime, now)) return false
        if (endTime <= now) return true
        return endTime - now <= shieldThresholdMs()
    }

    fun shouldRenewEnergyBomb(endTime: Long, now: Long): Boolean {
        if (isInvalidPastTime(endTime, now)) return false
        if (endTime <= now) return true

        val remaining = endTime - now
        val renewThreshold = 3 * TimeFormatter.ONE_DAY_MS
        val maximumDuration = 4 * TimeFormatter.ONE_DAY_MS
        return remaining <= renewThreshold && remaining + TimeFormatter.ONE_DAY_MS <= maximumDuration
    }

    fun shouldRenewDoubleCard(endTime: Long, now: Long): Boolean {
        if (isInvalidPastTime(endTime, now)) return false
        if (endTime <= now) return true
        return endTime - now <= 31 * TimeFormatter.ONE_DAY_MS
    }

    private fun shieldThresholdMs(): Long =
        shieldThresholdHours * TimeFormatter.ONE_HOUR_MS +
            shieldThresholdMinutes * TimeFormatter.ONE_MINUTE_MS

    private fun isInvalidPastTime(endTime: Long, now: Long): Boolean =
        endTime > 0 && endTime < now - 365 * TimeFormatter.ONE_DAY_MS
}
