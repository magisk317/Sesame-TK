package io.github.magisk317.sesame.task

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoroutineTaskRunnerTest {
    @Test
    fun `recognizes long-running task display names`() {
        assertTrue(CoroutineTaskRunner.isTimeoutWhitelisted("蚂蚁森林"))
        assertTrue(CoroutineTaskRunner.isTimeoutWhitelisted("蚂蚁庄园"))
        assertTrue(CoroutineTaskRunner.isTimeoutWhitelisted("运动"))
    }

    @Test
    fun `keeps ordinary tasks on the standard timeout`() {
        assertFalse(CoroutineTaskRunner.isTimeoutWhitelisted("农场"))
        assertFalse(CoroutineTaskRunner.isTimeoutWhitelisted("会员任务"))
    }
}
