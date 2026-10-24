package io.github.magisk317.sesame.task.antFish

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class FishpondRpcCallTest {
    @Test
    fun commonPayloadUsesPageContract() {
        val payload = FishpondRpcCall.commonPayload("bizNo" to "x")
        assertEquals("NORMAL", payload["requestType"]?.toString()?.trim('"'))
        assertEquals("GameCenter", payload["sceneCode"]?.toString()?.trim('"'))
        assertEquals("ch_appcenter__chsub_commonapp", payload["source"]?.toString()?.trim('"'))
        assertEquals("x", payload["bizNo"]?.toString()?.trim('"'))
    }

    @Test
    fun anglePayloadMatchesObservedSingleStepContract() {
        val payload = FishpondRpcCall.anglePayload()

        assertEquals("", payload["bizNo"]?.toString()?.trim('"'))
        assertFalse(payload.containsKey("riskToken"))
        assertFalse(payload.containsKey("areaType"))
    }

    @Test
    fun syncPayloadRequestsCurrentPageSections() {
        val types = listOf("FISH_ACTIVITY", "TASK_DISPLAY", "TOMORROW_ROD", "LOTTERY_PLUS")
        val payload = FishpondRpcCall.syncPayload(types)

        assertEquals(types, payload.getValue("syncTypeList").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(payload["appMode"]?.toString() == "\"normal\"")
    }

    @Test
    fun pageActionPayloadsMatchObservedContract() {
        val sign = FishpondRpcCall.signPayload("2026-08-17")
        assertEquals("2026-08-17", sign["signKey"]?.toString()?.trim('"'))
        assertEquals(120, FishpondRpcCall.exchangePayload("EXC", 120, 2)["manurePaid"]?.toString()?.toInt())
        assertEquals("SCENE", FishpondRpcCall.finishPayload("SCENE", "TASK", "OUT")["sceneCode"]?.toString()?.trim('"'))
        assertEquals(false, FishpondRpcCall.receivePayload("SCENE", "TASK")["ignoreLimit"]?.toString()?.toBoolean())
        val activity = FishpondRpcCall.triggerSubplotsPayload("TOMORROW_ROD", "FINISH")
        assertEquals("TOMORROW_ROD", activity["activityType"]?.toString()?.trim('"'))
        assertEquals("FINISH", activity["actionType"]?.toString()?.trim('"'))
    }
}
