package io.github.magisk317.sesame.task.other.credit2101

import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CreditAccountServiceTest {

    private fun service(raw: () -> String) = CreditAccountService(
        tag = { "CreditAccountServiceTest" },
        queryRaw = raw,
        logError = {},
    )

    private fun accountJson(
        success: Boolean = true,
        creditProfile: Int = 12,
        creditSpValue: Int = 3,
        exploreStamina: Int = 7,
        energyStamina: Int = 42,
        lotteryNo: Int = 2,
        cityCode: String = "110000",
        cityName: String = "北京市",
    ): String = jsonObject(
        "success" to success,
        "lotteryNo" to lotteryNo,
        "accountAssetVO" to jsonObject(
            "creditProfile" to creditProfile,
            "creditSpValue" to creditSpValue,
        ),
        "exploreStaminaVO" to jsonObject("staminaAvailable" to exploreStamina),
        "energyStaminaVO" to jsonObject("staminaAvailable" to energyStamina),
        "accountVO" to jsonObject(
            "cityCode" to cityCode,
            "cityName" to cityName,
        ),
    ).toString()

    @Test
    fun parsesAllAccountFields() {
        val svc = service { accountJson() }

        val account = svc.query()

        assertEquals(12, account?.creditProfile)
        assertEquals(3, account?.creditSpValue)
        assertEquals(7, account?.exploreStamina)
        assertEquals(42, account?.energyStamina)
        assertEquals(2, account?.lotteryNo)
        assertEquals("110000", account?.cityCode)
        assertEquals("北京市", account?.cityName)
    }

    @Test
    fun returnsNullWhenSuccessIsFalse() {
        val svc = service { accountJson(success = false) }

        assertNull(svc.query())
    }

    @Test
    fun toleratesMissingNestedObjects() {
        val raw = """{"success":true,"lotteryNo":0}"""
        val svc = service { raw }

        val account = svc.query()

        assertEquals(0, account?.creditProfile)
        assertEquals(0, account?.exploreStamina)
        assertEquals(0, account?.energyStamina)
        assertEquals(0, account?.lotteryNo)
    }

    @Test
    fun returnsNullWhenRawIsNotJson() {
        val svc = service { "not-json" }

        assertNull(svc.query())
    }
}
