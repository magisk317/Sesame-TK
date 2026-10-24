package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.entity.CollectEnergyEntity
import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestEnergyCollectionResultServiceTest {
    @Test
    fun singleBubbleRequestsDoubleCollectionWithoutReplacingRpc() {
        val service = ForestEnergyCollectionResultService({ "" }, {}, {}, { _, _, _ -> })
        val action = service.handle(
            entity = CollectEnergyEntity("user"),
            result = ForestEnergyResultService.Result(
                resultCode = "SUCCESS",
                resultDescription = "",
                bubbleCount = 1,
                collectedEnergy = 10,
                canCollectAgainIds = listOf(7L),
            ),
            needDouble = false,
            spendTime = 20,
            userName = "friend",
            returnCount = { 0 },
        )

        assertEquals(
            ForestEnergyCollectionResultService.NextAction.CollectAgain(
                bubbleIds = listOf(7L),
                doubleClick = true,
            ),
            action,
        )
    }

    @Test
    fun batchResultRequestsAnotherBatchAndReturnsWaterAfterwards() {
        var collected = 0
        val service = ForestEnergyCollectionResultService(
            emoji = { "*" },
            onCollected = { collected += it },
            showToast = {},
            water = { _, _, count -> assertEquals(1, count) },
        )
        val action = service.handle(
            entity = CollectEnergyEntity("user", jsonObject("bizNo" to "biz")),
            result = ForestEnergyResultService.Result(
                resultCode = "SUCCESS",
                resultDescription = "",
                bubbleCount = 2,
                collectedEnergy = 33,
                canCollectAgainIds = listOf(8L, 9L),
            ),
            needDouble = true,
            spendTime = 30,
            userName = "friend",
            returnCount = { 1 },
        )

        assertEquals(33, collected)
        assertTrue(action is ForestEnergyCollectionResultService.NextAction.CollectAgain)
        assertEquals(false, (action as ForestEnergyCollectionResultService.NextAction.CollectAgain).doubleClick)
    }
}
