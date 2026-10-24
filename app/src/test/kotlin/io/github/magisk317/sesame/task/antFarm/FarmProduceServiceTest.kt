package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FarmProduceServiceTest {
    private val service = FarmProduceService {}

    @Test
    fun parsesSuccessfulHarvest() {
        val harvest = service.parse(
            jsonObject(
                "success" to true,
                "harvestBenevolenceScore" to 3.5,
                "finalBenevolenceScore" to 8.0,
            ),
        )

        assertEquals(3.5, harvest?.amount)
        assertEquals(8.0, harvest?.remaining)
    }

    @Test
    fun rejectsFailedOrIncompleteResponse() {
        assertNull(service.parse(jsonObject("success" to false)))
        assertNull(service.parse(jsonObject("success" to true, "harvestBenevolenceScore" to 1.0)))
    }
}
