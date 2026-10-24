package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.toJsonArray
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LuckyCardRpcTest {
    @Test
    fun `consult request preserves live haoyunka contract`() {
        val request = LuckyCardRpc.consultPayload().toJsonArray().getJSONObject(0)

        assertEquals("HAOYUNKA", request.getString("bizScene"))
        assertEquals("PCCP_2025062404229513342", request.getString("pccpId"))
        assertEquals("", request.getString("benefitCampId"))
    }

    @Test
    fun `task query preserves live haoyunka contract`() {
        val request = LuckyCardRpc.queryTasksPayload().toJsonArray().getJSONObject(0)

        assertEquals("PCCP_2024120204226387059", request.getString("appletId"))
        assertEquals("HAOYUNKA_DAILY", request.getString("bizScene"))
        assertEquals("creditCard", request.getString("bizSceneFrom"))
        assertEquals(1, request.getJSONObject("extInfo").getInt("version"))
    }
}
