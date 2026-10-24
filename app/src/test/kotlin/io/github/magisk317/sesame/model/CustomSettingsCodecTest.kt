package io.github.magisk317.sesame.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomSettingsCodecTest {
    @Test
    fun roundTripsStableSettings() {
        val settings = CustomSettingsDto(
            onlyOnceDaily = true,
            onlyOnceDailyList = linkedSetOf("antForest", "antSports"),
            autoHandleOnceDaily = true,
            autoHandleOnceDailyTimes = listOf("0700", "2100"),
        )

        assertEquals(settings, CustomSettingsCodec.decode(CustomSettingsCodec.encode(settings)))
    }

    @Test
    fun ignoresFieldsAddedByNewerVersions() {
        val settings = CustomSettingsCodec.decode(
            """{"onlyOnceDaily":true,"onlyOnceDailyList":["antForest"],"futureOption":true}""",
        )

        assertEquals(true, settings.onlyOnceDaily)
        assertEquals(setOf("antForest"), settings.onlyOnceDailyList)
        assertEquals(listOf("0600", "2000"), settings.autoHandleOnceDailyTimes)
    }
}
