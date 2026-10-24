package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.toJsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestPropRpcPayloadsTest {
    @Test
    fun `prop list and collect payloads preserve captured fields`() {
        val list = ForestPropRpcPayloads.queryPropList(true, "v").toJsonArray().getJSONObject(0)
        assertEquals("Y", list.getString("onlyGive"))
        assertEquals("v", list.getString("version"))

        val collect = ForestPropRpcPayloads.collectProp("CONFIG", "GIVE").toJsonArray().getJSONObject(0)
        assertEquals("CONFIG", collect.getString("giveConfigId"))
        assertEquals("GIVE", collect.getString("giveId"))
    }

    @Test
    fun `consume payload omits optional fields only when absent`() {
        val confirmed = ForestPropRpcPayloads.consumeProp("shield", "P", "SHIELD", true, "v")
            .toJsonArray().getJSONObject(0)
        assertEquals("shield", confirmed.getString("propGroup"))
        assertEquals(true, confirmed.getBoolean("secondConfirm"))
        assertTrue(confirmed.getString("sToken").contains("_"))

        val direct = ForestPropRpcPayloads.consumeProp("", "P", "SHIELD", null, "v")
            .toJsonArray().getJSONObject(0)
        assertFalse(direct.has("propGroup"))
        assertFalse(direct.has("secondConfirm"))
    }
}
