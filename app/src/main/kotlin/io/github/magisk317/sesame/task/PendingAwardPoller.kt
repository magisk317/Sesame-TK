package io.github.magisk317.sesame.task

import io.github.magisk317.sesame.hook.keepalive.SmartSchedulerManager
import io.github.magisk317.sesame.task.antForest.ForestChouChouLe
import io.github.magisk317.sesame.task.antForest.ForestEnergyRainTask
import io.github.magisk317.sesame.task.antForest.ForestHomeTaskService
import io.github.magisk317.sesame.task.antForest.ForestVitalityTask
import io.github.magisk317.sesame.task.antDodo.AntDodoTaskRunner
import io.github.magisk317.sesame.task.antFarm.ChouChouLe
import io.github.magisk317.sesame.task.antFish.AntAiFishTaskRunner
import io.github.magisk317.sesame.task.antMember.LuckyCardTaskService
import io.github.magisk317.sesame.task.antOcean.OceanTaskRunner
import io.github.magisk317.sesame.task.antSports.AntSportsTaskService
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.entitlement.EntitlementGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight claim-only pass shared by all task modules.
 *
 * A FINISHED task is not the same as a completed task: its award can remain
 * claimable after the normal task runner has stopped (including only-once
 * daily mode). Each provider owns its RPC shape; this class only schedules
 * and serializes the pass.
 */
object PendingAwardPoller {
    private const val TAG = "PendingAwardPoller"
    private const val FIRST_DELAY_MS = 5_000L
    private const val PERIOD_MS = 15 * 60 * 1_000L

    private val running = AtomicBoolean(false)
    private val passLock = Mutex()
    @Volatile
    private var scheduledTaskId: Int = -1

    fun start() {
        if (!EntitlementGate.isAutomationAllowed()) {
            StructuredLog.record(TAG, "⛔ 授权无效，跳过启动领奖轮询")
            return
        }
        if (!running.compareAndSet(false, true)) return
        schedule(FIRST_DELAY_MS)
        StructuredLog.record(TAG, "领奖轮询已启动: 首次5秒, 周期15分钟")
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        scheduledTaskId.takeIf { it >= 0 }?.let(SmartSchedulerManager::cancelTask)
        scheduledTaskId = -1
        StructuredLog.record(TAG, "领奖轮询已停止")
    }

    suspend fun pollOnce() {
        if (!running.get()) return
        if (!EntitlementGate.isAutomationAllowed()) {
            StructuredLog.record(TAG, "⛔ 授权失效，停止领奖轮询")
            stop()
            return
        }
        if (!passLock.tryLock()) {
            StructuredLog.record(TAG, "已有领奖轮询执行中，跳过重入")
            return
        }
        try {
            val providers = listOf<suspend () -> Unit>(
                { ForestHomeTaskService.claimPendingAwards() },
                { LuckyCardTaskService.claimPendingCards() },
                { ForestChouChouLe().claimPendingAwards() },
                { ForestVitalityTask.claimPendingAwards() },
                { ForestEnergyRainTask.claimPendingAwards() },
                { ChouChouLe().claimPendingAwards() },
                { AntAiFishTaskRunner.pollPendingAwards() },
                { OceanTaskRunner.pollPendingAwards() },
                { AntDodoTaskRunner.pollPendingAwards() },
                { AntSportsTaskService().claimPendingAwards() },
            )
            providers.forEachIndexed { index, provider ->
                if (!EntitlementGate.isAutomationAllowed()) return@forEachIndexed
                runCatching { provider() }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        StructuredLog.printStackTrace(TAG, "全局领奖🎁 提供者[$index]扫描失败", error)
                    }
            }
        } finally {
            passLock.unlock()
        }
    }

    private fun schedule(delayMs: Long) {
        scheduledTaskId = SmartSchedulerManager.schedule(delayMs.coerceAtLeast(0L), "全局领奖轮询") {
            if (!running.get() || !EntitlementGate.isAutomationAllowed()) {
                stop()
                return@schedule
            }
            GlobalThreadPools.execute {
                try {
                    pollOnce()
                } finally {
                    if (running.get()) schedule(PERIOD_MS)
                }
            }
        }
    }
}
