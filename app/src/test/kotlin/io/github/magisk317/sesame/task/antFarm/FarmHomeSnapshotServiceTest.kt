package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FarmHomeSnapshotServiceTest {
    @Test
    fun extractsHomeSectionsUsedByFarmOrchestration() {
        val snapshot = FarmHomeSnapshotService().parse(
            jsonObject(
                "dynamicGlobalConfig" to jsonObject("rewardProductNum" to "REWARD"),
                "farmVO" to jsonObject(
                    "subFarmVO" to jsonObject("giftRecord" to jsonObject("count" to 1)),
                ),
                "activityData" to jsonObject("springGifts" to jsonArray()),
                "cuisineList" to jsonArray(jsonObject("cuisineId" to "cuisine")),
                "lotteryPlusInfo" to jsonObject("activityId" to "lottery"),
            ),
        )

        assertNotNull(snapshot)
        requireNotNull(snapshot)
        assertEquals("REWARD", snapshot.rewardProductNum)
        assertTrue(snapshot.hasGiftRecord)
        assertEquals(1, snapshot.cuisineList?.size)
        assertEquals("lottery", snapshot.lotteryPlusInfo?.get("activityId")?.toString()?.trim('"'))
    }

    @Test
    fun treatsOptionalHomeSectionsAsAbsent() {
        val snapshot = FarmHomeSnapshotService().parse(
            jsonObject(
                "dynamicGlobalConfig" to jsonObject("rewardProductNum" to "REWARD"),
                "farmVO" to jsonObject("subFarmVO" to jsonObject()),
            ),
        )

        assertNotNull(snapshot)
        requireNotNull(snapshot)
        assertNull(snapshot.activityData)
        assertNull(snapshot.cuisineList)
        assertNull(snapshot.lotteryPlusInfo)
        assertFalse(snapshot.hasGiftRecord)
    }

    @Test
    fun rejectsResponsesWithoutFarmStructure() {
        assertNull(FarmHomeSnapshotService().parse(jsonObject("resultCode" to "SUCCESS")))
    }
}
