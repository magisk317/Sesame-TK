package io.github.magisk317.sesame.data.db

import android.content.Context
import androidx.room.Room
import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

object TaskStatsRepository {
    private const val TAG = "TaskStatsRepository"
    private const val DATABASE_NAME = "sesame-stats.db"

    @Volatile
    private var database: SesameDatabase? = null

    // Statistics must survive account/session cancellation. This scope is
    // process-scoped and is only closed after pending writes have drained.
    private val writeScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("SesameTaskStats")
    )
    private val pendingWrites = ConcurrentHashMap.newKeySet<Job>()

    @Synchronized
    fun initialize(context: Context) {
        if (database != null) return
        database = Room.databaseBuilder(
            context.applicationContext,
            SesameDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(SesameDatabase.MIGRATION_1_2).build()
    }

    @Synchronized
    fun record(record: TaskExecutionRecord) {
        val dao = database?.statsDao() ?: return
        // Register the lazy job before it can finish. Starting immediately
        // after registration closes the completed-before-pending-set race.
        val job = writeScope.launch(start = CoroutineStart.LAZY) {
            runCatching { dao.insertTaskExecution(record) }
                .onFailure { StructuredLog.printStackTrace(TAG, "写入任务统计失败", it) }
        }
        pendingWrites += job
        job.invokeOnCompletion { pendingWrites -= job }
        job.start()
    }

    suspend fun flush() {
        pendingWrites.toList().joinAll()
    }

    @Synchronized
    fun close() {
        runBlocking { flush() }
        database?.close()
        database = null
    }
}
