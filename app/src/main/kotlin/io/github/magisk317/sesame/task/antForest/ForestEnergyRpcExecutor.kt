package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.entity.CollectEnergyEntity
import io.github.magisk317.sesame.entity.RpcEntity
import io.github.magisk317.sesame.hook.rpc.intervallimit.IntervalLimit
import io.github.magisk317.sesame.ui.ObjReference
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.xposed.HookHelpers
import kotlinx.serialization.json.JsonObject

/** Executes one energy RPC while keeping timing and transport state out of AntForest. */
class ForestEnergyRpcExecutor(
    private val collectLock: ObjReference<Long>,
    private val collectInterval: () -> IntervalLimit,
    private val doubleCollectInterval: () -> IntervalLimit,
    private val retryInterval: () -> Int,
    private val request: (RpcEntity) -> Unit,
    private val balanceNetworkDelay: () -> Boolean,
    private val recordNetworkDelay: (Int) -> Unit,
) {
    sealed interface Execution {
        data class TransportError(
            val errorCode: String?,
            val tryCount: Int,
        ) : Execution

        data class Response(
            val response: JsonObject,
            val tryCount: Int,
            val needDouble: Boolean,
            val spendTime: Long,
        ) : Execution
    }

    fun execute(entity: CollectEnergyEntity): Execution? {
        val rpcEntity = entity.rpcEntity ?: return null
        val needDouble = entity.needDouble
        val needRetry = entity.needRetry
        val tryCount = entity.addTryCount()
        val startTime: Long

        synchronized(collectLock) {
            val sleep = when {
                needDouble -> {
                    entity.unsetNeedDouble()
                    (doubleCollectInterval().interval ?: 1000) -
                        System.currentTimeMillis() + (collectLock.get() ?: 0L)
                }
                needRetry -> {
                    entity.unsetNeedRetry()
                    retryInterval() - System.currentTimeMillis() + (collectLock.get() ?: 0L)
                }
                else -> {
                    (collectInterval().interval ?: 1000) -
                        System.currentTimeMillis() + (collectLock.get() ?: 0L)
                }
            }
            if (sleep > 0) GlobalThreadPools.sleepCompat(sleep)
            startTime = System.currentTimeMillis()
            collectLock.setForce(startTime)
        }

        request(rpcEntity)
        val spendTime = System.currentTimeMillis() - startTime
        if (balanceNetworkDelay()) recordNetworkDelay((spendTime / 3).toInt())

        if (rpcEntity.hasError) {
            val errorCode = HookHelpers.callMethod(
                rpcEntity.responseObject,
                "getString",
                "error",
            ) as String?
            return Execution.TransportError(errorCode, tryCount)
        }

        // A bridge timeout/exhausted retry can leave the entity without a response
        // while still reporting no transport error. Keep it in the retry path instead
        // of parsing an empty object and losing the collection attempt.
        if (rpcEntity.responseString.isNullOrBlank()) {
            return Execution.TransportError(null, tryCount)
        }

        return Execution.Response(
            response = rpcEntity.responseString!!.toJsonObject(),
            tryCount = tryCount,
            needDouble = needDouble,
            spendTime = spendTime,
        )
    }
}
