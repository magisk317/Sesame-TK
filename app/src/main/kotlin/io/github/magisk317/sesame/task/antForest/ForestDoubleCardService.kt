package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optLong
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonObject

/** Selects, renews and consumes the configured forest double-click card. */
internal class ForestDoubleCardService(
    private val choice: () -> Int,
    private val constantRenewal: () -> Boolean,
    private val canUseToday: () -> Boolean,
    private val queryBag: () -> JsonObject?,
    private val exchange: () -> Boolean,
    private val isWithinUseTime: () -> Boolean,
    private val useProp: (JsonObject) -> Boolean,
    private val onActivated: () -> Unit,
) {
    fun use(initialBag: JsonObject?) {
        try {
            if (!canUseToday()) {
                StructuredLog.record(TAG, "双击卡使用条件检查: 今日次数已达上限")
                return
            }
            var bag = initialBag
            if (bag?.optBoolean("success") != true) {
                StructuredLog.record(TAG, "背包数据异常，无法使用双击卡$bag")
                return
            }
            var props = doubleClickProps(bag)
            if (props.isEmpty() && constantRenewal() && exchange()) {
                bag = queryBag()
                props = doubleClickProps(bag)
            }
            if (props.isEmpty()) {
                StructuredLog.record(TAG, "根据设置，背包中没有需要使用的双击卡")
                return
            }
            props.sortBy { it.optLong("recentExpireTime", Long.MAX_VALUE) }
            StructuredLog.record(TAG, "扫描到${props.size}种双击卡，将按过期顺序尝试使用...")
            for (prop in props) {
                val type = prop.optString("propType")
                val name = prop.optJSONObject("propConfigVO")?.optString("propName") ?: ""
                if (type == "ENERGY_DOUBLE_CLICK" && !isWithinUseTime()) {
                    StructuredLog.record(TAG, "跳过[$name]，当前不在指定使用时间段内")
                    continue
                }
                if (type == "LIMIT_TIME_ENERGY_DOUBLE_CLICK" && choice() == 2) {
                    val expire = prop.optLong("recentExpireTime", 0)
                    if (expire > 0 && expire - System.currentTimeMillis() > 2 * 24 * 60 * 60 * 1000L) {
                        StructuredLog.record(TAG, "跳过[$name]，该卡有效期剩余超过2天 (仅限时模式)")
                        continue
                    }
                }
                StructuredLog.record(TAG, "尝试使用卡: $name")
                if (useProp(prop)) {
                    onActivated()
                    return
                }
            }
            StructuredLog.record(TAG, "所有可用的双击卡均不满足使用条件")
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "useDoubleCard err", error)
        }
    }

    private fun doubleClickProps(bag: JsonObject?): MutableList<JsonObject> {
        val result = mutableListOf<JsonObject>()
        val list = bag?.optJSONArray("forestPropVOList") ?: return result
        for (index in list.indices) {
            val prop = list.getJSONObject(index)
            if (prop.optString("propGroup") != "doubleClick") continue
            when (choice()) {
                1 -> result += prop
                2 -> if (prop.optString("propType").let { it.contains("LIMIT_TIME") || it.contains("DAYS") }) {
                    result += prop
                }
            }
        }
        return result
    }

    private companion object {
        const val TAG = "ForestDoubleCardService"
    }
}
