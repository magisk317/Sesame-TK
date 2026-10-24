package io.github.magisk317.sesame.task.antDodo

import io.github.magisk317.sesame.util.TimeUtil

/** Date-window rules used by Dodo collection decisions. */
object DodoTimePolicy {
    @JvmStatic
    fun isLastDay(endDate: String): Boolean {
        val remaining = TimeUtil.timeToStamp(endDate) - System.currentTimeMillis()
        return remaining > 0 && remaining < 86_400_000L
    }

    @JvmStatic
    fun isWithinEightDays(endDate: String): Boolean {
        val remaining = TimeUtil.timeToStamp(endDate) - System.currentTimeMillis()
        return remaining > 0 && remaining < 691_200_000L
    }
}
