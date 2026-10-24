package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getDouble
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.length
import kotlinx.serialization.json.JsonObject

/** Parses the farm animal sync response without mutating AntFarm state. */
internal class FarmAnimalStateService {
    internal data class ParsedState(
        val finalScore: Double?,
        val serverUseBigEaterTool: Boolean,
        val foodStock: Int?,
        val foodInTroughLimit: Int,
        val foodStockLimit: Int,
        val ownerFarmId: String,
        val countdown: Long,
        val benevolenceScore: Double,
        val rewardList: Array<AntFarm.RewardFriend>?,
        val hasFence: Boolean,
        val fenceCountDown: Int,
        val animals: Array<AntFarm.Animal>,
        val ownerAnimal: AntFarm.Animal,
    )

    fun parse(response: JsonObject): ParsedState? {
        return try {
            if (!response.has("subFarmVO")) return null
            val finalScore = response.optJSONObject("emotionInfo")?.getDouble("finalScore")
            val subFarm = response.getJSONObject("subFarmVO")
            collectManure(subFarm)

            val ownerFarmId = subFarm.getString("farmId")
            val foodStock = if (subFarm.has("foodStock")) subFarm.getInt("foodStock") else null
            val foodInTroughLimit = when {
                subFarm.has("foodInTroughLimit") -> subFarm.getInt("foodInTroughLimit")
                subFarm.has("foodStockLimit") -> subFarm.getInt("foodStockLimit")
                response.has("foodStockLimit") -> response.getInt("foodStockLimit")
                else -> 180
            }
            val foodStockLimit = when {
                subFarm.has("foodStockLimit") -> subFarm.getInt("foodStockLimit")
                response.has("foodStockLimit") -> response.getInt("foodStockLimit")
                else -> 1800
            }
            val farmProduce = subFarm.getJSONObject("farmProduce")
            val rewards = subFarm.optJSONArray("rewardList")?.let { list ->
                Array(list.length()) { index ->
                    val item = list.getJSONObject(index)
                    AntFarm.RewardFriend(
                        consistencyKey = item.getString("consistencyKey"),
                        friendId = item.getString("friendId"),
                        time = item.getString("time"),
                    )
                }
            }
            val fence = response.optJSONObject("buffInfoVO")?.let { info ->
                if (info.optString("buffType") == "FENCE") {
                    info.optBoolean("hasBuffEffect", false) to info.optInt("buffCountDown", 0)
                } else false to 0
            } ?: (false to 0)

            val animalList = subFarm.getJSONArray("animals").let { list ->
                Array(list.length()) { index ->
                    AntFarm.Animal.fromJson(list.getJSONObject(index))
                }
            }
            val ownerAnimal = animalList.firstOrNull { it.masterFarmId == ownerFarmId }
                ?: AntFarm.Animal()

            ParsedState(
                finalScore = finalScore,
                serverUseBigEaterTool = subFarm.optBoolean("useBigEaterTool", false),
                foodStock = foodStock,
                foodInTroughLimit = foodInTroughLimit,
                foodStockLimit = foodStockLimit,
                ownerFarmId = ownerFarmId,
                countdown = subFarm.getLong("countdown"),
                benevolenceScore = farmProduce.getDouble("benevolenceScore"),
                rewardList = rewards,
                hasFence = fence.first,
                fenceCountDown = fence.second,
                animals = animalList,
                ownerAnimal = ownerAnimal,
            )
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "parseSyncAnimalStatusResponse err:", error)
            null
        }
    }

    private fun collectManure(subFarm: JsonObject) {
        val pots = subFarm.optJSONObject("manureVO")?.optJSONArray("manurePotList") ?: return
        for (index in pots.indices) {
            val pot = pots.getJSONObject(index)
            if (pot.getInt("manurePotNum") < 100) continue
            val result = FarmCoreRpc.collectManurePot(pot.getString("manurePotNO")).toJsonObject()
            if (ResChecker.checkRes(TAG, result)) {
                StructuredLog.farm("打扫鸡屎🧹[${result.getInt("collectManurePotNum")}g]${index + 1}次")
            } else {
                StructuredLog.record(TAG, "打扫鸡屎失败: 第${index + 1}次$result")
            }
        }
    }

    private companion object {
        const val TAG = "FarmAnimalStateService"
    }
}
