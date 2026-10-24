package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.toJsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestCoreRpcTest {
    @Test
    fun `energy entities preserve collection protocol`() {
        val single = ForestCoreRpc.energyEntity("jiaoshui", "USER", 7)
            .requestData!!.toJsonArray().getJSONObject(0)
        assertEquals("jiaoshui", single.getString("bizType"))
        assertEquals("USER", single.getString("userId"))
        assertEquals("7", single.getJSONArray("bubbleIds").getString(0))

        val batch = ForestCoreRpc.batchEnergyEntity("rob", "USER", listOf(1L, 2L))
            .requestData!!.toJsonArray().getJSONObject(0)
        assertEquals(2, batch.getJSONArray("bubbleIds").length())
        assertEquals("BATCH_ROB_ENERGY", batch.getString("fromAct"))
    }
}
