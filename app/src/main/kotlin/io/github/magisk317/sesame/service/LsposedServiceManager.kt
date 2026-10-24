package io.github.magisk317.sesame.service


import android.content.SharedPreferences
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TaskRuntimeStore
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object LsposedServiceManager {

    private const val TAG = "LsposedServiceManager"

    /** 当前连接状态，线程安全 */
    private val _connectionState = AtomicReference<ConnectionState>(ConnectionState.Connecting)

    /** 外部获取当前状态 */
    val connectionState: ConnectionState
        get() = _connectionState.get()

    /** 已连接的服务（如果有） */
    val service: XposedService?
        get() = (_connectionState.get() as? ConnectionState.Connected)?.service

    /** 模块是否激活 */
    val isModuleActivated: Boolean
        get() = _connectionState.get() is ConnectionState.Connected

    /**
     * Returns the framework-owned remote preference group when the module service is bound.
     * Callers must treat a null result as an unavailable IPC channel and use their migration
     * policy rather than reopening world-readable files.
     */
    fun getRemotePreferences(group: String): SharedPreferences? =
        runCatching { service?.getRemotePreferences(group) }.getOrNull()

    /** 状态监听器列表 */
    private val listeners = CopyOnWriteArrayList<(ConnectionState) -> Unit>()

    /** ✨ 修复：使用 AtomicBoolean 保证值比较的正确性 */
    private val isInitialized = AtomicBoolean(false)

    /** 初始化 ServiceManager 并注册 XposedService 监听 */
    fun init() {
        if (!isInitialized.compareAndSet(false, true)) return

        val listener = object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(boundService: XposedService) {
                if (isModuleActivated) {
                    StructuredLog.record(TAG, "Another Xposed service tried to connect: ${boundService.frameworkName}. Ignoring.")
                    return
                }
                StructuredLog.record(TAG, "LSPosed service connected: ${boundService.frameworkName} v${boundService.frameworkVersion}")
                RemotePrefsStore.installProvider { boundService.getRemotePreferences(RemotePrefsStore.GROUP) }
                TaskRuntimeStore.applyRemoteResetBarrier()
                updateState(ConnectionState.Connected(boundService))
            }

            override fun onServiceDied(deadService: XposedService) {
                // 检查 service 属性而不是直接比较，避免在多线程环境下的竞态条件
                if (service == deadService) {
                    StructuredLog.record(TAG, "LSPosed service died.")
                    RemotePrefsStore.installProvider(null)
                    updateState(ConnectionState.Disconnected)
                }
            }
        }

        XposedServiceHelper.registerListener(listener)
        StructuredLog.record(TAG, "ServiceManager initialized and listener registered.")
    }

    /** 添加状态监听器，添加后立即触发一次当前状态 */
    fun addConnectionListener(listener: (ConnectionState) -> Unit) {
        listeners.add(listener)
        listener(connectionState)
    }

    /** 移除状态监听器 */
    fun removeConnectionListener(listener: (ConnectionState) -> Unit) {
        listeners.remove(listener)
    }

    /** 更新状态并通知监听器，线程安全 */
    private fun updateState(newState: ConnectionState) {
        _connectionState.set(newState)
        notifyListeners(newState)
    }

    /** 通知所有监听器状态变化 */
    private fun notifyListeners(state: ConnectionState) {
        for (listener in listeners) {
            listener(state)
        }
    }
}

sealed interface ConnectionState {
    data object Connecting : ConnectionState
    data class Connected(val service: XposedService) : ConnectionState
    data object Disconnected : ConnectionState
}
