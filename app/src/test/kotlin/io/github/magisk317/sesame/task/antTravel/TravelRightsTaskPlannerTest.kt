package io.github.magisk317.sesame.task.antTravel

import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TravelRightsTaskPlannerTest {
    @Test
    fun choosesNextPageTaskAndSkipsAttemptedTask() {
        val tasks = TravelRightsStateParser.parseTasks(
            """{"success":true,"code":"SUCCESS","taskList":[
                {"taskCode":"A","taskStatus":"claim","displayInfo":{"taskName":"a","jumpLink":"alipays://a"}},
                {"taskCode":"B","taskStatus":"claim","displayInfo":{"taskName":"b","jumpLink":"alipays://b"}}
            ]}""".toJsonObject(),
        )

        assertEquals("A", TravelRightsTaskPlanner.nextPageTask(tasks, emptySet())?.taskCode)
        assertEquals("B", TravelRightsTaskPlanner.nextPageTask(tasks, setOf("A"))?.taskCode)
    }

    @Test
    fun treatsTerminalStatusOrMissingTaskAsProgress() {
        val before = task("A", "claim", 1)
        assertTrue(TravelRightsTaskPlanner.progressed(before, listOf(task("A", "FINISHED", 1))))
        assertTrue(TravelRightsTaskPlanner.progressed(before, emptyList()))
        assertFalse(TravelRightsTaskPlanner.progressed(before, listOf(task("A", "claim", 1))))
        assertTrue(TravelRightsTaskPlanner.progressed(before, listOf(task("A", "claim", 2))))
    }

    @Test
    fun selectsFiveDrawOnlyWhenAtLeastFiveRemain() {
        assertEquals(TravelRightsDrawMode.NONE, TravelRightsDrawPlanner.modeFor(0))
        assertEquals(TravelRightsDrawMode.SINGLE, TravelRightsDrawPlanner.modeFor(4))
        assertEquals(TravelRightsDrawMode.FIVE, TravelRightsDrawPlanner.modeFor(5))
        assertEquals(TravelRightsDrawMode.FIVE, TravelRightsDrawPlanner.modeFor(8))
    }

    private fun task(code: String, status: String, progress: Int) = TravelRightsTaskState(
        taskCode = code,
        title = code,
        status = status,
        claimType = "",
        advanceType = "",
        progress = progress,
        recordNo = "",
        jumpLink = "alipays://$code",
        browseTimeMs = 0L,
        action = TravelRightsTaskAction.PAGE_REQUIRED,
    )
}
