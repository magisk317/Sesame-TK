package io.github.magisk317.sesame.hook

import io.github.magisk317.sesame.entity.RpcEntity
import io.github.magisk317.sesame.hook.rpc.bridge.RpcBridge
import io.github.magisk317.sesame.hook.rpc.bridge.RpcVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

class SessionLifecycleTest {
    private val resetGeneration = AtomicLong(7L)
    private var remoteAvailable = true

    @BeforeEach
    fun setUp() {
        SessionLifecycle.installConfigurationResetReader(resetGeneration::get)
        SessionLifecycle.installConfigurationResetAvailableReader { remoteAvailable }
        SessionLifecycle.beginDestruction()
        SessionLifecycle.rpcBridge = StubBridge
        SessionLifecycle.completeInitialization(nextGeneration = 1L, resetGeneration = resetGeneration.get())
    }

    @AfterEach
    fun tearDown() {
        SessionLifecycle.beginDestruction()
        SessionLifecycle.clearBridgeAndTask()
        SessionLifecycle.installConfigurationResetReader(null)
        SessionLifecycle.installConfigurationResetAvailableReader(null)
    }

    @Test
    fun `remote reset generation invalidates session and bridge`() {
        assertTrue(SessionLifecycle.isSessionActive(1L))
        assertTrue(SessionLifecycle.isRpcBridgeAvailable(1L))

        resetGeneration.incrementAndGet()

        assertFalse(SessionLifecycle.isSessionActive(1L))
        assertFalse(SessionLifecycle.isRpcBridgeAvailable(1L))
        assertFalse(SessionLifecycle.withRpcBridge(1L) { true } ?: false)
    }

    @Test
    fun `remote provider loss invalidates a zero generation session`() {
        resetGeneration.set(0L)
        SessionLifecycle.completeInitialization(nextGeneration = 2L, resetGeneration = 0L)
        assertTrue(SessionLifecycle.isSessionActive(2L))

        remoteAvailable = false

        assertFalse(SessionLifecycle.isSessionActive(2L))
        assertFalse(SessionLifecycle.isRpcBridgeAvailable(2L))
    }

    private object StubBridge : RpcBridge {
        override fun getVersion(): RpcVersion = RpcVersion.NEW
        override fun load() = Unit
        override fun unload() = Unit
        override fun requestString(entity: RpcEntity?, tryCount: Int, retryInterval: Int): String? = null
        override fun requestObject(entity: RpcEntity?, tryCount: Int, retryInterval: Int): RpcEntity? = null
    }
}
