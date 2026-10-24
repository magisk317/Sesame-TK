package io.github.magisk317.sesame.task

/**
 * Result of one model task invocation.
 *
 * A task that was not eligible to run is different from a task that ran and
 * failed. Keeping that distinction here prevents callers from inferring
 * success from a completed Job.
 */
data class TaskExecutionResult(
    val status: Status,
    val errorMessage: String? = null,
) {
    enum class Status {
        SUCCESS,
        FAILED,
        CANCELLED,
        SKIPPED,
    }
}
