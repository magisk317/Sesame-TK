package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonObject

/** Handles friend-home side effects that are independent of energy collection. */
class ForestFriendSupportService(private val currentUserId: () -> String?) {
    private val tag = "ForestFriendSupportService"
    private fun parse(raw: String): JsonObject = raw.toJsonObject()

    fun collectGiftBox(userHome: JsonObject) {
        try {
            val giftBoxInfo = userHome.optJSONObject("giftBoxInfo")
            val userEnergy = userHome.optJSONObject("userEnergy")
            val userId = (userEnergy?.optString("userId") ?: currentUserId()) ?: return
            val giftBoxes = giftBoxInfo?.optJSONArray("giftBoxList") ?: return
            for (index in 0..<giftBoxes.length()) {
                try {
                    val giftBox = giftBoxes.getJSONObject(index)
                    val giftBoxId = giftBox.getString("giftBoxId")
                    val title = giftBox.getString("title")
                    val result = parse(ForestAnimalRpc.collectFriendGiftBox(giftBoxId, userId))
                    if (!ResChecker.checkRes(tag + "领取好友礼盒失败:", result)) {
                        StructuredLog.record(tag, result.getString("resultDesc"))
                        StructuredLog.record(result.toString())
                        continue
                    }
                    StructuredLog.forest(
                        "礼盒能量🎁[${UserMap.getMaskName(userId)}-$title]#${result.optInt("energy", 0)}g",
                    )
                } catch (t: Throwable) {
                    StructuredLog.printStackTrace(t)
                    break
                } finally {
                    GlobalThreadPools.sleepCompat(500L)
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(t)
        }
    }

    fun protectFriendEnergy(userHome: JsonObject) {
        try {
            val bubbles = userHome.optJSONArray("wateringBubbles") ?: return
            val userEnergy = userHome.optJSONObject("userEnergy")
            val userId = (userEnergy?.optString("userId") ?: currentUserId()) ?: return
            for (index in 0..<bubbles.length()) {
                try {
                    val bubble = bubbles.getJSONObject(index)
                    if (bubble.getString("bizType") != "fuhuo") continue
                    if (!bubble.getBoolean("canProtect")) continue
                    val result = parse(ForestAnimalRpc.protectBubble(userId))
                    if (!ResChecker.checkRes(tag + "复活能量失败:", result)) continue
                    Status.protectBubbleToday(currentUserId())
                    val vitality = result.optInt("vitalityAmount", 0)
                    val fullEnergy = bubble.optInt("fullEnergy", 0)
                    StructuredLog.forest(
                        "复活能量🚑[${UserMap.getMaskName(userId)}-${fullEnergy}g]" +
                            if (vitality > 0) "#活力值+$vitality" else "",
                    )
                    break
                } catch (t: Throwable) {
                    StructuredLog.printStackTrace(t)
                    break
                } finally {
                    GlobalThreadPools.sleepCompat(500)
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(t)
        }
    }
}
