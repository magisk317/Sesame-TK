package io.github.magisk317.sesame.task.antDodo

import kotlinx.serialization.json.JsonObject
import io.github.magisk317.sesame.util.optString

/** Pure book-completion rules, independent of task orchestration. */
object DodoBookPolicy {
    @JvmStatic
    fun isFinished(book: JsonObject?): Boolean {
        if (book == null) return true
        val medalStatus = book.optString("medalGenerationStatus")
        if (medalStatus == "CAN_GENERATE" || medalStatus == "GENERATED") return true

        val progress = book.optString("collectProgress", "")
        val parts = progress.split("/")
        if (parts.size != 2) return false
        val current = parts[0].trim().toIntOrNull() ?: return false
        val total = parts[1].trim().toIntOrNull() ?: return false
        return current >= total
    }
}
