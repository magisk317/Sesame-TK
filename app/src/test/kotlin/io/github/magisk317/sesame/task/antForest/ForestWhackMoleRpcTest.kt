package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestWhackMoleRpcTest {
    @Test
    fun `start and hit payloads keep source and version`() {
        assertEquals("senlinguangchangdadishu", ForestWhackMoleRpcPayloads.start("senlinguangchangdadishu").getString("source"))
        val hit = ForestWhackMoleRpcPayloads.hit(7, "TOKEN", "legacy-source")
        assertEquals(7L, hit.getString("moleId").toLong())
        assertEquals("legacy-source", hit.getString("source"))
        assertEquals("TOKEN", hit.getString("token"))
        assertEquals("20250813", hit.getString("version"))
    }

    @Test
    fun `settlement preserves numeric ids and activity fields`() {
        val settle = ForestWhackMoleRpcPayloads.settle("TOKEN", listOf("1", "20"), "legacy-source")
        assertEquals(1L, settle.getJSONArray("moleIdList").getString(0).toLong())
        assertEquals(20L, settle.getJSONArray("moleIdList").getString(1).toLong())
        assertEquals("NORMAL", settle.getString("settlementScene"))
        assertEquals("legacy-source", settle.getString("source"))
    }
}
