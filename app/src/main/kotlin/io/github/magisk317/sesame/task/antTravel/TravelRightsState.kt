package io.github.magisk317.sesame.task.antTravel

import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonObject

internal data class TravelRightsBenefitState(
    val currentPoint: String,
    val totalPoint: String,
    val signOpen: Boolean,
    val signed: Boolean,
    val couponTabCount: Int,
    val couponProductCount: Int,
)

internal enum class TravelRightsTaskAction {
    SKIP,
    PAGE_REQUIRED,
    RECEIVE,
}

internal data class TravelRightsTaskState(
    val taskCode: String,
    val title: String,
    val status: String,
    val claimType: String,
    val advanceType: String,
    val progress: Int,
    val recordNo: String,
    val jumpLink: String,
    val browseTimeMs: Long,
    val action: TravelRightsTaskAction,
)

internal object TravelRightsStateParser {
    fun isSuccessful(response: JsonObject): Boolean =
        response.optBoolean("success") && response.optString("code") == "SUCCESS"

    fun parseBenefits(response: JsonObject): TravelRightsBenefitState? {
        if (!isSuccessful(response)) return null
        val pointInfo = response.optJSONObject("pointInfo") ?: return null
        val signInfo = response.optJSONObject("signInfo")
        return TravelRightsBenefitState(
            currentPoint = pointInfo.optString("currentPoint"),
            totalPoint = pointInfo.optString("totalPoint"),
            signOpen = signInfo?.optBoolean("openSignFlag") ?: false,
            signed = signInfo?.optBoolean("signInFlag") ?: false,
            couponTabCount = response.optJSONArray("trafficCouponTabs")?.size ?: 0,
            couponProductCount = response.optJSONArray("trafficCouponProduct")?.size ?: 0,
        )
    }

    fun parseTasks(response: JsonObject): List<TravelRightsTaskState> =
        parseTasksOrNull(response).orEmpty()

    fun parseTasksOrNull(response: JsonObject): List<TravelRightsTaskState>? {
        if (!isSuccessful(response)) return null
        return response.optJSONArray("taskList")
            ?.asSequence()
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { task ->
                val taskCode = task.optString("taskCode")
                if (taskCode.isBlank()) return@mapNotNull null
                val display = task.optJSONObject("displayInfo")
                TravelRightsTaskState(
                    taskCode = taskCode,
                    title = display?.optString("taskName").orEmpty().trim().ifBlank { taskCode },
                    status = task.optString("taskStatus"),
                    claimType = task.optString("claimType"),
                    advanceType = task.optString("advanceType"),
                    progress = display?.optInt("taskProgressPercentage") ?: 0,
                    recordNo = task.optString("recordNo"),
                    jumpLink = display?.optString("jumpLink").orEmpty().trim(),
                    browseTimeMs = display?.optInt("browseTime")?.coerceAtLeast(0)?.times(1_000L) ?: 0L,
                    action = actionFor(task),
                )
            }
            ?.toList()
            .orEmpty()
    }

    private fun actionFor(task: JsonObject): TravelRightsTaskAction {
        val status = task.optString("taskStatus").uppercase()
        if (status in setOf("RECEIVED", "COMPLETED")) {
            return TravelRightsTaskAction.SKIP
        }
        if (status == "FINISHED") return TravelRightsTaskAction.RECEIVE
        val display = task.optJSONObject("displayInfo")
        val jumpLink = display?.optString("jumpLink").orEmpty().trim()
        return if (jumpLink.isBlank()) TravelRightsTaskAction.SKIP else TravelRightsTaskAction.PAGE_REQUIRED
    }
}
