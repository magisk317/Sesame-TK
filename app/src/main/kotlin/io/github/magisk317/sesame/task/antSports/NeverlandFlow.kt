package io.github.magisk317.sesame.task.antSports

internal enum class NeverlandTaskAction {
    EXECUTE,
    RECEIVE,
    COMPLETE,
    REQUIRES_SIGNUP,
    WAIT,
    UNSUPPORTED,
}

internal data class NeverlandTaskState(
    val taskId: String,
    val taskType: String,
    val taskStatus: String,
    val progress: String,
    val condition: String,
)

internal fun neverlandTaskAction(taskType: String, taskStatus: String): NeverlandTaskAction {
    if (taskStatus == "NOT_SIGNUP") return NeverlandTaskAction.REQUIRES_SIGNUP
    if (taskType !in NEVERLAND_SUPPORTED_TASK_TYPES) return NeverlandTaskAction.UNSUPPORTED

    return when (taskStatus) {
        "INIT", "SIGNUP_COMPLETE" -> NeverlandTaskAction.EXECUTE
        "TO_RECEIVE" -> NeverlandTaskAction.RECEIVE
        "RECEIVE_SUCCESS", "FINISHED" -> NeverlandTaskAction.COMPLETE
        else -> NeverlandTaskAction.WAIT
    }
}

internal fun neverlandTaskFingerprint(states: Collection<NeverlandTaskState>): String = states
    .sortedWith(compareBy(NeverlandTaskState::taskId, NeverlandTaskState::taskType))
    .joinToString(separator = "|") { state ->
        listOf(
            state.taskId,
            state.taskType,
            state.taskStatus,
            state.progress,
            state.condition,
        ).joinToString(separator = ":")
    }

internal fun neverlandTaskIsUnresolved(
    action: NeverlandTaskAction,
    blocked: Boolean = false,
): Boolean = when (action) {
    NeverlandTaskAction.COMPLETE -> false
    NeverlandTaskAction.EXECUTE,
    NeverlandTaskAction.RECEIVE -> blocked
    NeverlandTaskAction.REQUIRES_SIGNUP,
    NeverlandTaskAction.WAIT,
    NeverlandTaskAction.UNSUPPORTED -> true
}

internal class NeverlandTaskExecutionGuard {
    private val successfulRequests = mutableSetOf<String>()

    fun wasSuccessfullyRequested(state: NeverlandTaskState, action: NeverlandTaskAction): Boolean =
        requestKey(state, action) in successfulRequests

    fun recordSuccessfulRequest(state: NeverlandTaskState, action: NeverlandTaskAction) {
        if (action == NeverlandTaskAction.EXECUTE || action == NeverlandTaskAction.RECEIVE) {
            successfulRequests += requestKey(state, action)
        }
    }

    private fun requestKey(state: NeverlandTaskState, action: NeverlandTaskAction): String =
        "$action:${neverlandTaskFingerprint(listOf(state))}"
}

internal data class NeverlandBuildSnapshot(
    val mapId: String,
    val branchId: String,
    val mapEnergyProcess: Int?,
    val buildingIndex: Int?,
    val buildingEnergyProcess: Int?,
) {
    fun advancedFrom(previous: NeverlandBuildSnapshot): Boolean {
        if (mapId.isNotBlank() && previous.mapId.isNotBlank() && mapId != previous.mapId) return true
        if (mapEnergyProcess != null && previous.mapEnergyProcess != null &&
            mapEnergyProcess > previous.mapEnergyProcess
        ) {
            return true
        }

        val currentIndex = buildingIndex ?: return false
        val previousIndex = previous.buildingIndex ?: return false
        if (currentIndex > previousIndex) return true
        if (currentIndex < previousIndex) return false

        val currentProgress = buildingEnergyProcess ?: return false
        val previousProgress = previous.buildingEnergyProcess ?: return false
        return currentProgress > previousProgress
    }
}

internal fun latestAdvancedNeverlandSnapshot(
    previous: NeverlandBuildSnapshot,
    candidates: Iterable<NeverlandBuildSnapshot?>,
): NeverlandBuildSnapshot? {
    var latest = previous
    for (candidate in candidates) {
        if (candidate == null) continue
        latest = when {
            candidate.advancedFrom(latest) -> mergeNeverlandSnapshotAtSameStage(latest, candidate)
            sameNeverlandStage(latest, candidate) -> mergeNeverlandSnapshotAtSameStage(latest, candidate)
            else -> latest
        }
    }
    return latest.takeIf { it.advancedFrom(previous) }
}

private fun sameNeverlandStage(
    first: NeverlandBuildSnapshot,
    second: NeverlandBuildSnapshot,
): Boolean = first.mapId == second.mapId &&
    first.branchId == second.branchId &&
    first.buildingIndex == second.buildingIndex &&
    first.buildingEnergyProcess == second.buildingEnergyProcess

private fun mergeNeverlandSnapshotAtSameStage(
    current: NeverlandBuildSnapshot,
    candidate: NeverlandBuildSnapshot,
): NeverlandBuildSnapshot {
    if (!sameNeverlandStage(current, candidate)) return candidate
    val mergedMapEnergy = listOfNotNull(current.mapEnergyProcess, candidate.mapEnergyProcess).maxOrNull()
    return candidate.copy(mapEnergyProcess = mergedMapEnergy)
}

internal fun confirmedNeverlandMapEnergyIncrease(
    previous: NeverlandBuildSnapshot,
    candidates: Iterable<NeverlandBuildSnapshot?>,
): Int = candidates
    .asSequence()
    .filterNotNull()
    .filter { candidate -> candidate.mapId == previous.mapId }
    .mapNotNull { candidate ->
        val current = candidate.mapEnergyProcess ?: return@mapNotNull null
        val before = previous.mapEnergyProcess ?: return@mapNotNull null
        (current - before).takeIf { it > 0 }
    }
    .maxOrNull()
    ?: 0

internal class NeverlandNoProgressGuard(
    private val maxConsecutiveUnchanged: Int,
) {
    init {
        require(maxConsecutiveUnchanged > 0)
    }

    var consecutiveUnchanged: Int = 0
        private set

    fun record(progressed: Boolean): Boolean {
        consecutiveUnchanged = if (progressed) 0 else consecutiveUnchanged + 1
        return consecutiveUnchanged >= maxConsecutiveUnchanged
    }
}

internal data class NeverlandBuildBudget(
    val remainingUnits: Int,
    val maxMultiplier: Int,
    val maxCalls: Int,
)

internal class NeverlandBuildCallLimiter(maxCalls: Int) {
    init {
        require(maxCalls > 0)
    }

    var remainingCalls: Int = maxCalls
        private set

    fun record(calls: Int) {
        remainingCalls = (remainingCalls - calls.coerceAtLeast(0)).coerceAtLeast(0)
    }
}

internal fun neverlandBuildBudget(
    mapEnergyRemaining: Int,
    oneBuildEnergy: Int,
    allowedMultipliers: Collection<Int>,
    availableEnergy: Int,
    hardCallLimit: Int,
): NeverlandBuildBudget {
    require(hardCallLimit > 0)
    val energyPerUnit = oneBuildEnergy.coerceAtLeast(1)
    val mapUnits = divideRoundingUp(mapEnergyRemaining.coerceAtLeast(0), energyPerUnit)
    val energyUnits = availableEnergy.coerceAtLeast(0) / energyPerUnit
    val remainingUnits = minOf(mapUnits, energyUnits)
    val multipliers = normalizedNeverlandMultipliers(allowedMultipliers)
    val maxMultiplier = multipliers.maxOrNull() ?: 1
    val maxReachableThisRound = maxMultiplier * hardCallLimit
    val requiredCalls = when {
        remainingUnits <= 0 -> 0
        remainingUnits > maxReachableThisRound -> hardCallLimit
        else -> minimumNeverlandBuildCalls(remainingUnits, multipliers, hardCallLimit) ?: 0
    }
    return NeverlandBuildBudget(
        remainingUnits = remainingUnits,
        maxMultiplier = maxMultiplier,
        maxCalls = requiredCalls,
    )
}

internal fun neverlandBuildMultiplier(
    remainingUnits: Int,
    availableEnergy: Int,
    oneBuildEnergy: Int,
    allowedMultipliers: Collection<Int>,
    callsRemaining: Int,
): Int {
    if (callsRemaining <= 0) return 0
    val energyUnits = availableEnergy.coerceAtLeast(0) / oneBuildEnergy.coerceAtLeast(1)
    val upperBound = minOf(remainingUnits.coerceAtLeast(0), energyUnits, 10)
    if (upperBound <= 0) return 0
    val multipliers = normalizedNeverlandMultipliers(allowedMultipliers)
    val candidates = multipliers.filter { it <= upperBound }.sortedDescending()
    val maxMultiplier = multipliers.maxOrNull() ?: return 0

    if (remainingUnits > maxMultiplier * callsRemaining) {
        return candidates.firstOrNull() ?: 0
    }

    return candidates.firstOrNull { candidate ->
        val after = remainingUnits - candidate
        after == 0 || minimumNeverlandBuildCalls(after, multipliers, callsRemaining - 1) != null
    } ?: 0
}

internal fun neverlandViewDelayMillis(viewSeconds: Int): Long =
    viewSeconds.coerceAtLeast(0).toLong() * 1_000L

internal enum class NeverlandBuildStatus {
    ADVANCED,
    UNCHANGED,
    SKIPPED,
    FAILED,
}

internal data class NeverlandBuildOutcome(
    val status: NeverlandBuildStatus,
    val calls: Int = 0,
    val stopReason: String,
) {
    val progressed: Boolean
        get() = status == NeverlandBuildStatus.ADVANCED

    companion object {
        fun skipped(reason: String, calls: Int = 0) = NeverlandBuildOutcome(
            status = NeverlandBuildStatus.SKIPPED,
            calls = calls,
            stopReason = reason,
        )

        fun failed(reason: String, calls: Int = 0) = NeverlandBuildOutcome(
            status = NeverlandBuildStatus.FAILED,
            calls = calls,
            stopReason = reason,
        )
    }
}

internal data class NeverlandWorkflowResult(
    val rounds: Int,
    val postBuildRescans: Int,
    val lastOutcome: NeverlandBuildOutcome,
    val reachedRoundLimit: Boolean,
)

internal class NeverlandBuildCoordinator(
    private val maxRounds: Int,
    private val buildRound: suspend () -> NeverlandBuildOutcome,
    private val rescanTaskCenter: suspend () -> Unit,
    private val pause: suspend (Long) -> Unit,
) {
    init {
        require(maxRounds > 0)
    }

    suspend fun run(rescanEnabled: Boolean): NeverlandWorkflowResult {
        var rescans = 0
        var lastOutcome = NeverlandBuildOutcome.skipped("尚未执行建造")

        repeat(maxRounds) { index ->
            lastOutcome = buildRound()
            if (!lastOutcome.progressed) {
                return NeverlandWorkflowResult(
                    rounds = index + 1,
                    postBuildRescans = rescans,
                    lastOutcome = lastOutcome,
                    reachedRoundLimit = false,
                )
            }

            if (rescanEnabled) {
                rescanTaskCenter()
                rescans++
            }

            if (index < maxRounds - 1) pause(NEVERLAND_FLOW_ROUND_DELAY_MS)
        }

        return NeverlandWorkflowResult(
            rounds = maxRounds,
            postBuildRescans = rescans,
            lastOutcome = lastOutcome,
            reachedRoundLimit = true,
        )
    }
}

private val NEVERLAND_SUPPORTED_TASK_TYPES = setOf(
    "PROMOKERNEL_TASK",
    "LIGHT_TASK",
    "GAME_TASK",
)

private const val NEVERLAND_FLOW_ROUND_DELAY_MS = 1_000L

private fun normalizedNeverlandMultipliers(allowedMultipliers: Collection<Int>): List<Int> =
    allowedMultipliers.asSequence()
        .filter { it in 1..10 }
        .distinct()
        .sorted()
        .toList()
        .ifEmpty { listOf(1) }

private fun minimumNeverlandBuildCalls(
    units: Int,
    multipliers: Collection<Int>,
    maxCalls: Int,
): Int? {
    if (units == 0) return 0
    if (units < 0 || maxCalls <= 0) return null
    val maximumReachable = (multipliers.maxOrNull() ?: return null) * maxCalls
    if (units > maximumReachable) return null

    val unreachable = maxCalls + 1
    val minimumCalls = IntArray(units + 1) { unreachable }
    minimumCalls[0] = 0
    for (current in 1..units) {
        for (multiplier in multipliers) {
            if (multiplier > current) break
            val previous = minimumCalls[current - multiplier]
            if (previous < maxCalls) {
                minimumCalls[current] = minOf(minimumCalls[current], previous + 1)
            }
        }
    }
    return minimumCalls[units].takeIf { it <= maxCalls }
}

private fun divideRoundingUp(value: Int, divisor: Int): Int {
    if (value <= 0) return 0
    return ((value.toLong() + divisor - 1L) / divisor).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
