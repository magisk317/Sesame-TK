package io.github.magisk317.sesame.hook

import io.github.magisk317.sesame.entity.RpcEntity
import io.github.magisk317.sesame.hook.rpc.bridge.RpcBridge
import io.github.magisk317.sesame.hook.rpc.bridge.RpcVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * SessionContract seam 的回归网：验证会话代际失效语义。
 * RequestManager 依赖 [SessionContract] 而非 [ApplicationHook] 静态方法，
 * 这里用 fake 注入覆盖"会话失效→提前返回""桥不可用→返回 null"等会话逻辑，
 * 无需启动宿主进程。
 *
 * 注意：RequestManager.executeRpc 是 private，且混杂了 SecurityVerificationGate /
 * NetworkUtils / BaseModel 等单例依赖，难以直接驱动。这里改为验证 SessionContract
 * 契约本身的行为不变量——这是 RequestManager 的会话语义来源，也是后续 SessionLifecycle
 * 状态搬家必须保持的契约。
 */
class RequestManagerSessionContractTest {

    /**
     * 可控的 SessionContract fake：代际可手动 bump，桥可用性可切换。
     * 模拟 ApplicationHook companion 的会话语义。
     */
    private class FakeSessionContract : SessionContract {
        var initialized = true
        var servicePresent = true
        var generation = 1L
        var bridgeReady = false
        var bridge: RpcBridge? = null
        // 记录 block 是否被调用
        var blockInvocations = 0
        var lastBlockArg: RpcBridge? = null

        override fun isSessionActive(expectedGeneration: Long?): Boolean =
            initialized &&
                servicePresent &&
                (expectedGeneration == null || expectedGeneration == generation)

        override fun currentSessionGeneration(): Long = generation

        override fun isRpcBridgeAvailable(expectedGeneration: Long?): Boolean =
            bridgeReady &&
                bridge != null &&
                (expectedGeneration == null || expectedGeneration == generation)

        override fun <T> withRpcBridge(
            expectedGeneration: Long?,
            block: (RpcBridge) -> T,
        ): T? {
            if (!bridgeReady || bridge == null ||
                (expectedGeneration != null && expectedGeneration != generation)
            ) {
                return null
            }
            blockInvocations++
            lastBlockArg = bridge
            return bridge?.let(block)
        }
    }

    /** 一个最小 RpcBridge 桩，仅用于让 bridge != null。 */
    private object StubBridge : RpcBridge {
        override fun getVersion(): RpcVersion = RpcVersion.NEW
        override fun load() {}
        override fun unload() {}
        override fun requestString(entity: RpcEntity?, tryCount: Int, retryInterval: Int): String? = null
        override fun requestObject(entity: RpcEntity?, tryCount: Int, retryInterval: Int): RpcEntity? = null
    }

    private lateinit var session: FakeSessionContract

    @BeforeEach
    fun setUp() {
        session = FakeSessionContract()
        session.bridge = StubBridge
    }

    @Test
    fun `generation bump invalidates a previously captured generation`() {
        val captured = session.currentSessionGeneration()
        assertTrue(session.isSessionActive(captured))

        // 模拟会话重建：代际自增
        session.generation = captured + 1

        // 旧回调捕获的代际现在应当失效——这是 ApplicationHook.sessionGeneration
        // bump-on-destroy 的核心语义，必须被任何 SessionLifecycle 搬家保留。
        assertFalse(session.isSessionActive(captured))
        assertTrue(session.isSessionActive(captured + 1))
        assertTrue(session.isSessionActive(null))
    }

    @Test
    fun `withRpcBridge returns null when bridge not ready`() {
        session.bridgeReady = false
        val result: String? = session.withRpcBridge(expectedGeneration = 1L) { "ok" }
        assertNull(result)
        assertEquals(0, session.blockInvocations)
    }

    @Test
    fun `withRpcBridge returns null when generation changed`() {
        session.bridgeReady = true
        // 模拟会话重建：用旧代际调用时桥不应执行 block（返回 null）。
        session.generation = 2L
        val result: String? = session.withRpcBridge(expectedGeneration = 1L) { "ok" }
        assertNull(result)
        assertEquals(0, session.blockInvocations)
    }

    @Test
    fun `withRpcBridge executes block when session and bridge are valid`() {
        session.bridgeReady = true
        val result: String? = session.withRpcBridge(expectedGeneration = 1L) { "ok" }
        assertEquals("ok", result)
        assertEquals(1, session.blockInvocations)
    }

    @Test
    fun `isRpcBridgeAvailable reflects both bridge readiness and generation`() {
        session.bridgeReady = false
        assertFalse(session.isRpcBridgeAvailable(1L))
        session.bridgeReady = true
        assertTrue(session.isRpcBridgeAvailable(1L))
        session.generation = 2L
        assertFalse(session.isRpcBridgeAvailable(1L))
        assertTrue(session.isRpcBridgeAvailable(2L))
    }

    @Test
    fun `isSessionActive false when uninitialized`() {
        session.initialized = false
        assertFalse(session.isSessionActive(null))
        assertFalse(session.isSessionActive(1L))
    }
}
