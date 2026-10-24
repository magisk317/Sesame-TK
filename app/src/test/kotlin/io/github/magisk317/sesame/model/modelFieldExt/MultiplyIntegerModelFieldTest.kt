package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField.MultiplyIntegerModelField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MultiplyIntegerModelFieldTest {
    private val minuteMillis = 60_000

    @Test
    fun exposesConfigValueInDisplayUnitsAndKeepsRuntimeValueScaled() {
        val field = MultiplyIntegerModelField("interval", "Interval", 50, 1, 12 * 60, minuteMillis)

        assertEquals("50", field.getConfigValue())
        assertEquals(50 * minuteMillis, field.value)

        field.setConfigValue("15")

        assertEquals("15", field.getConfigValue())
        assertEquals(15 * minuteMillis, field.value)
    }

    @Test
    fun appliesLimitsInConfigUnitsBeforeScaling() {
        val field = MultiplyIntegerModelField("interval", "Interval", 50, 1, 12 * 60, minuteMillis)

        field.setConfigValue("0")
        assertEquals("1", field.getConfigValue())
        assertEquals(minuteMillis, field.value)

        field.setConfigValue("721")
        assertEquals("720", field.getConfigValue())
        assertEquals(720 * minuteMillis, field.value)
    }

    @Test
    fun nullConfigRestoresScaledDefault() {
        val field = MultiplyIntegerModelField("interval", "Interval", 50, 1, 12 * 60, minuteMillis)
        field.setConfigValue("15")

        field.setConfigValue(null)

        assertEquals("50", field.getConfigValue())
        assertEquals(50 * minuteMillis, field.value)
    }
}
