package io.github.magisk317.sesame.task.AnswerAI

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class TongyiAI(apiKey: String?) : AbstractAiProvider(apiKey.orEmpty()) {
    init { selectedModel = "qwen-turbo" }

    override fun endpoint(): String = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

    override fun payload(text: String) = buildJsonObject {
        put("model", selectedModel)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "user")
                put("content", text)
            })
        }
    }

    override fun answerPrompt(title: String, answerList: List<String>): String {
        val options = answerList.mapIndexed { index, answer -> "${index + 1}.[${answer}]" }.joinToString("\n")
        return "问题：$title\n\n答案列表：\n\n$options\n\n请只返回答案列表中的序号"
    }

    override fun parseAnswer(answerText: String, answerList: List<String>): Int {
        val index = answerText.trim().toIntOrNull()?.minus(1)
        if (index != null && index in answerList.indices) return index
        return answerList.indexOfFirst { answerText.contains(it) }
    }
}
