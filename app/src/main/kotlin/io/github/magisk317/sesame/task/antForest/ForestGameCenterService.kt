package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/** Opens forest game-center chests and reports the corresponding task progress. */
internal class ForestGameCenterService(
    private val queryList: () -> String,
    private val drawAward: (Int) -> String,
    private val reportTask: (Int) -> Unit,
) {
    fun run() {
        try {
            val response = queryList().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) {
                StructuredLog.error(TAG, "queryGameList 失败: ${response.optString("desc")}")
                return
            }

            val rights = response.optJSONObject("gameCenterDrawRights") ?: return
            val quotaPerTime = rights.optInt("quotaPerTime", DEFAULT_QUOTA_PER_TIME)
            if (quotaPerTime <= 0) {
                StructuredLog.error(TAG, "游戏中心返回无效的 quotaPerTime=$quotaPerTime")
                return
            }

            val state = DrawState(
                canUseCount = rights.optInt("quotaCanUse") / quotaPerTime,
                limitCount = rights.optInt("quotaLimit") / quotaPerTime,
                usedCount = rights.optInt("usedQuota") / quotaPerTime,
            )
            if (state.canUseCount > 0) openChests(state.canUseCount)

            val remainToTask = state.limitCount - state.usedCount
            if (remainToTask > 0) reportTask(remainToTask)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "doforestgame 流程异常", error)
        }
    }

    private fun openChests(count: Int) {
        StructuredLog.forest("森林🎁 一次性开启 $count 个宝箱...")
        val response = drawAward(count)
        if (!ResChecker.checkRes(TAG, response)) return

        val result = response.toJsonObject()
        val data = result.optJSONObject("resData") ?: result
        if (data.optString("desc") != "success") return

        val summary = summarizeAwards(data)
        if (summary.energy == 0 && summary.otherAwards.isEmpty()) return
        val message = buildString {
            append("[开宝箱] ")
            if (summary.energy > 0) append("获得能量: ${summary.energy}g")
            if (summary.otherAwards.isNotEmpty()) {
                if (summary.energy > 0) append(", ")
                append("其他: ${summary.otherAwards.joinToString("/")}")
            }
        }
        StructuredLog.forest(message)
    }

    internal fun summarizeAwards(response: JsonObject): AwardSummary {
        var energy = 0
        val otherAwards = mutableListOf<String>()
        val awards = response.optJSONArray("gameCenterDrawAwardList") ?: return AwardSummary(0, emptyList())
        for (index in awards.indices) {
            val award = awards.optJSONObject(index) ?: continue
            val type = award.optString("awardType")
            val name = award.optString("awardName")
            val count = award.optInt("awardCount")
            if (type == "ENERGY") energy += count else otherAwards += "${name}x$count"
        }
        return AwardSummary(energy, otherAwards)
    }

    internal data class DrawState(
        val canUseCount: Int,
        val limitCount: Int,
        val usedCount: Int,
    )

    internal data class AwardSummary(
        val energy: Int,
        val otherAwards: List<String>,
    )

    private companion object {
        const val TAG = "ForestGameCenterService"
        const val DEFAULT_QUOTA_PER_TIME = 100
    }
}
