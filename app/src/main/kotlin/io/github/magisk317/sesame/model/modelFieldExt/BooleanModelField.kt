package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.model.ModelField

class BooleanModelField(
    code: String,
    name: String,
    value: Boolean,
) : ModelField<Boolean>(code, name, value) {
    override fun getType(): String = "BOOLEAN"
}
