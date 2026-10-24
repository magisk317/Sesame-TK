package io.github.magisk317.sesame.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class TaskExecutionConverters {
    @TypeConverter
    fun statusToString(status: TaskExecutionRecord.Status): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): TaskExecutionRecord.Status =
        runCatching { TaskExecutionRecord.Status.valueOf(value) }
            .getOrDefault(TaskExecutionRecord.Status.FAILED)
}

@Database(
    entities = [TaskExecutionRecord::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(TaskExecutionConverters::class)
abstract class SesameDatabase : RoomDatabase() {
    abstract fun statsDao(): StatsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE task_execution_records ADD COLUMN userId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("DROP INDEX IF EXISTS index_task_execution_records_taskName_startedAt")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_execution_records_userId_taskName_startedAt " +
                        "ON task_execution_records(userId, taskName, startedAt)"
                )
            }
        }
    }
}
