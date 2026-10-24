package io.github.magisk317.sesame.util

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.lang.reflect.Array
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

/** JSON facade shared by Kotlin and the remaining Java code. */
object JsonUtil {
    private const val TAG = "JsonUtil"
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    private val prettyJson = Json { prettyPrint = true }

    @JvmStatic
    fun formatJson(value: Any?): String = formatJson(value, true)

    @JvmStatic
    fun formatJson(value: Any?, pretty: Boolean): String {
        return runCatching {
            val element = toElement(value)
            if (pretty) prettyJson.encodeToString(JsonElement.serializer(), element)
            else json.encodeToString(JsonElement.serializer(), element)
        }.getOrElse {
            StructuredLog.error(TAG, "formatJson failed: ${it.message}")
            "null"
        }
    }

    @JvmStatic
    fun <T> decode(body: String, deserializer: DeserializationStrategy<T>): T =
        json.decodeFromString(deserializer, body)

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> parseObject(body: String, type: Type): T = convert(parseElement(body), type) as T

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> parseObject(bean: Any?, type: Type): T = convert(toElement(bean), type) as T

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> parseObject(body: String, clazz: Class<T>): T = convert(parseElement(body), clazz) as T

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> parseObject(bean: Any?, clazz: Class<T>): T = convert(toElement(bean), clazz) as T

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> parseList(body: String, clazz: Class<T>): List<T> =
        (parseElement(body) as? JsonArray)?.map { convert(it, clazz) as T } ?: emptyList()

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T> parseMap(body: String, valueClass: Class<T>): Map<String, T> =
        (parseElement(body) as? JsonObject)?.mapValues { convert(it.value, valueClass) as T } ?: emptyMap()

    @JvmStatic
    fun parseString(body: String, field: String): String? = parseElement(body).jsonObject[field]?.asString()
    @JvmStatic
    fun parseInteger(body: String, field: String): Int? = parseElement(body).jsonObject[field]?.asString()?.toIntOrNull()
    @JvmStatic
    fun parseBoolean(body: String, field: String): Boolean? = parseElement(body).jsonObject[field]?.asString()?.toBooleanStrictOrNull()
    @JvmStatic
    fun parseShort(body: String, field: String): Short? = parseInteger(body, field)?.toShort()
    @JvmStatic
    fun parseByte(body: String, field: String): Byte? = parseInteger(body, field)?.toByte()
    @JvmStatic
    fun parseIntegerList(body: String, field: String): List<Int>? =
        (parseElement(body).jsonObject[field] as? JsonArray)?.mapNotNull { it.asString().toIntOrNull() }

    @JvmStatic
    fun toNode(value: String?): JsonElement? = value?.let(::parseElement)

    @JvmStatic
    fun updateObject(target: Any, body: String) {
        applyObject(target, parseElement(body).jsonObject)
    }

    private fun parseElement(body: String): JsonElement =
        runCatching { json.decodeFromString(JsonElement.serializer(), body) }.getOrElse { JsonNull }

    private fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> runCatching {
            if (value.trim().startsWith('{') || value.trim().startsWith('[')) json.decodeFromString(JsonElement.serializer(), value)
            else JsonPrimitive(value)
        }.getOrElse { JsonPrimitive(value) }
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject { value.forEach { (key, item) -> if (key != null) put(key.toString(), toElement(item)) } }
        is Iterable<*> -> buildJsonArray { value.forEach { add(toElement(it)) } }
        else -> objectToElement(value)
    }

    private fun objectToElement(value: Any): JsonElement = buildJsonObject {
        allFields(value.javaClass).forEach { field ->
            if (Modifier.isStatic(field.modifiers) || Modifier.isTransient(field.modifiers) || field.name.startsWith("$")) return@forEach
            runCatching {
                field.isAccessible = true
                put(field.name, toElement(field.get(value)))
            }
        }
    }

    private fun convert(element: JsonElement, type: Type): Any? = when (type) {
        is Class<*> -> convert(element, type)
        is ParameterizedType -> when (val raw = type.rawType) {
            List::class.java, MutableList::class.java, ArrayList::class.java ->
                (element as? JsonArray)?.map { convert(it, type.actualTypeArguments[0]) }?.toMutableList()
            Set::class.java, MutableSet::class.java, LinkedHashSet::class.java ->
                (element as? JsonArray)?.map { convert(it, type.actualTypeArguments[0]) }?.toMutableSet()
            Map::class.java, MutableMap::class.java, LinkedHashMap::class.java ->
                (element as? JsonObject)?.mapValues { convert(it.value, type.actualTypeArguments[1]) }?.toMutableMap()
            else -> convert(element, raw as Class<*>)
        }
        else -> null
    }

    private fun convert(element: JsonElement, clazz: Class<*>): Any? {
        val primitive = element.asString()
        return when (clazz) {
            String::class.java -> primitive
            Int::class.javaObjectType, Int::class.javaPrimitiveType -> primitive.toIntOrNull() ?: 0
            Long::class.javaObjectType, Long::class.javaPrimitiveType -> primitive.toLongOrNull() ?: 0L
            Boolean::class.javaObjectType, Boolean::class.javaPrimitiveType -> primitive.toBoolean()
            Short::class.javaObjectType, Short::class.javaPrimitiveType -> primitive.toShortOrNull() ?: 0
            Byte::class.javaObjectType, Byte::class.javaPrimitiveType -> primitive.toByteOrNull() ?: 0
            List::class.java, ArrayList::class.java ->
                (element as? JsonArray)?.map(::elementToAny)?.toMutableList() ?: mutableListOf<Any?>()
            Set::class.java, LinkedHashSet::class.java, HashSet::class.java ->
                (element as? JsonArray)?.map(::elementToAny)?.toMutableSet() ?: linkedSetOf<Any?>()
            Map::class.java, LinkedHashMap::class.java, HashMap::class.java ->
                (element as? JsonObject)?.mapValues { elementToAny(it.value) }?.toMutableMap() ?: mutableMapOf<String, Any?>()
            Any::class.java -> elementToAny(element)
            else -> instantiate(element, clazz)
        }
    }

    private fun instantiate(element: JsonElement, clazz: Class<*>): Any? {
        val instance = clazz.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        if (element is JsonObject) applyObject(instance, element)
        return instance
    }

    private fun applyObject(target: Any, objectValue: JsonObject) {
        allFields(target.javaClass).forEach { field ->
            val value = objectValue[field.name] ?: return@forEach
            if (Modifier.isStatic(field.modifiers) || Modifier.isFinal(field.modifiers) || Modifier.isTransient(field.modifiers)) return@forEach
            runCatching {
                field.isAccessible = true
                field.set(target, convert(value, field.genericType))
            }
        }
    }

    private fun allFields(type: Class<*>): List<Field> = buildList {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            addAll(current.declaredFields)
            current = current.superclass
        }
    }

    private fun JsonElement.asString(): String = (this as? JsonPrimitive)?.content ?: ""
    private fun elementToAny(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonObject -> element.mapValues { elementToAny(it.value) }
        is JsonArray -> element.map(::elementToAny)
        is JsonPrimitive -> element.content
    }
}
