package io.github.magisk317.sesame.task.AnswerAI

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class DeepSeek(apiKey: String?) : AbstractAiProvider(apiKey.orEmpty()) {
    init { selectedModel = "deepseek-reasoner" }

    override fun endpoint(): String = "https://api.deepseek.com/v1/chat/completions"

    override fun payload(text: String) = buildJsonObject {
        put("model", selectedModel)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "system")
                put("content", "你是一个拥有丰富的知识，并且能根据知识回答问题的专家。")
            })
            add(buildJsonObject {
                put("role", "user")
                put("content", text.replace(Regex("\\p{Cntrl}&&[^\\n\\t]"), ""))
            })
        }
        put("stream", false)
    }
}
