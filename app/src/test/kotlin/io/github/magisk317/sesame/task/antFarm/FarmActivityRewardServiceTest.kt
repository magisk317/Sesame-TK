package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.getString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FarmActivityRewardServiceTest {
    @Test
    fun extractsGiftsFromEveryActivityGiftBucket() {
        val gifts = FarmActivityRewardService().gifts(
            jsonObject(
                "springGifts" to jsonArray(
                    jsonObject("foodType" to "WHEAT", "giftIndex" to 1),
                ),
                "summerGifts" to jsonArray(
                    jsonObject("foodType" to "CORN", "giftIndex" to 2),
                ),
                "notGiftData" to jsonArray(jsonObject("giftIndex" to 3)),
            ),
        )

        assertEquals(
            listOf("WHEAT", "CORN"),
            gifts.map { it.getString("foodType") },
        )
    }

    @Test
    fun ignoresMissingOrMalformedGiftBuckets() {
        val gifts = FarmActivityRewardService().gifts(
            jsonObject(
                "missingGifts" to jsonArray(),
                "malformedGifts" to jsonArray(jsonObject("unrelated" to true)),
            ),
        )

        assertEquals(0, gifts.size)
    }
}
