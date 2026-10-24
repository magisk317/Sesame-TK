package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestTakeLookServiceTest {
    @Test
    fun continuesUntilServerStopsReturningFriends() {
        val responses = ArrayDeque(
            listOf(
                jsonObject("success" to true, "actionType" to "FRIEND", "friendId" to "friend-1"),
                jsonObject("success" to true, "actionType" to "FRIEND", "friendId" to "friend-2"),
                jsonObject("success" to true, "actionType" to "NONE"),
            ),
        )
        val requests = mutableListOf<Set<String>>()
        val collected = mutableListOf<String>()
        val service = ForestTakeLookService(
            currentUserId = { "self" },
            takeLook = { skipUsers ->
                requests += skipUsers.keys
                responses.removeFirst()
            },
            queryFriendHome = { jsonObject() },
            collectEnergy = { userId, _, _ -> collected += userId },
            sleep = {},
        )

        service.run(fastMode = false)

        assertEquals(listOf("friend-1", "friend-2"), collected)
        assertEquals(listOf(emptySet(), setOf("friend-1"), setOf("friend-1", "friend-2")), requests)
    }

    @Test
    fun fastModeCollectsWithoutProtectionOrSkipFiltering() {
        val responses = ArrayDeque(
            listOf(
                jsonObject("success" to true, "actionType" to "FRIEND", "friendId" to "friend"),
                jsonObject("success" to true, "actionType" to "NONE"),
            ),
        )
        var collected = 0
        val service = ForestTakeLookService(
            currentUserId = { "self" },
            takeLook = { responses.removeFirst() },
            queryFriendHome = { jsonObject() },
            collectEnergy = { _, _, _ -> collected++ },
            sleep = {},
        )

        service.run(fastMode = true)

        assertEquals(1, collected)
    }
}
