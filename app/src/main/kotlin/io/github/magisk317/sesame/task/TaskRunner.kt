package io.github.magisk317.sesame.task

import android.annotation.SuppressLint
import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.entitlement.EntitlementGate
import io.github.magisk317.sesame.data.db.TaskExecutionRecord
import io.github.magisk317.sesame.data.db.TaskStatsRepository
import io.github.magisk317.sesame.hook.ApplicationHook
import io.github.magisk317.sesame.model.BaseModel
import io.github.magisk317.sesame.model.CustomSettings
import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.task.customTasks.ManualTask
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 协程任务执行器 (优化版)
 *
 * 核心改进:
 * 1. **并发执行**: 支持任务并发运行，缩短总耗时。
 * 2. **生命周期**: 绑定到调用者的生命周期，防止泄漏。
 * 3. **逻辑简化**: 移除复杂的宽限期嵌套，使用标准的协程超时机制。
 */
class CoroutineTaskRunner(allModels: List<Model>) {

    companion object {
        private const val TAG = "CoroutineTaskRunner"
        private const val DEFAULT_TASK_TIMEOUT = 10 * 60 * 1000L // 10分钟

        // 最大并发数，防止请求过于频繁触发风控
        // 可以做成配置项，目前硬编码为 3
        private const val MAX_CONCURRENCY = 3

        private val TIMEOUT_WHITELIST_KEYWORDS = setOf("森林", "庄园", "运动")

        /** Retained for configuration/test compatibility; no task is auto-success on timeout. */
        internal fun isTimeoutWhitelisted(taskName: String): Boolean =
            TIMEOUT_WHITELIST_KEYWORDS.any(taskName::contains)
    }

    private val taskList: List<ModelTask> = allModels.filterIsInstance<ModelTask>()

    // 统计数据
    private val successCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)
    private val skippedCount = AtomicInteger(0)
    private val taskExecutionTimes = ConcurrentHashMap<String, Long>()

    /**
     * 启动任务执行流程
     * 注意：现在这是一个 suspend 函数，需要在一个协程作用域内调用
     */
    fun launchIn(scope: CoroutineScope, isFirst: Boolean, rounds: Int): Job =
        scope.launch { run(isFirst, rounds) }

    suspend fun run(
        isFirst: Boolean = true,
        rounds: Int = BaseModel.taskExecutionRounds.value
    ) = coroutineScope { // 使用 coroutineScope 创建子作用域
        val startTime = System.currentTimeMillis()

        // 授权状态通过 LibXposed remote preferences 同步，任务边界仍必须对 JWS
        // lease token 自验签名和 exp；token 缺失、签名无效或过期时 fail-closed。
        if (ApplicationHook.appContext == null || !EntitlementGate.isAutomationAllowed()) {
            StructuredLog.record(TAG, "⛔ 授权无效，跳过任务执行")
            return@coroutineScope
        }

        if (isFirst) {
            ApplicationHook.updateDay()
            resetCounters()
        }

        // 【互斥检查】如果手动任务流正在运行，则跳过本次自动执行
        if (ManualTask.isManualRunning) {
            StructuredLog.record(TAG, "⏸ 检测到“手动庄园任务流”正在运行中，跳过本次自动任务调度")
            val skippedTasks = taskList.filter { it.isEnable() }
            val userId = UserMap.currentUid.orEmpty()
            repeat(rounds.coerceAtLeast(0)) { roundIndex ->
                val round = roundIndex + 1
                val now = System.currentTimeMillis()
                skippedTasks.forEach { task ->
                    skippedCount.incrementAndGet()
                    recordExecution(
                        taskName = task.getName() ?: "未知任务",
                        round = round,
                        userId = userId,
                        startedAt = now,
                        finishedAt = now,
                        status = TaskExecutionRecord.Status.SKIPPED,
                        errorMessage = "manual task is running",
                    )
                }
            }
            printExecutionSummary(startTime, System.currentTimeMillis())
            if (currentCoroutineContext().isActive) {
                scheduleNext()
            }
            return@coroutineScope
        }

        try {
            CustomSettings.loadForTaskRunner()
            val status = CustomSettings.getOnceDailyStatus(enableLog = true)

            // 执行多轮任务
            repeat(rounds) { roundIndex ->
                val round = roundIndex + 1
                executeRound(round, status)
            }

            if (CustomSettings.onlyOnceDaily.value) {
                // 确保时间状态是最新的
                TaskCommon.update()
                if (TaskCommon.IS_MODULE_SLEEP_TIME) {
                    StructuredLog.record(TAG, "💤 当前处于模块休眠时间，不设置 OnceDaily::Finished 标记")
                } else {
                    Status.setFlagToday("OnceDaily::Finished")
                }
            }

        } catch (e: CancellationException) {
            StructuredLog.record(TAG, "🚫 任务流程被取消")
            throw e
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "任务流程异常", e)
        } finally {
            printExecutionSummary(startTime, System.currentTimeMillis())
            // 外部 stop、账号切换和 Hook 销毁都会取消当前协程；取消路径不能重新创建轮询任务。
            if (currentCoroutineContext().isActive) {
                scheduleNext()
            } else {
                StructuredLog.record(TAG, "🚫 当前任务流已取消，不调度下一轮")
            }
        }
    }

    /**
     * 执行一轮任务 (并发模式)
     */
    private suspend fun executeRound(round: Int, status: CustomSettings.OnceDailyStatus) = coroutineScope {
        val roundStartTime = System.currentTimeMillis()

        // 1. 筛选任务
        val enabledTasks = taskList.filter { it.isEnable() }
        val tasksToRun = enabledTasks.filter { task ->
            task.isEnable() && !CustomSettings.isOnceDailyBlackListed(task.getName(), status)
        }

        val excludedTasks = enabledTasks - tasksToRun.toSet()
        excludedTasks.forEach { task ->
            skippedCount.incrementAndGet()
            recordExecution(
                taskName = task.getName() ?: "未知任务",
                round = round,
                userId = UserMap.currentUid.orEmpty(),
                startedAt = roundStartTime,
                finishedAt = roundStartTime,
                status = TaskExecutionRecord.Status.SKIPPED,
                errorMessage = "once-daily blacklist or task filter",
            )
        }

        // 2. 并发执行
        // 使用 Semaphore 限制并发数量
        val semaphore = Semaphore(MAX_CONCURRENCY)

        // 创建所有任务的 Deferred 对象
        val deferreds = tasksToRun.map { task ->
            async {
                // 【互斥检查】再次检查手动任务，防止并发启动
                if (ManualTask.isManualRunning) {
                     skippedCount.incrementAndGet()
                     recordExecution(
                         taskName = task.getName() ?: "未知任务",
                         round = round,
                         userId = UserMap.currentUid.orEmpty(),
                         startedAt = System.currentTimeMillis(),
                         finishedAt = System.currentTimeMillis(),
                         status = TaskExecutionRecord.Status.SKIPPED,
                         errorMessage = "manual task is running",
                     )
                     StructuredLog.record(TAG, "⏸ 任务 ${task.getName()} 因手动模式启动而中止")
                     return@async
                }
                semaphore.withPermit {
                    executeSingleTask(task, round)
                }
            }
        }

        // 3. 等待本轮所有任务完成
        deferreds.awaitAll()

    }

    /**
     * 执行单个任务
     */
    private suspend fun executeSingleTask(task: ModelTask, round: Int) {
        val taskName = task.getName() ?: "未知任务"
        val taskId = "$taskName-R$round"
        val startTime = System.currentTimeMillis()

        val userId = UserMap.currentUid.orEmpty()

        try {
            val result = withTimeout(DEFAULT_TASK_TIMEOUT) {
                task.startTaskWithResult(force = false, rounds = 1).await()
            }
            val time = System.currentTimeMillis() - startTime
            when (result.status) {
                TaskExecutionResult.Status.SUCCESS -> {
                    successCount.incrementAndGet()
                    taskExecutionTimes[taskId] = time
                    recordExecution(
                        taskName = taskName,
                        round = round,
                        userId = userId,
                        startedAt = startTime,
                        finishedAt = System.currentTimeMillis(),
                        status = TaskExecutionRecord.Status.SUCCESS,
                        durationMs = time,
                    )
                }

                TaskExecutionResult.Status.SKIPPED -> {
                    skippedCount.incrementAndGet()
                    recordExecution(
                        taskName = taskName,
                        round = round,
                        userId = userId,
                        startedAt = startTime,
                        finishedAt = System.currentTimeMillis(),
                        status = TaskExecutionRecord.Status.SKIPPED,
                        durationMs = time,
                        errorMessage = result.errorMessage,
                    )
                    StructuredLog.record(TAG, "⏭️ 跳过: $taskId")
                }

                TaskExecutionResult.Status.CANCELLED -> {
                    skippedCount.incrementAndGet()
                    recordExecution(
                        taskName = taskName,
                        round = round,
                        userId = userId,
                        startedAt = startTime,
                        finishedAt = System.currentTimeMillis(),
                        status = TaskExecutionRecord.Status.CANCELLED,
                        durationMs = time,
                        errorMessage = result.errorMessage,
                    )
                    StructuredLog.record(TAG, "🚫 取消: $taskId")
                }

                TaskExecutionResult.Status.FAILED -> {
                    failureCount.incrementAndGet()
                    recordExecution(
                        taskName = taskName,
                        round = round,
                        userId = userId,
                        startedAt = startTime,
                        finishedAt = System.currentTimeMillis(),
                        status = TaskExecutionRecord.Status.FAILED,
                        durationMs = time,
                        errorMessage = result.errorMessage,
                    )
                    StructuredLog.error(TAG, "❌ 失败: $taskId (${result.errorMessage})")
                }
            }

        } catch (e: TimeoutCancellationException) {
            val time = System.currentTimeMillis() - startTime
            failureCount.incrementAndGet()
            recordExecution(
                taskName = taskName,
                round = round,
                userId = userId,
                startedAt = startTime,
                finishedAt = System.currentTimeMillis(),
                status = TaskExecutionRecord.Status.TIMED_OUT,
                durationMs = time,
                errorMessage = e.message,
            )
            StructuredLog.error(TAG, "⏰ 超时: $taskId (${TimeFormatter.formatDuration(time)} > ${TimeFormatter.formatDuration(DEFAULT_TASK_TIMEOUT)})")
            task.stopTask()
        } catch (e: CancellationException) {
            val time = System.currentTimeMillis() - startTime
            skippedCount.incrementAndGet()
            recordExecution(
                taskName = taskName,
                round = round,
                userId = userId,
                startedAt = startTime,
                finishedAt = System.currentTimeMillis(),
                status = TaskExecutionRecord.Status.CANCELLED,
                durationMs = time,
                errorMessage = e.message,
            )
            StructuredLog.record(TAG, "🚫 取消: $taskId")
            if (!currentCoroutineContext().isActive) throw e
        } catch (e: Exception) {
            val time = System.currentTimeMillis() - startTime
            failureCount.incrementAndGet()
            recordExecution(
                taskName = taskName,
                round = round,
                userId = userId,
                startedAt = startTime,
                finishedAt = System.currentTimeMillis(),
                status = TaskExecutionRecord.Status.FAILED,
                durationMs = time,
                errorMessage = e.message,
            )
            StructuredLog.error(TAG, "❌ 失败: $taskId (${e.message})")
        }
    }

    private fun recordExecution(
        taskName: String,
        round: Int,
        userId: String,
        startedAt: Long,
        finishedAt: Long,
        status: TaskExecutionRecord.Status,
        durationMs: Long = (finishedAt - startedAt).coerceAtLeast(0),
        errorMessage: String? = null,
    ) {
        TaskStatsRepository.record(
            TaskExecutionRecord(
                userId = userId,
                taskName = taskName,
                round = round,
                startedAt = startedAt,
                finishedAt = finishedAt,
                status = status,
                durationMs = durationMs,
                errorMessage = errorMessage,
            )
        )
    }

    private fun scheduleNext() {
        try {
            ApplicationHook.scheduleNextExecutionInternal(ApplicationHook.lastExecTime)
            StructuredLog.record(TAG, "📅 已调度下次执行")
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "调度失败", e)
        }
    }

    private fun resetCounters() {
        successCount.set(0)
        failureCount.set(0)
        skippedCount.set(0)
        taskExecutionTimes.clear()
    }

    @SuppressLint("DefaultLocale")
    private fun printExecutionSummary(startTime: Long, endTime: Long) {
        val totalTime = endTime - startTime
        val avgTime = if (taskExecutionTimes.isNotEmpty()) taskExecutionTimes.values.average() else 0.0

        StructuredLog.record(TAG, "📈 执行统计（并发模式）")
        StructuredLog.record(TAG, "⏱️ 总耗时: ${TimeFormatter.formatDuration(totalTime)}")
        StructuredLog.record(TAG, "✓ 成功: ${successCount.get()} | ❌ 失败: ${failureCount.get()} | ⏭️ 跳过: ${skippedCount.get()}")
        if (taskExecutionTimes.isNotEmpty()) {
            StructuredLog.record(TAG, "⚡ 平均耗时: ${TimeFormatter.formatDuration(avgTime.toLong())}")
        }

        val nextTime = ApplicationHook.nextExecutionTime
        if (nextTime > 0) {
            StructuredLog.record(TAG, "📅 下次: ${TimeUtil.getCommonDate(nextTime)}")
        }
    }
}
