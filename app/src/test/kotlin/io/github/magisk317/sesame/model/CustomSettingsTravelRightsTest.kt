package io.github.magisk317.sesame.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomSettingsTravelRightsTest {
    @Test
    fun mapsTravelRightsByDisplayNameAndStableId() {
        assertEquals("travelRights", CustomSettings.getModuleId("出行权益"))
        assertEquals("travelRights", CustomSettings.getModuleId("travelRights"))
    }
}
