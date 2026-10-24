package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.Toast
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Consumes reward entries exposed by the forest home response. */
class ForestHomeRewardService(
    private val currentUserId: () -> String?,
    private val nextHomePage: () -> JsonObject?,
) {
    private val tag = "ForestHomeRewardService"
    private fun parse(raw: String): JsonObject = raw.toJsonObject()

    fun collectWateringBubbles(home: JsonObject?) {
        processPages(home, "wateringBubbles") { bubbles -> collectBubbles(bubbles) }
    }

    fun collectGivenProps(home: JsonObject?) {
        processPages(home, "givenProps") { props -> collectProps(props) }
    }

    private fun processPages(initial: JsonObject?, key: String, consume: (JsonArray) -> Unit) {
        var current = initial
        while (true) {
            val values = current?.optJSONArray(key)
            if (values == null || values.length() == 0) return
            consume(values)
            if (values.length() < 20) return
            GlobalThreadPools.sleepCompat(2000L)
            current = nextHomePage()
        }
    }

    private fun collectBubbles(bubbles: JsonArray) {
        for (index in 0..<bubbles.length()) {
            try {
                val bubble = bubbles.getJSONObject(index)
                when (val type = bubble.getString("bizType")) {
                    "jiaoshui" -> collectWater(bubble)
                    "fuhuo" -> collectRebornEnergy()
                    "baohuhuizeng" -> collectReturnEnergy(bubble)
                    else -> {
                        StructuredLog.record(tag, "未知bizType: $type")
                        continue
                    }
                }
                GlobalThreadPools.sleepCompat(500L)
            } catch (e: RuntimeException) {
                StructuredLog.record(tag, "浇水金球处理异常: ${e.message}")
            }
        }
    }

    private fun collectWater(bubble: JsonObject) {
        try {
            val userId = currentUserId() ?: return
            val response = ForestCoreRpc.collectEnergy("jiaoshui", userId, bubble.getLong("id"))
            processResult(response, "收取金球🍯浇水")
        } catch (e: RuntimeException) {
            StructuredLog.record(tag, "收取浇水JSON解析错误: ${e.message}")
        }
    }

    private fun collectRebornEnergy() {
        try {
            processResult(ForestCoreRpc.collectRebornEnergy(), "收取金球🍯复活")
        } catch (e: RuntimeException) {
            StructuredLog.record(tag, "收取金球运行时异常: ${e.message}")
        }
    }

    private fun collectReturnEnergy(bubble: JsonObject) {
        try {
            val userId = currentUserId() ?: return
            val friendId = bubble.getString("userId")
            val response = ForestCoreRpc.collectEnergy("baohuhuizeng", userId, bubble.getLong("id"))
            processResult(response, "收取金球🍯[${UserMap.getMaskName(friendId)}]复活回赠")
        } catch (e: RuntimeException) {
            StructuredLog.record(tag, "收取金球回赠JSON解析错误: ${e.message}")
        }
    }

    private fun processResult(response: String, successMessage: String) {
        try {
            val result = parse(response)
            if (!ResChecker.checkRes(tag + "收集能量失败:", result)) {
                StructuredLog.record(tag, "${successMessage}失败:${result.getString("resultDesc")}")
                StructuredLog.record(response)
                return
            }
            val bubbles = result.getJSONArray("bubbles")
            if (bubbles.length() == 0) {
                StructuredLog.record(tag, "${successMessage}失败: 未找到金球信息")
                return
            }
            val collected = bubbles.getJSONObject(0).getInt("collectedEnergy")
            if (collected > 0) {
                val message = "$successMessage[${collected}g]"
                StructuredLog.forest(message)
                Toast.show(message)
            } else {
                StructuredLog.record("${successMessage}失败")
            }
        } catch (e: RuntimeException) {
            StructuredLog.record(tag, "JSON解析错误: ${e.message}")
        } catch (e: Exception) {
            StructuredLog.record(tag, "处理收能量结果错误: ${e.message}")
        }
    }

    private fun collectProps(props: JsonArray) {
        try {
            for (index in 0..<props.length()) {
                val prop = props.getJSONObject(index)
                val propName = prop.getJSONObject("propConfig").getString("propName")
                try {
                    val result = parse(ForestPropRpc.collect(prop.getString("giveConfigId"), prop.getString("giveId")))
                    if (ResChecker.checkRes(tag + "领取道具失败:", result)) {
                        val message = "领取道具🎭[$propName]"
                        StructuredLog.forest(message)
                        Toast.show(message)
                    } else {
                        StructuredLog.record(tag, "领取道具🎭[$propName]失败:${result.getString("resultDesc")}")
                        StructuredLog.record(result.toString())
                    }
                } catch (e: Exception) {
                    StructuredLog.printStackTrace(tag, "领取道具时发生错误: ${e.message}", e)
                }
                GlobalThreadPools.sleepCompat(1000L)
            }
        } catch (e: RuntimeException) {
            StructuredLog.printStackTrace(tag, "givenProps JSON解析错误: ${e.message}", e)
        }
    }
}
