package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optLong
import kotlinx.serialization.json.JsonObject

/** Extracts collectable and waiting bubbles from a forest home response. */
class ForestBubbleService(
    private val isTeam: (JsonObject) -> Boolean,
    private val selfId: () -> String?,
    private val userName: (String?, JsonObject?, String?) -> String?,
    private val shouldCollectSelfBubble: (Int, Boolean) -> Boolean,
) {
    fun extract(
        home: JsonObject,
        serverTime: Long,
        availableBubbles: MutableList<Long>,
        userId: String?,
    ) {
        val bubbles = if (isTeam(home)) {
            home.optJSONObject("teamHomeResult")
                ?.optJSONObject("mainMember")
                ?.optJSONArray("bubbles")
        } else {
            home.optJSONArray("bubbles")
        } ?: jsonArray()
        if (bubbles.isEmpty()) return

        val name = userName(userId, home, null)
        val isSelf = selfId() == userId
        val protectionLog = if (isSelf) "" else protectionLog(home, serverTime)
        var waitingCount = 0

        for (index in bubbles.indices) {
            val bubble = bubbles.getJSONObject(index)
            val bubbleId = bubble.getLong("id")
            val status = AntForest.CollectStatus.valueOf(bubble.getString("collectStatus"))
            val amount = bubble.getInt("fullEnergy")
            when (status) {
                AntForest.CollectStatus.AVAILABLE -> {
                    if (!isSelf || shouldCollectSelfBubble(
                            amount,
                            bubble.optBoolean("canBeRobbedAgain", false),
                        )
                    ) {
                        availableBubbles += bubbleId
                    }
                }

                AntForest.CollectStatus.WAITING -> {
                    if (amount <= 0) {
                        StructuredLog.record(TAG, "跳过数量为[$bubbleId]的等待能量球的蹲点任务")
                        continue
                    }
                    if (isSelf && !shouldCollectSelfBubble(
                            amount,
                            bubble.optBoolean("canBeRobbedAgain", true),
                        )
                    ) continue

                    val produceTime = bubble.optLong("produceTime", 0L)
                    if (produceTime <= serverTime) continue
                    if (!isSelf && ForestUtil.shouldSkipWaitingDueToProtection(home, produceTime)) {
                        val protectionEnd = ForestUtil.getProtectionEndTime(home)
                        StructuredLog.record(
                            TAG,
                            "⏭️ 跳过好友蹲点[$name]球[$bubbleId]：保护罩覆盖整个成熟期" +
                                "(保护还剩${TimeFormatter.formatDuration(protectionEnd - serverTime)}，" +
                                "能量${TimeUtil.getCommonDate(produceTime)}成熟)",
                        )
                        continue
                    }

                    waitingCount++
                    EnergyWaitingManager.addWaitingTask(
                        userId = userId ?: "",
                        userName = name ?: "未知用户",
                        bubbleId = bubbleId,
                        produceTime = produceTime,
                        fromTag = "蹲点收取",
                    )
                    StructuredLog.record(
                        TAG,
                        "添加蹲点: [$name] 能量球[$bubbleId] 将在[${TimeUtil.getCommonDate(produceTime)}]成熟$protectionLog",
                    )
                }

                else -> Unit
            }
        }

        if (availableBubbles.isNotEmpty() || waitingCount > 0) {
            StructuredLog.record(TAG, "[$name] 可收集能量球: ${availableBubbles.size}个")
            if (waitingCount > 0) {
                StructuredLog.record(TAG, "[$name] 等待成熟能量球: ${waitingCount}个")
            }
        }
    }

    private fun protectionLog(home: JsonObject, serverTime: Long): String {
        val shieldEnd = ForestUtil.getShieldEndTime(home)
        val bombEnd = ForestUtil.getBombCardEndTime(home)
        return buildString {
            if (shieldEnd > serverTime) {
                append(" 保护罩剩余: ")
                append(TimeFormatter.formatTimeDifference(shieldEnd - serverTime))
                append(". ")
            }
            if (bombEnd > serverTime) {
                append(" 炸弹卡剩余: ")
                append(TimeFormatter.formatTimeDifference(bombEnd - serverTime))
                append('.')
            }
        }
    }

    private companion object {
        const val TAG = "ForestBubbleService"
    }
}
