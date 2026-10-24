package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForestPatrolRpcTest {
    @Test
    fun `patrol payloads share source and timezone`() {
        val query = ForestPatrolRpcPayloads.base()
        assertEquals("ant_forest", query.getString("source"))
        assertEquals("Asia/Shanghai", query.getString("timezoneId"))

        val keepGoing = ForestPatrolRpcPayloads.patrolKeepGoing(2, 7, "video")
        assertEquals("Y", keepGoing.getJSONObject("reactParam").getString("viewed"))
        assertEquals(2, keepGoing.getInt("nodeIndex"))
        assertEquals(7, keepGoing.getInt("patrolId"))
    }
}
