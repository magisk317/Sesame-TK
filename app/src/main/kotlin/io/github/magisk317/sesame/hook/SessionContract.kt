package io.github.magisk317.sesame.hook

import io.github.magisk317.sesame.hook.rpc.bridge.RpcBridge

/**
 * 会话契约 seam：把 RPC 请求路径对会话/桥状态的依赖抽成接口，
 * 使 [RequestManager] 不再硬编码 [ApplicationHook] 静态方法，便于注入 fake 单测。
 *
 * 生产实现为 [ApplicationHook] 的 companion（行为与原静态方法完全一致，仅声明实现接口）。
 * 这是为后续会话态集中搬家（SessionLifecycle）铺路的 seam，本身不改变运行时行为。
 */
interface SessionContract {
    /** 会话是否仍活跃；expectedGeneration 为 null 时仅看 init/service，否则额外比对代际。 */
    fun isSessionActive(expectedGeneration: Long? = null): Boolean

    /** 当前会话代号，回调捕获后用于稍后重检 [isSessionActive]。 */
    fun currentSessionGeneration(): Long

    /** RPC 桥是否就绪（持读锁判断，避免销毁中途误判）。 */
    fun isRpcBridgeAvailable(expectedGeneration: Long? = null): Boolean

    /**
     * 在 bridge 读锁范围内执行 [block]，确保会话销毁不会在反射调用中途卸载 bridge。
     * 桥不可用/会话失效时返回 null。
     */
    fun <T> withRpcBridge(
        expectedGeneration: Long? = null,
        block: (RpcBridge) -> T,
    ): T?
}
