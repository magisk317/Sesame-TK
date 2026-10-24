package io.github.magisk317.sesame.hook

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs capture-only hooks in Alipay's independent lite2 game process.
 *
 * This deliberately does not initialize ApplicationHook's task/session lifecycle.
 * The process is used only to observe the game's own bridge traffic.
 */
internal object Lite2DebugCaptureHook {
    private const val TAG = "Lite2DebugCaptureHook"
    private const val RPC_BRIDGE_CLASS = "com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension"
    private const val DEFAULT_CALLBACK_CLASS =
        "com.alibaba.ariver.engine.common.bridge.internal.DefaultBridgeCallback"

    private val installed = AtomicBoolean(false)
    private val classLoaderObserverInstalled = AtomicBoolean(false)

    @Volatile
    private var forwardEnabled = false

    @Volatile
    private var forwardUrl = ""

    fun install(classLoader: ClassLoader, captureForwardEnabled: Boolean, captureForwardUrl: String) {
        forwardEnabled = captureForwardEnabled
        forwardUrl = captureForwardUrl
        if (!installed.compareAndSet(false, true)) return

        installClassLoaderObserver(classLoader)
        installAvailableHooks(classLoader)
        XRiverBridgeObserver.install(classLoader)
        StructuredLog.capture(
            TAG,
            "lite2 调试抓包 Hook 初始化: process=${XposedEnv.processName} " +
                "rpc=true forward=$captureForwardEnabled",
        )
    }

    private fun installAvailableHooks(classLoader: ClassLoader) {
        HookUtil.hookRpcBridgeExtension(classLoader, forwardEnabled, forwardUrl)
        HookUtil.hookDefaultBridgeCallback(classLoader)
    }

    private fun installClassLoaderObserver(classLoader: ClassLoader) {
        if (!classLoaderObserverInstalled.compareAndSet(false, true)) return
        runCatching {
            val callback = object : MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val className = param.args.firstOrNull() as? String ?: return
                    if (className != RPC_BRIDGE_CLASS && className != DEFAULT_CALLBACK_CLASS) return
                    val loadedClass = param.result as? Class<*> ?: return
                    val loadedClassLoader = loadedClass.classLoader ?: classLoader
                    when (className) {
                        RPC_BRIDGE_CLASS -> HookUtil.hookRpcBridgeExtension(
                            loadedClassLoader,
                            forwardEnabled,
                            forwardUrl,
                        )
                        DEFAULT_CALLBACK_CLASS -> HookUtil.hookDefaultBridgeCallback(loadedClassLoader)
                    }
                    StructuredLog.capture(TAG, "lite2 目标类已加载: $className")
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
        }.onFailure {
            classLoaderObserverInstalled.set(false)
            StructuredLog.capture(TAG, "lite2 类加载观测 Hook 失败: ${it.javaClass.simpleName}")
        }
    }
}
