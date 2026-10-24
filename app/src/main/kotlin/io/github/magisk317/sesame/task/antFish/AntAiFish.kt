package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

/** Coordinates the AI-fish state machine; task transport lives in the runner. */
class AntAiFish : ModelTask() {
    private lateinit var touchFishEnabled: BooleanModelField

    override fun getName(): String = "AI摸鱼"

    override fun getGroup(): ModelGroup = ModelGroup.FOREST

    override fun getIcon(): String = "AntAiFish.png"

    override fun getFields(): ModelFields = ModelFields().also { fields ->
        touchFishEnabled = BooleanModelField("touchFishEnabled", "摸鱼 | 自动执行", true)
        fields.addField(touchFishEnabled)
    }

    override suspend fun runSuspend() {
        if (!::touchFishEnabled.isInitialized || !touchFishEnabled.value) return

        try {
            // A task can create the next touch chance, so process the task
            // state before reading the fish balance.
            AntAiFishTaskRunner.run()

            val status = AntAiFishRpcCall.status().toJsonObject()
            if (!ResChecker.checkRes(TAG, status)) return
            if (isCaptured(status)) {
                if (!autoRescue()) return
                // 解救成功后刷新状态,确认已恢复到可摸鱼状态。
                val restored = AntAiFishRpcCall.status().toJsonObject()
                if (!ResChecker.checkRes(TAG, restored)) return
                if (restored.optString("fishStatus") !in AVAILABLE_FISH_STATUS) {
                    StructuredLog.record(TAG, "解救后仍未恢复可摸鱼状态，跳过摸鱼")
                    return
                }
            } else if (status.optString("fishStatus") !in AVAILABLE_FISH_STATUS) {
                StructuredLog.record(TAG, "当前没有可用鱼塘状态，跳过摸鱼")
                return
            }

            val homepage = AntAiFishRpcCall.homepage().toJsonObject()
            if (!ResChecker.checkRes(TAG, homepage)) return
            val interact = homepage.optJSONObject("myFish")
                ?.optJSONObject("interactVO")
                ?: run {
                    StructuredLog.record(TAG, "鱼塘首页没有 interactVO，跳过摸鱼")
                    return
                }
            var remaining = interact.optInt("remainTouchChance", -1)
            if (remaining <= 0) {
                StructuredLog.forest("AI摸鱼🐟 今日次数已用尽")
                return
            }
            if (!canTouchFish(homepage)) {
                StructuredLog.forest("AI摸鱼🐟 服务端未标记为可摸鱼")
                return
            }

            // 自动摸鱼：把今日剩余摸鱼次数全部摸完，每轮用服务端返回值重新对账。
            var touched = 0
            while (remaining > 0 && touched < MAX_TOUCH_ROUNDS) {
                val result = AntAiFishRpcCall.touchFish().toJsonObject()
                if (!ResChecker.checkRes(TAG, result)) {
                    StructuredLog.forest("AI摸鱼🐟 第${touched + 1}次摸鱼请求未成功")
                    return
                }
                touched++
                val reported = result.optJSONObject("myFish")
                    ?.optJSONObject("interactVO")
                    ?.optInt("remainTouchChance", -1)
                remaining = if (reported != null && reported >= 0) reported else remaining - 1
                val rewardText = touchRewardDescription(result)
                    .takeIf { it.isNotBlank() }
                    ?.let { "，获得$it" }
                    .orEmpty()
                StructuredLog.forest("AI摸鱼🐟 第${touched}次摸鱼完成$rewardText，剩余${maxOf(remaining, 0)}次")
                if (remaining <= 0) return
                delay(TOUCH_STATE_DELAY_MS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "AI摸鱼执行异常", t)
        }
    }

    /** 鱼被别人摸走时自动解救;成功返回 true，失败返回 false 并记录日志。 */
    private suspend fun autoRescue(): Boolean {
        StructuredLog.forest("AI摸鱼🐟 鱼被摸走了，尝试自动解救")
        val rescue = AntAiFishRpcCall.rescueFish().toJsonObject()
        if (!ResChecker.checkRes(TAG, rescue)) {
            StructuredLog.record(TAG, "自动解救失败，跳过摸鱼")
            return false
        }
        StructuredLog.forest("AI摸鱼🐟 已自动解救回来，继续摸鱼")
        delay(TOUCH_STATE_DELAY_MS)
        return true
    }

    companion object {
        private const val TAG = "AntAiFish"
        private const val MAX_TOUCH_ROUNDS = 8
        private const val TOUCH_STATE_DELAY_MS = 500L
        private const val FISH_STATUS_HAVE_FISH = "HAVE_FISH"
        private const val FISH_STATUS_CAN_TOUCH = "CAN_TOUCH"
        private const val FISH_STATUS_CAPTURED = "CAPTURED"
        private val AVAILABLE_FISH_STATUS = setOf(FISH_STATUS_HAVE_FISH, FISH_STATUS_CAN_TOUCH)

        internal fun canTouchFish(homepage: JsonObject): Boolean {
            val interact = homepage.optJSONObject("myFish")
                ?.optJSONObject("interactVO")
                ?: return false
            val status = interact.optString("fishInteractStatus")
            return status in AVAILABLE_FISH_STATUS && interact.optInt("remainTouchChance", -1) > 0
        }

        internal fun isCaptured(status: JsonObject): Boolean =
            status.optString("fishStatus") == FISH_STATUS_CAPTURED

        /** 摸鱼奖励文案，优先取 touchRewardList 首个带描述的条目。 */
        internal fun touchRewardDescription(result: JsonObject): String {
            val list = result.optJSONArray("touchRewardList") ?: return ""
            for (index in 0 until list.size) {
                val entry = list.optJSONObject(index) ?: continue
                val extInfo = entry.optJSONObject("extInfo") ?: continue
                val popup = extInfo.optJSONObject("popup")
                val desc = popup?.optString("desc")
                    ?.takeIf { it.isNotBlank() }
                    ?: popup?.optString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?: extInfo.optString("desc")
                    .takeIf { it.isNotBlank() }
                if (desc != null) return desc
                val rightsNums = popup?.optString("rightsNums")
                    ?.takeIf { it.isNotBlank() }
                if (rightsNums != null) return "能量${rightsNums}g"
            }
            return ""
        }

        init {
            ModelOrder.register(ModelDefinition(AntAiFish::class.java) { AntAiFish() })
        }
    }
}
