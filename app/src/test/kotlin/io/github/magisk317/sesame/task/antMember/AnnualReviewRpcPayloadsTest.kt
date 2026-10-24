package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.toJsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnnualReviewRpcPayloadsTest {
    @Test
    fun `query uses the query component and shared envelope`() {
        val body = AnnualReviewRpcPayloads.query().toJsonArray().getJSONObject(0)

        assertEquals("share", body.getString("channel"))
        assertEquals("110000", body.getString("cityCode"))
        assertEquals(AnnualReviewRpcPayloads.QUERY_COMPONENT, body.getString("source"))
        assertEquals(
            "{}",
            body.getJSONObject("components")
                .getJSONObject(AnnualReviewRpcPayloads.QUERY_COMPONENT)
                .toString(),
        )
    }

    @Test
    fun `action payloads use their component and only required fields`() {
        val apply = action(AnnualReviewRpcPayloads.apply("CODE"), AnnualReviewRpcPayloads.APPLY_COMPONENT)
        assertEquals("CODE", apply.getString("code"))
        assertEquals("false", apply.getString("consultAfterLuckDraw"))
        assertEquals("true", apply.getString("skipLuckDrawConsult"))

        val process = action(
            AnnualReviewRpcPayloads.process("CODE", "RECORD"),
            AnnualReviewRpcPayloads.PROCESS_COMPONENT,
        )
        assertEquals("CODE", process.getString("code"))
        assertEquals("RECORD", process.getString("recordNo"))

        val reward = action(
            AnnualReviewRpcPayloads.getReward("CODE", "RECORD"),
            AnnualReviewRpcPayloads.GET_REWARD_COMPONENT,
        )
        assertEquals("CODE", reward.getString("code"))
        assertEquals("RECORD", reward.getString("recordNo"))
        assertEquals("false", reward.getString("consultAfterLuckDraw"))
        assertEquals("true", reward.getString("skipLuckDrawConsult"))
    }

    private fun action(payload: String, component: String) =
        payload.toJsonArray().getJSONObject(0).getJSONObject("components").getJSONObject(component)
}
