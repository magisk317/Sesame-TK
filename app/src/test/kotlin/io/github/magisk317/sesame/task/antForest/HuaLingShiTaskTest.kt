package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HuaLingShiTaskTest {
    @Test
    fun `requires all three game actions`() {
        assertTrue(HuaLingShiTask.actions.any { it.keyword == "坐骑召唤" && it.count == 3 })
        assertTrue(HuaLingShiTask.actions.any { it.keyword == "灵田施肥" && it.count == 1 })
        assertTrue(HuaLingShiTask.actions.any { it.keyword == "武道会" && it.count == 3 })
    }

    @Test
    fun `builds a label based click script`() {
        val script = HuaLingShiTask.clickScript("坐骑召唤")
        assertTrue(script.contains("坐骑召唤"))
        assertTrue(script.contains("target.click()"))
    }

    @Test
    fun `only a finished server task is accepted`() {
        val response = """
            {
              "forestTasksNew": [{
                "taskInfoList": [{
                  "taskBaseInfo": {
                    "taskType": "${HuaLingShiTask.TASK_TYPE}",
                    "taskStatus": "FINISHED"
                  }
                }]
              }]
            }
        """.toJsonObject()
        assertTrue(HuaLingShiTask.isTaskFinished(response))
        assertFalse(HuaLingShiTask.isTaskFinished(response, "other_task"))
    }
}
