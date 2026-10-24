package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestProtectionCardServiceTest {
    @Test
    fun usesTheSoonestExpiringShield() {
        val used = mutableListOf<String>()
        val service = service(
            useProp = {
                used += it["propConfigVO"]!!.toString()
                true
            },
        )

        service.useShield(
            jsonObject(
                "forestPropVOList" to jsonArray(
                    jsonObject(
                        "recentExpireTime" to 20,
                        "propConfigVO" to jsonObject("propType" to "ENERGY_SHIELD", "propName" to "late"),
                    ),
                    jsonObject(
                        "recentExpireTime" to 10,
                        "propConfigVO" to jsonObject("propType" to "ENERGY_SHIELD", "propName" to "early"),
                    ),
                ),
            ),
            constantEnabled = false,
        )

        assertEquals(1, used.size)
        assertEquals("{\"propType\":\"ENERGY_SHIELD\",\"propName\":\"early\"}", used.single())
    }

    @Test
    fun exchangesBombWhenInventoryDoesNotContainOne() {
        var exchanged = 0
        var used = 0
        val service = service(
            queryBag = {
                jsonObject(
                    "forestPropVOList" to jsonArray(
                        jsonObject("propType" to "ENERGY_BOMB_CARD"),
                    ),
                )
            },
            exchangeBomb = { exchanged++; true },
            useProp = { used++; true },
        )

        service.useBomb(jsonObject("forestPropVOList" to jsonArray()))

        assertEquals(1, exchanged)
        assertEquals(1, used)
    }

    private fun service(
        queryBag: () -> JsonObject? = { null },
        useProp: (JsonObject) -> Boolean = { true },
        exchangeBomb: () -> Boolean = { false },
    ) = ForestProtectionCardService(
        findProp = { bag, type ->
            bag?.get("forestPropVOList")
                ?.let { it as kotlinx.serialization.json.JsonArray }
                ?.firstOrNull { item -> item.jsonObject["propType"]?.toString()?.trim('"') == type }
                ?.jsonObject
        },
        queryBag = queryBag,
        queryHome = { null },
        useProp = useProp,
        exchangeShield = { false },
        exchangeBomb = exchangeBomb,
        updateHome = {},
    )
}
