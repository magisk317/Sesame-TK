package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestFriendBatchServiceTest {
    @Test
    fun processesJsonFriendsAndPreservesFlag() = runBlocking {
        val flags = mutableListOf<String>()
        var processed = 0
        ForestFriendBatchService(
            errorWait = { false },
            fillUserRobFlag = { _, _ -> error("not needed") },
            processFriend = { _, flag -> flags += flag; processed++ },
            displayName = { "好友" },
            concurrency = 2,
        ).process(
            jsonArray(
                jsonObject("userId" to "one"),
                jsonObject("userId" to "two"),
            ),
            "pk",
        )

        assertEquals(2, processed)
        assertEquals(listOf("pk", "pk"), flags.sorted())
    }
}
