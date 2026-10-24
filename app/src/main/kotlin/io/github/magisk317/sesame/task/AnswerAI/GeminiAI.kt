package io.github.magisk317.sesame.task.AnswerAI

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class GeminiAI(apiKey: String?) : AbstractAiProvider(apiKey.orEmpty()) {
    init { selectedModel = "gemini-2.5-flash" }

    override fun endpoint(): String = "https://api.genai.gd.edu.kg/google/v1beta/models/$selectedModel:generateContent"

    override fun queryKey(): Boolean = true

    override fun responsePath(): Array<String> = arrayOf("candidates", "0", "content", "parts", "0", "text")

    override fun payload(text: String) = buildJsonObject {
        putJsonArray("contents") {
            add(buildJsonObject {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", "只回答答案 ${text.replace(Regex("\\p{Cntrl}&&[^\\n\\t]"), "")}") })
                }
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
