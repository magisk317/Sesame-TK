package io.github.magisk317.sesame.hook

import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.sesame.data.General
import io.github.magisk317.sesame.entity.UserEntity
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.toJsonElement
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque


object HookUtil {
    private const val TAG = "HookUtil"

    /** A H5 callback can be reused by several concurrent RPCs. */
    val rpcHookMap = ConcurrentHashMap<Any, ConcurrentLinkedDeque<Array<Any?>>>()

    @Volatile
    private var captureForwardEnabled = false

    @Volatile
    private var captureForwardUrl = ""

    private const val RPC_CAPTURE_TIMEOUT_MS = 120_000L

    @Volatile
    private var rpcCaptureInstalled = false

    @Volatile
    private var callbackCaptureInstalled = false

    private var lastToastTime = 0L

    // A page can deliver late callbacks after its bridge records were discarded.
    // Keep the diagnostic useful without flooding the task log.
    @Volatile
    private var lastUnmatchedResponseLogAt = 0L

    private var microContextCache: Any? = null

    internal fun isCaptureForwardEnabled(): Boolean = captureForwardEnabled

    internal fun captureForwardUrl(): String = captureForwardUrl

    /**
     * Hook RpcBridgeExtension.rpc 方法，记录请求信息
     */
    fun hookRpcBridgeExtension(classLoader: ClassLoader, isdebug: Boolean, debugUrl: String) {
        if (rpcCaptureInstalled) return
        try {
            captureForwardEnabled = isdebug
            captureForwardUrl = debugUrl
            val className = "com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension"
            val rpcClass = HookHelpers.findClass(className, classLoader)
            val rpcHook = object : MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val args = param.args
                    if (args.size < 5) return
                    val methodName = args[0] as? String ?: return
                    val rawParams = args[4]
                    val callback = args.lastOrNull() ?: return
                    FishpondProbe.beforeRequest(methodName, rawParams)
                    if (rawParams != null) {
                        runCatching {
                            TokenHooker.handleRpc(methodName, rawParams.toString().toJsonObject())
                        }
                    }
                    cleanupExpiredRpcRecords()
                    val pending = rpcHookMap.computeIfAbsent(callback) { ConcurrentLinkedDeque() }
                    pending.addLast(
                        arrayOf(
                            System.currentTimeMillis(),
                            methodName,
                            rawParams ?: "null",
                            "process=${XposedEnv.processName},owner=${param.thisObject?.javaClass?.name}",
                        ),
                    )
                    StructuredLog.capture(
                        TAG,
                        "capture.request method=$methodName callback=${callback.javaClass.name}" +
                            " id=${System.identityHashCode(callback)} pending=${pending.size}",
                    )
                    if (methodName.contains("promoprod") || methodName.contains("RENT_GREEN") || methodName.contains("loanpromoweb") || methodName.contains("taskTrigger") || methodName.contains("camp.trigger")) {
                        runCatching {
                            val allArgs = args.mapIndexed { i, a ->
                                "$i(${a?.javaClass?.simpleName})=${a?.toString()?.take(300)}"
                            }.joinToString(" | ")
                            StructuredLog.record(TAG, "HOOK_RPC $methodName → $allArgs")
                        }
                    }
                }
            }
            val methods = rpcClass.declaredMethods.filter { it.name == "rpc" }
            if (methods.isEmpty()) error("RpcBridgeExtension#rpc 未找到")
            methods.forEach { method ->
                method.isAccessible = true
                SesameHooks.hookMethod(method, rpcHook)
            }
            rpcCaptureInstalled = true
            StructuredLog.record(
                TAG,
                "Hook RpcBridgeExtension#rpc 重载成功: ${methods.size} 个, process=${XposedEnv.processName}",
            )
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "Hook RpcBridgeExtension#rpc 失败", t)
        }
    }

    fun hookOtherService(classLoader: ClassLoader) {
        try {
            //hook 服务不在后台
            SesameHooks.findAndHookMethod(
                "com.alipay.mobile.common.fgbg.FgBgMonitorImpl",
                classLoader, "isInBackground",
                SesameHooks.returnConstant(false))
            SesameHooks.findAndHookMethod(
                "com.alipay.mobile.common.fgbg.FgBgMonitorImpl",
                classLoader,
                "isInBackground",
                Boolean::class.javaPrimitiveType,
                SesameHooks.returnConstant(false)
            )
            SesameHooks.findAndHookMethod(
                "com.alipay.mobile.common.fgbg.FgBgMonitorImpl",
                classLoader, "isInBackgroundV2",
                SesameHooks.returnConstant(false))
            //hook 服务在前台
            SesameHooks.findAndHookMethod(
                "com.alipay.mobile.common.transport.utils.MiscUtils",
                classLoader,
                "isAtFrontDesk",
                classLoader.loadClass("android.content.Context"),
                SesameHooks.returnConstant(true)
            )
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "hookOtherService 失败", e)
        }
    }

    /**
     * Hook DefaultBridgeCallback.sendJSONResponse 方法，记录响应内容
     */
    fun hookDefaultBridgeCallback(classLoader: ClassLoader) {
        if (callbackCaptureInstalled) return
        try {
            val className = "com.alibaba.ariver.engine.common.bridge.internal.DefaultBridgeCallback"
            val jsonClassName = General.JSON_OBJECT_NAME
            val jsonClass = Class.forName(jsonClassName, false, classLoader)
            val callbackHook = object : MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val callback = param.thisObject
                    if (param.args.isNotEmpty()) {
                        captureRpcResponse(callback, param.args[0])
                    }
                }
            }
            val callbackClass = HookHelpers.findClass(className, classLoader)
            val methods = callbackClass.declaredMethods.filter { it.name == "sendJSONResponse" }
            if (methods.isEmpty()) error("DefaultBridgeCallback#sendJSONResponse 未找到")
            methods.forEach { method ->
                method.isAccessible = true
                SesameHooks.hookMethod(method, callbackHook)
            }
            callbackCaptureInstalled = true
            StructuredLog.record(
                TAG,
                "Hook DefaultBridgeCallback#sendJSONResponse 成功: ${methods.size} 个, process=${XposedEnv.processName}",
            )
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "Hook DefaultBridgeCallback#sendJSONResponse 失败", t)
        }
    }

    /**
     * Completes a pending capture when a real JSON response arrives. This is
     * also called by NewRpcBridge's in-process callback because that callback
     * is a Proxy rather than DefaultBridgeCallback.
     */
    @JvmStatic
    fun captureRpcResponse(callback: Any?, response: Any?) {
        if (callback == null || response == null) return
        val pending = rpcHookMap[callback]
        val recordArray = pending?.pollFirst()
        if (pending != null && pending.isEmpty()) rpcHookMap.remove(callback, pending)
        if (recordArray == null) {
            val now = System.currentTimeMillis()
            val shouldLog = synchronized(this) {
                if (now - lastUnmatchedResponseLogAt < 60_000L) false
                else {
                    lastUnmatchedResponseLogAt = now
                    true
                }
            }
            if (shouldLog) {
                StructuredLog.record(
                    TAG,
                    "capture.response_unmatched callback=${callback.javaClass.name}" +
                        " pending=${pending?.size ?: 0} (late callback or bridge teardown)",
                )
            }
            return
        }
        try {
            val time = recordArray[0]
            val method = recordArray.getOrNull(1)
            val params = recordArray.getOrNull(2)
            FishpondProbe.afterResponse(method as? String, response)
            RpcCaptureRecorder.record(method as? String, params, response, time as Long, "page-hook")
        } catch (e: Throwable) {
            StructuredLog.record(TAG, "JSON 构建失败: ${e.message}")
        }
    }

    /** Records an RPC issued directly by NewRpcBridge's private callback. */
    @JvmStatic
    fun captureDirectRpc(method: String?, request: Any?, response: Any?, startedAt: Long) {
        FishpondProbe.afterResponse(method, response)
        RpcCaptureRecorder.record(method, request, response, startedAt, "new-bridge")
    }

    private fun parseJsonValue(value: Any?): JsonElement {
        if (value == null) return JsonNull
        if (value !is String) return (value as? JsonElement) ?: JsonPrimitive(value.toString())
        return value.toJsonElement()
    }

    private fun cleanupExpiredRpcRecords() {
        val now = System.currentTimeMillis()
        rpcHookMap.entries.removeIf { entry ->
            val pending = entry.value
            pending.removeIf { record ->
                val startedAt = record.getOrNull(0) as? Long
                startedAt == null || now - startedAt > RPC_CAPTURE_TIMEOUT_MS
            }
            pending.isEmpty()
        }
    }

    /**
     * 突破目标应用最大可登录账号数量限制
     * @param classLoader 类加载器
     */
    fun fuckAccounLimit(classLoader: ClassLoader) {
        StructuredLog.record(TAG, "Hook AccountManagerListAdapter#getCount")
        SesameHooks.findAndHookMethod(
            "com.alipay.mobile.security.accountmanager.data.AccountManagerListAdapter",  // target class
            classLoader, "getCount",  // method name
            object : MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // 获取真实账号列表大小
                    try {
                        val list = HookHelpers.getObjectField(param.thisObject, "queryAccountList") as? List<*>
                        if (list != null) {
                            param.result = list.size  // 设置返回值为真实数量
                            val now = System.currentTimeMillis()
                            if (now - lastToastTime > 1000 * 60) { // 每N秒最多显示一次
                                Toast.show("🎉 TK已尝试为您突破限制")
                                lastToastTime = now
                            }
                        }
                        return
//                        StructuredLog.runtime(TAG, "Hook AccountManagerListAdapter#getCount but return is null")
                    } catch (e: Throwable) {
                        // 错误日志处理（你可以替换为自己的日志方法）
                        e.printStackTrace()
                        StructuredLog.error(TAG, "Hook AccountManagerListAdapter#getCount failed: ${e.message}")
                    }
                }
            })
        StructuredLog.record(TAG, "Hook AccountManagerListAdapter#getCount END")
    }


    fun getMicroApplicationContext(classLoader: ClassLoader): Any? {
        if (microContextCache != null) return microContextCache
        return runCatching {
            val appClass = HookHelpers.findClass(
                "com.alipay.mobile.framework.AlipayApplication", classLoader
            )
            val appInstance = HookHelpers.callStaticMethod(appClass, "getInstance")
            HookHelpers.callMethod(appInstance, "getMicroApplicationContext")
                .also { microContextCache = it }
        }.onFailure {
            StructuredLog.printStackTrace(TAG, it)
        }.getOrNull()
    }

    fun getServiceObject(classLoader: ClassLoader, serviceName: String): Any? = runCatching {
        val microContext = getMicroApplicationContext(classLoader)
        HookHelpers.callMethod(microContext, "findServiceByInterface", serviceName)
    }.onFailure {
        StructuredLog.printStackTrace(TAG, it)
    }.getOrNull()

    fun getUserObject(classLoader: ClassLoader): Any? = runCatching {
        val serviceClassName = "com.alipay.mobile.personalbase.service.SocialSdkContactService"
        val serviceClass = HookHelpers.findClass(serviceClassName, classLoader)
        val serviceObject = getServiceObject(classLoader, serviceClass.name)
        HookHelpers.callMethod(serviceObject, "getMyAccountInfoModelByLocal")
    }.onFailure {
        StructuredLog.printStackTrace(TAG, it)
    }.getOrNull()

    fun getUserId(classLoader: ClassLoader): String? = runCatching {
        val userObject = getUserObject(classLoader)
        HookHelpers.getObjectField(userObject, "userId") as? String
    }.onFailure {
        StructuredLog.printStackTrace(TAG, it)
    }.getOrNull()

    fun hookUser(classLoader: ClassLoader) {
        runCatching {
            UserMap.unload()
            val selfId = getUserId(classLoader)
            UserMap.setCurrentUserId(selfId) //有些地方要用到 要set一下
            val clsUserIndependentCache = classLoader.loadClass("com.alipay.mobile.socialcommonsdk.bizdata.UserIndependentCache")
            val clsAliAccountDaoOp = classLoader.loadClass("com.alipay.mobile.socialcommonsdk.bizdata.contact.data.AliAccountDaoOp")
            val aliAccountDaoOp = HookHelpers.callStaticMethod(clsUserIndependentCache, "getCacheObj", clsAliAccountDaoOp)
            val allFriends = HookHelpers.callMethod(aliAccountDaoOp, "getAllFriends") as? List<*> ?: emptyList<Any>()
            if (allFriends.isEmpty()) return
            val friendClass = allFriends.firstOrNull()?.javaClass ?: return
            val userIdField = HookHelpers.findField(friendClass, "userId")
            val accountField = HookHelpers.findField(friendClass, "account")
            val nameField = HookHelpers.findField(friendClass, "name")
            val nickNameField = HookHelpers.findField(friendClass, "nickName")
            val remarkNameField = HookHelpers.findField(friendClass, "remarkName")
            val friendStatusField = HookHelpers.findField(friendClass, "friendStatus")
            var selfEntity: UserEntity? = null
            allFriends.forEach { userObject ->
                runCatching {
                    val userId = userIdField.get(userObject) as? String
                    val account = accountField.get(userObject) as? String
                    val name = nameField.get(userObject) as? String
                    val nickName = nickNameField.get(userObject) as? String
                    val remarkName = remarkNameField.get(userObject) as? String
                    val friendStatus = friendStatusField.get(userObject) as? Int
                    val userEntity = UserEntity(userId, account, friendStatus, name, nickName, remarkName)
                    if (userId == selfId) selfEntity = userEntity
                    UserMap.add(userEntity)
                }.onFailure {
                    StructuredLog.record(TAG, "addUserObject err:")
                    StructuredLog.printStackTrace(it)
                }
            }

            UserMap.saveSelf(selfEntity)
            UserMap.save(selfId)
            StructuredLog.record(TAG, "userCache load scuess !")
        }.onFailure {
            StructuredLog.printStackTrace(TAG, "hookUser 失败", it)
        }
    }
}
