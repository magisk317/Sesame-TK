package io.github.magisk317.sesame.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_execution_records",
    indices = [Index(value = ["userId", "taskName", "startedAt"])]
)
data class TaskExecutionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val taskName: String,
    val round: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val status: Status,
    val durationMs: Long,
    val errorMessage: String? = null,
) {
    enum class Status {
        SUCCESS,
        FAILED,
        SKIPPED,
        CANCELLED,
        TIMED_OUT,
    }
}
