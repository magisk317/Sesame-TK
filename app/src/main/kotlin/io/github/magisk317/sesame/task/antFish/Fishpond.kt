package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

/** Regular fishpond workflow observed from the fishpond page. */
class Fishpond : ModelTask() {
    private lateinit var autoFish: BooleanModelField

    override fun getName() = "普通鱼塘"
    override fun getGroup() = ModelGroup.FOREST
    override fun getIcon() = "AntAiFish.png"

    override fun getFields() = ModelFields().also { fields ->
        autoFish = BooleanModelField("autoFish", "自动钓鱼", false)
        fields.addField(autoFish)
    }

    override suspend fun runSuspend() {
        if (!autoFish.value) return
        try {
            val execution = FishpondExecutionState()
            var syncResponse = syncState()
            if (!ResChecker.checkRes(TAG, syncResponse)) {
                StructuredLog.record(TAG, "普通鱼塘🎣 状态同步失败，跳过钓鱼")
                return
            }

            syncResponse = runActivityActions(syncResponse, execution)
            var taskSnapshot = listTasks()?.let { runTaskActions(it, execution) }
            syncResponse = syncState()
            if (!ResChecker.checkRes(TAG, syncResponse)) return

            var state = FishpondFlow.snapshot(syncResponse) ?: run {
                StructuredLog.record(TAG, "普通鱼塘🎣 状态缺少鱼竿或鱼重，跳过钓鱼")
                return
            }
            StructuredLog.record(
                TAG,
                "鱼塘状态: 鱼竿=${state.rodSumCount}, 鱼重=${state.currentFishWeight.toPlainString()}",
            )

            var completedRounds = 0
            while (state.rodSumCount > 0) {
                val attempt = completedRounds + 1
                val angle = FishpondRpcCall.angle().toJsonObject()
                val angleSucceeded = ResChecker.checkRes(TAG, angle)

                val syncedResponse = syncState()
                if (!ResChecker.checkRes(TAG, syncedResponse)) {
                    StructuredLog.record(TAG, "钓鱼第${attempt}次后的状态同步失败，停止本轮")
                    break
                }
                val syncedState = FishpondFlow.snapshot(syncedResponse)
                if (syncedState == null) {
                    StructuredLog.record(TAG, "钓鱼第${attempt}次后的状态缺少鱼竿或鱼重，停止本轮")
                    break
                }
                if (!angleSucceeded) {
                    StructuredLog.record(
                        TAG,
                        "钓鱼第${attempt}次请求失败，停止本轮；同步状态: 鱼竿=${state.rodSumCount}->${syncedState.rodSumCount}, " +
                            "鱼重=${state.currentFishWeight.toPlainString()}->${syncedState.currentFishWeight.toPlainString()}",
                    )
                    break
                }

                when (FishpondFlow.progress(state, syncedState)) {
                    FishpondProgress.ROD_NOT_DECREASED -> {
                        val reason = if (FishpondFlow.requiresRodPositioning(angle)) {
                            "服务端进入页面定位挑战，当前无已验证的自动完成动作"
                        } else {
                            "鱼竿未减少"
                        }
                        StructuredLog.record(
                            TAG,
                            "钓鱼第${attempt}次未确认成功($reason, ${state.rodSumCount}->${syncedState.rodSumCount})，停止本轮",
                        )
                        break
                    }

                    FishpondProgress.FISH_WEIGHT_NOT_INCREASED -> {
                        StructuredLog.record(
                            TAG,
                            "钓鱼第${attempt}次未确认鱼重增加(" +
                                "${state.currentFishWeight.toPlainString()}->${syncedState.currentFishWeight.toPlainString()})，停止本轮",
                        )
                        break
                    }

                    FishpondProgress.CONFIRMED -> Unit
                }

                completedRounds++
                val caughtFish = FishpondFlow.caughtFish(angle)
                val fishName = caughtFish?.fishName.orEmpty().ifBlank { "未知鱼种" }
                val fishWeight = caughtFish?.fishWeight.orEmpty().ifBlank { "未知" }
                StructuredLog.forest(
                    "✓ 普通鱼塘钓鱼成功[$completedRounds] 鱼=$fishName ${fishWeight}斤 " +
                        "鱼竿=${state.rodSumCount}->${syncedState.rodSumCount}",
                )
                syncResponse = runActivityActions(syncedResponse, execution)
                if (taskSnapshot == null || taskSnapshot.hasPendingFishingTask()) {
                    listTasks()?.let { taskSnapshot = runTaskActions(it, execution) }
                }
                syncResponse = syncState()
                if (!ResChecker.checkRes(TAG, syncResponse)) break
                state = FishpondFlow.snapshot(syncResponse) ?: syncedState
            }

            listTasks()?.let { runTaskActions(it, execution) }

            if (state.rodSumCount == 0) {
                StructuredLog.record(TAG, "普通鱼塘🎣 鱼竿已用完，成功钓鱼${completedRounds}次")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "普通鱼塘执行异常", t)
        }
    }

    private fun syncState() = FishpondRpcCall.sync(*SYNC_TYPES).toJsonObject()

    private fun listTasks(): FishpondTaskSnapshot? {
        val response = FishpondRpcCall.listTask().toJsonObject()
        if (!ResChecker.checkRes(TAG, response)) return null
        return FishpondTaskStateParser.parse(response)
    }

    /** Performs only actions which the page can complete from listTask state. */
    private fun runTaskActions(
        initial: FishpondTaskSnapshot,
        execution: FishpondExecutionState,
    ): FishpondTaskSnapshot {
        var snapshot = initial
        while (true) {
            val action = FishpondTaskPlanner.actions(snapshot, execution.signAttempted)
                .firstOrNull { execution.attemptedTaskActions.add(it.executionKey(snapshot)) }
                ?: return snapshot
            val response = when (action) {
                is FishpondTaskAction.Sign -> {
                    execution.signAttempted = true
                    FishpondRpcCall.sign(action.signKey).toJsonObject()
                }

                is FishpondTaskAction.Exchange -> FishpondRpcCall.exchangeManureForRod(
                    action.taskId,
                    action.manurePaid,
                    action.rodExchangeCount,
                ).toJsonObject()

                is FishpondTaskAction.Finish -> FishpondRpcCall.finishTask(
                    action.sceneCode,
                    action.taskType,
                    "${UserMap.currentUid.orEmpty()}${System.currentTimeMillis()}",
                ).toJsonObject()

                is FishpondTaskAction.Receive -> FishpondRpcCall.receiveTaskAward(
                    action.sceneCode,
                    action.taskId,
                ).toJsonObject()
            }
            if (ResChecker.checkRes(TAG, response)) {
                when (action) {
                    is FishpondTaskAction.Sign -> StructuredLog.forest("鱼塘🎣 签到完成")
                    is FishpondTaskAction.Exchange -> StructuredLog.forest(
                        "鱼塘🎣 兑换钓竿${action.rodExchangeCount}次",
                    )
                    is FishpondTaskAction.Finish -> StructuredLog.forest(
                        "鱼塘🎣 完成任务[${action.title}]",
                    )
                    is FishpondTaskAction.Receive -> StructuredLog.forest(
                        "鱼塘🎣 领取任务奖励[${action.title}]",
                    )
                }
            }
            val refreshed = listTasks() ?: return snapshot
            snapshot = refreshed
        }
    }

    private fun runActivityActions(initial: JsonObject, execution: FishpondExecutionState): JsonObject {
        var response = initial
        repeat(MAX_ACTIVITY_ACTION_PASSES) {
            val before = FishpondFlow.activityState(response)
            val action = when {
                before.tomorrowRodStatus == "TODAY_TODO" -> "TOMORROW_ROD" to "FINISH"
                before.fishActivityStatus == "FINISHED" -> "FISH_ACTIVITY" to "receiveAward"
                before.giftBoxStatus == "TODO" -> "GIFT_BOX" to "receiveAward"
                before.hasLotteryPlusAward() -> "LOTTERY_PLUS" to "RECEIVE"
                else -> return response
            }
            if (!execution.attemptedActivityActions.add("${action.first}:${action.second}:${before.signature()}")) {
                return response
            }
            val result = FishpondRpcCall.triggerSubplotsActivity(action.first, action.second).toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) return response
            StructuredLog.forest("鱼塘🎣 领取活动奖励[${action.first}]")
            val refreshed = syncState()
            if (!ResChecker.checkRes(TAG, refreshed)) return response
            response = refreshed
            if (FishpondFlow.activityState(response).signature() == before.signature()) return response
        }
        return response
    }

    private fun FishpondTaskSnapshot.hasPendingFishingTask(): Boolean = tasks.any {
        it.actionType in FISHING_ACTIONS && it.taskStatus != "RECEIVED"
    }

    private fun FishpondTaskAction.executionKey(snapshot: FishpondTaskSnapshot): String = when (this) {
        is FishpondTaskAction.Sign -> "sign:$signKey"
        is FishpondTaskAction.Exchange -> "exchange:$taskId:$manurePaid:$rodExchangeCount"
        is FishpondTaskAction.Finish -> "finish:${taskStateKey(snapshot, sceneCode, taskType)}"
        is FishpondTaskAction.Receive -> "receive:${taskStateKey(snapshot, sceneCode, taskId)}"
    }

    private fun taskStateKey(snapshot: FishpondTaskSnapshot, sceneCode: String, taskId: String): String = snapshot.tasks
        .firstOrNull { it.sceneCode == sceneCode && it.taskId == taskId }
        ?.let { "${it.taskId}:${it.taskStatus}:${it.rightsTimes}:${it.taskProgress}" }
        ?: "$sceneCode:$taskId"

    companion object {
        private const val TAG = "Fishpond"
        private const val MAX_ACTIVITY_ACTION_PASSES = 8
        private val FISHING_ACTIONS = setOf("GOFISH", "FISH_WEIGHT")
        private val SYNC_TYPES = arrayOf(
            "FISH_ACTIVITY",
            "GIFT_BOX",
            "TASK_DISPLAY",
            "TOMORROW_ROD",
            "LOTTERY_PLUS",
        )
        init {
            ModelOrder.register(ModelDefinition(Fishpond::class.java) { Fishpond() })
        }
    }
}

private class FishpondExecutionState {
    var signAttempted = false
    val attemptedTaskActions = mutableSetOf<String>()
    val attemptedActivityActions = mutableSetOf<String>()
}
