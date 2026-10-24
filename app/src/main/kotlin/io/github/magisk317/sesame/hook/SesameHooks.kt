package io.github.magisk317.sesame.hook

import io.github.magisk317.xposed.HookHandle
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.XposedRuntime
import java.lang.reflect.Executable

/**
 * App-side thin wrappers over magisk-xposed-kit for legacy findAndHook* call sites.
 * Not a reimplementation of old Xposed API 82/100.
 */
object SesameHooks {

    @JvmStatic
    fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypesAndCallback: Any?,
    ): HookHandle {
        val (params, callback) = splitCallback(parameterTypesAndCallback)
        val method = HookHelpers.findMethodExact(clazz, methodName, *params)
        return XposedRuntime.hook(method, callback)
    }

    @JvmStatic
    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypesAndCallback: Any?,
    ): HookHandle {
        val clazz = HookHelpers.findClass(className, classLoader)
        return findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
    }

    @JvmStatic
    fun findAndHookConstructor(
        clazz: Class<*>,
        vararg parameterTypesAndCallback: Any?,
    ): HookHandle {
        val (params, callback) = splitCallback(parameterTypesAndCallback)
        val ctor = HookHelpers.findConstructorExact(clazz, *params)
        return XposedRuntime.hook(ctor, callback)
    }

    @JvmStatic
    fun findAndHookConstructor(
        className: String,
        classLoader: ClassLoader?,
        vararg parameterTypesAndCallback: Any?,
    ): HookHandle {
        val clazz = HookHelpers.findClass(className, classLoader)
        return findAndHookConstructor(clazz, *parameterTypesAndCallback)
    }

    @JvmStatic
    fun hookMethod(member: Executable, callback: MethodHook): HookHandle =
        XposedRuntime.hook(member, callback)

    @JvmStatic
    fun returnConstant(value: Any?): MethodHook = object : MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            param.result = value
        }
    }

    private fun splitCallback(parameterTypesAndCallback: Array<out Any?>): Pair<Array<Any?>, MethodHook> {
        require(parameterTypesAndCallback.isNotEmpty()) { "no callback defined" }
        val last = parameterTypesAndCallback.last()
        require(last is MethodHook) { "callback must be MethodHook, got ${last?.javaClass}" }
        val params = parameterTypesAndCallback.dropLast(1).toTypedArray()
        return params to last
    }
}
