package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Opens farm game-center chests and reports the remaining draw task progress. */
internal class FarmGameCenterService(
    private val queryList: () -> JsonObject,
    private val drawAward: () -> JsonObject,
    private val reportTask: (Int) -> Unit,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun run() {
        try {
            val response = queryList()
            if (!response.optBoolean("success")) {
                StructuredLog.record(TAG, "queryGameList 失败: $response")
                return
            }
            val rights = response.optJSONObject("gameCenterDrawRights") ?: return
            var quotaCanUse = rights.optInt("quotaCanUse")
            while (quotaCanUse > 0) {
                val result = drawAward()
                if (!result.optBoolean("success")) {
                    StructuredLog.record(TAG, "开启宝箱失败: ${result.optString("desc")}")
                    break
                }
                quotaCanUse = result.optJSONObject("gameCenterDrawRights")
                    ?.optInt("quotaCanUse") ?: (quotaCanUse - 1)
                val awards = result.optJSONArray("gameCenterDrawAwardList")
                    ?.let { list ->
                        buildList {
                            for (index in list.indices) {
                                val item = list.getJSONObject(index)
                                add("${item.optString("awardName")}*${item.optInt("awardCount")}")
                            }
                        }
                    }
                    .orEmpty()
                StructuredLog.farm("庄园小鸡🎁[获得奖品: ${awards.joinToString(",")}]")
                pause(DRAW_DELAY_MS)
            }

            val remaining = rights.optInt("quotaLimit") - rights.optInt("usedQuota")
            if (remaining > 0 && quotaCanUse == 0) reportTask(remaining)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "drawGameCenterAward 流程异常", error)
        }
    }

    internal companion object {
        private const val TAG = "FarmGameCenterService"
        const val DRAW_DELAY_MS = 3_000L
    }
}
