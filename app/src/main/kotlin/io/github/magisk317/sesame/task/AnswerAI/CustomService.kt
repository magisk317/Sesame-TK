package io.github.magisk317.sesame.task.AnswerAI

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class CustomService(apiKey: String?, baseUrl: String?) : AbstractAiProvider(apiKey.orEmpty()) {
    private val serviceBaseUrl = baseUrl.orEmpty().ifBlank { "https://api.openai.com/v1" }.trimEnd('/')

    init { selectedModel = "gpt-3.5-turbo" }

    override fun endpoint(): String = "$serviceBaseUrl/chat/completions"

    override fun payload(text: String) = buildJsonObject {
        put("model", selectedModel)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "system")
                put("content", "你是一个拥有丰富的知识，并且能根据知识回答问题的专家。")
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", text)
            })
        }
        put("stream", false)
    }
}
