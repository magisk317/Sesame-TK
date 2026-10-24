package io.github.magisk317.sesame.hook

import io.github.magisk317.sesame.hook.rpc.bridge.RpcBridge
import io.github.magisk317.sesame.hook.rpc.bridge.RpcVersion
import io.github.magisk317.sesame.util.StructuredLog
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 会话生命周期状态簇的集中持有者（Phase 2.3b 状态搬家）。
 *
 * 把原散落在 [ApplicationHook] companion 的会话态作为一个整体搬入此 object：
 * `init`、`sessionGeneration`、`rpcBridge`、`rpcBridgeReady`、`rpcBridgeLock`、
 * `rpcVersion`、`mainTask`、`taskRunnerAdapter`、`isTaskRunning`、`taskLock`、
 * `offline`、`lastExecTime`、`nextExecutionTime`。
 *
 * [ApplicationHook] 保留同名转发薄委托（`ApplicationHook.rpcBridge`、`ApplicationHook.isSessionActive` 等），
 * 这样外部消费方（[RequestManager]、[NewRpcBridge]、[OldRpcBridge]、`TaskRunner` 等）调用点不变；
 * 搬家是"所有权转移"而非"API 变更"。
 *
 * 桥的读写锁语义保持不变：请求路径持读锁覆盖反射调用；init/destroy 持写锁换桥。
 * 会话代际守卫（bump on destroy、publish on init）严格保持原 teardown 顺序。
 *
 * 注意：`mainTask`/`taskRunnerAdapter`/`isTaskRunning`/`taskLock`/`lastExecTime`/
 * `nextExecutionTime` 在外部仍以 `ApplicationHook.xxx` 访问，故此处用 public 字段直接暴露；
 * 真正的 init/destroy 流程仍在 [ApplicationHook.initHandler]/[destroyHandler] 编排，
 * 因为它们耦合 Model/Config/Notify/广播/调试服务器/电池权限等大量非会话态依赖。
 * 本对象只持有"会话态数据"本身，不接管编排逻辑——这把高风险面限制在字段归属上。
 */
internal object SessionLifecycle {
    private const val TAG = "SessionLifecycle"

    @Volatile
    var initialized: Boolean = false
        internal set

    @Volatile
    private var sessionGeneration: Long = 0L

    @Volatile
    private var activeConfigurationResetGeneration = 0L

    @Volatile
    private var configurationResetReader: (() -> Long)? = null

    @Volatile
    private var configurationResetAvailableReader: (() -> Boolean)? = null

    val generation: Long get() = sessionGeneration

    fun installConfigurationResetReader(reader: (() -> Long)?) {
        configurationResetReader = reader
    }

    fun installConfigurationResetAvailableReader(reader: (() -> Boolean)?) {
        configurationResetAvailableReader = reader
    }

    /** 自增会话代际。destroy 前调用，使旧回调的 isSessionActive(expected) 失效。 */
    fun bumpGeneration() {
        sessionGeneration += 1L
    }

    /** init 末尾把代际切到 next（与 bump 配对，保持"先失效旧会话再发布新会话"语义）。 */
    fun publishGeneration(next: Long) {
        sessionGeneration = next
    }

    @Volatile
    var rpcBridge: RpcBridge? = null
        internal set

    @Volatile
    private var rpcBridgeReady: Boolean = false

    private val rpcBridgeLock = ReentrantReadWriteLock(true)

    internal var rpcVersion: RpcVersion? = null

    @Volatile
    var offline: Boolean = false
        internal set

    @Volatile
    var lastExecTime: Long = 0L
        internal set

    @Volatile
    var nextExecutionTime: Long = 0L
        internal set

    // --- 任务运行器/主任务态（外部直接访问，故 public） ---
    @Volatile
    var mainTask: io.github.magisk317.sesame.task.MainTask? = null

    @Volatile
    var taskRunnerAdapter: io.github.magisk317.sesame.task.TaskRunnerAdapter? = null
        internal set

    private val taskLock = Any()

    @Volatile
    var isTaskRunning: Boolean = false
        private set

    /** 任务运行锁：tryAcquire 返回 true 表示获锁成功，调用方须在结束时 release()。 */
    internal fun tryAcquireTaskLock(): Boolean {
        synchronized(taskLock) {
            if (isTaskRunning) return false
            isTaskRunning = true
            return true
        }
    }

    internal fun releaseTaskLock() {
        synchronized(taskLock) {
            isTaskRunning = false
        }
    }

    /** 任务锁 AutoCloseable（沿用原 TaskLock 模式）。 */
    internal class TaskLock {
        private val acquired: Boolean

        init {
            acquired = tryAcquireTaskLock()
            if (!acquired) throw IllegalStateException("任务已在运行中")
        }

        fun close() {
            if (acquired) releaseTaskLock()
        }
    }

    // --- SessionContract 实现（桥读写锁语义与原 ApplicationHook 完全一致） ---

    fun isSessionActive(expectedGeneration: Long? = null): Boolean =
        initialized &&
            (expectedGeneration == null || expectedGeneration == sessionGeneration) &&
            isConfigurationResetCurrent()

    fun currentSessionGeneration(): Long = sessionGeneration

    fun isRpcBridgeAvailable(expectedGeneration: Long? = null): Boolean {
        val readLock = rpcBridgeLock.readLock()
        readLock.lock()
        return try {
            rpcBridgeReady &&
                rpcBridge != null &&
                (expectedGeneration == null || expectedGeneration == sessionGeneration) &&
                isConfigurationResetCurrent()
        } finally {
            readLock.unlock()
        }
    }

    fun <T> withRpcBridge(
        expectedGeneration: Long? = null,
        block: (RpcBridge) -> T,
    ): T? {
        val readLock = rpcBridgeLock.readLock()
        readLock.lock()
        return try {
            if (rpcBridgeReady &&
                (expectedGeneration == null || expectedGeneration == sessionGeneration) &&
                isConfigurationResetCurrent()
            ) {
                rpcBridge?.let(block)
            } else {
                null
            }
        } finally {
            readLock.unlock()
        }
    }

    /**
     * 在写锁范围内换桥。block 负责卸载旧桥/装载新桥并赋值 [rpcBridge]。
     * 进入前先把 [rpcBridgeReady] 置 false，避免调用方拿到半初始化桥。
     */
    fun <T> withBridgeWriteLock(block: () -> T): T {
        rpcBridgeReady = false
        val writeLock = rpcBridgeLock.writeLock()
        writeLock.lock()
        return try {
            block()
        } finally {
            writeLock.unlock()
        }
    }

    /** init 成功末尾调用：标记就绪、设代际。 */
    fun completeInitialization(nextGeneration: Long, resetGeneration: Long = 0L) {
        sessionGeneration = nextGeneration
        activeConfigurationResetGeneration = resetGeneration
        initialized = true
        rpcBridgeReady = true
        offline = false
    }

    /** destroy 开头调用：先失效会话再取消旧任务（原 destroyHandler 顺序）。 */
    fun beginDestruction() {
        initialized = false
        bumpGeneration()
        activeConfigurationResetGeneration = 0L
        offline = false
        rpcBridgeReady = false
    }

    /** destroy 末尾清理桥/主任务态。 */
    fun clearBridgeAndTask() {
        rpcVersion = null
        rpcBridge?.unload()
        rpcBridge = null
        mainTask = null
    }

    private fun isConfigurationResetCurrent(): Boolean {
        val available = configurationResetAvailableReader?.let { reader ->
            runCatching { reader() }.getOrDefault(false)
        } ?: true
        if (!available) return false
        val reader = configurationResetReader ?: return true
        return runCatching { reader() == activeConfigurationResetGeneration }.getOrDefault(false)
    }

    internal fun record(message: String) {
        StructuredLog.record(TAG, message)
    }
}
