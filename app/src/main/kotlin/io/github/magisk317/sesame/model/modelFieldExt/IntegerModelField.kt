package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

open class IntegerModelField : ModelField<Int> {
    val minLimit: Int?
    val maxLimit: Int?

    constructor(code: String, name: String, value: Int) : this(code, name, value, null, null)

    constructor(code: String, name: String, value: Int, minLimit: Int?, maxLimit: Int?) :
        super(code, name, value) {
        this.minLimit = minLimit
        this.maxLimit = maxLimit
    }

    override fun getType(): String = "INTEGER"

    override fun getConfigValue(): String = value.toString()

    override fun setConfigValue(configValue: String?) {
        val normalizedConfigValue = configValue.unwrapLegacyValue()
        var newValue = if (normalizedConfigValue.isNullOrBlank()) {
            defaultValue
        } else {
            normalizedConfigValue.toIntOrNull() ?: run {
                StructuredLog.printStackTrace(IllegalArgumentException("Invalid integer: $configValue"))
                defaultValue
            }
        }
        if (minLimit != null) newValue = maxOf(minLimit, newValue)
        if (maxLimit != null) newValue = minOf(maxLimit, newValue)
        value = newValue
    }

    private fun String?.unwrapLegacyValue(): String? {
        if (this.isNullOrBlank() || !trim().startsWith("{")) return this
        return runCatching {
            val value = Json.parseToJsonElement(this).let { element ->
                (element as? JsonObject)?.get("value") as? JsonPrimitive
            }
            value?.content ?: this
        }.getOrDefault(this)
    }

    class MultiplyIntegerModelField(
        code: String,
        name: String,
        value: Int,
        minLimit: Int?,
        maxLimit: Int?,
        val multiple: Int,
    ) : IntegerModelField(code, name, value * multiple, minLimit, maxLimit) {
        override fun getType(): String = "MULTIPLY_INTEGER"

        override fun setConfigValue(configValue: String?) {
            if (configValue.isNullOrBlank()) {
                reset()
                return
            }
            super.setConfigValue(configValue)
            runCatching { value *= multiple }
                .onFailure { StructuredLog.printStackTrace(it); reset() }
        }

        override fun getConfigValue(): String = (value / multiple).toString()
    }
}
