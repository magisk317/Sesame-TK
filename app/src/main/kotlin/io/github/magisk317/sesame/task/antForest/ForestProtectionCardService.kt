package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optLong
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonObject

/** Selects, replenishes and consumes forest shield or bomb cards. */
internal class ForestProtectionCardService(
    private val findProp: (JsonObject?, String) -> JsonObject?,
    private val queryBag: () -> JsonObject?,
    private val queryHome: () -> JsonObject?,
    private val useProp: (JsonObject) -> Boolean,
    private val exchangeShield: () -> Boolean,
    private val exchangeBomb: () -> Boolean,
    private val updateHome: () -> Unit,
) {
    fun useShield(bag: JsonObject?, constantEnabled: Boolean) {
        try {
            val shields = collectShields(bag)
            if (shields.isEmpty() && constantEnabled && exchangeShield()) {
                collectShields(queryHome(), "LIMIT_TIME_ENERGY_SHIELD", shields)
            }
            shields.sortBy { it.optLong("recentExpireTime", Long.MAX_VALUE) }
            for (shield in shields) {
                val name = shield.optJSONObject("propConfigVO")?.optString("propName") ?: ""
                StructuredLog.record(TAG, "尝试使用保护罩: $name")
                if (useProp(shield)) {
                    StructuredLog.record(TAG, "保护罩使用成功: $name")
                    return
                }
            }
            StructuredLog.record(TAG, "背包中未找到或无法使用任何可用保护罩")
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "useShieldCard err", error)
        }
    }

    fun useBomb(bag: JsonObject?) {
        try {
            StructuredLog.record(TAG, "尝试使用炸弹卡...")
            var bomb = findProp(bag, "ENERGY_BOMB_CARD")
            if (bomb == null) {
                StructuredLog.record(TAG, "背包中没有炸弹卡，尝试兑换...")
                if (exchangeBomb()) bomb = findProp(queryBag(), "ENERGY_BOMB_CARD")
            }
            if (bomb != null) {
                StructuredLog.record(TAG, "找到炸弹卡，准备使用: $bomb")
                if (useProp(bomb)) {
                    updateHome()
                    StructuredLog.record(TAG, "森林道具🌳 能量炸弹卡使用成功，已刷新到期时间")
                }
            } else {
                StructuredLog.record(TAG, "背包中未找到任何可用炸弹卡。")
                updateHome()
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "useEnergyBombCard err", error)
        }
    }

    private fun collectShields(
        bag: JsonObject?,
        onlyType: String? = null,
        output: MutableList<JsonObject> = mutableListOf(),
    ): MutableList<JsonObject> {
        val list = bag?.optJSONArray("forestPropVOList") ?: return output
        val types = setOf(
            "LIMIT_TIME_ENERGY_SHIELD_TREE",
            "LIMIT_TIME_ENERGY_SHIELD",
            "ENERGY_SHIELD_YONGJIU",
            "RUIHE_ENERGY_SHIELD",
            "PK_SEASON1_ENERGY_SHIELD_TREE",
            "ENERGY_SHIELD",
        )
        for (index in list.indices) {
            val prop = list.getJSONObject(index)
            val type = prop.optJSONObject("propConfigVO")?.optString("propType") ?: continue
            if ((onlyType == null && type in types) || type == onlyType) output += prop
        }
        return output
    }

    private companion object {
        const val TAG = "ForestProtectionCardService"
    }
}
