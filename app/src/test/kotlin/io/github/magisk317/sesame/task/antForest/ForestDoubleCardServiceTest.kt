package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestDoubleCardServiceTest {
    @Test
    fun selectsExpiringDoubleCardAndActivatesOnce() {
        val used = mutableListOf<String>()
        var activated = 0
        val service = ForestDoubleCardService(
            choice = { 1 },
            constantRenewal = { false },
            canUseToday = { true },
            queryBag = { null },
            exchange = { false },
            isWithinUseTime = { true },
            useProp = {
                used += it["propType"]!!.jsonPrimitive.content
                true
            },
            onActivated = { activated++ },
        )

        service.use(
            jsonObject(
                "success" to true,
                "forestPropVOList" to jsonArray(
                    jsonObject("propGroup" to "doubleClick", "propType" to "LATE", "recentExpireTime" to 2),
                    jsonObject("propGroup" to "doubleClick", "propType" to "EARLY", "recentExpireTime" to 1),
                ),
            ),
        )

        assertEquals(listOf("EARLY"), used)
        assertEquals(1, activated)
    }

    @Test
    fun constantRenewalQueriesFreshBagAfterExchange() {
        var exchanged = 0
        var queried = 0
        val service = ForestDoubleCardService(
            choice = { 2 },
            constantRenewal = { true },
            canUseToday = { true },
            queryBag = {
                queried++
                jsonObject(
                    "success" to true,
                    "forestPropVOList" to jsonArray(
                        jsonObject(
                            "propGroup" to "doubleClick",
                            "propType" to "LIMIT_TIME_ENERGY_DOUBLE_CLICK",
                            "recentExpireTime" to 1,
                        ),
                    ),
                )
            },
            exchange = { exchanged++; true },
            isWithinUseTime = { true },
            useProp = { true },
            onActivated = {},
        )

        service.use(jsonObject("success" to true, "forestPropVOList" to jsonArray()))

        assertEquals(1, exchanged)
        assertEquals(1, queried)
    }
}
