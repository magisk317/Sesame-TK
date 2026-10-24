package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.model.ModelField

open class TextModelField(
    code: String,
    name: String,
    value: String?,
) : ModelField<String?>(code, name, value) {
    override fun getType(): String = "TEXT"

    override fun getConfigValue(): String? = value

    override fun setConfigValue(configValue: String?) {
        value = configValue
    }

    open class UrlTextModelField(code: String, name: String, value: String) :
        ReadOnlyTextModelField(code, name, value) {
        override fun getType(): String = "URL_TEXT"
    }

    open class ReadOnlyTextModelField(code: String, name: String, value: String) :
        TextModelField(code, name, value) {
        override fun getType(): String = "READ_TEXT"
        fun getValue(): String? = null
        override fun setConfigValue(configValue: String?) = Unit
    }
}
