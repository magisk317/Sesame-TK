package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getDouble
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

/** Owns harvesting the farm's benevolence-score produce. */
internal class FarmProduceService(
    private val updateRemainingScore: (Double) -> Unit,
) {
    internal data class Harvest(val amount: Double, val remaining: Double)

    internal fun parse(response: JsonObject): Harvest? {
        if (!ResChecker.checkRes(TAG, response)) return null
        return runCatching {
            Harvest(
                amount = response.getDouble("harvestBenevolenceScore"),
                remaining = response.getDouble("finalBenevolenceScore"),
            )
        }.getOrNull()
    }

    internal fun harvest(farmId: String?) {
        try {
            val response = FarmCoreRpc.harvestProduce(farmId).toJsonObject()
            val harvest = parse(response)
            if (harvest != null) {
                updateRemainingScore(harvest.remaining)
                StructuredLog.farm(
                    "收取鸡蛋🥚[${harvest.amount}颗]#剩余${harvest.remaining}颗",
                )
            } else {
                StructuredLog.record(TAG, response.optString("memo"))
                StructuredLog.record(response.toString())
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "harvestProduce err:", error)
        }
    }

    private companion object {
        const val TAG = "FarmProduceService"
    }
}
