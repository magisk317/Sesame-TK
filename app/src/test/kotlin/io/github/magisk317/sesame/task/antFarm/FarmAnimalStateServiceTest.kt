package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FarmAnimalStateServiceTest {
    @Test
    fun parsesFarmStateAndSelectsOwnerAnimal() {
        val state = FarmAnimalStateService().parse(
            jsonObject(
                "emotionInfo" to jsonObject("finalScore" to 88.5),
                "foodStockLimit" to 2000,
                "subFarmVO" to jsonObject(
                    "farmId" to "farm-1",
                    "foodStock" to 120,
                    "foodInTroughLimit" to 360,
                    "foodStockLimit" to 1800,
                    "useBigEaterTool" to true,
                    "countdown" to 7200,
                    "farmProduce" to jsonObject("benevolenceScore" to 3.0),
                    "rewardList" to jsonArray(
                        jsonObject("consistencyKey" to "key", "friendId" to "friend", "time" to "now"),
                    ),
                    "animals" to jsonArray(
                        jsonObject("animalId" to "other", "masterFarmId" to "other-farm"),
                        jsonObject(
                            "animalId" to "owner",
                            "masterFarmId" to "farm-1",
                            "animalFeedStatus" to "EATING",
                        ),
                    ),
                ),
                "buffInfoVO" to jsonObject(
                    "buffType" to "FENCE",
                    "hasBuffEffect" to true,
                    "buffCountDown" to 600,
                ),
            ),
        )

        assertNotNull(state)
        requireNotNull(state)
        assertEquals(88.5, state.finalScore)
        assertEquals(120, state.foodStock)
        assertEquals(360, state.foodInTroughLimit)
        assertEquals(1800, state.foodStockLimit)
        assertEquals("farm-1", state.ownerFarmId)
        assertTrue(state.serverUseBigEaterTool)
        assertTrue(state.hasFence)
        assertEquals(600, state.fenceCountDown)
        assertEquals(2, state.animals.size)
        assertEquals("owner", state.ownerAnimal.animalId)
        assertEquals(1, state.rewardList?.size)
    }

    @Test
    fun rejectsResponsesWithoutSubFarmState() {
        val state = FarmAnimalStateService().parse(jsonObject("resultCode" to "SUCCESS"))

        assertNull(state)
    }
}
