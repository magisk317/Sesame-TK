package io.github.magisk317.sesame.util

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Shared time helpers. The legacy static API is kept for Java callers during migration. */
object TimeUtil {
    @JvmField
    val DATE_TIME_FORMAT_THREAD_LOCAL = threadLocal("yyyy-MM-dd HH:mm:ss.SSS")

    @JvmField
    val OTHER_DATE_TIME_FORMAT_THREAD_LOCAL = threadLocal("yyyy.MM.dd HH:mm:ss")

    @JvmStatic
    fun checkNowInTimeRange(timeRange: String?): Boolean = checkInTimeRange(System.currentTimeMillis(), timeRange)

    @JvmStatic
    fun checkInTimeRange(timeMillis: Long?, timeRangeList: List<String?>?): Boolean =
        timeRangeList.orEmpty().any { checkInTimeRange(timeMillis, it) }

    @JvmStatic
    fun checkInTimeRange(timeMillis: Long?, timeRange: String?): Boolean = try {
        if (timeRange == null) return false
        val parts = timeRange.split("-")
        parts.size == 2 &&
            isAfterOrCompareTimeStr(timeMillis, parts[0]) &&
            isBeforeOrCompareTimeStr(timeMillis, parts[1])
    } catch (e: Exception) {
        StructuredLog.printStackTrace(e)
        false
    }

    @JvmStatic fun isNowBeforeTimeStr(value: String): Boolean = isBeforeTimeStr(System.currentTimeMillis(), value)
    @JvmStatic fun isNowAfterTimeStr(value: String): Boolean = isAfterTimeStr(System.currentTimeMillis(), value)
    @JvmStatic fun isNowBeforeOrCompareTimeStr(value: String): Boolean = isBeforeOrCompareTimeStr(System.currentTimeMillis(), value)
    @JvmStatic fun isNowAfterOrCompareTimeStr(value: String): Boolean = isAfterOrCompareTimeStr(System.currentTimeMillis(), value)

    @JvmStatic fun isBeforeTimeStr(timeMillis: Long?, value: String): Boolean = (isCompareTimeStr(timeMillis, value) ?: 1) < 0
    @JvmStatic fun isAfterTimeStr(timeMillis: Long?, value: String): Boolean = (isCompareTimeStr(timeMillis, value) ?: -1) > 0
    @JvmStatic fun isBeforeOrCompareTimeStr(timeMillis: Long?, value: String): Boolean = (isCompareTimeStr(timeMillis, value) ?: 1) <= 0
    @JvmStatic fun isAfterOrCompareTimeStr(timeMillis: Long?, value: String): Boolean = (isCompareTimeStr(timeMillis, value) ?: -1) >= 0

    @JvmStatic
    fun isCompareTimeStr(timeMillis: Long?, value: String): Int? = try {
        val time = Calendar.getInstance().apply { timeInMillis = timeMillis ?: System.currentTimeMillis() }
        getTodayCalendarByTimeStr(value)?.let { time.compareTo(it) }
    } catch (e: Exception) {
        StructuredLog.printStackTrace(e)
        null
    }

    @JvmStatic fun getTodayCalendarByTimeStr(value: String?): Calendar? = getCalendarByTimeStr(null, value)

    @JvmStatic
    fun getCalendarByTimeStr(timeMillis: Long?, value: String?): Calendar? = try {
        getCalendarByTimeMillis(timeMillis).let { getCalendarByTimeStr(it, value) }
    } catch (e: Exception) {
        StructuredLog.printStackTrace(e)
        null
    }

    @JvmStatic
    fun getCalendarByTimeStr(calendar: Calendar, value: String?): Calendar? = try {
        val normalized = value?.trim()?.trim('"') ?: return null
        if (!normalized.matches(Regex("\\d{2}(\\d{2})?(\\d{2})?"))) return null
        when (normalized.length) {
            6 -> calendar.apply {
                set(Calendar.SECOND, normalized.substring(4).toInt())
                set(Calendar.MINUTE, normalized.substring(2, 4).toInt())
                set(Calendar.HOUR_OF_DAY, normalized.substring(0, 2).toInt())
            }
            4 -> calendar.apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MINUTE, normalized.substring(2, 4).toInt())
                set(Calendar.HOUR_OF_DAY, normalized.substring(0, 2).toInt())
            }
            2 -> calendar.apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.HOUR_OF_DAY, normalized.substring(0, 2).toInt())
            }
            else -> return null
        }.apply { set(Calendar.MILLISECOND, 0) }
    } catch (e: Exception) {
        StructuredLog.printStackTrace(e)
        null
    }

    @JvmStatic
    fun getCalendarByTimeMillis(timeMillis: Long?): Calendar = Calendar.getInstance().apply {
        if (timeMillis != null) timeInMillis = timeMillis
    }

    @JvmStatic fun getTimeStr(timestamp: Long): String = DateFormat.getTimeInstance().format(Date(timestamp))
    @JvmStatic fun getTimeStr(): String = getTimeStr(System.currentTimeMillis())
    @JvmStatic fun getDateStr(): String = getDateStr(0)

    @JvmStatic
    fun getDateStr(plusDay: Int): String = Calendar.getInstance().apply {
        if (plusDay != 0) add(Calendar.DATE, plusDay)
    }.let { DateFormat.getDateInstance().format(it.time) }

    @JvmStatic fun getDateStr2(): String = getDateStr2(0)

    @JvmStatic
    fun getDateStr2(plusDay: Int): String = Calendar.getInstance().apply {
        if (plusDay != 0) add(Calendar.DATE, plusDay)
    }.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.time) }

    @JvmStatic fun getToday(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    @JvmStatic fun getNow(): Calendar = Calendar.getInstance()
    @JvmStatic fun sleepCompat(millis: Long) = CoroutineUtils.sleepCompat(millis)

    @JvmStatic
    fun getWeekNumber(date: Date): Int = Calendar.getInstance().apply {
        time = date
        firstDayOfWeek = Calendar.MONDAY
    }.get(Calendar.WEEK_OF_YEAR)

    @JvmStatic fun isLessThanSecondOfDays(first: Calendar, second: Calendar): Boolean =
        first.get(Calendar.YEAR) < second.get(Calendar.YEAR) ||
            first.get(Calendar.YEAR) == second.get(Calendar.YEAR) && first.get(Calendar.DAY_OF_YEAR) < second.get(Calendar.DAY_OF_YEAR)

    @JvmStatic fun isLessThanSecondOfDays(first: Long?, second: Long?): Boolean =
        isLessThanSecondOfDays(getCalendarByTimeMillis(first), getCalendarByTimeMillis(second))

    @JvmStatic fun isLessThanNowOfDays(timestamp: Long?): Boolean =
        isLessThanSecondOfDays(getCalendarByTimeMillis(timestamp), getNow())

    @JvmStatic fun isSameDay(first: Calendar, second: Calendar): Boolean =
        first.get(Calendar.YEAR) == second.get(Calendar.YEAR) && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

    @JvmStatic fun isSameDay(first: Long?, second: Long?): Boolean =
        isSameDay(getCalendarByTimeMillis(first), getCalendarByTimeMillis(second))

    @JvmStatic fun isToday(calendar: Calendar): Boolean = isSameDay(getToday(), calendar)
    @JvmStatic fun isToday(timestamp: Long?): Boolean = isToday(getCalendarByTimeMillis(timestamp))
    @JvmStatic fun getCommonDateFormat(): DateFormat = SimpleDateFormat("dd日HH:mm:ss")
    @JvmStatic fun getCommonDate(timestamp: Long?): String = getCommonDateFormat().format(timestamp)

    @JvmStatic
    fun timeToStamp(timers: String): Long = try {
        OTHER_DATE_TIME_FORMAT_THREAD_LOCAL.get()?.parse(timers)?.time ?: System.currentTimeMillis()
    } catch (_: ParseException) {
        System.currentTimeMillis()
    }

    @JvmStatic fun getFormatDateTime(): String = DATE_TIME_FORMAT_THREAD_LOCAL.get()?.format(Date()).orEmpty()
    @JvmStatic fun getFormatDate(): String = getFormatDateTime().substringBefore(' ')
    @JvmStatic fun getFormatTime(): String = getFormatDateTime().substringAfter(' ')

    @JvmStatic
    fun getFormatTime(offset: Int, format: String): String = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offset)
    }.let { SimpleDateFormat(format).format(it.time) }

    @JvmStatic
    fun formatDuration(durationMillis: Long): String =
        TimeFormatter.formatDuration(durationMillis)

    private fun threadLocal(pattern: String): ThreadLocal<SimpleDateFormat> =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat = SimpleDateFormat(pattern, Locale.getDefault())
        }
}
