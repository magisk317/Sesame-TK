package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestChouChouLeTest {
    private val task = ForestChouChouLe()

    @Test
    fun `marks a scene only after tasks and lottery are complete`() {
        val complete = completion(total = 2, completed = 2)
        val pending = completion(total = 2, completed = 1)

        assertTrue(task.shouldMarkSceneComplete(true, complete))
        assertFalse(task.shouldMarkSceneComplete(false, complete))
        assertFalse(task.shouldMarkSceneComplete(true, pending))
    }

    @Test
    fun `does not start lottery while award state is pending or unconfirmed`() {
        assertFalse(ForestChouChouLe.TaskLoopResult(true, true).canStartLottery)
        assertFalse(ForestChouChouLe.TaskLoopResult(false, true).canStartLottery)
        assertFalse(ForestChouChouLe.TaskLoopResult(false, false).canStartLottery)
        assertTrue(ForestChouChouLe.TaskLoopResult(true, false).canStartLottery)
    }

    @Test
    fun `mixed malformed task data cannot be marked complete`() {
        val tasks = jsonArray(
            taskInfo("FOREST_NORMAL_DRAW_BROWSE", "RECEIVED", "逛一逛"),
            jsonObject(
                "taskBaseInfo" to jsonObject(
                    "taskType" to "FOREST_NORMAL_DRAW_BROKEN",
                    "taskStatus" to "TODO",
                    "bizInfo" to "{broken",
                ),
            ),
        )

        val summary = task.evaluateTaskCompletion(tasks)

        assertEquals(1, summary.totalTasks)
        assertEquals(1, summary.completedTasks)
        assertEquals(1, summary.malformedTasks)
        assertFalse(task.shouldMarkSceneComplete(true, summary))
    }

    @Test
    fun `malformed task details are skipped without throwing`() {
        assertNull(task.parseTaskName("FOREST_NORMAL_DRAW_BROKEN", "{broken"))
        assertEquals(
            "FOREST_NORMAL_DRAW_BROWSE",
            task.parseTaskName("FOREST_NORMAL_DRAW_BROWSE", "")
        )
    }

    @Test
    fun `blocked tasks still prevent a daily completion flag`() {
        val tasks = jsonArray(taskInfo("FOREST_ACTIVITY_DRAW_XS", "TODO", "玩游戏得新机会"))

        val summary = task.evaluateTaskCompletion(tasks)

        assertEquals(1, summary.totalTasks)
        assertEquals(0, summary.completedTasks)
        assertTrue(summary.pendingTasks.single().requiresManualAction)
        assertFalse(task.shouldMarkSceneComplete(true, summary))
    }

    @Test
    fun `only verified task protocols are eligible for background completion`() {
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_ANTIEP,
            task.backendTaskAction("SSBHC_LLRW_NORMAL")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_XLIGHT,
            task.backendTaskAction("FOREST_ACTIVITY_DRAW_XLIGHT_2")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_XLIGHT,
            task.backendTaskAction("FOREST_NORMAL_DRAW_XLIGHT_1")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_OPEN_GREEN,
            task.backendTaskAction("ZBT_LLRW_ACTIVITY")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_OPEN_GREEN,
            task.backendTaskAction("FOREST_ACTIVITY_DRAW_DINGYUE")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_OPEN_GREEN,
            task.backendTaskAction("MHJLR_LLRW_ACTIVITY")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.UNSUPPORTED,
            task.backendTaskAction("FOREST_ACTIVITY_DRAW_ZCSJHSL")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_OPEN_GREEN,
            task.backendTaskAction("FOREST_NORMAL_DRAW_SLLXX")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_OPEN_GREEN,
            task.backendTaskAction("FOREST_NORMAL_DRAW_QKSL")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.UNSUPPORTED,
            task.backendTaskAction("FOREST_ACTIVITY_DRAW_SQYT_1")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.UNSUPPORTED,
            task.backendTaskAction("FOREST_ACTIVITY_DRAW_LHY_ZHWUFU")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.UNSUPPORTED,
            task.backendTaskAction("FOREST_ACTIVITY_DRAW_ZCYLT_ZHWUFU")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.UNSUPPORTED,
            task.backendTaskAction("WDHYSJ_QDRW_NORMAL")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.UNSUPPORTED,
            task.backendTaskAction("FOREST_NORMAL_DRAW_YYDWLXQ_ZH")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.UNSUPPORTED,
            task.backendTaskAction("SGBHSD_QDRW_CCL_new")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_OPEN_GREEN,
            task.backendTaskAction("FOREST_NORMAL_DRAW_BWXRK_ZH")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_OPEN_GREEN,
            task.backendTaskAction("FKSSJ_LJRWtanxian_NORMAL")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_OPEN_GREEN,
            task.backendTaskAction("FOREST_ACTIVITY_DRAW_TBQD")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_OPEN_GREEN,
            task.backendTaskAction("FOREST_ACTIVITY_DRAW_SLJYD")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.UNSUPPORTED,
            task.backendTaskAction("WGDS_LLRW_ACTIVITY")
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.UNSUPPORTED,
            task.backendTaskAction("FOREST_ACTIVITY_DRAW_YXZY_ZHWUFU")
        )
    }

    @Test
    fun `live costume campaigns use the xlight finish and claim RPC`() {
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_XLIGHT,
            task.backendTaskAction("FOREST_NORMAL_DRAW_XLIGHT_1"),
        )
        assertEquals(
            ForestChouChouLe.BackendTaskAction.FINISH_XLIGHT,
            task.backendTaskAction("FOREST_ACTIVITY_DRAW_XLIGHT_2"),
        )
    }

    @Test
    fun `completion flags are isolated by campaign activity id`() {
        val normalOld = ForestChouChouLe.completionFlag("ANTFOREST_NORMAL_DRAW", "old")
        val normalNew = ForestChouChouLe.completionFlag("ANTFOREST_NORMAL_DRAW", "new")
        val activity = ForestChouChouLe.completionFlag("ANTFOREST_ACTIVITY_DRAW", "new")

        assertFalse(normalOld == normalNew)
        assertFalse(normalNew == activity)
        assertTrue(normalNew.contains("new"))
    }

    @Test
    fun `verified browse task is retryable rather than manual-only`() {
        val tasks = jsonArray(taskInfo("ZBT_LLRW_ACTIVITY", "TODO", "玩一玩找不停"))

        val summary = task.evaluateTaskCompletion(tasks)

        assertFalse(summary.pendingTasks.single().requiresManualAction)
        assertFalse(task.shouldMarkSceneComplete(true, summary))
    }

    @Test
    fun `unsupported task is reported as requiring manual action`() {
        val tasks = jsonArray(taskInfo("FOREST_ACTIVITY_DRAW_SQYT_1", "TODO", "去神奇鱼塘投喂动物"))

        val summary = task.evaluateTaskCompletion(tasks)

        assertTrue(summary.pendingTasks.single().requiresManualAction)
        assertFalse(task.shouldMarkSceneComplete(true, summary))
    }

    @Test
    fun `accepts only explicit non-negative draw balances`() = with(task) {
        assertEquals(3, jsonObject("blance" to 3).drawBalance())
        assertEquals(2, jsonObject("balance" to "2").drawBalance())
        assertNull(jsonObject("blance" to -1).drawBalance())
        assertNull(jsonObject("blance" to "unknown").drawBalance())
        assertNull(jsonObject("blance" to null).drawBalance())
        assertNull(jsonObject().drawBalance())
    }

    @Test
    fun `uses ten draw batch only when more than ten chances remain`() {
        assertEquals(10, task.drawRequestCount(balance = 11, batchDrawEnabled = true))
        assertEquals(1, task.drawRequestCount(balance = 10, batchDrawEnabled = true))
        assertEquals(1, task.drawRequestCount(balance = 11, batchDrawEnabled = false))
    }

    private fun completion(total: Int, completed: Int, malformed: Int = 0) =
        ForestChouChouLe.TaskCompletionSummary(total, completed, malformed, emptyList())

    private fun taskInfo(type: String, status: String, title: String) = jsonObject(
        "taskBaseInfo" to jsonObject(
            "taskType" to type,
            "taskStatus" to status,
            "sceneCode" to "scene",
            "bizInfo" to jsonObject("title" to title).toString(),
        ),
    )
}
