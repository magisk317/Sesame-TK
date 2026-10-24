package io.github.magisk317.sesame.model

import java.util.LinkedHashMap

/** Ordered runtime field collection for a model configuration. */
class ModelFields : LinkedHashMap<String, ModelField<*>>() {
    fun addField(modelField: ModelField<*>) {
        put(modelField.code, modelField)
    }

    fun getField(code: String): ModelField<*>? = this[code]
}
