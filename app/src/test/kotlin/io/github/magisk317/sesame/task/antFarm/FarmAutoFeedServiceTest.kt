package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.task.antFarm.AntFarm.AnimalFeedStatus
import io.github.magisk317.sesame.task.antFarm.AntFarm.AnimalInteractStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FarmAutoFeedServiceTest {
    @Test
    fun sleepingAnimalStopsBeforeRewardOrFeed() = runBlocking {
        var feedCalls = 0
        val service = service(
            state = FarmAutoFeedService.State(
                AnimalInteractStatus.HOME.name,
                AnimalFeedStatus.SLEEPY.name,
            ),
            onFeed = { feedCalls++ },
        )

        service.run(isChildTask = true)

        assertEquals(0, feedCalls)
    }

    @Test
    fun hungryAnimalFeedsAndSyncs() = runBlocking {
        var synced = 0
        val service = service(
            state = FarmAutoFeedService.State(
                AnimalInteractStatus.HOME.name,
                AnimalFeedStatus.HUNGRY.name,
            ),
            onSync = { synced++ },
        )

        service.run(isChildTask = false)

        assertEquals(1, synced)
    }

    private fun service(
        state: FarmAutoFeedService.State,
        onFeed: () -> Unit = {},
        onSync: () -> Unit = {},
    ) = FarmAutoFeedService(
        currentState = { state },
        foodStock = { 180 },
        receiveAwards = {},
        feed = { onFeed(); true },
        sync = onSync,
        useBigEater = { false },
        useAccelerate = { false },
        runFarmGame = {},
        runChouChouLe = {},
        rewardEnabled = { false },
        feedEnabled = { true },
        bigEaterEnabled = { false },
        accelerateEnabled = { false },
        recordFarmGameEnabled = { false },
        chouChouLeEnabled = { false },
    )
}
