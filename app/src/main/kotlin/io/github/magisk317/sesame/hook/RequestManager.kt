package io.github.magisk317.sesame.hook

import android.Manifest
import androidx.annotation.RequiresPermission
import io.github.magisk317.sesame.entity.RpcEntity
import io.github.magisk317.sesame.hook.rpc.bridge.RpcBridge
import io.github.magisk317.sesame.model.BaseModel
import io.github.magisk317.sesame.util.CoroutineUtils
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.NetworkUtils
import io.github.magisk317.sesame.util.Notify
import io.github.magisk317.sesame.util.TimeUtil
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicInteger

/**
 * RPC 请求管理器 (带熔断与兜底机制)
 */
object RequestManager {

    private const val TAG = "RequestManager"

    /**
     * 会话契约 seam：默认指向 [ApplicationHook]（生产实现）。
     * 单测可注入 fake 以验证"会话失效→提前返回""桥不可用→返回 null"等逻辑，
     * 无需启动宿主进程。运行时行为与原直接调用 ApplicationHook 静态方法一致。
     */
    @Volatile
    internal var session: SessionContract = ApplicationHook

    // 连续失败计数器
    private val errorCount = AtomicInteger(0)

    /**
     * 核心执行函数
     * 流程：离线检查 -> 获取 Bridge -> 执行请求 -> 结果校验 -> 错误计数/重置
     */
    private fun executeRpc(
        methodLog: String?,
        block: (RpcBridge) -> String?
    ): String {
        val expectedGeneration = session.currentSessionGeneration()

        // A security challenge is interactive. Do not keep issuing the same
        // RPC while its WebView is waiting for a legitimate user check.
        if (SecurityVerificationGate.isBlocked()) {
            StructuredLog.record(TAG, "支付宝安全验证未完成，拦截请求: $methodLog")
            return ""
        }

        // 1. 【前置检查】如果已经离线，直接中断并尝试恢复
        if (ApplicationHook.offline) {
            StructuredLog.record(TAG, "当前处于离线状态，拦截请求: $methodLog")
            handleOfflineRecovery(expectedGeneration)
            return ""
        }

        // 2. 等待 Bridge 就绪；真正调用通过 session.withRpcBridge 持有读锁。
        if (!awaitRpcBridge(expectedGeneration)) {
            handleFailure("Network/Bridge Unavailable", "网络或Bridge不可用", expectedGeneration)
            return ""
        }
        if (!session.isSessionActive(expectedGeneration)) return ""

        // 3. 执行请求
        val result = try {
            session.withRpcBridge(
                expectedGeneration = expectedGeneration,
                block = block,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "RPC 执行异常: $methodLog", e)
            null // 异常视为 null，触发失败逻辑
        }

        // 4. 结果校验与状态维护
        // The bridge read lock prevents unloading during the call. This check
        // prevents a request that waited behind that lock from being accounted
        // against the newly initialized session.
        if (!session.isSessionActive(expectedGeneration)) return ""
        if (result.isNullOrBlank()) {
            if (SecurityVerificationGate.isBlocked()) {
                StructuredLog.record(TAG, "检测到支付宝安全验证，跳过网络失败计数: $methodLog")
                return ""
            }
            // 失败：增加计数，检查兜底
            handleFailure(methodLog ?: "Unknown", "返回数据为空", expectedGeneration)
            return ""
        } else {
            // 成功：重置计数器
            if (errorCount.getAndSet(0) > 0) {
                StructuredLog.record(TAG, "RPC 恢复正常，错误计数重置")
            }
            return result
        }
    }

    /**
     * 处理失败逻辑：计数、报警、熔断
     */
    private fun handleFailure(method: String, reason: String, expectedGeneration: Long? = null) {
        if (expectedGeneration != null && !session.isSessionActive(expectedGeneration)) {
            StructuredLog.record(TAG, "忽略失效会话的 RPC 失败: $method")
            return
        }

        // Failure accounting must work precisely when the bridge is absent:
        // bridge availability is the condition being diagnosed.
        val currentCount = errorCount.incrementAndGet()
        val maxCount = BaseModel.setMaxErrorCount.value.coerceAtLeast(1)

        StructuredLog.error(TAG, "RPC 失败 ($currentCount/$maxCount) | Method: $method | Reason: $reason")

        if (currentCount >= maxCount && !ApplicationHook.offline) {
            StructuredLog.record(TAG, "🔴 连续失败次数达到阈值，触发熔断兜底机制！")
            ApplicationHook.offline = true
            if (BaseModel.errNotify.value) {
                val msg = "${TimeUtil.getTimeStr()} | 网络异常次数超过阈值[$maxCount]"
                Notify.sendNewNotification(msg, "RPC 连续失败，脚本已暂停")
            }
            handleOfflineRecovery(expectedGeneration)
        }
    }

    /**
     * 处理离线恢复逻辑
     * 可以是发送广播、拉起 App 等
     */
    private fun handleOfflineRecovery(expectedGeneration: Long? = null) {
        // Recovery deliberately does not require RpcBridge. It is the action
        // that should restore the host when bridge initialization failed.
        if (!session.isSessionActive(expectedGeneration)) {
            StructuredLog.record(TAG, "当前会话已失效，跳过离线恢复")
            return
        }
        StructuredLog.record(TAG, "正在尝试执行离线恢复策略...")
        ApplicationHook.reOpenApp()
    }

    @JvmStatic
    fun reset() {
        errorCount.set(0)
        SecurityVerificationGate.reset()
    }

    /** Called by the bridge when Alipay requires an interactive security check. */
    @JvmStatic
    fun markSecurityVerificationRequired(method: String?) {
        if (SecurityVerificationGate.mark()) {
            StructuredLog.record(TAG, "检测到支付宝安全验证，暂停自动任务: ${method ?: "Unknown"}")
            Notify.updateStatusText("支付宝需要完成安全验证，自动任务已暂停")
        }
    }

    @JvmStatic
    fun isSecurityVerificationRequired(): Boolean = SecurityVerificationGate.isBlocked()

    /**
     * 等待 RpcBridge 就绪
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun awaitRpcBridge(expectedGeneration: Long): Boolean {
        if (!session.isSessionActive(expectedGeneration)) return false

        if (!NetworkUtils.isNetworkAvailable()) {
            StructuredLog.record(TAG, "网络不可用，尝试等待 5秒...")
            CoroutineUtils.sleepCompat(5000)
            if (!session.isSessionActive(expectedGeneration) ||
                !NetworkUtils.isNetworkAvailable()
            ) {
                return false
            }
        }

        if (!session.isRpcBridgeAvailable(expectedGeneration)) {
            StructuredLog.record(TAG, "RpcBridge 未初始化，尝试等待 5秒...")
            CoroutineUtils.sleepCompat(5000)
        }

        return session.isRpcBridgeAvailable(expectedGeneration) &&
            session.isSessionActive(expectedGeneration)
    }

    // ================== 公开 API (保持不变) ==================

    @JvmStatic
    fun requestString(rpcEntity: RpcEntity): String {
        return executeRpc(rpcEntity.methodName) { bridge ->
            bridge.requestString(rpcEntity, 3, 1200)
        }
    }

    @JvmStatic
    fun requestString(rpcEntity: RpcEntity, tryCount: Int, retryInterval: Int): String {
        return executeRpc(rpcEntity.methodName) { bridge ->
            bridge.requestString(rpcEntity, tryCount, retryInterval)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, relation: String?): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, relation)
        }
    }

    @JvmStatic
    fun requestString(
        method: String?,
        data: String?,
        appName: String?,
        methodName: String?,
        facadeName: String?
    ): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, appName, methodName, facadeName)
        }
    }

    @JvmStatic
    fun requestString(method: String?, data: String?, tryCount: Int, retryInterval: Int): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, tryCount, retryInterval)
        }
    }

    @JvmStatic
    fun requestString(
        method: String?,
        data: String?,
        relation: String?,
        tryCount: Int,
        retryInterval: Int
    ): String {
        return executeRpc(method) { bridge ->
            bridge.requestString(method, data, relation, tryCount, retryInterval)
        }
    }

    @JvmStatic
    fun requestObject(rpcEntity: RpcEntity?, tryCount: Int, retryInterval: Int) {
        if (rpcEntity == null) return
        val expectedGeneration = session.currentSessionGeneration()
        // requestObject 不涉及返回值判断，但同样需要离线检查
        if (ApplicationHook.offline) {
            handleOfflineRecovery(expectedGeneration)
            return
        }

        if (!awaitRpcBridge(expectedGeneration)) {
            handleFailure("requestObject", "Bridge Unavailable", expectedGeneration)
            return
        }

        try {
            val completed = session.withRpcBridge(
                expectedGeneration = expectedGeneration,
            ) { bridge ->
                    bridge.requestObject(rpcEntity, tryCount, retryInterval)
                    true
                } ?: false
            if (!completed) {
                handleFailure(
                    rpcEntity.methodName ?: "Unknown",
                    "Bridge Unavailable",
                    expectedGeneration,
                )
                return
            }
            // requestObject 没有返回值，假设只要不抛异常就算成功？
            // 或者保守一点，不重置 errorCount，也不增加 errorCount
            if (session.isSessionActive(expectedGeneration)) {
                errorCount.set(0)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "requestObject 异常: ${rpcEntity.methodName}", e)
            handleFailure(rpcEntity.methodName ?: "Unknown", "Exception", expectedGeneration)
        }
    }
}
