package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optLong
import kotlinx.serialization.json.JsonObject

/** Uses a bubble-boost prop only when the user's forest has immature bubbles. */
internal class ForestBubbleBoostService(
    private val queryHome: () -> JsonObject?,
    private val findProp: (JsonObject?, String) -> JsonObject?,
    private val queryBag: () -> JsonObject?,
    private val useProp: (JsonObject) -> Boolean,
    private val collectSelf: (String) -> Unit,
) {
    fun use(initialBag: JsonObject?) {
        try {
            val home = queryHome()
            if (home == null) {
                StructuredLog.record(TAG, "无法获取自己主页信息，跳过使用加速卡")
                return
            }
            val serverTime = home.optLong("now", System.currentTimeMillis())
            if (!hasWaitingBubbles(home, serverTime)) {
                StructuredLog.record(TAG, "自己当前没有未来才会成熟的能量球，不使用加速卡")
                return
            }

            var prop = findProp(initialBag, LIMITED_PROP_TYPE)
            if (prop == null) {
                prop = findProp(queryBag(), LIMITED_PROP_TYPE)
                    ?: findProp(initialBag, NORMAL_PROP_TYPE)
            }
            if (prop == null) {
                StructuredLog.record(TAG, "背包中无可用加速卡")
                return
            }

            val name = prop.getJSONObject("propConfigVO").getString("propName")
            if (useProp(prop)) {
                StructuredLog.forest("使用加速卡🌪[$name]")
                collectSelf("加速卡")
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "useBubbleBoostCard err", error)
        }
    }

    internal fun hasWaitingBubbles(home: JsonObject, serverTime: Long): Boolean {
        val bubbles = home.optJSONArray("bubbles") ?: return false
        for (index in bubbles.indices) {
            val bubble = bubbles.getJSONObject(index)
            if (bubble.getInt("fullEnergy") <= 0) continue
            val produceTime = bubble.optLong("produceTime", 0L)
            if (produceTime > serverTime) return true
        }
        return false
    }

    private companion object {
        const val TAG = "ForestBubbleBoostService"
        const val LIMITED_PROP_TYPE = "LIMIT_TIME_ENERGY_BUBBLE_BOOST"
        const val NORMAL_PROP_TYPE = "BUBBLE_BOOST"
    }
}
