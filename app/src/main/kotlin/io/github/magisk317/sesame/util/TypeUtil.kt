package io.github.magisk317.sesame.util

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

/** Small reflection helpers used at the remaining dynamic model boundary. */
object TypeUtil {
    @JvmStatic
    fun getClass(type: Type?): Class<*>? = when (type) {
        null -> null
        is Class<*> -> type
        is ParameterizedType -> type.rawType as? Class<*>
        is TypeVariable<*> -> type.bounds.singleOrNull()?.let(::getClass)
        is WildcardType -> type.upperBounds.singleOrNull()?.let(::getClass)
        else -> null
    }

    @JvmStatic
    fun getType(field: Field?): Type? = field?.genericType

    @JvmStatic
    fun getClass(field: Field?): Class<*>? = field?.type

    @JvmStatic
    fun getFirstParamType(method: Method?): Type? = getParamType(method, 0)

    @JvmStatic
    fun getFirstParamClass(method: Method?): Class<*>? = getParamClass(method, 0)

    @JvmStatic
    fun getParamType(method: Method?, index: Int): Type? =
        getParamTypes(method)?.getOrNull(index)

    @JvmStatic
    fun getParamClass(method: Method?, index: Int): Class<*>? =
        getParamClasses(method)?.getOrNull(index)

    @JvmStatic
    fun getParamTypes(method: Method?): Array<Type>? = method?.genericParameterTypes

    @JvmStatic
    fun getParamClasses(method: Method?): Array<Class<*>>? = method?.parameterTypes

    @JvmStatic
    fun getReturnType(method: Method?): Type? = method?.genericReturnType

    @JvmStatic
    fun getReturnClass(method: Method?): Class<*>? = method?.returnType

    @JvmStatic
    fun getTypeArgument(type: Type?): Type? = getTypeArgument(type, 0)

    @JvmStatic
    fun getTypeArgument(type: Type?, index: Int): Type? = getTypeArguments(type)?.getOrNull(index)

    @JvmStatic
    fun getTypeArguments(type: Type?): Array<Type>? = toParameterizedType(type)?.actualTypeArguments

    @JvmStatic
    fun toParameterizedType(type: Type?): ParameterizedType? = toParameterizedType(type, 0)

    @JvmStatic
    fun toParameterizedType(type: Type?, interfaceIndex: Int): ParameterizedType? = when (type) {
        is ParameterizedType -> type
        is Class<*> -> getGenerics(type).getOrNull(interfaceIndex)
        else -> null
    }

    @JvmStatic
    fun getGenerics(clazz: Class<*>): Array<ParameterizedType> = buildList {
        clazz.genericSuperclass
            .takeUnless { it == Any::class.java }
            ?.let(::toParameterizedType)
            ?.let(::add)
        clazz.genericInterfaces.mapNotNull(::toParameterizedType).forEach(::add)
    }.toTypedArray()

    @JvmStatic
    fun isUnknown(type: Type?): Boolean = type == null || type is TypeVariable<*>

    @JvmStatic
    fun hasTypeVariable(vararg types: Type): Boolean = types.any { it is TypeVariable<*> }
}
