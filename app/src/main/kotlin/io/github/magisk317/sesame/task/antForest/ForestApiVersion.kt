package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.ApplicationHook
import io.github.magisk317.sesame.util.StructuredLog

/** Active forest protocol version selected from the host application version. */
object ForestApiVersion {
    @Volatile
    var value: String = "20250813"

    fun initialize() {
        val hostVersion = ApplicationHook.alipayVersion
        value = when (hostVersion.versionString) {
            "10.7.30.8000" -> "20250813"
            "10.5.88.8000" -> "20240403"
            "10.3.96.8100" -> "20230501"
            else -> "20250813"
        }
        StructuredLog.record("ForestApiVersion", "目标应用版本: $hostVersion, 使用API版本: $value")
    }
}
