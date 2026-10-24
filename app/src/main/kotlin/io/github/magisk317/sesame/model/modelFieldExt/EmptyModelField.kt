package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.model.ModelField

class EmptyModelField @JvmOverloads constructor(
    code: String,
    name: String,
    private val clickRunner: Runnable? = null,
) : ModelField<Any?>(code, name, null) {
    override fun getType(): String = "EMPTY"

    override fun setObjectValue(objectValue: Any?) = Unit

    fun hasAction(): Boolean = clickRunner != null

    fun runAction() {
        clickRunner?.run()
    }
}
