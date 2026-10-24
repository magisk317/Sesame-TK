package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FarmFriendAnimalStateServiceTest {
    @Test
    fun selectsTheFriendsOwnAnimal() {
        val target = FarmFriendAnimalStateService().parse(
            jsonObject(
                "farmVO" to jsonObject(
                    "subFarmVO" to jsonObject(
                        "farmId" to "friend-farm",
                        "animals" to jsonArray(
                            jsonObject(
                                "masterFarmId" to "other-farm",
                                "animalStatusVO" to jsonObject(
                                    "animalInteractStatus" to "HOME",
                                    "animalFeedStatus" to "HUNGRY",
                                ),
                            ),
                            jsonObject(
                                "masterFarmId" to "friend-farm",
                                "animalStatusVO" to jsonObject(
                                    "animalInteractStatus" to "HOME",
                                    "animalFeedStatus" to "HUNGRY",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(target?.isHome == true)
        assertTrue(target?.isHungry == true)
    }

    @Test
    fun reportsNonFeedableFriendStateWithoutDroppingTheTarget() {
        val target = FarmFriendAnimalStateService().parse(
            jsonObject(
                "farmVO" to jsonObject(
                    "subFarmVO" to jsonObject(
                        "farmId" to "friend-farm",
                        "animals" to jsonArray(
                            jsonObject(
                                "masterFarmId" to "friend-farm",
                                "animalStatusVO" to jsonObject(
                                    "animalInteractStatus" to "OUT",
                                    "animalFeedStatus" to "EATING",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertFalse(target?.isHome == true)
        assertFalse(target?.isHungry == true)
    }

    @Test
    fun rejectsMissingFarmAnimalState() {
        assertNull(FarmFriendAnimalStateService().parse(jsonObject("farmVO" to jsonObject())))
    }
}
