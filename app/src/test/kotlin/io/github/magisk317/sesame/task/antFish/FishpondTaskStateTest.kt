package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FishpondTaskStateTest {
    @Test
    fun parsesTodaySignFromObservedSignList() {
        val snapshot = FishpondTaskStateParser.parse(listTaskFixture())

        assertEquals("2026-08-17", snapshot.todaySign?.signKey)
        assertFalse(snapshot.todaySign?.signed ?: true)
        assertEquals(1, snapshot.todaySign?.awardCount)
    }

    @Test
    fun parsesTaskIdentityAndProgressWithoutInferringRpcActions() {
        val snapshot = FishpondTaskStateParser.parse(listTaskFixture())
        val tasks = snapshot.tasks

        assertEquals(listOf("EXC_MANURE_4_ROD", "FISH_TASK_14"), tasks.map { it.taskId })
        assertEquals(listOf("EXCH_MANURE_4_ROD", "GOFISH"), tasks.map { it.actionType })
        assertEquals(10, tasks.first().rightsTimesLimit)
        assertEquals(2, tasks.last().taskProgress)
        assertEquals(14, tasks.last().taskRequire)
        assertEquals("完成14次钓鱼", tasks.last().title)
        assertEquals(10, snapshot.exchange?.leftCountCanExchange)
        assertEquals(1000, snapshot.exchange?.manurePriceOfRod)
        assertEquals(1710459, snapshot.exchange?.userManure)
    }

    @Test
    fun signatureChangesForSignStatusRightsAndProgress() {
        val initial = FishpondTaskStateParser.parse(listTaskFixture())
        val signed = initial.copy(todaySign = initial.todaySign?.copy(signed = true))
        val exchanged = initial.copy(
            tasks = initial.tasks.map { if (it.taskId == "EXC_MANURE_4_ROD") it.copy(rightsTimes = 1) else it },
        )
        val fished = initial.copy(
            tasks = initial.tasks.map { if (it.taskId == "FISH_TASK_14") it.copy(taskProgress = 3) else it },
        )

        assertTrue(initial.signature() != signed.signature())
        assertTrue(initial.signature() != exchanged.signature())
        assertTrue(initial.signature() != fished.signature())
    }

    @Test
    fun missingTodayAndMalformedTasksAreIgnored() {
        val snapshot = FishpondTaskStateParser.parse(
            """{"signInfo":{"list":[{"today":false,"signed":false}]},"taskList":[{}, {"taskId":"NO_ACTION"}]}"""
                .toJsonObject(),
        )

        assertNull(snapshot.todaySign)
        assertTrue(snapshot.tasks.isEmpty())
    }

    private fun listTaskFixture() = """
        {
          "success": true,
          "resultCode": "100",
          "exchangeManure4RodTaskExt": {
            "leftCountCanExchange": 10,
            "manurePriceOfRod": 1000,
            "userManure": 1710459
          },
          "signInfo": {
            "continuousCount": 0,
            "list": [
              {"awardCount":1,"signKey":"2026-08-17","signed":false,"today":true},
              {"awardCount":1,"signKey":"2026-08-18","signed":false,"today":false}
            ]
          },
          "taskList": [
            {
              "taskId":"EXC_MANURE_4_ROD",
              "taskStatus":"TODO",
              "actionType":"EXCH_MANURE_4_ROD",
              "awardCount":"1",
              "rightsTimes":0,
              "rightsTimesLimit":10,
              "sceneCode":"ANTFISHPOND_TASK",
              "taskProgress":0,
              "taskRequire":0,
              "taskDisplayConfig":{"title":"消耗肥料兑换钓竿"}
            },
            {
              "taskId":"FISH_TASK_14",
              "taskStatus":"TODO",
              "actionType":"GOFISH",
              "awardCount":"3",
              "rightsTimes":0,
              "rightsTimesLimit":1,
              "sceneCode":"ANTFISHPOND_TASK",
              "taskProgress":2,
              "taskRequire":14,
              "taskDisplayConfig":{"title":"完成14次钓鱼"}
            },
            {"taskId":"BROKEN"}
          ]
        }
    """.trimIndent().toJsonObject()
}
