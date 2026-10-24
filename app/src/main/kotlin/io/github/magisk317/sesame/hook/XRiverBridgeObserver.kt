package io.github.magisk317.sesame.hook

import android.os.Handler
import android.os.Looper
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.HookHelpers
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Read-only diagnostics for XRiver JavaScript bridge dispatch.
 *
 * This observer deliberately does not serialize arguments or alter results.
 * The target page can carry account tokens in bridge payloads.
 */
object XRiverBridgeObserver {
    private const val TAG = "XRiverBridgeObserver"
    private const val LOG_INTERVAL_MS = 5_000L
    private const val MAX_RETRIES = 5
    private val retryDelaysMs = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L)
    private val installed = AtomicBoolean(false)
    private val retryScheduled = AtomicBoolean(false)
    private val classLoaderHookInstalled = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastLoggedAt = ConcurrentHashMap<String, AtomicLong>()

    private val candidateClasses = listOf(
        "com.alibaba.xriver.service.impl.BridgeCompServiceImpl",
        "com.alipay.mobile.nebulax.engine.webview.bridge.NXWebJavaScriptInterface",
        "com.alibaba.ariver.engine.common.bridge.NativeBridge",
    )

    private val candidateMethodWords = listOf(
        "jsapi", "bridge", "call", "invoke", "dispatch", "execute", "request",
    )

    fun install(classLoader: ClassLoader) {
        if (installed.get()) return


        installClassLoaderObserver()

        val hooked = installNow(classLoader)
        if (hooked > 0) {
            installed.set(true)
            return
        }

        scheduleRetry(classLoader, 0)
    }

    private fun installNow(classLoader: ClassLoader): Int {
        if (installed.get()) return 0

        var hooked = 0
        candidateClasses.forEach { className ->
            val clazz = runCatching { HookHelpers.findClass(className, classLoader) }
                .getOrNull()
                ?: return@forEach
            hooked += installClass(clazz, className)
            StructuredLog.record(TAG, "候选类已加载: $className")
        }

        StructuredLog.record(TAG, "XRiver 只读观测 Hook 安装完成: $hooked 个方法")
        return hooked
    }

    private fun installClassLoaderObserver() {
        if (!classLoaderHookInstalled.compareAndSet(false, true)) return
        runCatching {
            val callback = object : MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val className = param.args.firstOrNull() as? String ?: return
                    if (className !in candidateClasses) return
                    val clazz = param.result as? Class<*> ?: return
                    val count = installClass(clazz, className)
                    StructuredLog.record(TAG, "XRiver 候选类加载: $className 方法=$count")
                    if (count > 0) {
                        installed.set(true)
                    }
                }
            }
            listOf(
                ClassLoader::class.java.getDeclaredMethod("loadClass", String::class.java),
                ClassLoader::class.java.getDeclaredMethod(
                    "loadClass", String::class.java, Boolean::class.javaPrimitiveType,
                ),
            ).forEach { method ->
                method.isAccessible = true
                SesameHooks.hookMethod(method, callback)
            }
            StructuredLog.record(TAG, "XRiver 类加载观测 Hook 已安装")
        }.onFailure {
            classLoaderHookInstalled.set(false)
            StructuredLog.printStackTrace(TAG, "XRiver 类加载观测 Hook 安装失败", it)
        }
    }

    private fun installClass(clazz: Class<*>, className: String): Int {
        logMethodInventory(clazz, className)
        var hooked = 0
        (clazz.declaredMethods.asSequence() + clazz.methods.asSequence())
            .asSequence()
            .filter(::isCandidate)
            .distinctBy { it.toGenericString() }
            .forEach { method ->
                runCatching {
                    method.isAccessible = true
                    SesameHooks.hookMethod(method, observerHook(className, method))
                    hooked++
                }
            }
        return hooked
    }

    private fun logMethodInventory(clazz: Class<*>, className: String) {
        val methods = (clazz.declaredMethods.asSequence() + clazz.methods.asSequence())
            .distinctBy { it.toGenericString() }
            .sortedBy { it.name }
            .toList()
        StructuredLog.record(
            TAG,
            "XRiver 方法清单: $className count=${methods.size} " +
                methods.joinToString(";") { method ->
                    "${method.name}(args=${method.parameterTypes.size}):${method.returnType.name}"
                }.take(3_500),
        )
    }

    private fun scheduleRetry(classLoader: ClassLoader, retry: Int) {
        if (retry >= MAX_RETRIES || !retryScheduled.compareAndSet(false, true)) return
        val delay = retryDelaysMs[retry]
        mainHandler.postDelayed({
            retryScheduled.set(false)
            if (installed.get()) return@postDelayed
            val hooked = installNow(classLoader)
            if (hooked > 0) {
                installed.set(true)
            } else {
                scheduleRetry(classLoader, retry + 1)
            }
        }, delay)
    }

    private fun isCandidate(method: Method): Boolean {
        if (method.isSynthetic || Modifier.isAbstract(method.modifiers)) return false
        val name = method.name.lowercase()
        return candidateMethodWords.any(name::contains)
    }

    private fun observerHook(className: String, method: Method) = object : MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val key = "$className#${method.name}"
            val now = System.currentTimeMillis()
            val last = lastLoggedAt.computeIfAbsent(key) { AtomicLong(0L) }
            val previous = last.get()
            if (now - previous < LOG_INTERVAL_MS || !last.compareAndSet(previous, now)) return

            StructuredLog.record(
                TAG,
                "bridge.call $key args=${param.args.size} result=${param.result?.javaClass?.name ?: "null"}",
            )
        }
    }
}
