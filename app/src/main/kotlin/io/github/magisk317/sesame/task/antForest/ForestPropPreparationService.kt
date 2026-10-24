package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.serialization.json.JsonObject

/** Plans and orders the prop actions that precede energy collection. */
internal class ForestPropPreparationService(
    private val planner: ForestPropUsagePlanner,
    private val queryBag: () -> JsonObject?,
    private val useDouble: (JsonObject) -> Unit,
    private val useRobExpand: () -> Unit,
    private val useStealth: (JsonObject?) -> Unit,
    private val useBubbleBoost: () -> Unit,
    private val useShield: (JsonObject?) -> Unit,
    private val useBomb: (JsonObject?) -> Unit,
) {
    internal fun prepare(state: ForestPropUsagePlanner.State, skip: Boolean) {
        if (skip) {
            StructuredLog.record(TAG, "⚡ 快速收取通道：跳过道具检查，加速蹲点收取")
            return
        }
        try {
            val plan = planner.plan(state)
            if (!plan.hasActions) {
                StructuredLog.record(TAG, "道具使用检查: 当前收取批次无需使用道具")
                return
            }
            StructuredLog.record(
                TAG,
                "道具使用检查: useDouble=${plan.useDouble}, useRobExpand=${plan.useRobExpand}" +
                    ", useStealth=${plan.useStealth}, useShield=${plan.useShield}" +
                    ", useBomb=${plan.useBomb}, useBubbleBoost=${plan.useBubbleBoost}",
            )
            val bag = queryBag()
            if (plan.useDouble && bag != null) useDouble(bag)
            if (plan.useRobExpand) useRobExpand()
            if (plan.useStealth) useStealth(bag)
            if (plan.useBubbleBoost) useBubbleBoost()
            if (plan.useShield) {
                StructuredLog.record(TAG, "尝试使用保护罩罩")
                useShield(bag)
            } else if (plan.useBomb) {
                StructuredLog.record(TAG, "准备使用能量炸弹卡")
                useBomb(bag)
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "prepare props err", error)
        }
    }

    private companion object {
        const val TAG = "ForestPropPreparationService"
    }
}
