package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.TaskRuntimeStore
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 蹲点任务持久化数据类
 * 用于序列化和反序列化，存储到独立任务运行态存储
 */
@Serializable
data class WaitingTaskPersistData(
    val userId: String = "",
    val userName: String = "",
    val bubbleId: Long = 0L,
    val produceTime: Long = 0L,
    val fromTag: String = "",
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val shieldEndTime: Long = 0L,
    val bombEndTime: Long = 0L,
    val savedTime: Long = System.currentTimeMillis() // 保存时间，用于判断是否过期
) {
    /**
     * 转换为运行时任务对象
     */
    fun toWaitingTask(): EnergyWaitingManager.WaitingTask {
        return EnergyWaitingManager.WaitingTask(
            userId = userId,
            userName = userName,
            bubbleId = bubbleId,
            produceTime = produceTime,
            fromTag = fromTag,
            retryCount = retryCount,
            maxRetries = maxRetries,
            shieldEndTime = shieldEndTime,
            bombEndTime = bombEndTime
        )
    }

    companion object {
        /**
         * 从运行时任务对象创建持久化数据
         */
        fun fromWaitingTask(task: EnergyWaitingManager.WaitingTask): WaitingTaskPersistData {
            return WaitingTaskPersistData(
                userId = task.userId,
                userName = task.userName,
                bubbleId = task.bubbleId,
                produceTime = task.produceTime,
                fromTag = task.fromTag,
                retryCount = task.retryCount,
                maxRetries = task.maxRetries,
                shieldEndTime = task.shieldEndTime,
                bombEndTime = task.bombEndTime
            )
        }
    }
}

/**
 * 蹲点任务持久化管理器
 *
 * 职责：
 * 1. 保存蹲点任务到任务运行态存储
 * 2. 从任务运行态存储恢复蹲点任务
 * 3. 验证恢复的任务是否仍然有效
 * 4. 过滤过期或无效的任务
 */
object EnergyWaitingPersistence {
    private const val TAG = "EnergyWaitingPersistence"

    // Persistence is process-scoped. Session cancellation must not discard a
    // snapshot that was already accepted for writing.
    private val persistenceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("EnergyWaitingPersistence")
    )
    private val saveMutex = Mutex()
    private val saveSequence = AtomicLong(0)
    private val latestSequenceByKey = ConcurrentHashMap<String, AtomicLong>()
    private val latestFingerprintByKey = ConcurrentHashMap<String, String>()

    // 任务最大保存时间（8小时，超过此时间的任务视为过期）
    private const val MAX_TASK_AGE_MS = 8 * 60 * 60 * 1000L

    /**
     * 获取当前账号的任务运行态存储键
     * 每个账号使用独立的键，避免多账号切换时数据混淆
     *
     * @return 包含当前用户 uid 的存储键，如果 uid 为空则使用默认键
     */
    private fun getTaskRuntimeKey(): String {
        val currentUid = UserMap.currentUid
        return if (currentUid.isNullOrEmpty()) {
            "energy_waiting_tasks_default"
        } else {
            "energy_waiting_tasks_$currentUid"
        }
    }

    /**
     * 保存蹲点任务到任务运行态存储（异步）
     *
     * @param tasks 当前活跃的蹲点任务
     */
    fun saveTasks(tasks: Map<String, EnergyWaitingManager.WaitingTask>) {
        val persistDataList = try {
            tasks.values.map { task ->
                WaitingTaskPersistData.fromWaitingTask(task)
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "准备保存蹲点任务失败:", e)
            return
        }

        // 在切换账号前固定 key，避免异步执行时读取到新账号 UID。
        val taskRuntimeKey = getTaskRuntimeKey()
        val fingerprint = tasks.entries
            .sortedBy { it.key }
            .joinToString("|") { (taskId, task) ->
                "$taskId:${task.userId}:${task.userName}:${task.bubbleId}:" +
                    "${task.produceTime}:${task.fromTag}:${task.retryCount}:${task.maxRetries}:" +
                    "${task.shieldEndTime}:${task.bombEndTime}"
            }
        if (latestFingerprintByKey.put(taskRuntimeKey, fingerprint) == fingerprint) {
            return
        }
        val sequence = saveSequence.incrementAndGet()
        val latestSequence = latestSequenceByKey.computeIfAbsent(taskRuntimeKey) { AtomicLong(0) }
        latestSequence.accumulateAndGet(sequence) { current, candidate ->
            maxOf(current, candidate)
        }
        persistenceScope.launch {
            saveMutex.withLock {
                try {
                    // 多次保存是异步提交的，旧快照不能覆盖已经提交的新快照。
                    if (sequence < latestSequence.get()) return@withLock
                    TaskRuntimeStore.put(
                        taskRuntimeKey,
                        persistDataList,
                        ListSerializer(WaitingTaskPersistData.serializer()),
                    )

                    StructuredLog.record(TAG, "当前持久化蹲点任务快照：${persistDataList.size}个 (key: $taskRuntimeKey)")
                } catch (e: Exception) {
                    latestFingerprintByKey.remove(taskRuntimeKey, fingerprint)
                    StructuredLog.printStackTrace(TAG, "保存蹲点任务失败:", e)
                }
            }
        }
    }

    /**
     * 从任务运行态存储加载蹲点任务
     *
     * @return 恢复的任务列表（已过滤过期任务）
     */
    fun loadTasks(): List<EnergyWaitingManager.WaitingTask> {
        return try {
            val taskRuntimeKey = getTaskRuntimeKey()
            val persistDataList = TaskRuntimeStore.getOrCreate(
                taskRuntimeKey,
                emptyList<WaitingTaskPersistData>(),
                ListSerializer(WaitingTaskPersistData.serializer()),
            )

            if (persistDataList.isEmpty()) {
                StructuredLog.record(TAG, "持久化存储中无蹲点任务 (key: $taskRuntimeKey)")
                return emptyList()
            }

            val currentTime = System.currentTimeMillis()
            val validTasks = mutableListOf<EnergyWaitingManager.WaitingTask>()
            var expiredCount = 0
            var tooOldCount = 0

            persistDataList.forEach { persistData ->
                // 检查1：任务保存时间是否过久
                val taskAge = currentTime - persistData.savedTime
                if (taskAge > MAX_TASK_AGE_MS) {
                    tooOldCount++
                    StructuredLog.record(TAG, "  跳过[${persistData.userName}]：保存时间超过${TimeFormatter.formatDuration(taskAge)}")
                    return@forEach
                }

                // 检查2：能量是否已经过期超过1小时
                if (currentTime > persistData.produceTime + 60 * 60 * 1000L) {
                    expiredCount++
                    StructuredLog.record(TAG, "  跳过[${persistData.userName}]：能量已过期超过1小时")
                    return@forEach
                }

                // 任务有效，添加到列表
                validTasks.add(persistData.toWaitingTask())
            }

            StructuredLog.record(TAG, "📥 从持久化存储恢复${validTasks.size}个有效任务（跳过${expiredCount}个过期，${tooOldCount}个过旧）")

            validTasks
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "加载蹲点任务失败:", e)
            emptyList()
        }
    }

    /**
     * 清空持久化存储中的所有任务
     */
    fun clearTasks() {
        try {
            val taskRuntimeKey = getTaskRuntimeKey()
            val sequence = saveSequence.incrementAndGet()
            val latestSequence = latestSequenceByKey.computeIfAbsent(taskRuntimeKey) { AtomicLong(0) }
            latestSequence.set(sequence)
            runBlocking {
                saveMutex.withLock {
                    TaskRuntimeStore.put(
                        taskRuntimeKey,
                        emptyList<WaitingTaskPersistData>(),
                        ListSerializer(WaitingTaskPersistData.serializer()),
                    )
                }
            }
            StructuredLog.record(TAG, "清空持久化存储 (key: $taskRuntimeKey)")
        } catch (e: Exception) {
            StructuredLog.error(TAG, "清空持久化存储失败: ${e.message}")
        }
    }

    /**
     * 验证并重新添加恢复的任务
     *
     * @param tasks 恢复的任务列表
     * @param addTaskCallback 添加任务的回调函数
     * @return 实际重新添加的任务数量
     */
    suspend fun validateAndRestoreTasks(
        tasks: List<EnergyWaitingManager.WaitingTask>,
        addTaskCallback: suspend (EnergyWaitingManager.WaitingTask) -> Boolean
    ): Int {
        if (tasks.isEmpty()) {
            return 0
        }

        StructuredLog.record(TAG, "蹲点🌿 验证${tasks.size}个恢复任务")

        var restoredCount = 0
        var skippedCount = 0

        tasks.forEach { task ->
            try {
                // 自己的账号：无论是否有保护罩都要恢复（到时间后直接收取）
                if (task.isSelf()) {
                    val success = addTaskCallback(task)
                    if (success) {
                        restoredCount++
                        StructuredLog.record(
                            TAG,
                            "  ⭐️ 恢复[${task.getUserTypeTag()}${task.userName}]球[${task.bubbleId}]：能量${TimeUtil.getCommonDate(task.produceTime)}成熟，到时间直接收取"
                        )
                    } else {
                        skippedCount++
                    }
                    return@forEach
                }

                // 好友任务需要重新查询主页以获取最新保护罩状态。
                val userHomeResponse = ForestCoreRpc.queryFriendHomePage(task.userId, task.fromTag)

                if (userHomeResponse.isNullOrEmpty()) {
                    StructuredLog.record(TAG, "  验证[${task.userName}]：无法获取主页信息，跳过恢复")
                    skippedCount++
                    return@forEach
                }

                val userHomeObj = userHomeResponse.toJsonObject()

                // 好友账号：如果保护罩覆盖能量成熟期则跳过
                if (ForestUtil.shouldSkipWaitingDueToProtection(userHomeObj, task.produceTime)) {
                    val protectionEndTime = ForestUtil.getProtectionEndTime(userHomeObj)
                    val timeDifference = protectionEndTime - task.produceTime
                    StructuredLog.record(
                        TAG,
                        "  ❌ 跳过[${task.getUserTypeTag()}${task.userName}]球[${task.bubbleId}]：保护罩覆盖能量成熟期(${TimeFormatter.formatDuration(timeDifference)})"
                    )
                    skippedCount++
                } else {
                    // 好友任务有效，重新添加
                    val success = addTaskCallback(task)
                    if (success) {
                        restoredCount++
                        StructuredLog.record(TAG, "蹲点🌿 恢复[${task.getUserTypeTag()}${task.userName}]球[${task.bubbleId}]：能量${TimeUtil.getCommonDate(task.produceTime)}成熟")
                    } else {
                        skippedCount++
                    }
                }

                // 添加短暂延迟，避免请求过快
                kotlinx.coroutines.delay(200)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StructuredLog.record(TAG, "  验证任务[${task.userName}]时出错: ${e.message}，跳过")
                skippedCount++
            }
        }

        StructuredLog.record(TAG, "恢复完成：成功${restoredCount}个，跳过${skippedCount}个")

        return restoredCount
    }
}
