package io.github.magisk317.sesame.task.antTravel

import kotlinx.serialization.json.JsonObject

internal enum class TravelRightsDrawMode {
    NONE,
    SINGLE,
    FIVE,
}

internal object TravelRightsDrawPlanner {
    fun modeFor(remainingTimes: Int): TravelRightsDrawMode = when {
        remainingTimes <= 0 -> TravelRightsDrawMode.NONE
        remainingTimes >= 5 -> TravelRightsDrawMode.FIVE
        else -> TravelRightsDrawMode.SINGLE
    }
}

/** Keeps page-driven tasks bounded and independent from one another. */
internal object TravelRightsTaskPlanner {
    fun nextPageTask(
        tasks: List<TravelRightsTaskState>,
        attempted: Set<String>,
    ): TravelRightsTaskState? = tasks.firstOrNull {
        it.action == TravelRightsTaskAction.PAGE_REQUIRED && it.taskCode !in attempted
    }

    /** A successful refresh is required before treating a page visit as done. */
    fun progressed(before: TravelRightsTaskState, after: List<TravelRightsTaskState>): Boolean {
        val current = after.firstOrNull { it.taskCode == before.taskCode } ?: return true
        if (current.status.equals("FINISHED", ignoreCase = true) ||
            current.status.equals("RECEIVED", ignoreCase = true) ||
            current.status.equals("COMPLETED", ignoreCase = true)
        ) {
            return true
        }
        return current.status != before.status || current.progress > before.progress
    }

    fun parseTaskResponse(response: JsonObject): List<TravelRightsTaskState>? =
        TravelRightsStateParser.parseTasksOrNull(response)
}
