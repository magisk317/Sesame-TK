package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.ParadiseCoinBenefitIdMap
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Owns the farm game-center paradise-coin exchange workflow. */
internal object FarmParadiseCoinService {
    private const val TAG = "AntFarm"

    private enum class PropStatus(val displayName: String) {
        REACH_USER_HOLD_LIMIT("达到用户持有上限"),
        NO_ENOUGH_POINT("乐园币不足"),
        REACH_LIMIT("兑换达到上限"),
    }

    suspend fun run(selectedIds: Set<String?>) {
        try {
            val response = FarmGameMallRpc.getMallHome().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) {
                StructuredLog.error(TAG, "小鸡乐园币💸[未获取到可兑换权益]")
                return
            }

            val items = response.getJSONArray("mallItemSimpleList")
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val spuName = item.getString("spuName")
                val minPrice = item.getInt("minPrice")
                val controlTag = item.getString("controlTag")
                val spuId = item.getString("spuId")
                IdMapManager.getInstance(ParadiseCoinBenefitIdMap::class.java).add(
                    spuId,
                    "$spuName\n价格${minPrice}乐园币\n$controlTag",
                )

                val statuses = item.getJSONArray("itemStatusList")
                if (!Status.canParadiseCoinExchangeBenefitToday(spuId) ||
                    !selectedIds.contains(spuId) ||
                    isExchangeBlocked(statuses, spuId, spuName)
                ) continue

                var exchangedCount = 0
                while (exchange(spuId)) {
                    exchangedCount++
                    StructuredLog.farm("乐园币兑换💸#花费[${minPrice}乐园币]#第${exchangedCount}次兑换[$spuName]")
                    delay(3_000L)
                }
            }
            IdMapManager.getInstance(ParadiseCoinBenefitIdMap::class.java).save(UserMap.currentUid)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "paradiseCoinExchangeBenefit err:", t)
        }
    }

    private suspend fun exchange(spuId: String): Boolean {
        val response = FarmGameMallRpc.getMallItemDetail(spuId).toJsonObject()
        if (!ResChecker.checkRes(TAG, response)) return false
        val items = response.getJSONObject("mallItemDetail").getJSONArray("mallSubItemDetailList")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val skuId = item.getString("skuId")
            if (isExchangeBlocked(item.getJSONArray("itemStatusList"), spuId, item.getString("skuName"))) {
                return false
            }
            if (buy(spuId, skuId)) return true
        }
        return false
    }

    private fun buy(spuId: String, skuId: String): Boolean = runCatching {
        ResChecker.checkRes(TAG, FarmGameMallRpc.buyMallItem(spuId, skuId).toJsonObject())
    }.getOrElse {
        StructuredLog.printStackTrace(TAG, "exchangeBenefit err:", it)
        false
    }

    private fun isExchangeBlocked(statuses: JsonArray, spuId: String, name: String): Boolean {
        for (index in 0 until statuses.length()) {
            val statusName = statuses.getString(index)
            val status = runCatching { PropStatus.valueOf(statusName) }.getOrNull() ?: continue
            StructuredLog.record(TAG, "乐园兑换💸[$name]停止:${status.displayName}")
            if (status == PropStatus.REACH_LIMIT) {
                Status.setFlagToday("farm::paradiseCoinExchangeLimit::$spuId")
            }
            return true
        }
        return false
    }
}
