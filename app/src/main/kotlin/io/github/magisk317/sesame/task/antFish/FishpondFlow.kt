package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal

internal data class FishpondSnapshot(
    val rodSumCount: Int,
    val currentFishWeight: BigDecimal,
)

internal data class FishpondCatch(
    val fishName: String,
    val fishWeight: String,
)

internal data class FishpondActivityState(
    val tomorrowRodStatus: String,
    val fishActivityStatus: String,
    val giftBoxStatus: String,
    val lotteryPlusItemId: String = "",
    val lotteryPlusTodayReceived: Boolean? = null,
) {
    fun signature(): String = "$tomorrowRodStatus:$fishActivityStatus:$giftBoxStatus:" +
        "$lotteryPlusItemId:$lotteryPlusTodayReceived"

    fun hasLotteryPlusAward(): Boolean = lotteryPlusItemId.isNotBlank() && lotteryPlusTodayReceived == false
}

internal enum class FishpondProgress {
    CONFIRMED,
    ROD_NOT_DECREASED,
    FISH_WEIGHT_NOT_INCREASED,
}

internal object FishpondFlow {
    fun snapshot(response: JsonObject): FishpondSnapshot? {
        if (!response.containsKey("rodSumCount")) return null
        val rodSumCount = response.optInt("rodSumCount", -1)
        if (rodSumCount < 0) return null
        val currentFishWeight = response
            .optJSONObject("roundInfo")
            ?.optJSONObject("fishAssetInfo")
            ?.optString("currentFishWeight")
            ?.toBigDecimalOrNull()
            ?: return null
        return FishpondSnapshot(rodSumCount, currentFishWeight)
    }

    fun caughtFish(response: JsonObject): FishpondCatch? {
        val result = response.optJSONObject("angleResultInfo") ?: return null
        return FishpondCatch(
            fishName = result.optString("fishName"),
            fishWeight = result.optString("fishWeight"),
        )
    }

    fun activityState(response: JsonObject): FishpondActivityState {
        val lotteryPlus = response.optJSONObject("lotteryPlusInfo")
        val itemId = lotteryPlus?.optString("itemId").orEmpty()
        val todayItem = lotteryPlus
            ?.optJSONObject("userSevenDaysGiftsItem")
            ?.optJSONArray("userEverydayGiftItems")
            ?.asSequence()
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.optString("itemId") == itemId }
        val received = todayItem
            ?.takeIf { it.containsKey("received") }
            ?.get("received")
            ?.toString()
            ?.toBooleanStrictOrNull()
        return FishpondActivityState(
            tomorrowRodStatus = response.optJSONObject("tomorrowRod")?.optString("status").orEmpty(),
            fishActivityStatus = response.optJSONObject("fishActivity")?.optString("status").orEmpty(),
            giftBoxStatus = response.optJSONObject("giftBox")?.optString("status").orEmpty(),
            lotteryPlusItemId = itemId,
            lotteryPlusTodayReceived = received,
        )
    }

    fun requiresRodPositioning(response: JsonObject): Boolean =
        response["needRodPositioning"]?.toString()?.toBooleanStrictOrNull() == true

    fun progress(before: FishpondSnapshot, after: FishpondSnapshot): FishpondProgress = when {
        after.rodSumCount >= before.rodSumCount -> FishpondProgress.ROD_NOT_DECREASED
        after.currentFishWeight <= before.currentFishWeight -> FishpondProgress.FISH_WEIGHT_NOT_INCREASED
        else -> FishpondProgress.CONFIRMED
    }
}
