package io.github.magisk317.sesame.hook.rpc.bridge

import io.github.magisk317.sesame.data.General
import io.github.magisk317.sesame.data.RuntimeInfo
import io.github.magisk317.sesame.entity.RpcEntity
import io.github.magisk317.sesame.hook.ApplicationHook
import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.hook.rpc.intervallimit.RpcIntervalLimit
import io.github.magisk317.sesame.model.BaseModel
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.Notify
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Legacy H5 RPC transport. Reflection is intentionally isolated in this class. */
class OldRpcBridge : RpcBridge {
    private val tag = "OldRpcBridge"
    private var loader: ClassLoader? = null
    private var h5PageClass: Class<*>? = null
    private var rpcCallMethod: Method? = null
    private var responseMethod: Method? = null
    private var currentH5Page: Any? = null

    override fun getVersion(): RpcVersion = RpcVersion.OLD

    @Throws(Exception::class)
    override fun load() {
        loader = ApplicationHook.classLoader
        try {
            h5PageClass = loader!!.loadClass(General.H5PAGE_NAME)
            loadRpcMethods()
            StructuredLog.record(tag, "RPC 类加载成功")
        } catch (error: Throwable) {
            StructuredLog.record(tag, "加载 RPC 类时发生错误:")
            StructuredLog.printStackTrace(tag, error)
            throw error
        }
    }

    private fun loadRpcMethods() {
        if (rpcCallMethod != null) return
        try {
            val rpcUtilClass = loader!!.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5RpcUtil")
            val responseClass = loader!!.loadClass("com.alipay.mobile.nebulaappproxy.api.rpc.H5Response")
            rpcCallMethod = rpcUtilClass.getMethod(
                "rpcCall", String::class.java, String::class.java, String::class.java,
                Boolean::class.javaPrimitiveType, loader!!.loadClass(General.JSON_OBJECT_NAME), String::class.java,
                Boolean::class.javaPrimitiveType, h5PageClass, Int::class.javaPrimitiveType, String::class.java,
                Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
            )
            responseMethod = responseClass.getMethod("getResponse")
            StructuredLog.record(tag, "RPC 调用方法加载成功")
        } catch (error: Throwable) {
            StructuredLog.record(tag, "加载 RPC 调用方法时出错:")
            StructuredLog.printStackTrace(tag, error)
        }
    }

    override fun unload() {
        responseMethod = null
        rpcCallMethod = null
        h5PageClass = null
        loader = null
        currentH5Page = null
    }

    override fun requestString(entity: RpcEntity?, tryCount: Int, retryInterval: Int): String? =
        requestObject(entity, tryCount, retryInterval)?.responseString

    override fun requestObject(entity: RpcEntity?, tryCount: Int, retryInterval: Int): RpcEntity? {
        if (entity == null || ApplicationHook.offline) return null
        val method = entity.requestMethod ?: return null
        val args = entity.requestData
        repeat(tryCount.coerceAtLeast(0)) {
            try {
                RpcIntervalLimit.enterIntervalLimit(method)
                val response = invokeRpcCall(method, args)
                return processResponse(entity, response, method, args)
            } catch (error: Throwable) {
                handleError(entity, error, method, args)
            }
            if (retryInterval > 0) GlobalThreadPools.sleepCompat(retryInterval.toLong())
        }
        return null
    }

    private fun invokeRpcCall(method: String, args: String?): Any? {
        val call = rpcCallMethod ?: run {
            load()
            rpcCallMethod ?: throw IllegalStateException("RPC method unavailable")
        }
        val common = arrayOf<Any?>(method, args, "", true, null, null, false, currentH5Page, 0, "", false, -1)
        return if (call.parameterTypes.size == common.size) {
            call.invoke(null, *common)
        } else {
            call.invoke(null, *(common + ""))
        }
    }

    private fun processResponse(entity: RpcEntity, response: Any?, method: String, args: String?): RpcEntity? {
        val resultString = responseMethod?.invoke(response) as? String ?: return null
        val result = resultString.toJsonObject()
        entity.setResponseObject(result, resultString)
        if (result.optString("memo").contains("系统繁忙")) {
            Notify.updateStatusText("系统繁忙，可能需要滑动验证")
            StructuredLog.record(tag, "系统繁忙，可能需要滑动验证")
            return null
        }
        if (!result.optBoolean("success")) {
            entity.setError()
            StructuredLog.error(tag, "旧 RPC 响应 | method: $method args: $args | data: ${entity.responseString}")
            return null
        }
        return entity
    }

    private fun handleError(entity: RpcEntity, error: Throwable, method: String, args: String?) {
        entity.setError()
        StructuredLog.error(tag, "旧 RPC 请求 | method: $method args: $args err:")
        StructuredLog.printStackTrace(error)
        val cause = (error as? InvocationTargetException)?.cause ?: return
        val message = cause.message.orEmpty()
        when {
            message.contains("登录超时") -> {
                Notify.updateStatusText("登录超时")
                if (BaseModel.timeoutRestart.value) {
                    StructuredLog.record(tag, "尝试重新登录")
                    ApplicationHook.reLoginByBroadcast()
                }
            }
            message.contains("[1004]") && method == "alipay.antmember.forest.h5.collectEnergy" -> {
                val wait = BaseModel.waitWhenException.value
                if (wait > 0) {
                    RuntimeInfo.getInstance().put(RuntimeInfo.RuntimeInfoKey.ForestPauseTime, System.currentTimeMillis() + wait)
                    Notify.updateStatusText("异常")
                    StructuredLog.record(tag, "触发异常, 等待至${TimeUtil.getCommonDate(System.currentTimeMillis() + wait)}")
                }
            }
            message.contains("MMTPException") -> {
                val result = jsonObject(
                    "resultCode" to "FAIL",
                    "memo" to "MMTPException",
                    "resultDesc" to "MMTPException",
                )
                entity.setResponseObject(result, result.toString())
            }
        }
    }
}
