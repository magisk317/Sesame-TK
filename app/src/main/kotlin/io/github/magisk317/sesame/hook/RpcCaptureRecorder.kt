package io.github.magisk317.sesame.hook

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.toJsonElement
import kotlinx.serialization.json.JsonElement

/** Single sink for RPC capture records from both bridge implementations. */
object RpcCaptureRecorder {
    fun record(
        method: String?,
        request: Any?,
        response: Any?,
        startedAt: Long,
        source: String,
    ) {
        if (method.isNullOrBlank() || response == null) return
        runCatching {
            val record = jsonObject(
                "TimeStamp" to startedAt,
                "Method" to method,
                "Params" to parseJson(request),
                "Data" to parseJson(response),
                "Source" to source,
                "Process" to XposedEnv.processName,
            )
            if (HookUtil.isCaptureForwardEnabled()) {
                HookSender.sendHookData(record, HookUtil.captureForwardUrl())
            }
            StructuredLog.capture(record.toString())
            StructuredLog.capture("RpcCaptureRecorder", "capture.response_matched method=$method source=$source")
        }.onFailure {
            StructuredLog.capture("RpcCaptureRecorder", "抓包记录失败 method=$method source=$source: ${it.message}")
        }
    }

    private fun parseJson(value: Any?): JsonElement? = when (value) {
        null -> null
        is String -> value.toJsonElementOrText()
        else -> value.toString().toJsonElementOrText()
    }

    private fun String.toJsonElementOrText(): JsonElement = runCatching {
        toJsonElement()
    }.getOrElse { kotlinx.serialization.json.JsonPrimitive(this) }
}
