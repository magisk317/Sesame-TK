package io.github.magisk317.sesame.task.antOrchard

import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

/**
 * 金豆乐园 (com.alipay.goldenbean.*).
 *
 * Driven from the live `index` snapshot the same way ordinary fishpond is driven
 * from listTask: sign / fortune / manure exchange / auto-finishable tasks
 * (TRIGGER, GAMECENTER_TRIGGER) / claim FINISHED awards. VISIT and other
 * out-of-process tasks are left alone after RPC probe confirmed `不支持rpc调用`.
 */
class AntGoldenBean : ModelTask() {
    private val tag = javaClass.simpleName
    private lateinit var sign: BooleanModelField
    private lateinit var fortuneDraw: BooleanModelField
    private lateinit var manureExchange: BooleanModelField
    private lateinit var taskAward: BooleanModelField
    private lateinit var exchangeAmount: IntegerModelField

    override fun getName(): String = "金豆"
    override fun getGroup(): ModelGroup = ModelGroup.ORCHARD
    override fun getIcon(): String = "ConsumeGold.svg"

    override fun getFields(): ModelFields = ModelFields().apply {
        addField(BooleanModelField("sign", "金豆签到", true).also { sign = it })
        addField(BooleanModelField("fortuneDraw", "金豆夺宝抽签", true).also { fortuneDraw = it })
        addField(BooleanModelField("manureExchange", "肥料兑换金豆", false).also { manureExchange = it })
        addField(IntegerModelField("exchangeAmount", "兑换肥料克数", 0).also { exchangeAmount = it })
        addField(BooleanModelField("taskAward", "领取金豆任务奖励", true).also { taskAward = it })
    }

    override suspend fun runSuspend() {
        try {
            val index = loadIndex() ?: run {
                StructuredLog.forest("金豆🫘 index 不可用，跳过本轮")
                return
            }
            runFromSnapshot(GoldenBeanFlow.parse(index))
        } catch (e: CancellationException) {
            StructuredLog.record(tag, "任务被取消-${getName()}")
            throw e
        } catch (e: Exception) {
            StructuredLog.printStackTrace(tag, "执行异常:", e)
            throw e
        }
    }

    internal fun exchangeAmount(info: JsonObject?, requested: Int): Int =
        GoldenBeanFlow.exchangeAmount(
            info?.let {
                GoldenBeanManureState(
                    currentManure = it.optInt("currentManure"),
                    minExchangeAmount = it.optInt("minExchangeAmount"),
                    remainQuota = it.optInt("remainQuota"),
                )
            },
            requested,
        )

    internal fun shouldSign(info: JsonObject?): Boolean {
        if (info == null) return false
        return GoldenBeanFlow.parse(
            JsonObject(mapOf("signInfo" to info)),
        ).sign?.let { !it.todaySigned && it.signKey.isNotBlank() } == true
    }

    internal fun requireSuccess(operation: String, rawResponse: String): JsonObject {
        if (rawResponse.isBlank()) {
            throw IllegalStateException("金豆 $operation 返回为空")
        }
        val response = rawResponse.toJsonObject()
        if (!ResChecker.checkRes(tag, response)) {
            val detail = responseDetail(response)
            throw IllegalStateException("金豆 $operation 失败: $detail")
        }
        return response
    }

    private fun loadIndex(): JsonObject? {
        val raw = AntGoldenBeanRpc.index()
        if (raw.isBlank()) {
            StructuredLog.forest("金豆🫘 index 返回为空")
            return null
        }
        val response = raw.toJsonObject()
        if (!ResChecker.checkRes(tag, response)) {
            StructuredLog.forest("金豆🫘 index 失败: ${responseDetail(response)}")
            return null
        }
        return response
    }

    private fun runFromSnapshot(initial: GoldenBeanSnapshot) {
        var snapshot = initial
        val attempted = linkedSetOf<String>()
        var stagnant = 0
        repeat(MAX_ACTION_ROUNDS) {
            val actions = GoldenBeanTaskPlanner.actions(
                snapshot = snapshot,
                requestedExchangeAmount = exchangeAmount.value,
                signEnabled = sign.value,
                fortuneEnabled = fortuneDraw.value,
                manureEnabled = manureExchange.value,
                taskAwardEnabled = taskAward.value,
                attempted = attempted,
            )
            val action = actions.firstOrNull() ?: return
            if (!attempted.add(action.executionKey())) return

            val before = snapshot.signature()
            val ok = perform(action)
            if (!ok && action is GoldenBeanAction.FortuneDraw) {
                // Already drawn / missing info still counts as handled for this run.
            }

            val refreshed = loadIndex()?.let(GoldenBeanFlow::parse)
            if (refreshed == null) {
                StructuredLog.forest("金豆🫘 刷新 index 失败，停止任务闭环")
                return
            }
            snapshot = refreshed
            if (snapshot.signature() == before) {
                stagnant++
                if (stagnant >= MAX_STAGNANT_ROUNDS) {
                    StructuredLog.forest("金豆🫘 本轮任务状态无推进")
                    return
                }
            } else {
                stagnant = 0
            }
        }
        StructuredLog.forest("金豆🫘 任务闭环达到本轮上限")
    }

    private fun perform(action: GoldenBeanAction): Boolean = when (action) {
        is GoldenBeanAction.Sign -> {
            val response = callSoft("sign", AntGoldenBeanRpc.sign(action.signKey))
            if (response != null) {
                StructuredLog.forest("金豆🫘 签到成功")
                true
            } else {
                false
            }
        }

        GoldenBeanAction.FortuneDraw -> {
            val response = callSoft("fortuneDraw", AntGoldenBeanRpc.fortuneDraw())
            if (response != null) {
                StructuredLog.forest("金豆🫘 夺宝抽签成功")
                true
            } else {
                false
            }
        }

        is GoldenBeanAction.ManureExchange -> {
            val response = callSoft("manureExchange", AntGoldenBeanRpc.manureExchange(action.amount))
            if (response != null) {
                StructuredLog.forest("金豆🫘 肥料兑换成功「${action.amount}g」")
                true
            } else {
                false
            }
        }

        is GoldenBeanAction.Finish -> {
            val response = callSoft(
                "finishTask:${action.taskType}",
                AntGoldenBeanRpc.finishTask(action.taskType),
            )
            if (response != null) {
                val award = response.optJSONObject("awardInfo")?.optInt("deltaAwardCount")
                    ?: response.optInt("incAwardCount", 0)
                StructuredLog.record(
                    tag,
                    "✓ 金豆完成任务: ${action.title}" + if (award > 0) " +$award" else "",
                )
                // Claim immediately when the finish path leaves a claimable award.
                callSoft(
                    "receiveTaskAward:${action.taskType}",
                    AntGoldenBeanRpc.receiveTaskAward(action.taskType),
                )?.let { claim ->
                    val claimed = claim.optInt("incAwardCount", 0)
                    if (claimed > 0) {
                        StructuredLog.forest("金豆🫘 领取任务奖励[${action.title}] +$claimed")
                    }
                }
                true
            } else {
                false
            }
        }

        is GoldenBeanAction.Receive -> {
            val response = callSoft(
                "receiveTaskAward:${action.taskType}",
                AntGoldenBeanRpc.receiveTaskAward(action.taskType),
            )
            if (response != null) {
                val claimed = response.optInt("incAwardCount", 0)
                StructuredLog.record(
                    tag,
                    "✓ 金豆领取任务奖励: ${action.title}" + if (claimed > 0) " +$claimed" else "",
                )
                true
            } else {
                false
            }
        }
    }

    private fun callSoft(operation: String, rawResponse: String): JsonObject? {
        if (rawResponse.isBlank()) {
            StructuredLog.forest("金豆🫘 $operation 返回为空")
            return null
        }
        val response = rawResponse.toJsonObject()
        if (ResChecker.checkRes(tag, response)) return response
        val detail = responseDetail(response)
        if (GoldenBeanFlow.isAlreadyDoneMessage(detail)) {
            StructuredLog.forest("金豆🫘 $operation 跳过: $detail")
        } else {
            StructuredLog.forest("金豆🫘 $operation 失败: $detail")
        }
        return null
    }

    private fun responseDetail(response: JsonObject): String =
        response.optString(
            "resultDesc",
            response.optString(
                "desc",
                response.optString("memo", response.optString("errorMsg", "unknown")),
            ),
        )

    private companion object {
        init {
            ModelOrder.register(ModelDefinition(AntGoldenBean::class.java) { AntGoldenBean() })
        }
        const val MAX_ACTION_ROUNDS = 20
        const val MAX_STAGNANT_ROUNDS = 3
    }
}
