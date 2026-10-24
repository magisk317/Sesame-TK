package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FarmFriendFeedServiceTest {
    @Test
    fun feedsHungryFriendAndUpdatesStock() = runBlocking {
        var stock = 200
        var fed = 0
        FarmFriendFeedService(
            targets = { mapOf("friend" to 1) },
            currentUserId = { "self" },
            autoFeedEnabled = { false },
            canFeedToday = { _, _ -> true },
            enterFarm = { friendResponse() },
            parseTarget = FarmFriendAnimalStateService()::parse,
            foodStock = { stock },
            pendingAwardCount = { 0 },
            receiveAwards = {},
            feed = { jsonObject("success" to true, "foodStock" to 20) },
            setFoodStock = { stock = it },
            markFedToday = { fed++ },
            hasReachedLimit = { false },
            markReachedLimit = {},
            displayName = { "好友" },
            warnSelfConflict = {},
            pause = {},
        ).run()

        assertEquals(20, stock)
        assertEquals(1, fed)
    }

    private fun friendResponse() = jsonObject(
        "farmVO" to jsonObject(
            "subFarmVO" to jsonObject(
                "farmId" to "farm",
                "animals" to jsonArray(
                    jsonObject(
                        "masterFarmId" to "farm",
                        "animalStatusVO" to jsonObject(
                            "animalInteractStatus" to "HOME",
                            "animalFeedStatus" to "HUNGRY",
                        ),
                    ),
                ),
            ),
        ),
        "success" to true,
    )
}
