package io.github.magisk317.sesame.ui.settings

import io.github.magisk317.sesame.entity.MapperEntity
import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.model.modelFieldExt.ChoiceModelField
import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountOneModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectOneModelField

/** Kotlin-only accessors for model metadata used by the legacy renderer. */
object SettingsModelAccess {
    @JvmStatic
    fun intMinLimit(field: IntegerModelField?): Int? = field?.minLimit

    @JvmStatic
    fun intMaxLimit(field: IntegerModelField?): Int? = field?.maxLimit

    @JvmStatic
    fun choiceOptions(field: ChoiceModelField?): Array<String> =
        field?.getExpandKey()?.filterNotNull()?.toTypedArray() ?: emptyArray()

    @JvmStatic
    fun expandEntities(field: ModelField<*>?): List<MapperEntity> = runCatching {
        val values = when (field) {
            is SelectModelField -> field.getExpandValue()
            is SelectOneModelField -> field.getExpandValue()
            is SelectAndCountModelField -> field.getExpandValue()
            is SelectAndCountOneModelField -> field.getExpandValue()
            else -> null
        }
        values.orEmpty().toList()
    }.getOrDefault(emptyList())
}
