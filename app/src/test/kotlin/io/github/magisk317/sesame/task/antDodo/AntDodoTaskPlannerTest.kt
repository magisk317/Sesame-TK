package io.github.magisk317.sesame.task.antDodo

import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AntDodoTaskPlannerTest {
    @Test
    fun `plans finished and allowed todo tasks while ignoring malformed entries`() {
        val response = """
            {
              "data": {
                "taskGroupInfoList": [
                  {"taskInfoList": [
                    {"taskBaseInfo": {"taskType":"DONE","sceneCode":"SCENE","taskStatus":"FINISHED","bizInfo":"{\"taskTitle\":\"Done\",\"awardCount\":\"2\"}"}},
                    {"taskBaseInfo": {"taskType":"TODO","sceneCode":"SCENE","taskStatus":"TODO","bizInfo":"{}"}},
                    {"taskBaseInfo": {"taskType":"BLOCKED","sceneCode":"SCENE","taskStatus":"TODO","bizInfo":"{}"}},
                    {"taskBaseInfo": {"taskType":"BROKEN","sceneCode":"SCENE","taskStatus":"TODO","bizInfo":"not-json"}},
                    {"taskBaseInfo": {"taskType":"NO_SCENE","taskStatus":"TODO","bizInfo":"{}"}}
                  ]}
                ]
              }
            }
            """.trimIndent().toJsonObject()

        val actions = AntDodoTaskPlanner.plan(response, setOf("BLOCKED"))

        assertEquals(3, actions.size)
        assertEquals(AntDodoTaskActionType.RECEIVE_AWARD, actions[0].type)
        assertEquals("Done", actions[0].title)
        assertEquals("2", actions[0].awardCount)
        assertEquals(AntDodoTaskActionType.FINISH, actions[1].type)
        assertEquals("TODO", actions[1].taskType)
        assertEquals("BROKEN", actions[2].taskType)
    }
}
