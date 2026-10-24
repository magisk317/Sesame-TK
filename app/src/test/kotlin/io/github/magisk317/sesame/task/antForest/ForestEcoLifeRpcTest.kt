package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.length
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestEcoLifeRpcTest {
    @Test
    fun `basic payloads keep channel and sources`() {
        assertEquals("ALIPAY", ForestEcoLifeRpcPayloads.home().getString("channel"))
        assertEquals("search_brandbox", ForestEcoLifeRpcPayloads.home().getString("source"))
        assertEquals("renwuGD", ForestEcoLifeRpcPayloads.open().getString("source"))
        assertEquals(false, ForestEcoLifeRpcPayloads.tick("tick", "DAY", "source").getBoolean("generateEnergy"))
    }

    @Test
    fun `upload payload keeps nested ai result contract`() {
        val payload = ForestEcoLifeRpcPayloads.uploadDishImage("BEFORE_MEALS", "IMAGE", 0.1, 0.2, 0.3, "DAY")
        val upload = payload.getJSONObject("uploadParamMap")
        val results = upload.getJSONArray("AIResult")
        assertEquals(3, results.length())
        assertEquals("guangpan", results.getJSONObject(1).getString("label"))
        assertEquals("IMAGE", upload.getString("imageId"))
        assertEquals("BEFORE_MEALS", upload.getString("operateType"))
        assertEquals("https://mdn.alipayobjects.com/afts/img/IMAGE/original?bz=APM_20000067", upload.getString("imageUrl"))
    }
}
