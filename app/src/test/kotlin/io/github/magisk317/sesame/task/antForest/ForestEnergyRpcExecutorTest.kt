package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.entity.CollectEnergyEntity
import io.github.magisk317.sesame.entity.RpcEntity
import io.github.magisk317.sesame.hook.rpc.intervallimit.IntervalLimit
import io.github.magisk317.sesame.ui.ObjReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestEnergyRpcExecutorTest {
    private val interval = object : IntervalLimit {
        override val interval: Int = 0
        override var time: Long = 0
    }

    @Test
    fun executesResponseAndConsumesDoubleFlag() {
        val entity = CollectEnergyEntity("USER")
        entity.rpcEntity = RpcEntity()
        entity.setNeedDouble()
        val executor = executorFor { rpc ->
            rpc.setResponseObject(null, "{\"resultCode\":\"SUCCESS\",\"bubbles\":[]}")
        }

        val result = executor.execute(entity)

        assertTrue(result is ForestEnergyRpcExecutor.Execution.Response)
        val response = result as ForestEnergyRpcExecutor.Execution.Response
        assertTrue(response.needDouble)
        assertEquals(1, response.tryCount)
        assertFalse(entity.needDouble)
        assertFalse(entity.needRetry)
    }

    @Test
    fun reportsTransportErrorsWithoutParsingBusinessResponse() {
        val entity = CollectEnergyEntity("USER")
        entity.rpcEntity = RpcEntity()
        val executor = executorFor {
            it.setResponseObject(ErrorResponse(), null)
            it.setError()
        }

        val result = executor.execute(entity)

        assertTrue(result is ForestEnergyRpcExecutor.Execution.TransportError)
        val error = result as ForestEnergyRpcExecutor.Execution.TransportError
        assertEquals(1, error.tryCount)
        assertEquals("1004", error.errorCode)
    }

    @Test
    fun reportsEmptyResponseAsRetryableTransportFailure() {
        val entity = CollectEnergyEntity("USER")
        entity.rpcEntity = RpcEntity()
        val executor = executorFor { it.setResponseObject(null, "") }

        val result = executor.execute(entity)

        assertTrue(result is ForestEnergyRpcExecutor.Execution.TransportError)
        val error = result as ForestEnergyRpcExecutor.Execution.TransportError
        assertEquals(null, error.errorCode)
        assertEquals(1, error.tryCount)
    }

    private class ErrorResponse {
        fun getString(key: String): String = if (key == "error") "1004" else ""
    }

    private fun executorFor(request: (io.github.magisk317.sesame.entity.RpcEntity) -> Unit) =
        ForestEnergyRpcExecutor(
            collectLock = ObjReference(0L),
            collectInterval = { interval },
            doubleCollectInterval = { interval },
            retryInterval = { 0 },
            request = request,
            balanceNetworkDelay = { false },
            recordNetworkDelay = {},
        )
}
