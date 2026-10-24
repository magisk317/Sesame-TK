package io.github.magisk317.sesame.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Calendar

class UtilityMigrationTest {
    @Test
    fun timeRangeKeepsInclusiveBoundariesAndRejectsInvalidValues() {
        val today = TimeUtil.getToday()
        val atEight = today.timeInMillis + 8 * 60 * 60 * 1000L

        assertEquals(true, TimeUtil.checkInTimeRange(atEight, "0800-0900"))
        assertEquals(true, TimeUtil.checkInTimeRange(atEight, listOf("070000-080000")))
        assertEquals(false, TimeUtil.checkInTimeRange(atEight, "invalid"))
        assertEquals(null, TimeUtil.getTodayCalendarByTimeStr("8am"))
        assertEquals(0, TimeUtil.getTodayCalendarByTimeStr("\"0000\"")?.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun calendarParsingClearsSubsecondPrecision() {
        val calendar = Calendar.getInstance().apply { set(Calendar.MILLISECOND, 999) }
        val parsed = TimeUtil.getCalendarByTimeStr(calendar, "083015")

        assertEquals(8, parsed?.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, parsed?.get(Calendar.MINUTE))
        assertEquals(15, parsed?.get(Calendar.SECOND))
        assertEquals(0, parsed?.get(Calendar.MILLISECOND))
    }

    @Test
    fun timeFormatterKeepsUnitBoundaries() {
        assertEquals("+0秒", TimeFormatter.formatTimeDifference(0))
        assertEquals("-1分钟", TimeFormatter.formatTimeDifference(-60_000))
        assertEquals("1分钟1秒", TimeFormatter.formatDetailedRemainingTime(61_000))
        assertEquals("1天2小时3分钟", TimeFormatter.formatDetailedRemainingTime(86_400_000 + 7_380_000))
        assertEquals("1秒", TimeFormatter.formatDuration(1_500))
        assertEquals("2分钟5秒", TimeFormatter.formatDuration(125_000))
        assertEquals("2小时", TimeFormatter.formatDuration(7_200_000))
    }

    @Test
    fun stringUtilitiesKeepNullAsEmptyText() {
        assertEquals("a,,c", StringUtil.arrayToString("a", null, "c"))
        assertEquals("0007", StringUtil.padLeft(7, 4, '0'))
        assertEquals("value", StringUtil.getSubString("[value]", "[", "]"))
    }

    @Test
    fun averageUsesAFixedWindowAndCanBeCleared() {
        val average = Average(2)

        assertEquals(10.0, average.nextDouble(10))
        assertEquals(15.0, average.nextDouble(20))
        assertEquals(25.0, average.nextDouble(30))
        assertEquals(25, average.getAverageInteger())

        average.clear()
        assertEquals(0.0, average.averageDouble())
        assertEquals(7, average.nextInteger(7))
    }

    @Test
    fun averageRejectsAnEmptyWindow() {
        assertThrows(IllegalArgumentException::class.java) { Average(0) }
    }
}
