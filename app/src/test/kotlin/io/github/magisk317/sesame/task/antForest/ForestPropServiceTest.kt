package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestPropServiceTest {
    private val service = ForestPropService(
        refreshHome = {},
        onPropName = {},
        onError = { _, error -> throw error },
    )

    @Test
    fun extractsGiftCandidatesAndSkipsIncompleteProps() {
        val candidates = service.giftCandidates(
            jsonObject(
                "forestPropVOList" to jsonArray(
                    jsonObject(
                        "giveConfigVO" to jsonObject("giveConfigId" to "give-1"),
                        "propConfigVO" to jsonObject("propName" to "肥料"),
                        "propIdList" to jsonArray(JsonPrimitive("prop-1")),
                        "holdsNum" to 2,
                    ),
                    jsonObject("propConfigVO" to jsonObject("propName" to "缺少赠送配置")),
                ),
            ),
        )

        assertEquals(1, candidates.size)
        assertEquals("give-1", candidates.single().giveConfigId)
        assertEquals("prop-1", candidates.single().propId)
        assertEquals(2, candidates.single().holdsNum)
    }

    @Test
    fun returnsNoCandidatesForEmptyOrMissingInventory() {
        assertTrue(service.giftCandidates(null).isEmpty())
        assertTrue(service.giftCandidates(jsonObject("forestPropVOList" to jsonArray())).isEmpty())
    }
}
