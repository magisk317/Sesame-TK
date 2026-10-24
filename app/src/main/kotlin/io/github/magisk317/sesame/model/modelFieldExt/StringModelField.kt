package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.util.TimeConfigNormalizer

open class StringModelField(
    code: String,
    name: String,
    value: String?,
) : ModelField<String?>(code, name, value) {
    override fun getType(): String = "STRING"

    override fun getConfigValue(): String? = if (TimeConfigNormalizer.isTimeField(code)) {
        TimeConfigNormalizer.normalize(value)
    } else {
        value
    }

    override fun setConfigValue(configValue: String?) {
        value = if (TimeConfigNormalizer.isTimeField(code)) {
            TimeConfigNormalizer.normalize(configValue)
        } else {
            configValue
        }
    }
}
