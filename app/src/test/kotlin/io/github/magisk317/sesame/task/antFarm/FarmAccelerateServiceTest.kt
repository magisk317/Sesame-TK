package io.github.magisk317.sesame.task.antFarm

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FarmAccelerateServiceTest {
    @Test
    fun doesNotUseCardWhenRemainingFoodIsBelowThreshold() = runBlocking {
        var uses = 0
        val service = service(
            state = FarmAccelerateService.State(60.0, 1.0, 3600.0, 100.0, 100, 1000, "EATING", "NONE"),
            useTool = { uses++ },
        )

        assertEquals(false, service.run())
        assertEquals(0, uses)
    }

    @Test
    fun singleUseStopsAfterOneSuccessfulCard() = runBlocking {
        var uses = 0
        val service = service(
            state = FarmAccelerateService.State(7200.0, 1.0, 3600.0, 100.0, 100, 1000, "EATING", "NONE"),
            useTool = { uses++ },
            continueEnabled = false,
        )

        assertEquals(true, service.run())
        assertEquals(1, uses)
    }

    private fun service(
        state: FarmAccelerateService.State,
        useTool: () -> Unit = {},
        continueEnabled: Boolean = true,
    ) = FarmAccelerateService(
        state = { state },
        canUse = { true },
        markLimit = {},
        useTool = { useTool(); true },
        recordUse = {},
        usedCount = { 1 },
        sync = {},
        feed = { false },
        receiveAwards = {},
        continueEnabled = { continueEnabled },
        maxEmotionEnabled = { false },
        remainingTimeMinutes = { 60 },
        gameFinished = { true },
        foodStock = { 100 },
        foodStockLimit = { 1000 },
        gameRewardMax = { 180 },
    )
}
