package io.github.magisk317.sesame.hook.rpc.bridge

import io.github.magisk317.sesame.data.General
import io.github.magisk317.sesame.entity.RpcEntity
import io.github.magisk317.sesame.hook.ApplicationHook
import io.github.magisk317.sesame.hook.HookUtil
import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.hook.Toast
import io.github.magisk317.sesame.hook.rpc.intervallimit.RpcIntervalLimit
import io.github.magisk317.sesame.util.CoroutineUtils
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.SwipeUtil
import io.github.magisk317.xposed.HookHelpers
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** New Ariver RPC transport. All target-app reflection is kept inside this class. */
class NewRpcBridge : RpcBridge {
    private companion object {
        const val ALIPAY_START_DEBOUNCE_MS = 8_000L
        const val RESPONSE_TIMEOUT_MS = 8_000L
        private val alipayStartLock = Any()
        @Volatile private var lastAlipayStartAt = 0L
    }

    private val tag = "NewRpcBridge"
    private val errorCodes = setOf("1004", "1009", "2000", "46", "48")
    private val errorMessages = setOf("繁忙", "拒绝", "网络不可用", "重试")
    private val silentMethods = setOf(
        "com.alipay.adexchange.ad.facade.xlightPlugin",
        "alipay.antforest.forest.h5.takeLook",
        "com.alipay.innovationprod.biz.rpc.queryAccountAsset",  // 信用2101 服务端持续 error:3000
    )

    @Volatile private var loader: ClassLoader? = null
    @Volatile private var rpcInstance: Any? = null
    @Volatile private var parseObjectMethod: Method? = null
    @Volatile private var callbackTypes: Array<Class<*>>? = null
    @Volatile private var rpcMethod: Method? = null

    override fun getVersion(): RpcVersion = RpcVersion.NEW

    @Synchronized
    @Throws(Exception::class)
    override fun load() {
        val classLoader = ApplicationHook.classLoader ?: throw IllegalStateException("Application class loader unavailable")
        loader = classLoader
        try {
            val nebula = HookHelpers.findClass("com.alipay.mobile.nebulacore.Nebula", classLoader)
            val service = HookHelpers.callStaticMethod(nebula, "getService")
            val extensionManager = HookHelpers.callMethod(service, "getExtensionManager")
            val factory = extensionManager!!.javaClass.getDeclaredMethod("createExtensionInstance", Class::class.java).apply {
                isAccessible = true
            }
            rpcInstance = factory.invoke(null, classLoader.loadClass("com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension"))
            if (rpcInstance == null) {
                val extensionMap = HookHelpers.callMethod(extensionManager, "getNodeExtensionMap") as? Map<*, *>
                rpcInstance = extensionMap?.values?.asSequence()
                    ?.filterIsInstance<Map<*, *>>()
                    ?.flatMap { it.entries.asSequence() }
                    ?.firstOrNull { it.key == "com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension" }
                    ?.value
            }
            val instance = rpcInstance ?: error("get newRpcInstance is null")
            parseObjectMethod = classLoader.loadClass("com.alibaba.fastjson.JSON").getMethod("parseObject", String::class.java)
            val callbackType = classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.extension.BridgeCallback")
            callbackTypes = arrayOf(callbackType)
            rpcMethod = instance.javaClass.getMethod(
                "rpc",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                String::class.java,
                classLoader.loadClass(General.JSON_OBJECT_NAME),
                String::class.java,
                classLoader.loadClass(General.JSON_OBJECT_NAME),
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                String::class.java,
                classLoader.loadClass("com.alibaba.ariver.app.api.App"),
                classLoader.loadClass("com.alibaba.ariver.app.api.Page"),
                classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.model.ApiContext"),
                callbackType,
            )
            StructuredLog.record(tag, "get newRpcCallMethod successfully")
        } catch (error: Throwable) {
            StructuredLog.record(tag, "get newRpcCallMethod err:")
            throw error
        }
    }

    override fun unload() {
        rpcMethod = null
        callbackTypes = null
        parseObjectMethod = null
        rpcInstance = null
        loader = null
    }

    override fun requestString(entity: RpcEntity?, tryCount: Int, retryInterval: Int): String? =
        requestObject(entity, tryCount, retryInterval)?.responseString

    override fun requestObject(entity: RpcEntity?, tryCount: Int, retryInterval: Int): RpcEntity? {
        if (entity == null || ApplicationHook.offline) return null
        var call = rpcMethod
        var parser = parseObjectMethod
        var instance = rpcInstance
        var classLoader = loader
        var callbackClasses = callbackTypes
        if (call == null || parser == null || instance == null || classLoader == null || callbackClasses == null) {
            runCatching { load() }.onFailure {
                StructuredLog.printStackTrace(tag, "RPC 重新初始化失败", it)
            }.getOrNull() ?: return null
            call = rpcMethod
            parser = parseObjectMethod
            instance = rpcInstance
            classLoader = loader
            callbackClasses = callbackTypes
        }
        if (call == null || parser == null || instance == null || classLoader == null || callbackClasses == null) return null
        val rpcCall = call
        val jsonParser = parser
        val rpcObject = instance
        val rpcLoader = classLoader
        val callbackTypesSnapshot = callbackClasses

        repeat(tryCount.coerceAtLeast(0)) { attempt ->
            try {
                val methodName = entity.requestMethod ?: return null
                RpcIntervalLimit.enterIntervalLimit(methodName)
                val attemptState = RpcAttempt()
                val requestStartedAt = System.currentTimeMillis()
                val callback = Proxy.newProxyInstance(
                    rpcLoader,
                    callbackTypesSnapshot,
                    responseHandler(entity, callbackTypesSnapshot, attemptState, requestStartedAt),
                )
                rpcCall.invoke(
                    rpcObject,
                    methodName,
                    false,
                    false,
                    "json",
                    jsonParser.invoke(null, entity.rpcFullRequestData),
                    "",
                    null,
                    true,
                    false,
                    0,
                    false,
                    "",
                    null,
                    null,
                    null,
                    callback,
                )
                if (!attemptState.completed.await(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    logNull(entity, "回调超时", attempt + 1)
                    sleepBeforeRetry(retryInterval)
                    return@repeat
                }
                if (!attemptState.hasResult) {
                    logNull(entity, "回调无结果", attempt + 1)
                    sleepBeforeRetry(retryInterval)
                    return@repeat
                }
                entity.setResponseObject(attemptState.responseObject, attemptState.responseString)
                if (!attemptState.hasError) return entity
                entity.setError()
                if (handleError(entity)) return null
                return entity
            } catch (error: Throwable) {
                StructuredLog.error(tag, "new rpc request | id: ${entity.hashCode()} | method: ${entity.requestMethod} err:")
                StructuredLog.printStackTrace(error)
            }
            sleepBeforeRetry(retryInterval)
        }
        logNull(entity, "重试次数耗尽", tryCount)
        return null
    }

    private class RpcAttempt {
        val completed = CountDownLatch(1)
        @Volatile var hasResult = false
        @Volatile var hasError = false
        @Volatile var responseObject: Any? = null
        @Volatile var responseString: String? = null
    }

    private fun responseHandler(
        entity: RpcEntity,
        callbackClasses: Array<Class<*>>,
        attempt: RpcAttempt,
        requestStartedAt: Long,
    ): InvocationHandler =
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Proxy for ${callbackClasses.first().name}"
                "sendJSONResponse" -> {
                    val raw = args?.firstOrNull() ?: return@InvocationHandler null
                    try {
                        val responseString = runCatching {
                            HookHelpers.callMethod(raw, "toJSONString") as String
                        }.getOrElse {
                            GlobalThreadPools.sleepCompat(100)
                            HookHelpers.callMethod(raw, "toJSONString") as String
                        }
                        attempt.hasResult = true
                        attempt.responseObject = raw
                        attempt.responseString = responseString
                        HookUtil.captureDirectRpc(
                            entity.requestMethod,
                            entity.rpcFullRequestData,
                            responseString,
                            requestStartedAt,
                        )
                        val hasSuccess = HookHelpers.callMethod(raw, "containsKey", "success") as Boolean
                        val hasIsSuccess = HookHelpers.callMethod(raw, "containsKey", "isSuccess") as Boolean
                        if (!hasSuccess && !hasIsSuccess) {
                            attempt.hasError = true
                            if (shouldShow(entity.requestMethod)) {
                                StructuredLog.error(tag, "new rpc response | method: ${entity.requestMethod} | data: ${entity.responseString}")
                            }
                        }
                    } catch (error: Throwable) {
                        attempt.hasError = true
                        StructuredLog.printStackTrace(tag, "new rpc response callback error", error)
                    } finally {
                        attempt.completed.countDown()
                    }
                    null
                }
                else -> null
            }
        }

    private fun handleError(entity: RpcEntity): Boolean {
        val raw = entity.responseObject ?: return false
        val errorCode = runCatching { HookHelpers.callMethod(raw, "getString", "error") as? String }.getOrNull()
        val errorMessage = runCatching { HookHelpers.callMethod(raw, "getString", "errorMessage") as? String }.getOrNull()
        if (errorMessage?.contains("为了保障您的操作安全，请进行验证后继续") == true) {
            RequestManager.markSecurityVerificationRequired(entity.requestMethod)
            if (!ApplicationHook.shouldEnableSimplePageManager()) {
                StructuredLog.record(tag, "当前支付宝版本不启用自动验证码手势，等待合法安全验证完成")
                return true
            }
            maybeStartAlipayForVerification()
            return true
        }
        if (errorCode in errorCodes || errorMessage in errorMessages) {
            logNull(entity, "网络错误: $errorCode/$errorMessage", 1)
            return true
        }
        return false
    }

    /** The challenge can be returned by several concurrent RPCs. Start the host once per window. */
    private fun maybeStartAlipayForVerification() {
        val now = System.currentTimeMillis()
        if (now - lastAlipayStartAt < ALIPAY_START_DEBOUNCE_MS) {
            StructuredLog.record(tag, "距离上次启动目标应用不足 8 秒，跳过本次启动")
            return
        }
        synchronized(alipayStartLock) {
            val lockedNow = System.currentTimeMillis()
            if (lockedNow - lastAlipayStartAt < ALIPAY_START_DEBOUNCE_MS) {
                StructuredLog.record(tag, "距离上次启动目标应用不足 8 秒，跳过本次启动（并发检查）")
                return
            }
            lastAlipayStartAt = lockedNow
            StructuredLog.record(tag, "检测到安全验证错误，自动启动目标应用进行滑块中...")
            Toast.show("为了保障您的操作安全，请进行验证后继续,自动启动目标应用进行滑块中...")
            ApplicationHook.appContext?.let(SwipeUtil::startAlipay)
        }
    }

    private fun sleepBeforeRetry(interval: Int) {
        when {
            interval < 0 -> CoroutineUtils.sleepCompat(600L + RandomUtil.delay())
            interval > 0 -> CoroutineUtils.sleepCompat(interval.toLong())
        }
    }

    private fun shouldShow(method: String?): Boolean = method != null && method !in silentMethods

    private fun logNull(entity: RpcEntity, reason: String, count: Int) {
        if (shouldShow(entity.requestMethod)) {
            StructuredLog.error(tag, "RPC 返回 null | 方法: ${entity.requestMethod} | 原因: $reason | 重试: $count")
        }
    }
}
