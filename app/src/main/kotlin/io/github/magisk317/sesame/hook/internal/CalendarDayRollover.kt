package io.github.magisk317.sesame.hook.internal

import java.util.Calendar

/**
 * 纯日期逻辑：判断日期是否跨日、重置日历到午夜。
 * 不持有任何会话态（调度、save、bridge 等仍由 ApplicationHook 负责）。
 */
object CalendarDayRollover {

    /**
     * 判断 [now] 与 [currentDayCalendar] 是否跨日。
     *
     * @return (归零到午后的新日历, 是否发生翻日)
     *         当 [currentDayCalendar] 为 null 或与 [now] 的 DAY_OF_MONTH 不同时视为翻日；
     *         否则返回原日历（同样归零），changed=false。
     */
    fun checkAndRollover(now: Calendar, currentDayCalendar: Calendar?): Pair<Calendar, Boolean> {
        val changed = currentDayCalendar == null ||
            currentDayCalendar.get(Calendar.DAY_OF_MONTH) != now.get(Calendar.DAY_OF_MONTH)
        val newCal = if (changed) {
            now.clone() as Calendar
        } else {
            currentDayCalendar
        }
        resetToMidnight(newCal)
        return newCal to changed
    }

    /**
     * 将 [calendar] 的时间分量归零到当日 0 点。
     */
    fun resetToMidnight(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
}
