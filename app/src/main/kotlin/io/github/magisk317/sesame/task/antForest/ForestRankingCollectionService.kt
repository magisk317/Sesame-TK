package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.telemetry.SesameTelemetry
import io.github.magisk317.sesame.util.TaskRuntimeStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

/** Queries a forest ranking and dispatches its friends in bounded sequential batches. */
internal class ForestRankingCollectionService(
    private val currentUserId: () -> String?,
    private val processFriends: suspend (Any, String, String) -> Unit,
    private val pause: (Long) -> Unit = GlobalThreadPools::sleepCompat,
) {
    suspend fun run(
        rankingName: String,
        query: () -> String?,
        arrayKey: String,
        flag: String,
        preCondition: ((JsonObject) -> Boolean)? = null,
    ) = withContext(Dispatchers.Default) {
        try {
            val ranking = queryWithRetry(rankingName, query) ?: return@withContext
            if (!ResChecker.checkRes("$TAG 获取${rankingName}失败:", ranking)) {
                StructuredLog.error(TAG, "获取${rankingName}失败: ${ranking.optString("resultDesc")}")
                return@withContext
            }
            val total = ranking.optJSONArray(arrayKey) ?: run {
                StructuredLog.record(TAG, "$rankingName 数据为空，跳过处理。")
                return@withContext
            }
            if (preCondition != null && !preCondition(ranking)) return@withContext
            rankingValue(ranking, arrayKey, currentUserId())?.let(SesameTelemetry::recordEnergyRanking)

            val snapshot = RankingSnapshotStore.load(currentUserId(), flag)
            val nextRows = snapshot.rows.toMutableMap()
            val changedTotal = changedRows(total, snapshot, nextRows)

            StructuredLog.record(TAG, "成功获取$rankingName 数据，共发现${total.length()}位好友。")
            val skipped = total.length() - changedTotal.size
            if (skipped > 0) StructuredLog.record(TAG, "$rankingName 使用上一轮快照跳过${skipped}位未变化好友")
            if (changedTotal.isEmpty()) {
                RankingSnapshotStore.save(currentUserId(), flag, RankingSnapshot(nextRows))
                StructuredLog.record(TAG, "$rankingName 没有发生变化的好友，跳过主页查询")
                return@withContext
            }
            processFriends(JsonArray(changedTotal), flag, "${rankingName}发生变化的好友")
            RankingSnapshotStore.save(currentUserId(), flag, RankingSnapshot(nextRows))
            StructuredLog.record(TAG, "收取$rankingName 能量完成！")
        } catch (error: CancellationException) {
            StructuredLog.record(TAG, "处理$rankingName 时协程被取消")
            throw error
        } catch (error: Exception) {
            StructuredLog.printStackTrace(TAG, "collectRankings 异常", error)
        }
    }

    private fun queryWithRetry(name: String, query: () -> String?): JsonObject? {
        repeat(RETRY_COUNT) { index ->
            var response: String? = null
            try {
                response = query()
                if (!response.isNullOrEmpty()) return response.toJsonObject()
            } catch (error: Exception) {
                StructuredLog.printStackTrace(TAG, "collectRankings $name, response: $response", error)
            }
            if (index < RETRY_COUNT - 1) {
                val retryDelay = 5_000L * (index + 1)
                StructuredLog.record(TAG, "获取$name 失败，${TimeFormatter.formatDuration(retryDelay)}后重试")
                pause(retryDelay)
            }
        }
        StructuredLog.error(TAG, "获取$name 失败")
        return null
    }

    private fun rankingValue(ranking: JsonObject, arrayKey: String, userId: String?): Int? {
        if (userId.isNullOrBlank()) return null
        val rows = ranking.optJSONArray(arrayKey) ?: return null
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            if (row.optString("userId") != userId) continue
            for (key in RANKING_VALUE_KEYS) {
                row.optString(key).toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun changedRows(
        rows: JsonArray,
        snapshot: RankingSnapshot,
        nextRows: MutableMap<String, String>,
    ): List<JsonObject> = buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val userId = row.optString("userId")
            if (userId.isBlank()) continue
            val fingerprint = rankingFingerprint(row)
            if (snapshot.rows[userId] != fingerprint) add(row)
            nextRows[userId] = fingerprint
        }
    }

    /** Keep only energy state; rank and presentation fields are volatile. */
    private fun rankingFingerprint(row: JsonObject): String = buildString {
        append(row.optString("userId"))
        append('|').append(row.optString("energySummation"))
        append('|').append(row.optString("totalEnergy"))
        append('|').append(row.optString("energy"))
        append('|').append(row.optString("energyValue"))
        append('|').append(row.optString("totalEnergyValue"))
        append('|').append(row.optString("canThumb"))
    }

    internal companion object {
        private const val TAG = "ForestRankingCollectionService"
        const val FIRST_PAGE_SIZE = 20
        const val RETRY_COUNT = 3
        private val RANKING_VALUE_KEYS = arrayOf("totalEnergy", "energy", "energyValue", "totalEnergyValue")

        internal fun remainingUserIds(total: JsonArray, currentUserId: String?): List<String> = buildList {
            for (index in FIRST_PAGE_SIZE until total.length()) {
                val userId = total.getJSONObject(index).getString("userId")
                if (userId != currentUserId) add(userId)
            }
        }
    }
}

@Serializable
private data class RankingSnapshot(val rows: Map<String, String> = emptyMap())

private object RankingSnapshotStore {
    private const val KEY_PREFIX = "forest::rankingSnapshotV2::"

    fun load(userId: String?, flag: String): RankingSnapshot {
        if (userId.isNullOrBlank()) return RankingSnapshot()
        return TaskRuntimeStore.getOrCreate(
            "$KEY_PREFIX$userId::$flag",
            RankingSnapshot(),
            RankingSnapshot.serializer(),
        )
    }

    fun save(userId: String?, flag: String, previous: RankingSnapshot) {
        if (userId.isNullOrBlank()) return
        TaskRuntimeStore.put(
            "$KEY_PREFIX$userId::$flag",
            previous,
            RankingSnapshot.serializer(),
        )
    }
}
