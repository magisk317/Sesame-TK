package io.github.magisk317.sesame.model

import io.github.magisk317.sesame.entity.KVMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.LinkedHashMap

/** Converts the small, closed set of types supported by configuration fields. */
internal object ModelFieldValueCodec {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun decode(input: Any?, type: Type): Any? = convert(toElement(input, type), type)

    private fun toElement(input: Any?, type: Type): JsonElement = when {
        input == null -> JsonNull
        input is JsonElement -> input
        input is String && type == String::class.java -> JsonPrimitive(input)
        input is String && type.isStructured() -> runCatching {
            json.decodeFromString(JsonElement.serializer(), input)
        }.getOrElse { JsonNull }
        input is String -> JsonPrimitive(input)
        input is Number -> JsonPrimitive(input)
        input is Boolean -> JsonPrimitive(input)
        input is Map<*, *> -> buildJsonObject {
            input.forEach { (key, value) -> if (key != null) put(key.toString(), toElement(value, Any::class.java)) }
        }
        input is Iterable<*> -> buildJsonArray {
            input.forEach { add(toElement(it, Any::class.java)) }
        }
        input is KVMap<*, *> -> buildJsonObject {
            put("key", toElement(input.key, Any::class.java))
            put("value", toElement(input.value, Any::class.java))
        }
        else -> JsonPrimitive(input.toString())
    }

    private fun convert(element: JsonElement, type: Type): Any? = when (type) {
        is ParameterizedType -> {
            val raw = type.rawType as? Class<*> ?: return null
            when {
                raw == KVMap::class.java -> KVMap(
                    convert(element.objectValue()["key"] ?: JsonNull, type.actualTypeArguments[0]),
                    convert(element.objectValue()["value"] ?: JsonNull, type.actualTypeArguments[1]),
                )
                raw.isListType() -> element.arrayValue().map {
                    convert(it, type.actualTypeArguments[0])
                }.toMutableList()
                raw.isSetType() -> element.arrayValue().map {
                    convert(it, type.actualTypeArguments[0])
                }.toMutableSet()
                raw.isMapType() -> element.objectValue().entries.associateTo(LinkedHashMap()) { (key, value) ->
                    convert(JsonPrimitive(key), type.actualTypeArguments[0]) to
                        convert(value, type.actualTypeArguments[1])
                }
                else -> convert(element, raw)
            }
        }
        is Class<*> -> when {
            type == Any::class.java -> elementToAny(element)
            type == String::class.java -> element.primitiveValue()
            type == Int::class.java || type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType ->
                element.intValue()
            type == Long::class.java || type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType ->
                element.longValue()
            type == Boolean::class.java || type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType ->
                element.primitiveValue().toBoolean()
            type == Short::class.java || type == Short::class.javaPrimitiveType || type == Short::class.javaObjectType ->
                element.primitiveValue().toShortOrNull() ?: 0
            type == Byte::class.java || type == Byte::class.javaPrimitiveType || type == Byte::class.javaObjectType ->
                element.primitiveValue().toByteOrNull() ?: 0
            type.isListType() -> element.arrayValue().map(::elementToAny).toMutableList()
            type.isSetType() -> element.arrayValue().map(::elementToAny).toMutableSet()
            type.isMapType() -> element.objectValue().mapValues { elementToAny(it.value) }.toMutableMap()
            else -> error("Unsupported model field type: $type")
        }
        else -> error("Unsupported model field type: $type")
    }

    private fun Type.isStructured(): Boolean = this is ParameterizedType ||
        this == List::class.java || this == Set::class.java || this == Map::class.java

    private fun Class<*>.isListType() = List::class.java.isAssignableFrom(this)
    private fun Class<*>.isSetType() = Set::class.java.isAssignableFrom(this)
    private fun Class<*>.isMapType() = Map::class.java.isAssignableFrom(this)

    private fun JsonElement.objectValue() = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement.arrayValue() = this as? JsonArray ?: JsonArray(emptyList())
    private fun JsonElement.primitiveValue() = (this as? JsonPrimitive)?.content.orEmpty()
    private fun JsonElement.intValue(): Int = primitiveValue().toIntOrNull() ?: primitiveValue().toDoubleOrNull()?.toInt() ?: 0
    private fun JsonElement.longValue(): Long = primitiveValue().toLongOrNull() ?: primitiveValue().toDoubleOrNull()?.toLong() ?: 0L

    private fun elementToAny(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonObject -> element.mapValues { elementToAny(it.value) }
        is JsonArray -> element.map(::elementToAny)
        is JsonPrimitive -> element.content
    }
}
