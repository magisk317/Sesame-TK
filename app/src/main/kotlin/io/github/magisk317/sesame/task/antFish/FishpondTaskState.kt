package io.github.magisk317.sesame.task.antFish

import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonObject

internal data class FishpondSignState(
    val signKey: String,
    val signed: Boolean,
    val awardCount: Int,
)

internal data class FishpondTaskState(
    val taskId: String,
    val taskStatus: String,
    val actionType: String,
    val title: String,
    val sceneCode: String,
    val adBizNo: String,
    val awardCount: Int,
    val rightsTimes: Int,
    val rightsTimesLimit: Int,
    val taskProgress: Int,
    val taskRequire: Int,
)

internal data class FishpondExchangeState(
    val leftCountCanExchange: Int,
    val manurePriceOfRod: Int,
    val userManure: Int,
)

internal data class FishpondTaskSnapshot(
    val todaySign: FishpondSignState?,
    val tasks: List<FishpondTaskState>,
    val exchange: FishpondExchangeState?,
) {
    /** Includes every server field which can move after a task action. */
    fun signature(): String = buildString {
        append(todaySign?.let { "${it.signKey}:${it.signed}" }.orEmpty())
        append("|exchange:")
        append(exchange?.let { "${it.leftCountCanExchange}:${it.manurePriceOfRod}:${it.userManure}" }.orEmpty())
        tasks.sortedBy(FishpondTaskState::taskId).forEach { task ->
            append('|')
            append(task.taskId)
            append(':').append(task.taskStatus)
            append(':').append(task.rightsTimes)
            append(':').append(task.taskProgress)
        }
    }
}

/** Parses only fields returned by the live fishpond listTask response. */
internal object FishpondTaskStateParser {
    fun parse(response: JsonObject): FishpondTaskSnapshot {
        val sign = response.optJSONObject("signInfo")
            ?.optJSONArray("list")
            ?.asSequence()
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.optBoolean("today") }
            ?.let { entry ->
                FishpondSignState(
                    signKey = entry.optString("signKey"),
                    signed = entry.optBoolean("signed"),
                    awardCount = entry.optInt("awardCount"),
                )
            }

        val tasks = response.optJSONArray("taskList")
            ?.asSequence()
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull(::parseTask)
            ?.toList()
            .orEmpty()
        val exchange = response.optJSONObject("exchangeManure4RodTaskExt")?.let { ext ->
            FishpondExchangeState(
                leftCountCanExchange = ext.optInt("leftCountCanExchange"),
                manurePriceOfRod = ext.optInt("manurePriceOfRod"),
                userManure = ext.optInt("userManure"),
            )
        }
        return FishpondTaskSnapshot(sign, tasks, exchange)
    }

    private fun parseTask(task: JsonObject): FishpondTaskState? {
        val taskId = task.optString("taskId")
        val actionType = task.optString("actionType")
        if (taskId.isBlank() || actionType.isBlank()) return null
        return FishpondTaskState(
            taskId = taskId,
            taskStatus = task.optString("taskStatus"),
            actionType = actionType,
            title = task.optJSONObject("taskDisplayConfig")?.optString("title").orEmpty()
                .ifBlank { taskId },
            sceneCode = task.optString("sceneCode"),
            adBizNo = task.optString("adBizNo"),
            awardCount = task.optInt("awardCount"),
            rightsTimes = task.optInt("rightsTimes"),
            rightsTimesLimit = task.optInt("rightsTimesLimit"),
            taskProgress = task.optInt("taskProgress"),
            taskRequire = task.optInt("taskRequire"),
        )
    }
}
