package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Coordinates state application and side effects driven by a farm home response. */
internal class FarmHomeWorkflowService(
    private val snapshotService: FarmHomeSnapshotService,
    private val applySnapshot: (FarmHomeSnapshotService.Snapshot) -> Unit,
    private val claimActivityRewards: (JsonObject?) -> Unit,
    private val shouldUseSpecialFood: () -> Boolean,
    private val useSpecialFood: (JsonArray) -> Unit,
    private val drawLotteryPlus: (JsonObject) -> Unit,
    private val shouldAcceptGift: () -> Boolean,
    private val canAcceptGift: () -> Boolean,
    private val acceptGift: () -> Unit,
) {
    fun process(response: JsonObject): Boolean {
        return try {
            val snapshot = snapshotService.parse(response) ?: return false
            applySnapshot(snapshot)
            claimActivityRewards(snapshot.activityData)

            if (shouldUseSpecialFood()) {
                useSpecialFood(snapshot.cuisineList ?: return false)
            }
            snapshot.lotteryPlusInfo?.let(drawLotteryPlus)

            if (shouldAcceptGift() && snapshot.hasGiftRecord && canAcceptGift()) {
                acceptGift()
            }
            true
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "process farm home response err: ${error.message}", error)
            false
        }
    }

    private companion object {
        const val TAG = "FarmHomeWorkflowService"
    }
}
