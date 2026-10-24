package io.github.magisk317.sesame.hook.server.handlers

import io.github.magisk317.sesame.util.JsonUtil
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RpcRequestTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun preservesStringAndObjectRequestData() {
        val stringRequest = JsonUtil.decode(
            "{\"methodName\":\"demo\",\"requestData\":\"raw-payload\"}",
            RpcRequest.serializer(),
        )
        val objectRequest = JsonUtil.decode(
            json.encodeToString(
                RpcRequest.serializer(),
                RpcRequest("demo", buildJsonObject { put("value", 1) }),
            ),
            RpcRequest.serializer(),
        )

        assertEquals("raw-payload", stringRequest.getRequestDataString())
        assertEquals("{\"value\":1}", objectRequest.getRequestDataString())
    }
}
