package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestRankingCollectionServiceTest {
    @Test
    fun remainingIdsSkipTheFirstPageAndCurrentUser() {
        val ranking = jsonArray(
            *((0 until 22).map { index ->
                jsonObject("userId" to if (index == 20) "self" else "user$index")
            }.toTypedArray()),
        )

        assertEquals(
            listOf("user21"),
            ForestRankingCollectionService.remainingUserIds(ranking, "self"),
        )
    }
}
