package io.github.magisk317.sesame.util

/** Keeps time-of-day configuration stable when legacy JSON stored 0800 as 800. */
object TimeConfigNormalizer {
    private val explicitTimeFields = setOf("execAtTimeList", "wakenAtTimeList", "autoHandleOnceDailyTimes")

    fun isTimeField(code: String): Boolean = code.endsWith("Time") || code in explicitTimeFields

    fun normalize(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed == "-1") return trimmed
        return trimmed.split(',').joinToString(",") { item ->
            item.trim().split('-', limit = 2).joinToString("-") { part -> normalizePart(part) }
        }
    }

    private fun normalizePart(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.matches(Regex("\\d{1,3}"))) trimmed.padStart(4, '0') else trimmed
    }
}
