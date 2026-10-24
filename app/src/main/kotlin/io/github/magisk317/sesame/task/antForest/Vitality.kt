package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.entity.VitalityStore.ExchangeStatus
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonArray
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.util.maps.VitalityRewardsMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/** Forest vitality shop. SKU metadata is isolated by account to prevent cross-account leakage. */
object Vitality {
    private const val TAG = "Vitality"
    private val skuInfoByUser = ConcurrentHashMap<String, ConcurrentHashMap<String, JsonObject>>()

    private fun cache(): ConcurrentHashMap<String, JsonObject> =
        skuInfoByUser.computeIfAbsent(UserMap.currentUid.orEmpty()) { ConcurrentHashMap() }

    @JvmStatic
    fun ItemListByType(labelType: String): JsonArray? = try {
        ForestVitalityRpc.itemList(labelType).toJsonObject().takeIf {
            ResChecker.checkRes("$TAG 查询森林活力值商品列表失败:", it)
        }?.optJSONArray("itemInfoVOList")
    } catch (t: Throwable) {
        StructuredLog.record(TAG, "ItemListByType err")
        StructuredLog.printStackTrace(TAG, t)
        null
    }

    @JvmStatic
    fun ItemDetailBySpuId(spuId: String) {
        try {
            val root = ForestVitalityRpc.itemDetail(spuId).toJsonObject()
            if (ResChecker.checkRes("$TAG 查询森林活力值商品详情失败:", root)) {
                handleItemDetail(root.getJSONObject("spuItemInfoVO"))
            }
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "ItemDetailBySpuId err")
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    @JvmStatic
    fun initVitality(labelType: String) {
        try {
            val items = ItemListByType(labelType)
            if (items == null) {
                StructuredLog.error(TAG, "活力兑换🍃初始化失败！")
                return
            }
            for (index in 0 until items.length()) handleVitalityItem(items.getJSONObject(index))
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "initVitality err")
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    @JvmStatic
    fun handleVitalityExchange(skuId: String): Boolean {
        if (Status.hasFlagToday("forest::VitalityExchangeLimit::$skuId")) {
            StructuredLog.record(TAG, "活力兑换🍃[$skuId]今日已达上限，跳过兑换")
            return false
        }
        val cache = cache()
        if (cache.isEmpty()) initVitality("SC_ASSETS")
        val sku = cache[skuId] ?: run {
            StructuredLog.record(TAG, "活力兑换🍃找不到要兑换的权益！")
            return false
        }
        return try {
            val skuName = sku.getString("skuName")
            val statuses = sku.getJSONArray("itemStatusList")
            for (index in 0 until statuses.length()) {
                val statusName = statuses.getString(index)
                val status = runCatching { ExchangeStatus.valueOf(statusName) }.getOrNull() ?: continue
                StructuredLog.record(TAG, "活力兑换🍃[$skuName]停止:${status.nickName}")
                if (status == ExchangeStatus.REACH_LIMIT) {
                    Status.setFlagToday("forest::VitalityExchangeLimit::$skuId")
                    StructuredLog.forest("活力兑换🍃[$skuName]已达上限,停止兑换！")
                }
                return false
            }
            val spuId = sku.getString("spuId")
            if (VitalityExchange(spuId, skuId, skuName)) {
                if (skuName.contains("限时")) Status.setFlagToday("forest::VitalityExchangeLimit::$skuId")
                true
            } else {
                ItemDetailBySpuId(spuId)
                false
            }
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "VitalityExchange err")
            StructuredLog.printStackTrace(TAG, t)
            false
        }
    }

    @JvmStatic
    fun VitalityExchange(spuId: String, skuId: String, skuName: String): Boolean = try {
        if (!vitalityExchange(spuId, skuId)) return false
        Status.vitalityExchangeToday(skuId)
        StructuredLog.forest("活力兑换🍃[$skuName]#第${Status.getVitalityCount(skuId)}次")
        true
    } catch (t: Throwable) {
        StructuredLog.record(TAG, "VitalityExchange err:$spuId,$skuId")
        StructuredLog.printStackTrace(TAG, t)
        false
    }

    @JvmStatic
    fun findSkuInfoBySkuName(spuName: String): JsonObject? {
        val cache = cache()
        if (cache.isEmpty()) initVitality("SC_ASSETS")
        return runCatching { cache.values.firstOrNull { it.getString("skuName").contains(spuName) } }
            .onFailure {
                StructuredLog.record(TAG, "findSkuInfoBySkuName err:")
                StructuredLog.printStackTrace(TAG, it)
            }.getOrNull()
    }

    private fun vitalityExchange(spuId: String, skuId: String): Boolean = try {
        val root = ForestVitalityRpc.exchangeBenefit(spuId, skuId).toJsonObject()
        if (!root.optBoolean("success") && root.optString("resultCode") == "QUOTA_USER_NOT_ENOUGH") {
            StructuredLog.forest("活力兑换🍃[兑换次数已达上限]#${root.optString("resultDesc")}")
            Status.setFlagToday("forest::VitalityExchangeLimit::$skuId")
            return false
        }
        ResChecker.checkRes("$TAG 森林活力值兑换失败:", root)
    } catch (t: Throwable) {
        StructuredLog.record(TAG, "VitalityExchange err:$spuId,$skuId")
        StructuredLog.printStackTrace(TAG, t)
        false
    }

    private fun handleVitalityItem(item: JsonObject) {
        try {
            val spuId = item.optString("spuId")
            val models = item.getJSONArray("skuModelList")
            for (index in 0 until models.length()) {
                val model = models.getJSONObject(index)
                val skuId = model.getString("skuId")
                val name = model.getString("skuName")
                val price = model.getJSONObject("price").getInt("amount")
                val suffix = when {
                    listOf("能量雨", "敦煌", "保护罩", "海洋", "物种", "收能量", "隐身").any(name::contains) -> "\n每日限时兑1个"
                    name == "限时31天内使用31天长效双击卡" -> "\n每月限时兑1个，记得关，艹"
                    else -> ""
                }
                val cachedModel = if (model.has("spuId")) model else jsonObject(
                    *model.entries.map { it.key to it.value }.toTypedArray(),
                    "spuId" to spuId,
                )
                cache()[skuId] = cachedModel
                IdMapManager.getInstance(VitalityRewardsMap::class.java).add(skuId, "$name\n价格${price}🍃活力值$suffix")
            }
            IdMapManager.getInstance(VitalityRewardsMap::class.java).save(UserMap.currentUid)
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "handleVitalityItem err")
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    private fun handleItemDetail(item: JsonObject) {
        try {
            val spuId = item.getString("spuId")
            val models = item.getJSONArray("skuModelList")
            for (index in 0 until models.length()) {
                val model = models.getJSONObject(index)
                val skuId = model.getString("skuId")
                val cachedModel = if (model.has("spuId")) model else jsonObject(
                    *model.entries.map { it.key to it.value }.toTypedArray(),
                    "spuId" to spuId,
                )
                cache()[skuId] = cachedModel
                IdMapManager.getInstance(VitalityRewardsMap::class.java).add(skuId, model.getString("skuName"))
            }
            IdMapManager.getInstance(VitalityRewardsMap::class.java).save(UserMap.currentUid)
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "handleItemDetail err:")
            StructuredLog.printStackTrace(TAG, t)
        }
    }
}
