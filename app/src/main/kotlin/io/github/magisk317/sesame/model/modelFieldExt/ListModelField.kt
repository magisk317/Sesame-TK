package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.model.ModelField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.encodeToString
import io.github.magisk317.sesame.util.TimeConfigNormalizer

open class ListModelField(
    code: String,
    name: String,
    value: List<String?>?,
) : ModelField<MutableList<String?>>(code, name, value?.toMutableList() ?: mutableListOf()) {
    override fun getType(): String = "LIST"

    class ListJoinCommaToStringModelField(
        code: String,
        name: String,
        value: List<String?>?,
    ) : ListModelField(code, name, value) {
        override fun setConfigValue(configValue: String?) {
            if (configValue == null) {
                reset()
                return
            }
            val normalized = configValue.trim()
            value = runCatching {
                val element = Json.parseToJsonElement(normalized)
                if (element is JsonArray) {
                    ArrayList<String?>().apply {
                        element.forEach { item ->
                            (item as? JsonPrimitive)?.content?.let { add(normalizeItem(it)) }
                        }
                    }
                } else {
                    null
                }
            }.getOrNull() ?: normalized.split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map(::normalizeItem)
                .toMutableList()
        }

        override fun getConfigValue(): String = Json.encodeToString(value.orEmpty().map { it?.let(::normalizeItem) })

        private fun normalizeItem(item: String): String =
            if (TimeConfigNormalizer.isTimeField(code)) TimeConfigNormalizer.normalize(item).orEmpty() else item
    }
}
