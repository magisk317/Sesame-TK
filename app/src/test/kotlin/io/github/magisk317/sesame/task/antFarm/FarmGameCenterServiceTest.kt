package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FarmGameCenterServiceTest {
    @Test
    fun opensAvailableChestsAndReportsRemainingQuota() = runBlocking {
        var draws = 0
        var reported = 0
        FarmGameCenterService(
            queryList = {
                jsonObject(
                    "success" to true,
                    "gameCenterDrawRights" to jsonObject(
                        "quotaCanUse" to 1,
                        "quotaLimit" to 5,
                        "usedQuota" to 2,
                    ),
                )
            },
            drawAward = {
                draws++
                jsonObject(
                    "success" to true,
                    "gameCenterDrawRights" to jsonObject("quotaCanUse" to 0),
                    "gameCenterDrawAwardList" to jsonArray(
                        jsonObject("awardName" to "饲料", "awardCount" to 10),
                    ),
                )
            },
            reportTask = { reported = it },
            pause = {},
        ).run()

        assertEquals(1, draws)
        assertEquals(3, reported)
    }
}
