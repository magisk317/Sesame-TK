package io.github.magisk317.sesame.task.antSports

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NeverlandFlowTest {
    @Test
    fun `maps game task states to a closed submit receive complete flow`() {
        assertEquals(NeverlandTaskAction.EXECUTE, neverlandTaskAction("GAME_TASK", "INIT"))
        assertEquals(NeverlandTaskAction.EXECUTE, neverlandTaskAction("GAME_TASK", "SIGNUP_COMPLETE"))
        assertEquals(NeverlandTaskAction.RECEIVE, neverlandTaskAction("GAME_TASK", "TO_RECEIVE"))
        assertEquals(NeverlandTaskAction.COMPLETE, neverlandTaskAction("GAME_TASK", "RECEIVE_SUCCESS"))
        assertEquals(NeverlandTaskAction.COMPLETE, neverlandTaskAction("GAME_TASK", "FINISHED"))
        assertEquals(NeverlandTaskAction.REQUIRES_SIGNUP, neverlandTaskAction("GAME_TASK", "NOT_SIGNUP"))
        assertEquals(NeverlandTaskAction.WAIT, neverlandTaskAction("GAME_TASK", "WAIT_COMPLETE"))
        assertEquals(NeverlandTaskAction.UNSUPPORTED, neverlandTaskAction("UNKNOWN_TASK", "INIT"))
    }

    @Test
    fun `task fingerprint is order independent and includes progress`() {
        val first = NeverlandTaskState("Task998", "GAME_TASK", "SIGNUP_COMPLETE", "0", "10")
        val second = NeverlandTaskState("Task005", "GAME_TASK", "SIGNUP_COMPLETE", "0", "100")
        val progressed = first.copy(progress = "1")

        assertEquals(
            neverlandTaskFingerprint(listOf(first, second)),
            neverlandTaskFingerprint(listOf(second, first)),
        )
        assertFalse(
            neverlandTaskFingerprint(listOf(first, second)) ==
                neverlandTaskFingerprint(listOf(progressed, second))
        )
    }

    @Test
    fun `signup and blacklisted task states remain unresolved`() {
        assertTrue(neverlandTaskIsUnresolved(NeverlandTaskAction.REQUIRES_SIGNUP))
        assertTrue(neverlandTaskIsUnresolved(NeverlandTaskAction.WAIT))
        assertTrue(neverlandTaskIsUnresolved(NeverlandTaskAction.EXECUTE, blocked = true))
        assertFalse(neverlandTaskIsUnresolved(NeverlandTaskAction.EXECUTE))
        assertFalse(neverlandTaskIsUnresolved(NeverlandTaskAction.COMPLETE))
    }

    @Test
    fun `successful request guard survives task center rescans and unlocks on state change`() {
        val guard = NeverlandTaskExecutionGuard()
        val initial = NeverlandTaskState("Task998", "GAME_TASK", "INIT", "0", "1")

        assertFalse(guard.wasSuccessfullyRequested(initial, NeverlandTaskAction.EXECUTE))
        guard.recordSuccessfulRequest(initial, NeverlandTaskAction.EXECUTE)
        assertTrue(guard.wasSuccessfullyRequested(initial, NeverlandTaskAction.EXECUTE))
        assertFalse(
            guard.wasSuccessfullyRequested(
                initial.copy(taskStatus = "TO_RECEIVE", progress = "1"),
                NeverlandTaskAction.RECEIVE,
            )
        )
    }

    @Test
    fun `build snapshot advances on map building or stage progress only`() {
        val initial = snapshot(mapProgress = 1_255, buildingIndex = 2, buildingProgress = 205)

        assertTrue(snapshot(mapProgress = 1_260, buildingIndex = 2, buildingProgress = 210).advancedFrom(initial))
        assertTrue(snapshot(mapProgress = 1_255, buildingIndex = 3, buildingProgress = 0).advancedFrom(initial))
        assertTrue(snapshot(mapId = "MM140", mapProgress = 0, buildingIndex = 0, buildingProgress = 0).advancedFrom(initial))
        assertFalse(initial.advancedFrom(initial))
        assertFalse(snapshot(mapProgress = 1_250, buildingIndex = 2, buildingProgress = 200).advancedFrom(initial))
        assertFalse(initial.copy(buildingEnergyProcess = null).advancedFrom(initial))
    }

    @Test
    fun `latest build snapshot does not regress to a stale confirmation`() {
        val initial = snapshot(mapProgress = 1_255, buildingIndex = 2, buildingProgress = 205)
        val response = snapshot(mapProgress = 1_265, buildingIndex = 2, buildingProgress = 215)
        val staleConfirmation = initial.copy()
        val freshConfirmation = snapshot(mapProgress = 1_270, buildingIndex = 2, buildingProgress = 220)

        assertEquals(
            response,
            latestAdvancedNeverlandSnapshot(initial, listOf(response, staleConfirmation)),
        )
        assertEquals(
            freshConfirmation,
            latestAdvancedNeverlandSnapshot(initial, listOf(response, freshConfirmation)),
        )
        assertEquals(null, latestAdvancedNeverlandSnapshot(initial, listOf(initial, staleConfirmation)))
    }

    @Test
    fun `same stage confirmation fills map energy without discarding response progress`() {
        val initial = snapshot(mapProgress = 1_255, buildingIndex = 2, buildingProgress = 205)
        val response = snapshot(mapProgress = null, buildingIndex = 2, buildingProgress = 215)
        val confirmation = snapshot(mapProgress = 1_265, buildingIndex = 2, buildingProgress = 215)

        assertEquals(
            confirmation,
            latestAdvancedNeverlandSnapshot(initial, listOf(response, confirmation)),
        )
    }

    @Test
    fun `map energy increase only uses confirmed forward progress on the same map`() {
        val initial = snapshot(mapProgress = 1_255, buildingIndex = 2, buildingProgress = 205)

        assertEquals(
            15,
            confirmedNeverlandMapEnergyIncrease(
                initial,
                listOf(
                    snapshot(mapProgress = 1_260, buildingIndex = 2, buildingProgress = 210),
                    snapshot(mapProgress = 1_270, buildingIndex = 2, buildingProgress = 220),
                ),
            ),
        )
        assertEquals(
            0,
            confirmedNeverlandMapEnergyIncrease(
                initial,
                listOf(
                    snapshot(mapProgress = 1_250, buildingIndex = 2, buildingProgress = 200),
                    snapshot(mapId = "MM140", mapProgress = 10, buildingIndex = 0, buildingProgress = 10),
                ),
            ),
        )
    }

    @Test
    fun `no progress guard stops after two unchanged successes and resets after progress`() {
        val guard = NeverlandNoProgressGuard(maxConsecutiveUnchanged = 2)

        assertFalse(guard.record(progressed = false))
        assertEquals(1, guard.consecutiveUnchanged)
        assertFalse(guard.record(progressed = true))
        assertEquals(0, guard.consecutiveUnchanged)
        assertFalse(guard.record(progressed = false))
        assertTrue(guard.record(progressed = false))
        assertEquals(2, guard.consecutiveUnchanged)
    }

    @Test
    fun `build budget uses current map progress and a hard call cap`() {
        val budget = neverlandBuildBudget(
            mapEnergyRemaining = 3_295,
            oneBuildEnergy = 5,
            allowedMultipliers = listOf(1, 5, 10),
            availableEnergy = 246_864,
            hardCallLimit = 100,
        )

        assertEquals(659, budget.remainingUnits)
        assertEquals(10, budget.maxMultiplier)
        assertEquals(70, budget.maxCalls)

        var remaining = budget.remainingUnits
        var callsRemaining = budget.maxCalls
        val plan = mutableListOf<Int>()
        while (remaining > 0 && callsRemaining > 0) {
            val multiplier = neverlandBuildMultiplier(
                remaining,
                availableEnergy = 10_000,
                oneBuildEnergy = 5,
                allowedMultipliers = listOf(1, 5, 10),
                callsRemaining = callsRemaining,
            )
            assertTrue(multiplier > 0)
            plan += multiplier
            remaining -= multiplier
            callsRemaining--
        }
        assertEquals(0, remaining)
        assertEquals(70, plan.size)
        assertEquals(659, plan.sum())
        assertEquals(0, neverlandBuildMultiplier(4, 0, 5, listOf(1, 5, 10), callsRemaining = 4))
    }

    @Test
    fun `unreachable sparse multiplier budget fails closed`() {
        val budget = neverlandBuildBudget(
            mapEnergyRemaining = 45,
            oneBuildEnergy = 5,
            allowedMultipliers = listOf(5, 10),
            availableEnergy = 100,
            hardCallLimit = 10,
        )

        assertEquals(9, budget.remainingUnits)
        assertEquals(0, budget.maxCalls)
    }

    @Test
    fun `build call limiter shares one cap across rounds`() {
        val limiter = NeverlandBuildCallLimiter(maxCalls = 100)
        limiter.record(66)
        assertEquals(34, limiter.remainingCalls)
        limiter.record(40)
        assertEquals(0, limiter.remainingCalls)
    }

    @Test
    fun `map availability distinguishes completion from invalid responses`() {
        fun map(status: String) = NeverlandMapSummary(
            mapId = status,
            mapName = status,
            branchId = "MASTER",
            status = status,
        )

        assertEquals(NeverlandMapAvailability.INVALID, neverlandMapAvailability(emptyList()))
        assertEquals(
            NeverlandMapAvailability.ALL_COMPLETE,
            neverlandMapAvailability(
                listOf(map("FINISHED"), map("FINISH_NOT_REWARD"), map("DONE")),
            ),
        )
        assertEquals(
            NeverlandMapAvailability.DOING,
            neverlandMapAvailability(listOf(map("FINISHED"), map("DOING"))),
        )
        assertEquals(
            NeverlandMapAvailability.AVAILABLE,
            neverlandMapAvailability(listOf(map("LOCKED"), map("FINISHED"))),
        )
        assertEquals(
            NeverlandMapAvailability.INVALID,
            neverlandMapAvailability(listOf(map("UNKNOWN"))),
        )
    }

    @Test
    fun `view duration is converted from seconds to milliseconds`() {
        assertEquals(15_000L, neverlandViewDelayMillis(15))
        assertEquals(0L, neverlandViewDelayMillis(-1))
    }

    @Test
    fun `confirmed build progress triggers a task center rescan`() = runBlocking {
        val events = mutableListOf<String>()
        val outcomes = ArrayDeque(
            listOf(
                NeverlandBuildOutcome(NeverlandBuildStatus.ADVANCED, calls = 1, stopReason = "progress"),
                NeverlandBuildOutcome.skipped("energy exhausted"),
            )
        )
        val result = NeverlandBuildCoordinator(
            maxRounds = 4,
            buildRound = {
                events += "build"
                outcomes.removeFirst()
            },
            rescanTaskCenter = { events += "task-center" },
            pause = {},
        ).run(rescanEnabled = true)

        assertEquals(listOf("build", "task-center", "build"), events)
        assertEquals(1, result.postBuildRescans)
        assertEquals(NeverlandBuildStatus.SKIPPED, result.lastOutcome.status)
        assertFalse(result.reachedRoundLimit)
    }

    @Test
    fun `unchanged build does not rescan and repeated progress is bounded`() = runBlocking {
        var rescans = 0
        val unchanged = NeverlandBuildCoordinator(
            maxRounds = 4,
            buildRound = {
                NeverlandBuildOutcome(NeverlandBuildStatus.UNCHANGED, calls = 2, stopReason = "no progress")
            },
            rescanTaskCenter = { rescans++ },
            pause = {},
        ).run(rescanEnabled = true)

        assertEquals(0, rescans)
        assertEquals(1, unchanged.rounds)

        val bounded = NeverlandBuildCoordinator(
            maxRounds = 3,
            buildRound = {
                NeverlandBuildOutcome(NeverlandBuildStatus.ADVANCED, calls = 1, stopReason = "progress")
            },
            rescanTaskCenter = { rescans++ },
            pause = {},
        ).run(rescanEnabled = true)

        assertEquals(3, bounded.rounds)
        assertEquals(3, bounded.postBuildRescans)
        assertTrue(bounded.reachedRoundLimit)
    }

    private fun snapshot(
        mapId: String = "MM139",
        mapProgress: Int?,
        buildingIndex: Int?,
        buildingProgress: Int?,
    ) = NeverlandBuildSnapshot(
        mapId = mapId,
        branchId = "MASTER",
        mapEnergyProcess = mapProgress,
        buildingIndex = buildingIndex,
        buildingEnergyProcess = buildingProgress,
    )
}
