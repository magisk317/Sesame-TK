package io.github.magisk317.sesame.model

import io.github.magisk317.sesame.util.JsonUtil
import io.github.magisk317.sesame.util.TimeConfigNormalizer
import io.github.magisk317.sesame.util.TypeUtil
import java.io.Serializable
import java.lang.reflect.Type
import java.util.Objects

/** Configuration value model. Android rendering belongs to the field renderers. */
open class ModelField<T> : Serializable {
    val valueType: Type
    var code: String = ""
    var name: String = ""
    var defaultValue: T = uninitialized()
    var desc: String? = null

    @Volatile
    var value: T = uninitialized()

    constructor() {
        valueType = TypeUtil.getTypeArgument(javaClass.genericSuperclass, 0) ?: Any::class.java
    }

    constructor(value: T) : this(null, null, value)

    constructor(code: String?, name: String?, value: T) : this() {
        this.code = code.orEmpty()
        this.name = name.orEmpty()
        this.defaultValue = value
        setObjectValue(value)
    }

    constructor(code: String?, name: String?, value: T, desc: String?) : this() {
        this.code = code.orEmpty()
        this.name = name.orEmpty()
        this.defaultValue = value
        this.desc = desc
        setObjectValue(value)
    }

    open fun setObjectValue(objectValue: Any?) {
        if (objectValue == null) {
            reset()
            return
        }
        val normalizedValue = if (TimeConfigNormalizer.isTimeField(code)) {
            when (objectValue) {
                is String -> TimeConfigNormalizer.normalize(objectValue)
                is Iterable<*> -> objectValue.map { (it as? String)?.let(TimeConfigNormalizer::normalize) ?: it }
                else -> objectValue
            }
        } else if (valueType == Int::class.javaObjectType && objectValue is Boolean) {
            if (objectValue) 1 else 0
        } else {
            objectValue
        }
        @Suppress("UNCHECKED_CAST")
        value = ModelFieldValueCodec.decode(normalizedValue, valueType) as T
    }

    open fun getType(): String = "DEFAULT"

    open fun getExpandKey(): Any? = null

    open fun getExpandValue(): Any? = null

    open fun toConfigValue(value: T): Any? = value

    open fun fromConfigValue(value: String?): Any? = value

    open fun getConfigValue(): String? = JsonUtil.formatJson(toConfigValue(value))

    open fun setConfigValue(configValue: String?) {
        if (configValue == null) {
            reset()
            return
        }
        val objectValue = fromConfigValue(configValue)
        @Suppress("UNCHECKED_CAST")
        value = ModelFieldValueCodec.decode(objectValue, valueType) as T
    }

    fun applyConfigValue(configValue: String?) {
        setConfigValue(configValue)
    }

    fun reset() {
        value = defaultValue
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModelField<*>) return false
        return valueType == other.valueType &&
            code == other.code &&
            name == other.name &&
            defaultValue == other.defaultValue &&
            desc == other.desc &&
            value == other.value
    }

    override fun hashCode(): Int = Objects.hash(valueType, code, name, defaultValue, desc, value)

    override fun toString(): String =
        "ModelField(valueType=$valueType, code=$code, name=$name, defaultValue=$defaultValue, desc=$desc, value=$value)"

    @Suppress("UNCHECKED_CAST")
    private fun uninitialized(): T = null as T
}
