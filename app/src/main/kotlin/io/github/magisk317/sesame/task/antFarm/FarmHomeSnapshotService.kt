package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Extracts the stable pieces of the farm home response used by orchestration. */
internal class FarmHomeSnapshotService {
    internal data class Snapshot(
        val farm: JsonObject,
        val rewardProductNum: String,
        val activityData: JsonObject?,
        val cuisineList: JsonArray?,
        val lotteryPlusInfo: JsonObject?,
        val hasGiftRecord: Boolean,
    )

    fun parse(response: JsonObject): Snapshot? {
        return try {
            val farm = response.getJSONObject("farmVO")
            Snapshot(
                farm = farm,
                rewardProductNum = response.getJSONObject("dynamicGlobalConfig").getString("rewardProductNum"),
                activityData = response.optJSONObject("activityData"),
                cuisineList = response.optJSONArray("cuisineList"),
                lotteryPlusInfo = response.optJSONObject("lotteryPlusInfo"),
                hasGiftRecord = farm.getJSONObject("subFarmVO").has("giftRecord"),
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun parse(rawResponse: String): Snapshot? = parse(rawResponse.toJsonObject())
}
