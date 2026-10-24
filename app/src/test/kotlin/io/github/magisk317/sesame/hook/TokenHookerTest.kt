package io.github.magisk317.sesame.hook

import io.github.magisk317.sesame.util.toJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TokenHookerTest {
    @Test
    fun `ignores xlight calls without an Ant Farm refer token`() {
        assertNull(TokenHooker.extractAntFarmReferToken("{}".toJsonObject()))
        assertNull(
            TokenHooker.extractAntFarmReferToken(
                """{"positionRequest":{"spaceCode":"OTHER_AD"}}""".toJsonObject(),
            ),
        )
        assertNull(
            TokenHooker.extractAntFarmReferToken(
                """{"positionRequest":{"referInfo":{"referToken":""}}}""".toJsonObject(),
            ),
        )
    }

    @Test
    fun `extracts a nonblank Ant Farm refer token`() {
        val params = """
            {
              "positionRequest": {
                "spaceCode": "ANTFARM",
                "referInfo": {"referToken": "token-value"}
              }
            }
        """.trimIndent().toJsonObject()

        assertEquals("token-value", TokenHooker.extractAntFarmReferToken(params))
    }
}
