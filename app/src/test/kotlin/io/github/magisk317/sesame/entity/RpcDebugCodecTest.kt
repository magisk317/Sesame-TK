package io.github.magisk317.sesame.entity

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RpcDebugCodecTest {
    @Test
    fun roundTripsDynamicRequestPayloadWithoutReflection() {
        val item = RpcDebugEntity(
            name = "demo",
            method = "demo.method",
            requestData = Json.parseToJsonElement("[{\"value\":1}]"),
            id = "1",
        )

        val restored = RpcDebugCodec.decodeList(RpcDebugCodec.encodeList(listOf(item))).single()

        assertEquals(item, restored)
        assertEquals("[{\"value\":1}]", restored.getRequestDataString())
    }

    @Test
    fun parsesLegacyFieldAliasesAtTheJsonBoundary() {
        val raw = RpcDebugCodec.parseFields(
            "{\"Name\":\"demo\",\"Method\":\"demo.method\",\"RequestData\":{\"ok\":true},\"Desc\":\"test\"}",
        )

        assertEquals("demo", raw.name)
        assertEquals("demo.method", raw.method)
        assertEquals("test", raw.description)
        assertTrue(raw.requestData.toString().contains("ok"))
    }
}
