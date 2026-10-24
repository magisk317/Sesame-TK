package io.github.magisk317.sesame.task.AnswerAI

import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal object AiHttpClient {
    private const val TAG = "AiHttpClient"
    private val jsonMediaType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    fun post(url: String, apiKey: String?, payload: JsonObject, queryKey: Boolean = false): JsonObject? {
        return runCatching {
            val requestBuilder = Request.Builder()
                .url(if (queryKey) "$url${if ('?' in url) '&' else '?'}key=${apiKey.orEmpty()}" else url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .addHeader("Content-Type", "application/json")
            if (!queryKey) requestBuilder.addHeader("Authorization", "Bearer ${apiKey.orEmpty()}")
            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    StructuredLog.record(TAG, "AI请求失败(${response.code}): $body")
                    return null
                }
                kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
            }
        }.onFailure { StructuredLog.printStackTrace(TAG, it) }.getOrNull()
    }

    fun content(root: JsonObject, vararg path: String): String? {
        var current: JsonElement = root
        path.forEach { segment ->
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> current.getOrNull(segment.toIntOrNull() ?: return null) ?: return null
                else -> return null
            }
        }
        return (current as? JsonPrimitive)?.content
    }
}

abstract class AbstractAiProvider(
    private val apiKey: String,
) : AnswerAIInterface {
    protected var selectedModel: String = ""

    protected abstract fun endpoint(): String
    protected abstract fun payload(text: String): JsonObject
    protected open fun responsePath(): Array<String> = arrayOf("choices", "0", "message", "content")
    protected open fun queryKey(): Boolean = false

    protected open fun answerPrompt(title: String, answerList: List<String>): String =
        title + "\n" + answerList.joinToString("") { "[${it}]" }

    protected open fun parseAnswer(answerText: String, answerList: List<String>): Int {
        return answerList.indexOfFirst { answerText.contains(it) }
    }

    override fun getModelName(): String = selectedModel

    override fun setModelName(modelName: String?) {
        if (!modelName.isNullOrBlank()) selectedModel = modelName
    }

    override fun getAnswerStr(text: String): String {
        val response = AiHttpClient.post(endpoint(), apiKey, payload(text), queryKey()) ?: return ""
        return AiHttpClient.content(response, *responsePath()).orEmpty()
    }

    override fun getAnswerStr(text: String, model: String): String {
        setModelName(model)
        return getAnswerStr(text)
    }

    override fun getAnswer(title: String, answerList: List<String>): Int {
        return parseAnswer(getAnswerStr(answerPrompt(title, answerList)), answerList)
    }
}
