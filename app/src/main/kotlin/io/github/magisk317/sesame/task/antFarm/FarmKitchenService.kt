package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Owns the farm kitchen, special-food, and surprise-gift RPC workflows. */
class FarmKitchenService(
    private val foodStock: () -> Int,
    private val foodStockLimit: () -> Int,
    private val ensureAnimalAtHome: () -> Boolean = { true },
) {
    private val tag = "FarmKitchenService"
    private fun parse(raw: String): JsonObject = raw.toJsonObject()

    /**
     * enterKitchen 带一次召回重试：刚召回小鸡后，服务端可能短时间仍返回
     * resultCode 312（"先去把小鸡找回来吧~"），等守卫再次确认在家后重试一次。
     */
    private fun enterKitchenWithRecallRetry(userId: String?): JsonObject {
        var result = parse(FarmKitchenRpc.enterKitchen(userId))
        if (!ResChecker.checkRes(tag, result) &&
            result.optString("resultCode") == "312" &&
            ensureAnimalAtHome()
        ) {
            result = parse(FarmKitchenRpc.enterKitchen(userId))
        }
        return result
    }

    fun collectDailyFoodMaterial() {
        try {
            if (!ensureAnimalAtHome()) return
            val userId = UserMap.currentUid
            var result = enterKitchenWithRecallRetry(userId)
            if (!ResChecker.checkRes(tag, result)) return
            val canCollect = result.getBoolean("canCollectDailyFoodMaterial")
            val amount = result.getInt("dailyFoodMaterialAmount")
            if (result.has("orchardFoodMaterialStatus") &&
                result.getJSONObject("orchardFoodMaterialStatus").optString("foodStatus") == "FINISHED"
            ) {
                result = parse(FarmKitchenRpc.farmFoodMaterialCollect())
                if (ResChecker.checkRes(tag, result)) {
                    StructuredLog.farm("小鸡厨房👨🏻‍🍳[领取农场食材]#${result.getInt("foodMaterialAddCount")}g")
                }
            }
            if (canCollect) {
                result = parse(FarmKitchenRpc.collectDailyFoodMaterial(amount))
                if (ResChecker.checkRes(tag, result)) {
                    StructuredLog.farm("小鸡厨房👨🏻‍🍳[领取今日食材]#${amount}g")
                }
            }
            val garbageAmount = result.optInt("garbageAmount", 0)
            if (garbageAmount > 0) {
                result = parse(FarmKitchenRpc.collectKitchenGarbage())
                if (ResChecker.checkRes(tag, result)) {
                    StructuredLog.farm("小鸡厨房👨🏻‍🍳[领取肥料]#${result.getInt("recievedKitchenGarbageAmount")}g")
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "收集每日食材", t)
        }
    }

    fun collectDailyLimitedFoodMaterial() {
        try {
            var result = parse(FarmKitchenRpc.queryFoodMaterialPack())
            if (!ResChecker.checkRes(tag, result) || !result.getBoolean("canCollectDailyLimitedFoodMaterial")) return
            val amount = result.getInt("dailyLimitedFoodMaterialAmount")
            result = parse(FarmKitchenRpc.collectDailyLimitedFoodMaterial(amount))
            if (ResChecker.checkRes(tag, result)) {
                StructuredLog.farm("小鸡厨房👨🏻‍🍳[领取爱心食材店食材]#${amount}g")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "领取爱心食材店食材", t)
        }
    }

    suspend fun cook() {
        try {
            if (!ensureAnimalAtHome()) return
            val userId = UserMap.currentUid
            var result = enterKitchenWithRecallRetry(userId)
            StructuredLog.record(tag, "cook userid :$userId")
            if (!ResChecker.checkRes(tag, result)) return
            val allowed = result.getInt("cookTimesAllowed")
            for (index in 0..<allowed) {
                result = parse(FarmKitchenRpc.cook(userId, "VILLA"))
                if (ResChecker.checkRes(tag, result)) {
                    StructuredLog.farm("小鸡厨房👨🏻‍🍳[${result.getJSONObject("cuisineVO").getString("name")}]制作成功")
                } else {
                    StructuredLog.record(tag, "小鸡厨房制作$result")
                }
                delay(RandomUtil.delay().toLong())
            }
        } catch (e: CancellationException) {
            StructuredLog.record(tag, "cook 协程被取消")
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "cook err:", t)
        }
    }

    fun useSpecialFood(cuisineList: JsonArray, maxUsage: Int = cuisineList.length()) {
        try {
            var totalUsed = 0
            val counts = IntArray(cuisineList.length()) { index -> cuisineList.getJSONObject(index).optInt("count", 0) }
            StructuredLog.record(tag, "美食总量为:${counts.sum()}")
            while (totalUsed < maxUsage) {
                var usedInRound = false
                for (index in 0..<cuisineList.length()) {
                    if (totalUsed >= maxUsage || counts[index] <= 0) continue
                    val cuisine = cuisineList.getJSONObject(index)
                    val result = parse(FarmKitchenRpc.useFarmFood(cuisine.getString("cookbookId"), cuisine.getString("cuisineId")))
                    if (ResChecker.checkRes(tag, result)) {
                        val delta = result.optJSONObject("foodEffect")?.optDouble("deltaProduce", 0.0) ?: 0.0
                        StructuredLog.farm("使用美食🍱[${cuisine.getString("name")}]#加速${delta}颗爱心鸡蛋")
                        counts[index]--
                        totalUsed++
                        usedInRound = true
                        CoroutineUtils.sleepCompat(RandomUtil.nextInt(800, 1100).toLong())
                    } else {
                        counts[index] = 0
                    }
                }
                if (!usedInRound) break
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "useSpecialFood err:", t)
        }
    }

    fun drawLotteryPlus(lotteryInfo: JsonObject) {
        try {
            if (!lotteryInfo.has("userSevenDaysGiftsItem")) return
            val itemId = lotteryInfo.getString("itemId")
            val gifts = lotteryInfo.getJSONObject("userSevenDaysGiftsItem").getJSONArray("userEverydayGiftItems")
            for (index in 0..<gifts.length()) {
                val gift = gifts.getJSONObject(index)
                if (gift.getString("itemId") != itemId) continue
                if (!gift.getBoolean("received")) {
                    val desc = gift.getString("singleDesc")
                    val amount = gift.getInt("awardCount")
                    if (desc.contains("饲料") && amount + foodStock() > foodStockLimit()) {
                        StructuredLog.record(tag, "暂停领取[$amount]g饲料，上限为[${foodStockLimit()}g]")
                        return
                    }
                    val result = parse(FarmDrawRpc.drawLotteryPlus())
                    if (result.getString("memo") == "SUCCESS") StructuredLog.farm("惊喜礼包🎁[$desc*$amount]")
                }
                return
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "drawLotteryPlus err:", t)
        }
    }
}
