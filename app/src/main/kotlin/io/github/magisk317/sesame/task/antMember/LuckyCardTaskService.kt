package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject

/** Claim-only flow for HAOYUNKA ordinary and rare card series. */
object LuckyCardTaskService {
    private const val TAG = "LuckyCardTaskService"

    suspend fun claimPendingCards() {
        val consult = runCatching { LuckyCardRpc.consult().toJsonObject() }
            .onFailure { StructuredLog.printStackTrace(TAG, "好运卡查询异常", it) }
            .getOrNull() ?: return
        if (!ResChecker.checkRes("好运卡查询失败:", consult)) return

        val result = consult.optJSONObject("result") ?: return
        val ordinaryCount = result.optJSONObject("directVoucher")
            ?.optJSONArray("tickets")?.length() ?: 0
        val rareCards = result.optJSONArray("pzThemeToday")?.length() ?: 0
        val ticketResult = result.optJSONObject("ticketResult")
        val available = ticketResult?.optInt("count", 0) ?: 0
        val openReward = result.optJSONObject("rewardRecord")?.optBoolean("openReward", false) == true

        StructuredLog.record(TAG, "好运卡状态: 普通卡库存=$ordinaryCount, 稀有卡配置=$rareCards, 可翻次数=$available")
        logTaskRewards()
        if (available <= 0 && !openReward) return

        // openLuckyCard consumes one ticket per call. Re-query after every
        // successful call because the response's count is not a reliable
        // remaining balance on all client versions.
        var remaining = available
        var openedCards = 0
        var attempts = 0
        var shouldOpen = openReward || remaining > 0
        while (shouldOpen && attempts++ < 50) {
            val opened = runCatching { LuckyCardRpc.open().toJsonObject() }
                .onFailure { StructuredLog.printStackTrace(TAG, "好运卡领取异常", it) }
                .getOrNull() ?: return
            if (!ResChecker.checkRes("好运卡领取失败:", opened)) return

            val openResult = opened.optJSONObject("result")
            val currentOpened = openResult?.optJSONArray("openCards")?.length() ?: 0
            openedCards += currentOpened
            StructuredLog.record(TAG, "好运卡翻开成功: 本次=$currentOpened, 累计=$openedCards")

            val refreshed = runCatching { LuckyCardRpc.consult().toJsonObject() }
                .onFailure { StructuredLog.printStackTrace(TAG, "好运卡刷新异常", it) }
                .getOrNull() ?: return
            if (!ResChecker.checkRes("好运卡刷新失败:", refreshed)) return
            val refreshedResult = refreshed.optJSONObject("result") ?: return
            remaining = refreshedResult.optJSONObject("ticketResult")?.optInt("count", 0) ?: 0
            shouldOpen = remaining > 0 ||
                (refreshedResult.optJSONObject("rewardRecord")?.optBoolean("openReward", false) == true)
        }
        if (attempts >= 50) {
            StructuredLog.error(TAG, "好运卡翻开达到单轮上限, 剩余次数=$remaining")
        } else {
            StructuredLog.record(TAG, "好运卡翻开领取完成: 共处理 $openedCards 张, 剩余次数=$remaining")
        }
    }

    private fun logTaskRewards() {
        val response = runCatching { LuckyCardRpc.queryTasks().toJsonObject() }
            .onFailure { StructuredLog.printStackTrace(TAG, "好运卡任务查询异常", it) }
            .getOrNull() ?: return
        val result = response.optJSONObject("data")?.optJSONObject("result")
            ?: response.optJSONObject("result")
            ?: response
        val tasks = result.optJSONArray("taskListResult") ?: return

        var ordinary = 0
        var rare = 0
        var pending = 0
        var completed = 0
        for (index in 0 until tasks.length()) {
            val task = tasks.optJSONObject(index) ?: continue
            val ext = task.optJSONObject("taskExtProps") ?: continue
            val reward = ext.optString("send_total").toIntOrNull() ?: 0
            when (ext.optString("card_level")) {
                "2" -> rare += reward
                else -> ordinary += reward
            }
            when (task.optString("taskStatus")) {
                "NOT_DONE", "NONE_SIGNUP" -> pending++
                "RECEIVE_SUCCESS" -> completed++
            }
        }
        StructuredLog.record(
            TAG,
            "好运卡任务奖励: 普通卡潜在+$ordinary, 稀有卡潜在+$rare, " +
                "未完成=$pending, 已发放=$completed (潜在奖励不等于可翻次数)",
        )
    }
}
