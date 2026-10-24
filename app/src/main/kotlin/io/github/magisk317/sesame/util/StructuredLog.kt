package io.github.magisk317.sesame.util

import android.content.Context
import io.github.magisk317.sesame.model.BaseModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
enum class LogCategory(val fileName: String) {
    RECORD("record"),
    BASE("base"),
    FOREST("forest"),
    FARM("farm"),
    SPORTS("sports"),
    ORCHARD("orchard"),
    STALL("stall"),
    MEMBER("member"),
    OTHER("other"),
    ERROR("error"),
    CAPTURE("capture"),
}

@Serializable
enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

@Serializable
data class LogContext(
    val thread: String,
    val pid: Int,
    val process: String,
)

@Serializable
data class LogError(
    val type: String,
    val message: String?,
    val stack: String,
)

@Serializable
data class LogEvent(
    val schemaVersion: Int = 1,
    val timestamp: Long,
    val time: String,
    val level: LogLevel,
    val category: LogCategory,
    val tag: String?,
    val event: String,
    val message: String?,
    val data: JsonElement?,
    val error: LogError?,
    val context: LogContext,
)

/** Single structured logging API used by both the UI and hooked host process. */
object StructuredLog {
    private const val TEXT_EVENT = "log.text"
    private const val MAX_DUPLICATE_ERRORS = 3
    private val errorCountMap = ConcurrentHashMap<String, AtomicInteger>()
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = false
    }

    @JvmStatic
    fun init(context: Context) {
        StructuredLogStore.init(Files.LOG_DIR)
    }

    @JvmStatic
    fun event(
        category: LogCategory,
        level: LogLevel,
        tag: String?,
        name: String,
        message: String? = null,
        data: Any? = null,
        throwable: Throwable? = null,
    ) {
        val event = LogEvent(
            timestamp = System.currentTimeMillis(),
            time = nowIso(),
            level = level,
            category = category,
            tag = tag?.takeIf { it.isNotBlank() },
            event = name,
            // JSONL records must stay physically single-line. Multi-line
            // sections are emitted as separate events by their caller.
            message = message?.replace("\\r\\n", " ")
                ?.replace("\\n", " ")
                ?.replace("\r", " ")
                ?.replace("\n", " "),
            data = toElement(data),
            error = throwable?.let(::toError),
            context = LogContext(
                thread = Thread.currentThread().name.orEmpty(),
                pid = runCatching { android.os.Process.myPid() }.getOrDefault(-1),
                process = currentProcessName(),
            ),
        )
        StructuredLogStore.write(event, json)
    }

    @JvmStatic
    fun info(category: LogCategory, tag: String?, name: String, message: String? = null, data: Any? = null) =
        event(category, LogLevel.INFO, tag, name, message, data)

    @JvmStatic
    fun debug(category: LogCategory, tag: String?, name: String, message: String? = null, data: Any? = null) =
        event(category, LogLevel.DEBUG, tag, name, message, data)

    @JvmStatic
    fun warn(category: LogCategory, tag: String?, name: String, message: String? = null, data: Any? = null) =
        event(category, LogLevel.WARN, tag, name, message, data)

    @JvmStatic
    fun errorEvent(
        category: LogCategory,
        tag: String?,
        name: String,
        message: String? = null,
        throwable: Throwable? = null,
        data: Any? = null,
    ) = event(category, LogLevel.ERROR, tag, name, message, data, throwable)

    @JvmStatic
    fun captureRpc(
        tag: String?,
        method: String?,
        request: Any?,
        response: Any?,
        startedAt: Long,
        durationMs: Long,
        throwable: Throwable? = null,
    ) {
        val data = buildJsonObject {
            put("method", method)
            put("startedAt", startedAt)
            put("durationMs", durationMs)
            put("request", toElement(request))
            put("response", toElement(response))
        }
        event(LogCategory.CAPTURE, LogLevel.INFO, tag, "rpc.response", "RPC response received", data, throwable)
    }

    // New category-specific text entry points. They intentionally write structured events.
    @JvmStatic fun record(message: String) = record(null, message)
    @JvmStatic fun record(tag: String?, message: String) {
        val category = taskCategoryFromCaller()
        if (category != null) {
            info(category, tag, TEXT_EVENT, message)
        } else if (BaseModel.recordLog.value == true) {
            info(LogCategory.RECORD, tag, TEXT_EVENT, message)
        }
    }

    private fun taskCategoryFromCaller(): LogCategory? = runCatching {
        Thread.currentThread().stackTrace.firstNotNullOfOrNull { frame ->
            when {
                // 森林组
                frame.className.contains(".task.antForest.") -> LogCategory.FOREST
                frame.className.contains(".task.antFish.") -> LogCategory.FOREST
                frame.className.contains(".task.antDodo.") -> LogCategory.FOREST
                frame.className.contains(".task.antOcean.") -> LogCategory.FOREST
                frame.className.contains(".task.reserve.") -> LogCategory.FOREST
                frame.className.contains(".task.antCooperate.") -> LogCategory.FOREST
                frame.className.contains(".task.EcoProtection.") -> LogCategory.FOREST
                // 庄园组
                frame.className.contains(".task.antFarm.") -> LogCategory.FARM
                // 运动组
                frame.className.contains(".task.antSports.") -> LogCategory.SPORTS
                // 农场组
                frame.className.contains(".task.antOrchard.") -> LogCategory.ORCHARD
                // 新村组
                frame.className.contains(".task.antStall.") -> LogCategory.STALL
                // 会员组
                frame.className.contains(".task.antMember.") -> LogCategory.MEMBER
                // 其他组
                frame.className.contains(".task.antTravel.") -> LogCategory.OTHER
                frame.className.contains(".task.other.") -> LogCategory.OTHER
                frame.className.contains(".task.greenFinance.") -> LogCategory.OTHER
                frame.className.contains(".task.customTasks.") -> LogCategory.OTHER
                frame.className.contains(".task.AnswerAI.") -> LogCategory.OTHER
                // 基础组
                frame.className.contains(".task.MainTask") -> LogCategory.BASE
                else -> null
            }
        }
    }.getOrNull()

    @JvmStatic fun forest(message: String) = forest(null, message)
    @JvmStatic fun forest(tag: String?, message: String) = debug(LogCategory.FOREST, tag, TEXT_EVENT, message)

    @JvmStatic fun farm(message: String) = debug(LogCategory.FARM, null, TEXT_EVENT, message)
    @JvmStatic fun farm(tag: String?, message: String) = debug(LogCategory.FARM, tag, TEXT_EVENT, message)
    @JvmStatic fun other(message: String) = debug(LogCategory.OTHER, null, TEXT_EVENT, message)
    @JvmStatic fun other(tag: String?, message: String) = debug(LogCategory.OTHER, tag, TEXT_EVENT, message)
    @JvmStatic fun debug(message: String) = debug(LogCategory.OTHER, null, TEXT_EVENT, message)
    @JvmStatic fun debug(tag: String?, message: String) = debug(LogCategory.OTHER, tag, TEXT_EVENT, message)
    @JvmStatic fun error(message: String) = errorEvent(LogCategory.ERROR, null, TEXT_EVENT, message)
    @JvmStatic fun error(tag: String?, message: String) = errorEvent(LogCategory.ERROR, tag, TEXT_EVENT, message)
    @JvmStatic fun capture(message: String) = info(LogCategory.CAPTURE, null, "capture.raw", "Raw capture", message)
    @JvmStatic fun capture(tag: String?, message: String) = info(LogCategory.CAPTURE, tag, TEXT_EVENT, message)

    @JvmStatic fun d(tag: String, message: String) = debug(tag, message)
    @JvmStatic fun i(tag: String, message: String) = info(LogCategory.OTHER, tag, TEXT_EVENT, message)
    @JvmStatic fun w(tag: String, message: String) = warn(LogCategory.RECORD, tag, TEXT_EVENT, message)
    @JvmStatic fun e(tag: String, message: String, throwable: Throwable? = null) =
        errorEvent(LogCategory.ERROR, tag, TEXT_EVENT, message, throwable)

    @JvmStatic fun printStackTrace(throwable: Throwable) {
        if (shouldSuppress(throwable)) return
        errorEvent(LogCategory.ERROR, null, "exception", throwable.message, throwable = throwable)
    }

    @JvmStatic fun printStackTrace(message: String, throwable: Throwable) {
        if (shouldSuppress(throwable)) return
        errorEvent(LogCategory.ERROR, null, "exception", message, throwable = throwable)
    }

    @JvmStatic fun printStackTrace(tag: String, message: String, throwable: Throwable) {
        if (shouldSuppress(throwable)) return
        errorEvent(LogCategory.ERROR, tag, "exception", message, throwable = throwable)
    }

    @JvmStatic fun printStackTrace(exception: Exception) = printStackTrace(exception as Throwable)
    @JvmStatic fun printStackTrace(message: String, exception: Exception) = printStackTrace(message, exception as Throwable)
    @JvmStatic fun printStackTrace(tag: String, message: String, exception: Exception) = printStackTrace(tag, message, exception as Throwable)

    private fun shouldSuppress(throwable: Throwable): Boolean {
        val signature = throwable.javaClass.simpleName + ":" + (throwable.message?.take(50) ?: "null")
        val count = errorCountMap.computeIfAbsent(signature) { AtomicInteger() }.incrementAndGet()
        if (count == MAX_DUPLICATE_ERRORS) {
            errorEvent(LogCategory.ERROR, null, "exception.suppressed", "Repeated exception: $signature")
        }
        return count > MAX_DUPLICATE_ERRORS
    }

    private fun toElement(value: Any?): JsonElement {
        if (value == null) return JsonNull
        if (value is JsonElement) return value
        return when (value) {
            is String -> runCatching {
                val trimmed = value.trim()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) json.decodeFromString(JsonElement.serializer(), value)
                else JsonPrimitive(value)
            }.getOrElse { JsonPrimitive(value) }
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> runCatching { json.decodeFromString(JsonElement.serializer(), value.toString()) }
                .getOrElse { JsonPrimitive(value.toString()) }
        }
    }

    private fun toError(throwable: Throwable) = LogError(
        type = throwable.javaClass.name,
        message = throwable.message,
        stack = throwable.stackTraceToString(),
    )

    private fun nowIso() = LocalDateTime.now().format(timestampFormatter)

    private fun currentProcessName(): String = runCatching {
        java.io.File("/proc/${android.os.Process.myPid()}/cmdline").readText().trim('\u0000', '\n')
    }.getOrDefault("")
}
