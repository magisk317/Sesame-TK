package io.github.magisk317.sesame.task.antOcean

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

internal object OceanRpcSupport {
    const val APP_SOURCE = "chInfo_ch_appcenter__chsub_9patch"
    const val FOREST_SOURCE = "ANT_FOREST"
    const val VERSION = "20241203"

    fun id(): String = System.currentTimeMillis().toString() + RandomUtil.nextLong()

    fun json(vararg values: Pair<String, Any?>): JsonObject = jsonObject(*values)

    fun request(method: String, payload: JsonObject): String =
        RequestManager.requestString(method, jsonArray(payload).toString())
}
