package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestGameCenterServiceTest {
    @Test
    fun summarizesEnergyAndOtherAwards() {
        val summary = ForestGameCenterService(
            queryList = { "{}" },
            drawAward = { "{}" },
            reportTask = {},
        ).summarizeAwards(
            jsonObject(
                "gameCenterDrawAwardList" to jsonArray(
                    jsonObject("awardType" to "ENERGY", "awardCount" to 12),
                    jsonObject("awardType" to "PROP", "awardName" to "肥料", "awardCount" to 2),
                    jsonObject("awardType" to "ENERGY", "awardCount" to 8),
                ),
            ),
        )

        assertEquals(20, summary.energy)
        assertEquals(listOf("肥料x2"), summary.otherAwards)
    }
}
