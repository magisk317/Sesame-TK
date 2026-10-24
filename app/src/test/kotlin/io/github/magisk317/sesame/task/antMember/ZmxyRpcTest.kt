package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.toJsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZmxyRpcTest {
    @Test
    fun `growth task payloads keep captured defaults and nested fields`() {
        val guide = ZmxyRpcPayloads.queryGrowthGuideToDoList(null, null).toJsonArray().getJSONObject(0)
        assertEquals("yuebao_7d", guide.getString("guideBehaviorId"))
        assertEquals("1.0.2025.10.27", guide.getString("invokeVersion"))
        assertTrue(guide.getBoolean("switchNewPage"))

        val push = ZmxyRpcPayloads.pushTask("meiriwenda", 123L, "A", "Q", null).toJsonArray()
            .getJSONObject(0)
        assertEquals(123L, push.getLong("bizDate"))
        assertEquals("RIGHT", push.getJSONObject("extInfo").getString("answerStatus"))
    }

    @Test
    fun `alchemy payloads preserve activity version and task identity`() {
        val checkIn = AlchemyRpcPayloads.queryCheckIn("alchemy", "2025-10-22").toJsonArray().getJSONObject(0)
        assertEquals("alchemy", checkIn.getString("sceneCode"))
        assertEquals("2025-10-22", checkIn.getString("version"))

        val zml = AlchemyRpcPayloads.queryCheckIn(AlchemyRpc.SCENE_ZML, "2025-10-22")
            .toJsonArray().getJSONObject(0)
        assertEquals("zml", zml.getString("sceneCode"))

        val complete = AlchemyRpcPayloads.completeTimeLimitedTask("wujianli").toJsonArray()
            .getJSONObject(0)
        assertEquals("wujianli", complete.getString("templateId"))
    }
}
