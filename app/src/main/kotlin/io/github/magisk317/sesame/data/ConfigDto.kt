package io.github.magisk317.sesame.data

import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.task.ModelTask
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ConfigDto(
    val modelFieldsMap: Map<String, Map<String, JsonElement>> = emptyMap(),
)

/** Explicit persistence boundary for the runtime model configuration. */
object ConfigCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    @JvmStatic
    fun encode(config: Config): String {
        val fields = config.modelFieldsMap.mapValues { (_, modelFields) ->
            modelFields.mapValues { (_, field) -> configValueToElement(field.getConfigValue()) }
        }
        return json.encodeToString(ConfigDto.serializer(), ConfigDto(fields))
    }

    @JvmStatic
    fun apply(config: Config, body: String) {
        val dto = json.decodeFromString(ConfigDto.serializer(), body)
        val requested = linkedMapOf<String, ModelFields>()
        dto.modelFieldsMap.forEach { (modelCode, values) ->
            val modelFields = Model.getModelConfigMap()[modelCode]?.fields ?: return@forEach
            values.forEach { (fieldCode, value) ->
                modelFields.getField(fieldCode)?.applyConfigValue(elementToConfigValue(value))
            }
            requested[modelCode] = modelFields
        }
        config.setModelFieldsMap(requested)
    }

    private fun configValueToElement(value: String?): JsonElement {
        if (value == null) return JsonNull
        return runCatching {
            json.decodeFromString(JsonElement.serializer(), value)
        }.getOrElse { JsonPrimitive(value) }
    }

    private fun elementToConfigValue(value: JsonElement): String = when (value) {
        is JsonPrimitive -> value.content
        is JsonObject -> if (value.keys == setOf("value")) {
            value.getValue("value").toString()
        } else {
            value.toString()
        }
        else -> value.toString()
    }
}
