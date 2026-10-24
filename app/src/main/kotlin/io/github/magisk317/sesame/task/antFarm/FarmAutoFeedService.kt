package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.task.antFarm.AntFarm.AnimalFeedStatus
import io.github.magisk317.sesame.task.antFarm.AntFarm.AnimalInteractStatus
import io.github.magisk317.sesame.util.StructuredLog

/** Runs the state-driven part of the owner-animal feeding workflow. */
internal class FarmAutoFeedService(
    private val currentState: () -> State,
    private val foodStock: () -> Int,
    private val receiveAwards: suspend () -> Unit,
    private val feed: () -> Boolean,
    private val sync: () -> Unit,
    private val useBigEater: suspend () -> Boolean,
    private val useAccelerate: suspend () -> Boolean,
    private val runFarmGame: suspend () -> Unit,
    private val runChouChouLe: () -> Unit,
    private val rewardEnabled: () -> Boolean,
    private val feedEnabled: () -> Boolean,
    private val bigEaterEnabled: () -> Boolean,
    private val accelerateEnabled: () -> Boolean,
    private val recordFarmGameEnabled: () -> Boolean,
    private val chouChouLeEnabled: () -> Boolean,
) {
    data class State(
        val interactStatus: String?,
        val feedStatus: String?,
    )

    suspend fun run(isChildTask: Boolean) {
        if (currentState().interactStatus != AnimalInteractStatus.HOME.name) return
        if (currentState().feedStatus == AnimalFeedStatus.SLEEPY.name) {
            StructuredLog.record(TAG, "投喂小鸡🥣[小鸡正在睡觉中，暂停投喂]")
            return
        }

        if (rewardEnabled() && foodStock() < FEED_AMOUNT) {
            StructuredLog.record(TAG, "饲料小于${FEED_AMOUNT}g，尝试领取饲料奖励")
            receiveAwards()
        }

        if (currentState().feedStatus == AnimalFeedStatus.HUNGRY.name && feedEnabled()) {
            StructuredLog.record(TAG, "小鸡在挨饿~Tk 尝试为你自动喂食")
            if (feed()) sync()
        }

        if (bigEaterEnabled() && currentState().feedStatus == AnimalFeedStatus.EATING.name) {
            if (useBigEater()) sync()
        }

        if (accelerateEnabled() && currentState().feedStatus == AnimalFeedStatus.EATING.name) {
            val accelerated = useAccelerate()
            if (accelerated) sync()
        }

        if (isChildTask) {
            if (recordFarmGameEnabled()) runFarmGame()
            if (chouChouLeEnabled()) runChouChouLe()
        }
    }

    private companion object {
        const val TAG = "FarmAutoFeedService"
        const val FEED_AMOUNT = 180
    }
}
