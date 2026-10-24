package io.github.magisk317.sesame.task.antFarm

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FarmGameWorkflowServiceTest {
    @Test
    fun completedGameDoesNotRunAgain() = runBlocking {
        var runs = 0
        service(gameFinished = true, playGames = { runs++ }).runGames()
        assertEquals(0, runs)
    }

    @Test
    fun timeWindowRunsGameWhenAccelerationPolicyIsIgnored() = runBlocking {
        var runs = 0
        service(
            insideTimeRange = true,
            ignoreAccelerateLimit = true,
            playGames = { runs++ },
        ).runGames()
        assertEquals(1, runs)
    }

    private fun service(
        gameFinished: Boolean = false,
        insideTimeRange: Boolean = false,
        ignoreAccelerateLimit: Boolean = false,
        playGames: suspend () -> Unit = {},
    ) = FarmGameWorkflowService(
        gameFinished = { gameFinished },
        accelerateEnabled = { false },
        accelerateLimitReached = { false },
        syncFarmState = {},
        insideGameTimeRange = { insideTimeRange },
        ignoreAccelerateLimit = { ignoreAccelerateLimit },
        accelerateCardCount = { 1 },
        usedAccelerateCardCount = { 0 },
        foodStock = { 1000 },
        foodStockLimit = { 1000 },
        gameRewardMax = { 180 },
        taskEnabled = { false },
        taskFinished = { false },
        receiveAwards = {},
        playGames = playGames,
        chouChouLeFinished = { false },
        gameEnabledForChouChouLe = { false },
        chouChouLeTimeReached = { false },
        playChouChouLe = {},
    )
}
