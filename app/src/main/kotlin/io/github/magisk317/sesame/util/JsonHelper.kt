package io.github.magisk317.sesame.util

import kotlinx.serialization.DeserializationStrategy

object JsonHelper {
    fun <T> fromJson(json: String, deserializer: DeserializationStrategy<T>): T = JsonUtil.decode(json, deserializer)
    fun toJson(obj: Any): String = JsonUtil.formatJson(obj, false)
}
