package io.github.magisk317.sesame.model

import io.github.magisk317.sesame.data.Config
import io.github.magisk317.sesame.data.ConfigCodec
import io.github.magisk317.sesame.entity.KVMap
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.ChoiceModelField
import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField
import io.github.magisk317.sesame.model.modelFieldExt.ListModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountOneModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModelFieldValueCodecTest {
    @Test
    fun configCodecUnwrapsLegacyValueOnlyTimeLists() {
        Model.initAllModel()
        ConfigCodec.apply(
            Config,
            """{"modelFieldsMap":{"BaseModel":{"execAtTimeList":{"value":["0010","0030"]}}}}""",
        )

        assertEquals(
            mutableListOf("0010", "0030"),
            Config.modelFieldsMap.getValue("BaseModel").getField("execAtTimeList")?.value,
        )
    }

    @Test
    fun decodesScalarAndBooleanCompatibility() {
        val enabled = BooleanModelField("enabled", "Enabled", false)
        enabled.setConfigValue("true")
        assertEquals(true, enabled.value)

        val choice = ChoiceModelField("choice", "Choice", 0)
        choice.setObjectValue(true)
        assertEquals(1, choice.value)
    }

    @Test
    fun integerFieldReadsWrappedLegacyConfigValue() {
        val field = IntegerModelField("count", "Count", 1)
        field.setConfigValue("{\"value\":120,\"minLimit\":0}")
        assertEquals(120, field.value)
    }

    @Test
    fun decodesMutableCollectionsFromConfigJson() {
        val list = ListModelField("list", "List", emptyList())
        list.setConfigValue("[\"a\",\"b\"]")
        assertEquals(mutableListOf("a", "b"), list.value)

        val selected = SelectModelField("selected", "Selected", emptySet(), emptyList())
        selected.setConfigValue("[\"a\",\"b\"]")
        assertEquals(mutableSetOf("a", "b"), selected.value)
    }

    @Test
    fun commaListFieldAcceptsLegacyJsonArrayAndPlainNumbers() {
        val list = ListModelField.ListJoinCommaToStringModelField("times", "Times", emptyList())

        list.setConfigValue("[\"0010\",\"0030\"]")
        assertEquals(mutableListOf("0010", "0030"), list.value)
        assertEquals("[\"0010\",\"0030\"]", list.getConfigValue())

        list.setConfigValue("10, 30")
        assertEquals(mutableListOf("10", "30"), list.value)
    }

    @Test
    fun decodesCountMapsAndSingleSelection() {
        val selected = SelectAndCountModelField("selected", "Selected", emptyMap(), emptyList())
        selected.setConfigValue("{\"a\":2.0,\"b\":3}")
        assertEquals(mutableMapOf("a" to 2, "b" to 3), selected.value)

        val one = SelectAndCountOneModelField("one", "One", KVMap(null, null), emptyList())
        one.setConfigValue("{\"key\":\"a\",\"value\":4}")
        assertEquals("a", one.value.key)
        assertEquals(4, one.value.value)
    }
}
