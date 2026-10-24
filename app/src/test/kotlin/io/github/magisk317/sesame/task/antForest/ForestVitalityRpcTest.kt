package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestVitalityRpcTest {
    @Test
    fun `vitality item payloads keep scene and request identity`() {
        val list = ForestVitalityRpcPayloads.itemList("SC_ASSETS")
        assertEquals("SC_ASSETS", list.getString("labelType"))
        assertEquals("ANTFOREST_VITALITY", list.getString("sceneCode"))

        val exchange = ForestVitalityRpcPayloads.exchangeBenefit("SPU", "SKU")
        assertEquals("SPU", exchange.getString("spuId"))
        assertEquals("SKU", exchange.getString("skuId"))
        assertTrue(exchange.getString("requestId").contains("_"))
    }

}
