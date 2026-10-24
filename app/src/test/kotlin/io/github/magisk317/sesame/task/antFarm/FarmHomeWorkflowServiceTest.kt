package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FarmHomeWorkflowServiceTest {
    @Test
    fun appliesHomeStateThenRunsConfiguredSideEffects() {
        val events = mutableListOf<String>()
        val service = FarmHomeWorkflowService(
            snapshotService = FarmHomeSnapshotService(),
            applySnapshot = { events += "state" },
            claimActivityRewards = { events += "rewards" },
            shouldUseSpecialFood = { true },
            useSpecialFood = { events += "food:${it.size}" },
            drawLotteryPlus = { events += "lottery" },
            shouldAcceptGift = { true },
            canAcceptGift = { true },
            acceptGift = { events += "gift" },
        )

        assertTrue(service.process(homeResponse(withOptionalSections = true)))
        assertEquals(listOf("state", "rewards", "food:1", "lottery", "gift"), events)
    }

    @Test
    fun skipsOptionalSideEffectsWhenDisabled() {
        val events = mutableListOf<String>()
        val service = FarmHomeWorkflowService(
            snapshotService = FarmHomeSnapshotService(),
            applySnapshot = { events += "state" },
            claimActivityRewards = { events += "rewards" },
            shouldUseSpecialFood = { false },
            useSpecialFood = { events += "food" },
            drawLotteryPlus = { events += "lottery" },
            shouldAcceptGift = { false },
            canAcceptGift = { true },
            acceptGift = { events += "gift" },
        )

        assertTrue(service.process(homeResponse(withOptionalSections = true)))
        assertEquals(listOf("state", "rewards", "lottery"), events)
    }

    @Test
    fun rejectsMalformedHomeResponsesBeforeApplyingState() {
        var applied = false
        val service = FarmHomeWorkflowService(
            snapshotService = FarmHomeSnapshotService(),
            applySnapshot = { applied = true },
            claimActivityRewards = {},
            shouldUseSpecialFood = { false },
            useSpecialFood = {},
            drawLotteryPlus = {},
            shouldAcceptGift = { false },
            canAcceptGift = { false },
            acceptGift = {},
        )

        assertFalse(service.process(jsonObject("resultCode" to "SUCCESS")))
        assertFalse(applied)
    }

    private fun homeResponse(withOptionalSections: Boolean): kotlinx.serialization.json.JsonObject =
        jsonObject(
            "dynamicGlobalConfig" to jsonObject("rewardProductNum" to "REWARD"),
            "farmVO" to jsonObject(
                "foodStock" to 100,
                "foodStockLimit" to 1800,
                "harvestBenevolenceScore" to 1.0,
                "masterUserInfoVO" to jsonObject("userId" to "user"),
                "subFarmVO" to jsonObject("giftRecord" to jsonObject()),
            ),
            "cuisineList" to if (withOptionalSections) {
                jsonArray(jsonObject("cuisineId" to "cuisine"))
            } else {
                null
            },
            "lotteryPlusInfo" to if (withOptionalSections) jsonObject("activityId" to "lottery") else null,
        )
}
