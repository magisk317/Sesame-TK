package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FarmStealingAnimalStateServiceTest {
    @Test
    fun selectsOnlyAnimalsBelongingToOtherMasters() {
        val targets = FarmStealingAnimalStateService().parse(
            jsonObject(
                "farmVO" to jsonObject(
                    "subFarmVO" to jsonObject(
                        "farmId" to "friend-farm",
                        "animals" to jsonArray(
                            jsonObject(
                                "animalId" to "friend-animal",
                                "masterFarmId" to "friend-farm",
                                "animalStatusVO" to jsonObject("animalFeedStatus" to "EATING"),
                            ),
                            jsonObject(
                                "animalId" to "owner-animal",
                                "masterFarmId" to "owner-farm",
                                "animalStatusVO" to jsonObject("animalFeedStatus" to "EATING"),
                            ),
                            jsonObject(
                                "animalId" to "guest-animal",
                                "masterFarmId" to "guest-farm",
                                "animalStatusVO" to jsonObject("animalFeedStatus" to "EATING"),
                            ),
                        ),
                    ),
                ),
            ),
            ownerFarmId = "owner-farm",
        )

        assertEquals(listOf("guest-animal"), targets.map { it.animalId })
        assertEquals("friend-farm", targets.single().farmId)
        assertTrue(targets.single().status.isNotEmpty())
    }

    @Test
    fun malformedResponsesProduceNoNotificationTargets() {
        assertTrue(
            FarmStealingAnimalStateService()
                .parse(jsonObject("farmVO" to jsonObject()), "owner-farm")
                .isEmpty(),
        )
    }
}
