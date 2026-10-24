package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.model.ModelFields
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier

class AntForestConfigurationTest {
    private val hiddenLegacyFields = listOf(
        "stealthCard",
        "stealthCardConstant",
        "giveProp",
        "whoYouWantToGiveTo",
        "studentCheckIn",
        "showBagList",
    )

    @Test
    fun initializesEveryRuntimeModelField() {
        val model = AntForest()
        model.getFields()

        val uninitializedFields = AntForest::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .filter { ModelField::class.java.isAssignableFrom(it.type) }
            .filter { readField(it, model) == null }
            .map(Field::getName)
            .sorted()

        assertEquals(emptyList<String>(), uninitializedFields)
    }

    @Test
    fun keepsHiddenLegacyFeaturesOutOfSettings() {
        val fields = AntForest().getFields()

        hiddenLegacyFields.forEach { fieldCode ->
            assertFalse(fields.containsKey(fieldCode), fieldCode)
        }
    }

    @Test
    fun keepsHiddenLegacyFeaturesSafelyDisabled() {
        val model = AntForest()
        model.getFields()

        assertEquals(0, readModelFieldValue("stealthCard", model))
        assertEquals(false, readModelFieldValue("stealthCardConstant", model))
        assertEquals(false, readModelFieldValue("giveProp", model))
        assertEquals(emptySet<Any?>(), readModelFieldValue("whoYouWantToGiveTo", model))
        assertEquals(false, readModelFieldValue("showBagList", model))
    }

    private fun readField(field: Field, model: AntForest): Any? = runCatching {
        field.isAccessible = true
        field.get(model)
    }.getOrElse { throw AssertionError("Cannot inspect forest field ${field.name}", it) }

    private fun readModelFieldValue(fieldName: String, model: AntForest): Any? = runCatching {
        val field = AntForest::class.java.getDeclaredField(fieldName)
        (readField(field, model) as ModelField<*>).value
    }.getOrElse { throw AssertionError("Cannot inspect forest model field $fieldName", it) }
}
