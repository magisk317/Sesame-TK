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

class ForestTaskRpcPayloadsTest {
    @Test
    fun `query payload keeps the home task list contract`() {
        val request = ForestTaskRpcPayloads.queryTaskList("20240101").toJsonArray().getJSONObject(0)

        assertEquals("home_task_list", request.getString("fromAct"))
        assertEquals("chInfo_ch_appcenter__chsub_9patch", request.getString("source"))
        assertEquals("20240101", request.getString("version"))
        assertTrue(request.getJSONObject("extend").isEmpty())
    }

    @Test
    fun `v2 query selects captured sources by task family`() {
        val college = ForestTaskRpcPayloads.queryTaskListV2("DNHZ_SL_college", "v").toJsonArray().getJSONObject(0)
        assertEquals("DNHZ_SL_college", college.getString("source"))
        assertEquals("DNHZ_SL_college", college.getJSONObject("extend").getString("firstTaskType"))

        val bhz = ForestTaskRpcPayloads.queryTaskListV2("DXS_BHZ", "v").toJsonArray().getJSONObject(0)
        assertEquals("202212TJBRW", bhz.getString("source"))

        val ordinary = ForestTaskRpcPayloads.queryTaskListV2("ordinary", "v").toJsonArray().getJSONObject(0)
        assertFalse(ordinary.has("source"))
    }

    @Test
    fun `award and finish payloads preserve task identity and scene`() {
        val award = ForestTaskRpcPayloads.receiveTaskAward("SCENE", "TASK").toJsonArray().getJSONObject(0)
        assertEquals(false, award.getBoolean("ignoreLimit"))
        assertEquals("H5", award.getString("requestType"))
        assertEquals("SCENE", award.getString("sceneCode"))
        assertEquals("TASK", award.getString("taskType"))

        val finish = ForestTaskRpcPayloads.finishTask("SCENE", "TASK").toJsonArray().getJSONObject(0)
        assertEquals("H5", finish.getString("requestType"))
        assertEquals("ANTFOREST", finish.getString("source"))
        assertEquals("SCENE", finish.getString("sceneCode"))
        assertEquals("TASK", finish.getString("taskType"))
        assertTrue(finish.getString("outBizNo").startsWith("TASK_"))
    }
}
