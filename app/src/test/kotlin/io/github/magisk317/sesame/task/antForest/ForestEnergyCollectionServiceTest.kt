package io.github.magisk317.sesame.task.antForest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForestEnergyCollectionServiceTest {
    @Test
    fun retryAndWaitPoliciesUseCapturedLimits() {
        assertEquals(true, ForestEnergyCollectionService.shouldRetry(1, 3))
        assertEquals(false, ForestEnergyCollectionService.shouldRetry(3, 3))
    }
}
