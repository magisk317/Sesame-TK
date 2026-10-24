package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

/** Claims food gifts exposed by the farm activity payload. */
class FarmActivityRewardService {
    private val tag = "FarmActivityRewardService"

    fun gifts(activityData: JsonObject?): List<JsonObject> {
        if (activityData == null) return emptyList()
        return buildList {
            for (key in activityData.keys) {
                if (!key.contains("Gifts")) continue
                val gifts = activityData.optJSONArray(key) ?: continue
                for (index in gifts.indices) {
                    val gift = gifts.getJSONObject(index)
                    if (gift.has("foodType") && gift.has("giftIndex")) add(gift)
                }
            }
        }
    }

    fun claimAll(activityData: JsonObject?) {
        gifts(activityData).forEach(::claim)
    }

    fun claim(gift: JsonObject?) {
        if (gift == null) return
        try {
            val result = FarmDrawRpc.clickForGiftV2(
                gift.getString("foodType"),
                gift.getInt("giftIndex"),
            ).toJsonObject()
            if (ResChecker.checkRes(tag, result)) {
                StructuredLog.farm("领取活动食物成功,已领取${result.optInt("foodCount")}")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "clickForGiftV2 err", t)
        }
    }
}
