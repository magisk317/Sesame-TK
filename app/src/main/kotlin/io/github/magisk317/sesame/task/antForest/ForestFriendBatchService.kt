package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.JsonArray

/** Converts ranking batches to friend entries and processes them with bounded concurrency. */
internal class ForestFriendBatchService(
    private val errorWait: () -> Boolean,
    private val fillUserRobFlag: (List<String?>, Boolean) -> String,
    private val processFriend: (kotlinx.serialization.json.JsonObject, String) -> Unit,
    private val displayName: (String) -> String?,
    concurrency: Int = DEFAULT_CONCURRENCY,
) {
    private val limiter = Semaphore(concurrency)

    suspend fun process(source: Any, flag: String, sourceName: String = "好友") =
        withContext(Dispatchers.Default) {
            try {
                if (errorWait()) return@withContext
                val friendList = resolve(source, flag)
                if (friendList == null) {
                    StructuredLog.record(TAG, "${sourceName}数据为空，跳过处理")
                    return@withContext
                }
                if (friendList.isEmpty()) {
                    StructuredLog.record(TAG, "${sourceName}列表为空，跳过处理")
                    return@withContext
                }

                val names = buildList {
                    for (index in friendList.indices) {
                        val friend = friendList.getJSONObject(index)
                        val userId = friend.optString("userId", "")
                        add(friend.optString("displayName", displayName(userId).orEmpty()))
                    }
                }
                StructuredLog.record(TAG, "森林🌿 批量处理${friendList.size}个${sourceName}（并发数:$DEFAULT_CONCURRENCY）")
                StructuredLog.record(TAG, "👥 ${names.joinToString(" | ")}")
                val start = System.currentTimeMillis()
                val jobs = mutableListOf<Deferred<Unit>>()
                for (index in friendList.indices) {
                    val friend = friendList.getJSONObject(index)
                    jobs += async {
                        limiter.acquire()
                        try {
                            processFriend(friend, flag)
                        } catch (error: Exception) {
                            StructuredLog.printStackTrace(TAG, "处理好友异常", error)
                        } finally {
                            limiter.release()
                        }
                    }
                }
                jobs.awaitAll()
                val elapsed = System.currentTimeMillis() - start
                StructuredLog.record(
                    TAG,
                    "✓ ${sourceName}处理完成，耗时${TimeFormatter.formatDuration(elapsed)}，平均${TimeFormatter.formatDuration(elapsed / friendList.size)}/人",
                )
            } catch (error: CancellationException) {
                StructuredLog.record(TAG, "处理${sourceName}时协程被取消")
                throw error
            } catch (error: Exception) {
                StructuredLog.printStackTrace(TAG, "处理${sourceName}出错", error)
            }
        }

    private fun resolve(source: Any, flag: String): JsonArray? = when (source) {
        is JsonArray -> source
        is MutableList<*> -> {
            @Suppress("UNCHECKED_CAST")
            val ids = source as MutableList<String?>
            val response = fillUserRobFlag(ids, flag == "pk").toJsonObject()
            response.optJSONArray("friendRanking")
        }
        else -> {
            StructuredLog.error(TAG, "不支持的好友数据源类型: ${source::class.simpleName}")
            null
        }
    }

    private companion object {
        const val TAG = "ForestFriendBatchService"
        const val DEFAULT_CONCURRENCY = 60
    }
}
