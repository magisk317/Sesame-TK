package fansirsqi.xposed.sesame.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

/** 时间工具类。 提供了一系列方法来处理时间相关的操作，包括时间范围检查、时间比较、日期格式化等。 */
public class TimeUtil {

  // 使用新的 DateTimeFormatter 进行日期格式化
  public static final ThreadLocal<DateTimeFormatter> OTHER_DATE_TIME_FORMAT_THREAD_LOCAL = ThreadLocal.withInitial(() -> DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));

  /**
   * 将日期字符串转换为时间戳。
   *
   * @param timers 日期字符串
   * @return 对应的时间戳
   */
  public static long timeToStamp(String timers) {
    try {
      // 使用 THREAD_LOCAL 的 DateTimeFormatter 进行解析
      DateTimeFormatter formatter = OTHER_DATE_TIME_FORMAT_THREAD_LOCAL.get();
      LocalDateTime dateTime = LocalDateTime.parse(timers, formatter);
      return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); // 转换为时间戳
    } catch (Exception e) {
      Log.printStackTrace(e); // 异常打印
    }
    return 0;
  }

  /**
   * 检查当前时间是否在给定的时间范围内。
   *
   * @param timeRange 时间范围，格式为 "HHmm-HHmm"。
   * @return 如果当前时间在范围内，返回 true，否则返回 false。
   */
  public static Boolean checkNowInTimeRange(String timeRange) {
    return checkInTimeRange(System.currentTimeMillis(), timeRange);
  }

  /**
   * 检查给定的时间毫秒数是否在给定的时间范围列表中。
   *
   * @param timeMillis 时间毫秒数。
   * @param timeRangeList 时间范围列表，每个范围格式为 "HH-mm-HH-mm"。
   * @return 如果时间在任一范围内，返回 true，否则返回 false。
   */
  public static Boolean checkInTimeRange(Long timeMillis, List<String> timeRangeList) {
    for (String timeRange : timeRangeList) {
      if (checkInTimeRange(timeMillis, timeRange)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 检查给定的时间毫秒数是否在给定的时间范围内。
   *
   * @param timeMillis 时间毫秒数。
   * @param timeRange 时间范围，格式为 "HHmm-HHmm"。
   * @return 如果时间在范围内，返回 true，否则返回 false。
   */
  public static Boolean checkInTimeRange(Long timeMillis, String timeRange) {
    try {
      String[] timeRangeArray = timeRange.split("-");
      if (timeRangeArray.length == 2) {
        String min = timeRangeArray[0];
        String max = timeRangeArray[1];
        return isAfterOrCompareTimeStr(timeMillis, min) && isBeforeOrCompareTimeStr(timeMillis, max);
      }else{
        Log.error("Time range bad format: [HHmm-HHmm] but got " + timeRange);
      }
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
    return false;
  }

  /**
   * 检查当前时间是否在给定的时间字符串之前。
   *
   * @param beforeTimeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 如果当前时间在之前，返回 true，否则返回 false。
   */
  public static Boolean isNowBeforeTimeStr(String beforeTimeStr) {
    return isBeforeTimeStr(System.currentTimeMillis(), beforeTimeStr);
  }

  /**
   * 检查当前时间是否在给定的时间字符串之后。
   *
   * @param afterTimeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 如果当前时间在之后，返回 true，否则返回 false。
   */
  public static Boolean isNowAfterTimeStr(String afterTimeStr) {
    return isAfterTimeStr(System.currentTimeMillis(), afterTimeStr);
  }

  /**
   * 检查当前时间是否在给定的时间字符串之前或相等。
   *
   * @param beforeTimeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 如果当前时间在之前或相等，返回 true，否则返回 false。
   */
  public static Boolean isNowBeforeOrCompareTimeStr(String beforeTimeStr) {
    return isBeforeOrCompareTimeStr(System.currentTimeMillis(), beforeTimeStr);
  }

  /**
   * 检查当前时间是否在给定的时间字符串之后或相等。
   *
   * @param afterTimeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 如果当前时间在之后或相等，返回 true，否则返回 false。
   */
  public static Boolean isNowAfterOrCompareTimeStr(String afterTimeStr) {
    return isAfterOrCompareTimeStr(System.currentTimeMillis(), afterTimeStr);
  }

  /**
   * 检查给定的时间毫秒数是否在给定的时间字符串之前。
   *
   * @param timeMillis 时间毫秒数。
   * @param beforeTimeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 如果时间在之前，返回 true，否则返回 false。
   */
  public static Boolean isBeforeTimeStr(Long timeMillis, String beforeTimeStr) {
    Integer compared = isCompareTimeStr(timeMillis, beforeTimeStr);
    if (compared != null) {
      return compared < 0;
    }
    return false;
  }

  /**
   * 检查给定的时间毫秒数是否在给定的时间字符串之后。
   *
   * @param timeMillis 时间毫秒数。
   * @param afterTimeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 如果时间在之后，返回 true，否则返回 false。
   */
  public static Boolean isAfterTimeStr(Long timeMillis, String afterTimeStr) {
    Integer compared = isCompareTimeStr(timeMillis, afterTimeStr);
    if (compared != null) {
      return compared > 0;
    }
    return false;
  }

  /**
   * 检查给定的时间毫秒数是否在给定的时间字符串之前或相等。
   *
   * @param timeMillis 时间毫秒数。
   * @param beforeTimeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 如果时间在之前或相等，返回 true，否则返回 false。
   */
  public static Boolean isBeforeOrCompareTimeStr(Long timeMillis, String beforeTimeStr) {
    Integer compared = isCompareTimeStr(timeMillis, beforeTimeStr);
    if (compared != null) {
      return compared <= 0;
    }
    return false;
  }

  /**
   * 检查给定的时间毫秒数是否在给定的时间字符串之后或相等。
   *
   * @param timeMillis 时间毫秒数。
   * @param afterTimeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 如果时间在之后或相等，返回 true，否则返回 false。
   */
  public static Boolean isAfterOrCompareTimeStr(Long timeMillis, String afterTimeStr) {
    Integer compared = isCompareTimeStr(timeMillis, afterTimeStr);
    if (compared != null) {
      return compared >= 0;
    }
    return false;
  }

  /**
   * 比较给定的时间毫秒数和时间字符串。
   *
   * @param timeMillis 时间毫秒数。
   * @param compareTimeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 如果时间在之前，返回负数；如果相等，返回 0；如果之后，返回正数。
   */
  public static Integer isCompareTimeStr(Long timeMillis, String compareTimeStr) {

    try {
      LocalDateTime timeDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneId.systemDefault());
      LocalTime compareTime = LocalTime.parse(compareTimeStr, DateTimeFormatter.ofPattern("HHmm")).withSecond(0).withNano(0);
      LocalDateTime compareDateTime = LocalDateTime.of(timeDateTime.toLocalDate(), compareTime);
      return timeDateTime.compareTo(compareDateTime);
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
    return null;
  }

  /**
   * 根据时间字符串获取今天的日历对象。
   *
   * @param timeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 日历对象。
   */
  public static LocalDateTime getTodayLocalDateTimeByTimeStr(String timeStr) {
    return getLocalDateTimeByTimeStr(LocalDateTime.now(), timeStr);
  }

  /**
   * 根据时间毫秒数和时间字符串获取日历对象。
   *
   * @param timeMillis 时间毫秒数。
   * @param timeStr 时间字符串，格式为 "HH-mm-ss"。
   * @return 日历对象。
   */
  public static LocalDateTime getLocalDateTimeByTimeStr(LocalDateTime timeMillis, String timeStr) {
    if (timeStr == null || timeStr.isEmpty()) {
      return null;
    }
    DateTimeFormatter formatter;
    LocalTime localTime;
    try {
      int length = timeStr.length();
      // 解析时间字符串并生成 LocalTime 对象
      if (length == 6) {
        formatter = DateTimeFormatter.ofPattern("HHmmss");
        localTime = LocalTime.parse(timeStr, formatter);
      } else if (length == 4) {
        formatter = DateTimeFormatter.ofPattern("HHmm");
        localTime = LocalTime.parse(timeStr, formatter).withSecond(0).withNano(0);
      } else if (length == 2) {
        formatter = DateTimeFormatter.ofPattern("HH");
        localTime = LocalTime.parse(timeStr, formatter).withMinute(0).withSecond(0).withNano(0);
      } else {
        return null; // 如果格式不对，直接返回 null
      }
      // 使用 `LocalTime` 调整原有的 `LocalDateTime`
      return timeMillis.with(localTime);
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
    return null;
  }

  public static LocalDateTime getLocalDateTimeByTimeStr(String timeStr) {
    if (timeStr == null || timeStr.isEmpty()) {
      return null;
    }
    DateTimeFormatter formatter;
    LocalTime localTime;
    try {
      int length = timeStr.length();
      // 解析时间字符串并生成 LocalTime 对象
      if (length == 6) {
        formatter = DateTimeFormatter.ofPattern("HHmmss");
        localTime = LocalTime.parse(timeStr, formatter);
      } else if (length == 4) {
        formatter = DateTimeFormatter.ofPattern("HHmm");
        localTime = LocalTime.parse(timeStr, formatter).withSecond(0).withNano(0);
      } else if (length == 2) {
        formatter = DateTimeFormatter.ofPattern("HH");
        localTime = LocalTime.parse(timeStr, formatter).withMinute(0).withSecond(0).withNano(0);
      } else {
        return null; // 如果格式不对，直接返回 null
      }
      // 当前日期 + 解析的时间
      return LocalDateTime.now().toLocalDate().atTime(localTime);
    } catch (DateTimeParseException e) {
      Log.printStackTrace(e);
    }
    return null;
  }

  /**
   * 根据时间毫秒数获取日历对象。
   *
   * @param timeMillis 时间毫秒数。
   * @return 日历对象。
   */
  public static LocalDateTime getLocalDateTimeByTimeMillis(Long timeMillis) {
    return (timeMillis != null) ? LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneId.systemDefault()) : LocalDateTime.now();
  }

  /**
   * 获取当前时间的字符串表示。
   *
   * @param ts 时间毫秒数。
   * @return 时间字符串，格式为 "HH:mm:ss"。
   */
  public static String getTimeStr(long ts) {
    LocalTime time = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalTime();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    return time.format(formatter);
  }

  /**
   * 获取当前日期的字符串表示，使用固定格式 "yyyy-MM-dd"。
   *
   * @return 日期字符串，格式为 "yyyy-MM-dd"。
   */
  public static String getFormatDate() {
    return getFormatDate(0, "yyyy-MM-dd");
  }

  /**
   * 获取给定天数偏移后的日期字符串表示，使用固定格式。 今天: getDateStr())<br>
   * 昨天: getDateStr(-1, "yyyy-MM-dd"))<br>
   * 明天: getDateStr(1, "yyyy-MM-dd"))<br>
   * 自定义 getDateStr(0, "dd/MM/yyyy"))<br>
   *
   * @param plusDay 天数偏移量。
   * @param pattern 日期格式模板，例如 "yyyy-MM-dd"。
   * @return 格式化后的日期字符串。
   */
  public static String getFormatDate(int plusDay, String pattern) {
    LocalDate date = LocalDate.now();
    if (plusDay != 0) date = date.plusDays(plusDay);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    return date.format(formatter);
  }

  /**
   * 获取当前日期时间的字符串表示，使用固定格式 "yyyy-MM-dd HH:mm:ss"。
   *
   * @return 日期时间字符串，格式为 "yyyy-MM-dd HH:mm:ss"。
   */
  public static String getFormatDateTime() {
    return getFormatDateTime(0, "yyyy-MM-dd HH:mm:ss");
  }

  /**
   * 获取给定天数偏移后的日期时间字符串表示，使用固定格式。
   *
   * @param plusDay 天数偏移量。
   * @param pattern 日期时间格式模板，例如 "yyyy-MM-dd HH:mm:ss"。
   * @return 格式化后的日期时间字符串。
   */
  public static String getFormatDateTime(int plusDay, String pattern) {
    LocalDateTime dateTime = LocalDateTime.now().plusDays(plusDay);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    return dateTime.format(formatter);
  }

  /**
   * 获取当前的日期时间对象。
   *
   * @return 当前的 LocalDateTime 对象。
   */
  public static LocalDateTime getNow() {
    return LocalDateTime.now();
  }

  /**
   * 根据时间字符串获取今天的日期时间对象。
   *
   * @param timeStr 时间字符串，支持格式：
   *                - "HHmmss"：精确到秒，例如 "123456"
   *                - "HHmm"：精确到分钟，例如 "1234"
   *                - "HH"：只包含小时，例如 "12"
   * @return 返回当前日期和给定时间组成的 LocalDateTime 对象；如果解析失败，返回 null。
   */
  public static LocalDateTime getTodayByTimeStr(String timeStr) {
    // 如果时间字符串为空或为null，返回null
    if (timeStr == null || timeStr.isEmpty()) {
      return null;
    }
    try {
      int length = timeStr.length();
      LocalDate today = LocalDate.now();
      if (length == 6) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmmss");
        return LocalDateTime.of(today, LocalTime.parse(timeStr, formatter));
      } else if (length == 4) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmm");
        // 使用当前日期和解析出来的 LocalTime 组合成 LocalDateTime
        return LocalDateTime.of(today, LocalTime.parse(timeStr, formatter));
      } else if (length == 2) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH");
        return LocalDateTime.of(today, LocalTime.parse(timeStr, formatter));
      } else {
        return null;
      }
    } catch (Exception e) {
      Log.printStackTrace(e);
      return null;
    }
  }



  /**
   * 获取指定时间的周数。
   *
   * @param dateTime 时间。
   * @return 当前年的第几周。
   */
  public static int getWeekNumber(LocalDate dateTime) {
    // 使用 WeekFields 获取当前日期是该年的第几周
    WeekFields weekFields = WeekFields.of(Locale.getDefault());
    return dateTime.get(weekFields.weekOfYear());
  }

  /**
   * 比较第一个时间戳的天数是否小于第二个时间戳的天数。
   *
   * @param firstdTimestamp 第一个时间戳。
   * @param secondTimestamp 第二个时间戳。
   * @return 如果小于，则为 true，否则为 false。
   */
  public static boolean isLessThanSecondOfDays(Long firstdTimestamp, Long secondTimestamp) {
    final long gmt8 = 8 * 60 * 60 * 1000;
    final long day = 24 * 60 * 60 * 1000;
    firstdTimestamp = firstdTimestamp + gmt8;
    secondTimestamp = secondTimestamp + gmt8;
    return firstdTimestamp / day < secondTimestamp / day;
  }

  /**
   * 通过时间戳比较传入的时间戳的天数是否小于当前时间戳的天数。
   *
   * @param timestamp 时间戳。
   * @return 如果小于当前时间戳所计算的天数，则为 true，否则为 false。
   */
  public static boolean isLessThanNowOfDays(Long timestamp) {
    return isLessThanSecondOfDays(timestamp, System.currentTimeMillis());
  }

  /**
   * 获取通用的日期字符串表示。
   *
   * @param timestamp 时间戳。
   * @return 日期字符串，格式为 "dd日HH:mm:ss"。
   */
  public static String getCommonDate(Long timestamp) {
    // 使用 DateTimeFormatter 和 java.time.Instant 来提高性能
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd日HH:mm:ss").withZone(ZoneId.systemDefault()); // 使用默认时区
    return formatter.format(Instant.ofEpochMilli(timestamp)); // 转换时间戳为 Instant 对象
  }
}
