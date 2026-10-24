package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.data.RuntimeInfo
import io.github.magisk317.sesame.entity.CollectEnergyEntity
import io.github.magisk317.sesame.model.BaseModel
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.Notify.updateLastExecText
import io.github.magisk317.sesame.util.Notify.updateStatusText
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/** Coordinates prop preparation, collection retries and result follow-up actions. */
internal class ForestEnergyCollectionService(
    private val errorWait: () -> Boolean,
    private val setErrorWait: (Boolean) -> Unit,
    private val prepareProps: (String?, Boolean) -> Unit,
    private val executor: ForestEnergyRpcExecutor,
    private val resultService: ForestEnergyResultService,
    private val collectionResultService: ForestEnergyCollectionResultService,
    private val tryCount: () -> Int,
    private val userName: (String?) -> String?,
    private val checkFrequencyError: (String, JsonObject) -> Boolean,
    private val returnCount: (Int) -> Int,
    private val onTaskStarted: () -> Unit,
    private val onFinished: () -> Unit,
    private val sleep: (Long) -> Unit = GlobalThreadPools::sleepCompat,
) {
    private val preparedUsers = ConcurrentHashMap<String, Long>()

    fun collect(entity: CollectEnergyEntity) {
        if (errorWait()) {
            StructuredLog.record(TAG, "异常⌛等待中...不收取能量")
            return
        }
        onTaskStarted()
        try {
            val propKey = entity.userId.orEmpty()
            val now = System.currentTimeMillis()
            val lastPrepared = preparedUsers[propKey]
            if (lastPrepared == null || now - lastPrepared >= PROP_PREPARE_CACHE_MS) {
                prepareProps(entity.userId, entity.skipPropCheck)
                preparedUsers[propKey] = now
            }
            val execution = executor.execute(entity) ?: return
            if (execution is ForestEnergyRpcExecutor.Execution.TransportError) {
                handleTransportError(entity, execution)
                return
            }

            val response = execution as ForestEnergyRpcExecutor.Execution.Response
            val result = resultService.parse(response.response)
            if (!result.isSuccess) {
                handleFailedResult(entity, response, result)
                return
            }

            val action = collectionResultService.handle(
                entity = entity,
                result = result,
                needDouble = response.needDouble,
                spendTime = response.spendTime,
                userName = userName(entity.userId).orEmpty(),
                returnCount = returnCount,
            )
            if (action is ForestEnergyCollectionResultService.NextAction.CollectAgain) {
                if (!action.doubleClick) {
                    entity.rpcEntity = ForestCoreRpc.batchEnergyEntity("", entity.userId, action.bubbleIds)
                }
                entity.setNeedDouble()
                entity.resetTryCount()
                collect(entity)
            }
        } catch (error: Exception) {
            StructuredLog.printStackTrace(TAG, "collectEnergy err", error)
        } finally {
            onFinished()
        }
    }

    private fun handleTransportError(
        entity: CollectEnergyEntity,
        error: ForestEnergyRpcExecutor.Execution.TransportError,
    ) {
        if (error.errorCode == "1004") {
            if (BaseModel.waitWhenException.value > 0) {
                val waitUntil = System.currentTimeMillis() + BaseModel.waitWhenException.value
                RuntimeInfo.getInstance().put(RuntimeInfo.RuntimeInfoKey.ForestPauseTime, waitUntil)
                updateStatusText("异常")
                StructuredLog.record(TAG, "触发异常,等待至${TimeUtil.getCommonDate(waitUntil)}")
                setErrorWait(true)
                return
            }
            sleep((600 + RandomUtil.delay()).toLong())
        }
        if (shouldRetry(error.tryCount, tryCount())) {
            entity.setNeedRetry()
            collect(entity)
        }
    }

    private fun handleFailedResult(
        entity: CollectEnergyEntity,
        response: ForestEnergyRpcExecutor.Execution.Response,
        result: ForestEnergyResultService.Result,
    ) {
        if (result.resultCode == "PARAM_ILLEGAL2") {
            StructuredLog.record(
                TAG,
                "[${userName(entity.userId)}]能量已被收取,取消重试 错误:${result.resultDescription}",
            )
            return
        }
        if (checkFrequencyError(entity.userId, response.response)) return
        StructuredLog.record(TAG, "[${userName(entity.userId)}]${result.resultDescription}")
        if (shouldRetry(response.tryCount, tryCount())) {
            entity.setNeedRetry()
            collect(entity)
        }
    }

    internal companion object {
        const val TAG = "ForestEnergyCollectionService"
        const val PROP_PREPARE_CACHE_MS = 30_000L
        fun shouldRetry(attempt: Int, limit: Int): Boolean = attempt < limit
    }
}
