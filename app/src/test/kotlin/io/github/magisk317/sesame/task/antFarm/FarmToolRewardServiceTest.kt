package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FarmToolRewardServiceTest {
    private val service = FarmToolRewardService { emptyArray() }

    @Test
    fun parsesFinishedToolTask() {
        val rewards = service.parse(
            jsonObject(
                "success" to true,
                "list" to jsonArray(
                    jsonObject(
                        "taskStatus" to "FINISHED",
                        "taskType" to "TOOL_TASK",
                        "bizInfo" to "{\"awardType\":\"STEALTOOL\",\"awardCount\":2,\"taskTitle\":\"访问好友\"}",
                    ),
                ),
            ),
        )

        assertEquals(1, rewards.size)
        assertEquals(AntFarm.ToolType.STEALTOOL, rewards.single().toolType)
        assertEquals(2, rewards.single().awardCount)
    }

    @Test
    fun filtersFullInventory() {
        val rewards = FarmToolRewardService {
            arrayOf(AntFarm.FarmTool(AntFarm.ToolType.STEALTOOL, toolCount = 20, toolHoldLimit = 20))
        }.parse(
            jsonObject(
                "success" to true,
                "list" to jsonArray(
                    jsonObject(
                        "taskStatus" to "FINISHED",
                        "taskType" to "TOOL_TASK",
                        "bizInfo" to "{\"awardType\":\"STEALTOOL\",\"awardCount\":1}",
                    ),
                ),
            ),
        )

        assertEquals(0, rewards.size)
    }

    @Test
    fun ignoresTodoAndMalformedTasks() {
        val rewards = service.parse(
            jsonObject(
                "success" to true,
                "list" to jsonArray(
                    jsonObject("taskStatus" to "TODO"),
                    jsonObject("taskStatus" to "FINISHED", "bizInfo" to "broken"),
                ),
            ),
        )

        assertEquals(0, rewards.size)
    }
}
