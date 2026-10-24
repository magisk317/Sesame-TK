package io.github.magisk317.sesame.hook.server

import kotlinx.serialization.json.Json

object ServerCommon {
    // 🚀 性能优化：全局单例，线程安全，避免每次请求都创建
    val jsonMapper: Json = Json { ignoreUnknownKeys = true }
    const val MIME_JSON = "application/json"
    const val MIME_PLAINTEXT = "text/plain" // 补上这个
}
