package io.github.magisk317.sesame.util

object TimeFormatter {
    const val ONE_SECOND_MS: Long = 1000L
    const val ONE_MINUTE_MS: Long = 60 * ONE_SECOND_MS
    const val ONE_HOUR_MS: Long = 60 * ONE_MINUTE_MS
    const val ONE_DAY_MS: Long = 24 * ONE_HOUR_MS

    @JvmStatic
    fun formatTimeDifference(milliseconds: Long, showSign: Boolean): String {
        val absolute = kotlin.math.abs(milliseconds)
        val sign = if (showSign) if (milliseconds >= 0) "+" else "-" else ""
        if (absolute < ONE_SECOND_MS) return sign + "0秒"
        return sign + formatDetailedRemainingTime(absolute)
    }

    @JvmStatic
    fun formatTimeDifference(milliseconds: Long): String =
        formatTimeDifference(milliseconds, true)

    @JvmStatic
    fun formatRemainingTime(milliseconds: Long): String =
        formatDetailedRemainingTime(kotlin.math.abs(milliseconds))

    /** Formats a duration using the largest useful human-readable unit. */
    @JvmStatic
    fun formatDuration(milliseconds: Long): String = formatDetailedRemainingTime(kotlin.math.abs(milliseconds))

    @JvmStatic
    fun formatDetailedRemainingTime(milliseconds: Long): String = when {
        milliseconds < ONE_SECOND_MS -> "不足1秒"
        milliseconds < ONE_MINUTE_MS -> "${milliseconds / ONE_SECOND_MS}秒"
        milliseconds < ONE_HOUR_MS -> {
            val minutes = milliseconds / ONE_MINUTE_MS
            val seconds = (milliseconds % ONE_MINUTE_MS) / ONE_SECOND_MS
            "${minutes}分钟" + if (seconds > 0) "${seconds}秒" else ""
        }
        milliseconds < ONE_DAY_MS -> {
            val hours = milliseconds / ONE_HOUR_MS
            val minutes = (milliseconds % ONE_HOUR_MS) / ONE_MINUTE_MS
            "${hours}小时" + if (minutes > 0) "${minutes}分钟" else ""
        }
        else -> {
            val days = milliseconds / ONE_DAY_MS
            val hours = (milliseconds % ONE_DAY_MS) / ONE_HOUR_MS
            val minutes = (milliseconds % ONE_HOUR_MS) / ONE_MINUTE_MS
            buildString {
                append(days).append("天")
                if (hours > 0) append(hours).append("小时")
                if (minutes > 0) append(minutes).append("分钟")
            }
        }
    }
}
