package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.entity.CollectEnergyEntity
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.optString

/** Converts a successful energy response into the next collection action. */
internal class ForestEnergyCollectionResultService(
    private val emoji: () -> String,
    private val onCollected: (Int) -> Unit,
    private val showToast: (String) -> Unit,
    private val water: (userId: String, bizNo: String, count: Int) -> Unit,
) {
    internal sealed interface NextAction {
        data class CollectAgain(val bubbleIds: List<Long>, val doubleClick: Boolean) : NextAction
        data object Done : NextAction
    }

    internal fun handle(
        entity: CollectEnergyEntity,
        result: ForestEnergyResultService.Result,
        needDouble: Boolean,
        spendTime: Long,
        userName: String,
        returnCount: (Int) -> Int,
    ): NextAction {
        val collected = result.collectedEnergy
        if (collected > 0) {
            val collectType = when {
                result.bubbleCount > 1 -> when (entity.fromTag) {
                    "takeLook" -> "找能量一键收取️"
                    "蹲点收取" -> "蹲点一键收取️"
                    else -> "一键收取️"
                }
                entity.fromTag == "takeLook" -> "找能量收取"
                entity.fromTag == "蹲点收取" -> "蹲点收取"
                else -> "普通收取"
            }
            val suffix = if (result.bubbleCount > 1) "#" else ""
            val message = "$collectType${emoji()}${collected}g[$userName]$suffix"
            onCollected(collected)
            val rendered = if (needDouble) "$message[双击]" else message
        StructuredLog.forest("${message}耗时[${TimeFormatter.formatDuration(spendTime)}]${if (needDouble) "[双击]" else ""}")
            showToast(rendered)
        }

        if (result.canCollectAgainIds.isNotEmpty()) {
            return NextAction.CollectAgain(
                bubbleIds = result.canCollectAgainIds,
                doubleClick = result.bubbleCount <= 1,
            )
        }

        val home = entity.userHome
        val bizNo = home?.optString("bizNo").orEmpty()
        val count = returnCount(collected)
        if (bizNo.isNotEmpty() && count > 0) water(entity.userId, bizNo, count)
        return NextAction.Done
    }
}
