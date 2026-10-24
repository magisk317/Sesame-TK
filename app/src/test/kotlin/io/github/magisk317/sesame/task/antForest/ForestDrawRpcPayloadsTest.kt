package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.toJsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestDrawRpcPayloadsTest {
    @Test
    fun `draw payloads keep the captured protocol fields`() {
        val enter = ForestDrawRpcPayloads.enter("activity", "SCENE", "source").toJsonArray().getJSONObject(0)
        assertEquals("activity", enter.getString("activityId"))
        assertEquals("RPC", enter.getString("requestType"))
        assertEquals("SCENE", enter.getString("sceneCode"))

        val draw = ForestDrawRpcPayloads.draw("activity", "SCENE", "source", "uid").toJsonArray().getJSONObject(0)
        assertEquals("uid", draw.getString("userId"))
        assertEquals("source", draw.getString("source"))

        val batch = ForestDrawRpcPayloads.batchDraw("activity", "SCENE", "source", "uid", 10)
            .toJsonArray().getJSONObject(0)
        assertEquals("activity", batch.getString("activityId"))
        assertEquals("SCENE", batch.getString("sceneCode"))
        assertEquals("source", batch.getString("source"))
        assertEquals("uid", batch.getString("userId"))
        assertEquals(10, batch.getInt("times"))
        assertEquals("RPC", batch.getString("requestType"))
    }

    @Test
    fun `list payload adds business source only when supplied`() {
        val plain = ForestDrawRpcPayloads.list("SCENE", "source", null).toJsonArray().getJSONObject(0)
        assertFalse(plain.has("extend"))

        val extended = ForestDrawRpcPayloads.list("SCENE", "source", "campaign").toJsonArray().getJSONObject(0)
        assertEquals("campaign", extended.getJSONObject("extend").getString("businessSource"))
        assertEquals("android", extended.getJSONObject("extend").getString("osType"))
    }

    @Test
    fun `finish payload chooses the legacy xlight source`() {
        val xlight = ForestDrawRpcPayloads.finishTask4("FOREST_XLIGHT_1", "SCENE")
            .toJsonArray().getJSONObject(0)
        assertEquals("ADBASICLIB", xlight.getString("source"))

        val normal = ForestDrawRpcPayloads.finishTask4("FOREST_ACTIVITY_DRAW_1", "SCENE")
            .toJsonArray().getJSONObject(0)
        assertEquals("task_entry", normal.getString("source"))
        assertTrue(normal.has("outBizNo"))
    }
}
