package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnergyRainCoroutineTest {
    @Test
    fun existingTodoIsProcessedEvenWhenInitializationIsNotNeeded() {
        val response = endGameResponse("GAME_DONE_SLJYD", "TODO", needInit = false)

        assertTrue(EnergyRainCoroutine.hasPendingForestRescueTask(response))
    }

    @Test
    fun unrelatedGameTaskIsNotSentThroughTheRescueReportFlow() {
        val response = endGameResponse("GAME_DONE_WDHYSJ_new", "TODO", needInit = false)

        assertFalse(EnergyRainCoroutine.hasPendingForestRescueTask(response))
    }

    @Test
    fun finishedRescueTaskDoesNotReportAgain() {
        val response = endGameResponse("GAME_DONE_SLJYD", "FINISHED", needInit = false)

        assertFalse(EnergyRainCoroutine.hasPendingForestRescueTask(response))
    }

    private fun endGameResponse(taskType: String, status: String, needInit: Boolean) = jsonObject(
        "needInitTask" to needInit,
        "energyRainEndGameGroupTask" to jsonObject(
            "taskInfoList" to jsonArray(
                jsonObject("taskBaseInfo" to jsonObject("taskType" to taskType, "taskStatus" to status)),
            ),
        ),
    )
}
