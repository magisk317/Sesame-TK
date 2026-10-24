package io.github.magisk317.sesame.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StatsDao {
    @Insert
    suspend fun insertTaskExecution(record: TaskExecutionRecord)

    @Query("SELECT * FROM task_execution_records WHERE userId = :userId ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recentTaskExecutions(userId: String, limit: Int): List<TaskExecutionRecord>

    @Query("DELETE FROM task_execution_records WHERE startedAt < :before")
    suspend fun deleteBefore(before: Long): Int
}
