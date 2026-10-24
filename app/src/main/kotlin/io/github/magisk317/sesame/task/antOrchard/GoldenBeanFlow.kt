package io.github.magisk317.sesame.task.antOrchard

import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonObject

internal data class GoldenBeanSignState(
    val signKey: String,
    val todaySigned: Boolean,
)

internal data class GoldenBeanManureState(
    val currentManure: Int,
    val minExchangeAmount: Int,
    val remainQuota: Int,
)

internal data class GoldenBeanTaskState(
    val taskId: String,
    val taskStatus: String,
    val actionType: String,
    val title: String,
    val sceneCode: String,
    val awardCount: Int,
    val rightsTimes: Int,
    val rightsTimesLimit: Int,
    val taskProgress: Int,
    val taskRequire: Int,
)

internal data class GoldenBeanSnapshot(
    val sign: GoldenBeanSignState?,
    val manure: GoldenBeanManureState?,
    val fortuneDrawAvailable: Boolean?,
    val tasks: List<GoldenBeanTaskState>,
) {
    fun signature(): String = buildString {
        append(sign?.let { "${it.signKey}:${it.todaySigned}" }.orEmpty())
        append("|manure:")
        append(manure?.let { "${it.currentManure}:${it.remainQuota}" }.orEmpty())
        append("|fortune:").append(fortuneDrawAvailable)
        tasks.sortedBy(GoldenBeanTaskState::taskId).forEach { task ->
            append('|')
            append(task.taskId)
            append(':').append(task.taskStatus)
            append(':').append(task.rightsTimes)
            append(':').append(task.taskProgress)
        }
    }
}

internal sealed interface GoldenBeanAction {
    data class Sign(val signKey: String) : GoldenBeanAction
    data object FortuneDraw : GoldenBeanAction
    data class ManureExchange(val amount: Int) : GoldenBeanAction
    data class Finish(val sceneCode: String, val taskType: String, val title: String) : GoldenBeanAction
    data class Receive(val sceneCode: String, val taskType: String, val title: String) : GoldenBeanAction

    fun executionKey(): String = when (this) {
        is Sign -> "sign:$signKey"
        FortuneDraw -> "fortuneDraw"
        is ManureExchange -> "manure:$amount"
        is Finish -> "finish:$sceneCode:$taskType"
        is Receive -> "receive:$sceneCode:$taskType"
    }
}

/**
 * Live-state helpers for 金豆乐园.
 *
 * Only action types verified by RPC probe as auto-completable via
 * `finishTaskantorchard` are finished here. VISIT / ADD_HOME / PUSH_SUBSCRIBE
 * return `不支持rpc调用` and stay out of the auto path.
 */
internal object GoldenBeanFlow {
    /** Verified 2026-08-19 via /debugHandler: finish+receive → RECEIVED. */
    val AUTO_FINISH_ACTIONS = setOf("TRIGGER", "GAMECENTER_TRIGGER")

    fun parse(response: JsonObject): GoldenBeanSnapshot {
        val root = response.optJSONObject("data") ?: response
        return GoldenBeanSnapshot(
            sign = parseSign(root.optJSONObject("signInfo")),
            manure = parseManure(root.optJSONObject("manureExchangeInfo")),
            fortuneDrawAvailable = root.optJSONObject("fortuneDrawInfo")?.let {
                !it.optBoolean("todayDrawn", true)
            },
            tasks = root.optJSONArray("taskList")
                ?.asSequence()
                ?.mapNotNull { it as? JsonObject }
                ?.mapNotNull(::parseTask)
                ?.toList()
                .orEmpty(),
        )
    }

    fun exchangeAmount(info: GoldenBeanManureState?, requested: Int): Int {
        if (info == null || info.remainQuota <= 0) return 0
        if (info.currentManure < info.minExchangeAmount) return 0
        return requested.coerceIn(info.minExchangeAmount, minOf(info.currentManure, info.remainQuota))
    }

    fun isAlreadyDoneMessage(detail: String): Boolean =
        detail.contains("今日已抽") ||
            detail.contains("已签到") ||
            detail.contains("已领取") ||
            detail.contains("任务未完成") ||
            detail.contains("不支持rpc")

    private fun parseSign(info: JsonObject?): GoldenBeanSignState? {
        if (info == null) return null
        val key = info.optString("currentSignKey")
        if (key.isBlank()) return null
        val todaySigned = when {
            info.containsKey("todaySigned") -> info.optBoolean("todaySigned")
            else -> info.optJSONArray("signList")
                ?.asSequence()
                ?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { it.optBoolean("today") || it.optString("signKey") == key }
                ?.optBoolean("signed")
                ?: true
        }
        return GoldenBeanSignState(signKey = key, todaySigned = todaySigned)
    }

    private fun parseManure(info: JsonObject?): GoldenBeanManureState? {
        if (info == null) return null
        return GoldenBeanManureState(
            currentManure = info.optInt("currentManure"),
            minExchangeAmount = info.optInt("minExchangeAmount"),
            remainQuota = info.optInt("remainQuota"),
        )
    }

    private fun parseTask(task: JsonObject): GoldenBeanTaskState? {
        val taskId = task.optString("taskId")
        val actionType = task.optString("actionType")
        if (taskId.isBlank() || actionType.isBlank()) return null
        val title = task.optJSONObject("taskDisplayConfig")?.optString("title").orEmpty()
            .ifBlank { taskId }
        return GoldenBeanTaskState(
            taskId = taskId,
            taskStatus = task.optString("taskStatus"),
            actionType = actionType,
            title = title,
            sceneCode = task.optString("sceneCode").ifBlank { "GOLDEN_BEAN_MASTER_TASK" },
            awardCount = task.optInt("awardCount"),
            rightsTimes = task.optInt("rightsTimes"),
            rightsTimesLimit = task.optInt("rightsTimesLimit"),
            taskProgress = task.optInt("taskProgress"),
            taskRequire = task.optInt("taskRequire"),
        )
    }
}

internal object GoldenBeanTaskPlanner {
    fun actions(
        snapshot: GoldenBeanSnapshot,
        requestedExchangeAmount: Int,
        signEnabled: Boolean,
        fortuneEnabled: Boolean,
        manureEnabled: Boolean,
        taskAwardEnabled: Boolean,
        attempted: Set<String>,
    ): List<GoldenBeanAction> = buildList {
        if (signEnabled) {
            snapshot.sign
                ?.takeIf { !it.todaySigned && it.signKey.isNotBlank() }
                ?.let {
                    val action = GoldenBeanAction.Sign(it.signKey)
                    if (action.executionKey() !in attempted) add(action)
                }
        }

        if (fortuneEnabled && snapshot.fortuneDrawAvailable != false) {
            val action = GoldenBeanAction.FortuneDraw
            // When fortuneDrawInfo is absent (null available), still try once per run.
            if (action.executionKey() !in attempted) add(action)
        }

        if (manureEnabled) {
            val amount = GoldenBeanFlow.exchangeAmount(snapshot.manure, requestedExchangeAmount)
            if (amount > 0) {
                val action = GoldenBeanAction.ManureExchange(amount)
                if (action.executionKey() !in attempted) add(action)
            }
        }

        if (!taskAwardEnabled) return@buildList

        snapshot.tasks
            .filter {
                it.taskStatus == "TODO" &&
                    it.actionType in GoldenBeanFlow.AUTO_FINISH_ACTIONS &&
                    it.sceneCode.isNotBlank()
            }
            .forEach {
                val action = GoldenBeanAction.Finish(it.sceneCode, it.taskId, it.title)
                if (action.executionKey() !in attempted) add(action)
            }

        snapshot.tasks
            .filter { it.taskStatus == "FINISHED" && it.sceneCode.isNotBlank() }
            .forEach {
                val action = GoldenBeanAction.Receive(it.sceneCode, it.taskId, it.title)
                if (action.executionKey() !in attempted) add(action)
            }
    }
}
