package fansirsqi.xposed.sesame.util;

import static fansirsqi.xposed.sesame.util.TimeUtil.getFormatDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.SimpleDateFormat;
import java.util.Locale;
import fansirsqi.xposed.sesame.model.BaseModel;

public class Loger {
    // 使用 SLF4J 的 LoggerFactory 来创建日志记录器
    private static final Logger runtimeLogger = LoggerFactory.getLogger("runtime");
    private static final Logger recordLogger = LoggerFactory.getLogger("record");
    private static final Logger systemLogger = LoggerFactory.getLogger("system");
    private static final Logger captureLogger = LoggerFactory.getLogger("capture");
    private static final Logger forestLogger = LoggerFactory.getLogger("forest");
    private static final Logger farmLogger = LoggerFactory.getLogger("farm");
    private static final Logger otherLogger = LoggerFactory.getLogger("other");
    private static final Logger errorLogger = LoggerFactory.getLogger("error");
    private static final Logger debugLogger = LoggerFactory.getLogger("debug");


    // 初始化日志系统
    static {
        // 这里可以根据需要设置日志级别
        // Logback 会使用 logback.xml 配置文件中的设置
    }

    // 日志记录器方法
    public static void runtime(String s) {
        runtimeLogger.info(s);
    }

    public static void runtime(String tag, String s) {
        runtime(tag + ", " + s);
    }

    public static void record(String str) {
        runtimeLogger.info(str);
        if (BaseModel.getRecordLog().getValue()) {
            recordLogger.info(str);
        }
    }

    public static void system(String tag, String s) {
        systemLogger.info(tag + ", " + s);
    }

    public static void capture(String s) {
        captureLogger.debug(s);
    }

    public static void forest(String s) {
        record(s);
        forestLogger.info(s);
    }

    public static void farm(String s) {
        record(s);
        farmLogger.info(s);
    }

    public static void other(String s) {
        record(s);
        otherLogger.info(s);
    }

    public static void debug(String s) {
        debugLogger.debug(s);
    }

    public static void error(String s) {
        errorLogger.error(s);
        runtime(s);
    }

    public static void printStackTrace(Throwable t) {
        String str = android.util.Log.getStackTraceString(t);
        errorLogger.error(str);
        runtime(str);
    }

    public static void printStackTrace(String tag, Throwable t) {
        String str = tag + ", " + android.util.Log.getStackTraceString(t);
        errorLogger.error(str);
        runtime(str);
    }

    public static String getLogFileName(String logName) {
        String date = TimeUtil.getFormatDate();
        return logName + "." + date + ".log";
    }


    public static String getFormatDate() {
        return getFormatDateTime().split(" ")[0];
    }

    public static String getFormatTime() {
        return getFormatDateTime().split(" ")[1];
    }

}
