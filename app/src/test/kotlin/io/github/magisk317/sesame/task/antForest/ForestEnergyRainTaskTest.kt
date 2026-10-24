package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestEnergyRainTaskTest {
    @Test
    fun onlyTheLiveVerifiedTaskGetsBackendActions() {
        assertEquals(
            ForestEnergyRainTask.Action.FINISH,
            ForestEnergyRainTask.actionFor("GAME_30s_SLLXX", "TODO"),
        )
        assertEquals(
            ForestEnergyRainTask.Action.RECEIVE,
            ForestEnergyRainTask.actionFor("GAME_30s_SLLXX", "FINISHED"),
        )
        assertEquals(
            ForestEnergyRainTask.Action.SKIP,
            ForestEnergyRainTask.actionFor("GAME_30s_SLLXX", "RECEIVED"),
        )
        assertEquals(
            ForestEnergyRainTask.Action.SKIP,
            ForestEnergyRainTask.actionFor("GAME_DONE_SLJYD", "TODO"),
        )
        assertEquals(
            ForestEnergyRainTask.Action.SKIP,
            ForestEnergyRainTask.actionFor("GAME_30s_SLXCC", "NOT_TRIGGER"),
        )
        assertEquals(
            ForestEnergyRainTask.Action.SKIP,
            ForestEnergyRainTask.actionFor("GAME_DONE_SGBHSD_new", "TODO"),
        )
        assertEquals(
            ForestEnergyRainTask.Action.SKIP,
            ForestEnergyRainTask.actionFor("GAME_DONE_WDHYSJ_new", "TODO"),
        )
    }

    @Test
    fun parsesEnergyRainTaskTitles() {
        val response = jsonObject(
            "taskInfoList" to jsonArray(
                jsonObject(
                    "taskBaseInfo" to jsonObject(
                        "taskType" to "GAME_30s_SLLXX",
                        "taskStatus" to "TODO",
                        "bizInfo" to jsonObject("taskTitle" to "森林乐消消").toString(),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(ForestEnergyRainTask.TaskInfo("GAME_30s_SLLXX", "TODO", "森林乐消消")),
            ForestEnergyRainTask.parseTasks(response),
        )
    }

    @Test
    fun recognizesProtocolResultCodes() {
        assertTrue(ForestEnergyRainTask.isSuccessful(jsonObject("code" to "100000000")))
        assertTrue(ForestEnergyRainTask.isUnsupported(jsonObject("code" to "400000040")))
        assertTrue(ForestEnergyRainTask.isUnsupported(jsonObject("code" to "400000057")))
        assertFalse(ForestEnergyRainTask.isUnsupported(jsonObject("code" to "100000000")))
    }
}
