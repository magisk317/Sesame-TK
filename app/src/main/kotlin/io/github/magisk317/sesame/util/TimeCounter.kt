package io.github.magisk317.sesame.util

import java.time.Duration
import java.time.Instant

class TimeCounter(private val name: String) : AutoCloseable {
    private val start = Instant.now()
    private var lastCheckpoint = start
    private var stopped = false
    private var unexpectedCount = 0
    private val resultMessage = StringBuilder()

    override fun close() {
        if (!stopped && unexpectedCount > 0) stop()
    }

    fun stop() {
        val durationMs = Duration.between(start, Instant.now()).toMillis()
        StructuredLog.record(name, "$name 耗时: ${TimeFormatter.formatDuration(durationMs)}${
            if (resultMessage.isNotEmpty()) " ($resultMessage)" else ""
        }")
        stopped = true
    }

    fun countDebug(message: String) {
        val durationMs = Duration.between(lastCheckpoint, Instant.now()).toMillis()
        StructuredLog.record(name, "$message 耗时: ${TimeFormatter.formatDuration(durationMs)}")
        lastCheckpoint = Instant.now()
    }

    fun count(message: String) {
        val now = Instant.now()
        val durationMs = Duration.between(lastCheckpoint, now).toMillis()
        resultMessage.append(message).append(":").append(TimeFormatter.formatDuration(durationMs)).append(", ")
        lastCheckpoint = now
    }

    fun countUnexcept(message: String, exceptMs: Long) {
        val now = Instant.now()
        val durationMs = Duration.between(lastCheckpoint, now).toMillis()
        if (durationMs > exceptMs) {
            resultMessage.append(message).append(":").append(TimeFormatter.formatDuration(durationMs))
                .append("(阈值:").append(TimeFormatter.formatDuration(exceptMs)).append("), ")
            unexpectedCount++
        }
        lastCheckpoint = now
    }
}
