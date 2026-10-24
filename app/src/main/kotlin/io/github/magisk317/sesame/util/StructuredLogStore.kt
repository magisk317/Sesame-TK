package io.github.magisk317.sesame.util

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Cross-process JSONL writer. File locks protect the shared external log directory. */
object StructuredLogStore {
    private const val MAX_ACTIVE_BYTES = 10L * 1024L * 1024L
    private const val MAX_BACKUP_BYTES = 32L * 1024L * 1024L
    private const val MAX_BACKUP_FILES = 12
    private val lock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    @Volatile private var directory: File? = null

    fun init(logDirectory: File) {
        synchronized(lock) {
            logDirectory.mkdirs()
            File(logDirectory, "bak").mkdirs()
            directory = logDirectory
        }
    }

    fun write(event: LogEvent, json: kotlinx.serialization.json.Json) {
        val targetDirectory = directory ?: return
        val file = File(targetDirectory, "${event.category.fileName}.jsonl")
        val bytes = (json.encodeToString(LogEvent.serializer(), event) + "\n").toByteArray(Charsets.UTF_8)
        synchronized(lock) {
            runCatching {
                // Rotate before opening the channel. Renaming an already-open channel
                // would keep subsequent writes attached to the backup inode.
                val backup = rotateIfNeeded(file, event.category.fileName, event.timestamp)
                FileChannel.open(
                    file.toPath(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                ).use { channel ->
                    channel.lock().use {
                        channel.position(channel.size())
                        if (backup != null) {
                            val rotationEvent = event.copy(
                                timestamp = event.timestamp,
                                time = event.time,
                                level = LogLevel.INFO,
                                tag = "StructuredLogStore",
                                event = "log.rotation",
                                message = "日志已轮转，上一段日志已保存到 ${backup.name}",
                                data = buildJsonObject {
                                    put("backupFile", backup.name)
                                    put("maxActiveBytes", MAX_ACTIVE_BYTES)
                                },
                                error = null,
                            )
                            val marker = (json.encodeToString(LogEvent.serializer(), rotationEvent) + "\n")
                                .toByteArray(Charsets.UTF_8)
                            channel.write(ByteBuffer.wrap(marker))
                        }
                        channel.write(ByteBuffer.wrap(bytes))
                        channel.force(false)
                    }
                }
                pruneBackups(targetDirectory)
            }.onFailure {
                android.util.Log.e("SesameStructuredLog", "Unable to write structured log", it)
            }
        }
        writeLogcat(event, json)
    }

    private fun rotateIfNeeded(file: File, category: String, timestamp: Long): File? {
        val day = dateFormat.format(Date(timestamp))
        val isNewDay = file.exists() && file.length() > 0L &&
            dateFormat.format(Date(file.lastModified())) != day
        if (!isNewDay && (!file.exists() || file.length() < MAX_ACTIVE_BYTES)) return null
        var index = 0
        var backup = File(file.parentFile, "bak/$category-$day.$index.jsonl")
        while (backup.exists()) {
            index++
            backup = File(file.parentFile, "bak/$category-$day.$index.jsonl")
        }
        return if (file.renameTo(backup)) {
            backup
        } else {
            file.delete()
            null
        }
    }

    private fun pruneBackups(directory: File) {
        val backups = File(directory, "bak").listFiles()
            ?.filter { it.isFile && it.extension == "jsonl" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        var total = 0L
        backups.forEachIndexed { index, file ->
            total += file.length()
            if (index >= MAX_BACKUP_FILES || total > MAX_BACKUP_BYTES) file.delete()
        }
    }

    private fun writeLogcat(event: LogEvent, json: kotlinx.serialization.json.Json) {
        val priority = when (event.level) {
            LogLevel.VERBOSE -> android.util.Log.VERBOSE
            LogLevel.DEBUG -> android.util.Log.DEBUG
            LogLevel.INFO -> android.util.Log.INFO
            LogLevel.WARN -> android.util.Log.WARN
            LogLevel.ERROR -> android.util.Log.ERROR
        }
        runCatching {
            android.util.Log.println(
                priority,
                "Sesame/${event.category.fileName}",
                json.encodeToString(LogEvent.serializer(), event),
            )
        }
    }
}
