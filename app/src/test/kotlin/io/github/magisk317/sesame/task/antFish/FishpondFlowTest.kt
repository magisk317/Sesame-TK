package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FishpondFlowTest {
    @Test
    fun parsesObservedSyncFixture() {
        val snapshot = FishpondFlow.snapshot(syncFixture(1023, "5555.17"))

        assertNotNull(snapshot)
        assertEquals(1023, snapshot?.rodSumCount)
        assertEquals(BigDecimal("5555.17"), snapshot?.currentFishWeight)
    }

    @Test
    fun parsesObservedSingleStepAngleWithoutPositioningFields() {
        val response = """
            {
              "success": true,
              "resultCode": "100",
              "needRodPositioning": true,
              "rodSumCount": 1023,
              "angleResultInfo": {
                "canExchange": false,
                "fishName": "小丑鱼",
                "fishType": "SMALL_FISH",
                "fishWeight": "2.21",
                "needUserClick": false,
                "newBreed": false
              }
            }
        """.trimIndent().toJsonObject()

        assertEquals(FishpondCatch("小丑鱼", "2.21"), FishpondFlow.caughtFish(response))
        assertFalse(response.getValue("angleResultInfo").toString().contains("fishAreaType"))
    }

    @Test
    fun confirmsCatchOnlyWhenRodAndFishWeightBothAdvance() {
        val before = FishpondSnapshot(1024, BigDecimal("5552.96"))
        val after = FishpondSnapshot(1023, BigDecimal("5555.17"))

        assertEquals(FishpondProgress.CONFIRMED, FishpondFlow.progress(before, after))
        assertEquals(
            FishpondProgress.ROD_NOT_DECREASED,
            FishpondFlow.progress(before, after.copy(rodSumCount = 1024)),
        )
        assertEquals(
            FishpondProgress.FISH_WEIGHT_NOT_INCREASED,
            FishpondFlow.progress(before, after.copy(currentFishWeight = BigDecimal("5552.96"))),
        )
    }

    @Test
    fun rejectsIncompleteSyncFixture() {
        assertNull(FishpondFlow.snapshot("""{"success":true,"rodSumCount":1}""".toJsonObject()))
        assertNull(FishpondFlow.snapshot(syncFixture(-1, "10.00")))
    }

    @Test
    fun parsesObservedActivityStatesAndPositioningChallenge() {
        val response = """
            {
              "needRodPositioning": true,
              "tomorrowRod": {"status":"TODAY_TODO"},
              "fishActivity": {"status":"FINISHED"},
              "giftBox": {"status":"TODO"}
            }
        """.trimIndent().toJsonObject()

        assertTrue(FishpondFlow.requiresRodPositioning(response))
        assertEquals(
            FishpondActivityState("TODAY_TODO", "FINISHED", "TODO"),
            FishpondFlow.activityState(response),
        )
    }

    @Test
    fun recognizesUnreceivedCurrentLotteryPlusGift() {
        val response = """
            {
              "lotteryPlusInfo": {
                "itemId": "3",
                "userSevenDaysGiftsItem": {
                  "userEverydayGiftItems": [
                    {"itemId":"2","received":true},
                    {"itemId":"3","received":false}
                  ]
                }
              }
            }
        """.trimIndent().toJsonObject()

        assertTrue(FishpondFlow.activityState(response).hasLotteryPlusAward())
    }

    private fun syncFixture(rodSumCount: Int, currentFishWeight: String) = """
        {
          "success": true,
          "resultCode": "100",
          "rodSumCount": $rodSumCount,
          "roundInfo": {
            "fishAssetInfo": {
              "currentFishWeight": "$currentFishWeight",
              "diffFishWeight": "4444.83",
              "targetFishWeight": "10000"
            }
          }
        }
    """.trimIndent().toJsonObject()
}
