package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FishpondTaskPlannerTest {
    @Test
    fun plansSignExchangeAndFinishedAwardFromLiveFields() {
        val snapshot = FishpondTaskStateParser.parse(fixture("FINISHED", signed = false))

        assertEquals(
            listOf(
                FishpondTaskAction.Sign("2026-08-17"),
                FishpondTaskAction.Exchange("EXC_MANURE_4_ROD", 3000, 3),
                FishpondTaskAction.Receive("ANTFISHPOND_TASK", "FISH_TASK_14", "完成14次钓鱼"),
            ),
            FishpondTaskPlanner.actions(snapshot, signAttempted = false),
        )
    }

    @Test
    fun neverRepeatsSignAndDoesNotReceiveAlreadyReceivedTask() {
        val snapshot = FishpondTaskStateParser.parse(fixture("RECEIVED", signed = false))

        val actions = FishpondTaskPlanner.actions(snapshot, signAttempted = true)

        assertTrue(actions.none { it is FishpondTaskAction.Sign })
        assertTrue(actions.none { it is FishpondTaskAction.Receive })
    }

    @Test
    fun plansFinishForDirectTriggerAndVisitTasks() {
        // 2026-09-05 真机实测：VISIT/SUBSCRIBE 类任务可直接 finishTask 完成。
        val snapshot = FishpondTaskStateParser.parse(
            """
                {
                  "taskList":[
                    {"taskId":"DIRECT_TRIGGER","taskStatus":"TODO","actionType":"TRIGGER","sceneCode":"ANTFISHPOND_TASK","taskDisplayConfig":{"title":"点击即完成"}},
                    {"taskId":"VISIT_TASK","taskStatus":"TODO","actionType":"VISIT","sceneCode":"ANTFISHPOND_TASK"}
                  ]
                }
            """.trimIndent().toJsonObject(),
        )

        assertEquals(
            listOf(
                FishpondTaskAction.Finish("ANTFISHPOND_TASK", "DIRECT_TRIGGER", "点击即完成"),
                FishpondTaskAction.Finish("ANTFISHPOND_TASK", "VISIT_TASK", "VISIT_TASK"),
            ),
            FishpondTaskPlanner.actions(snapshot, signAttempted = false),
        )
    }

    private fun fixture(taskStatus: String, signed: Boolean) = """
        {
          "success":true,
          "signInfo":{"list":[{"today":true,"signed":$signed,"signKey":"2026-08-17","awardCount":1}]},
          "exchangeManure4RodTaskExt":{"leftCountCanExchange":3,"manurePriceOfRod":1000,"userManure":5000},
          "taskList":[
            {"taskId":"EXC_MANURE_4_ROD","taskStatus":"TODO","actionType":"EXCH_MANURE_4_ROD","sceneCode":"ANTFISHPOND_TASK"},
            {"taskId":"FISH_TASK_14","taskStatus":"$taskStatus","actionType":"GOFISH","sceneCode":"ANTFISHPOND_TASK","taskDisplayConfig":{"title":"完成14次钓鱼"}}
          ]
        }
    """.trimIndent().toJsonObject()
}
