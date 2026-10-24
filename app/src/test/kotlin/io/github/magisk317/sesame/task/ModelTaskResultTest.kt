package io.github.magisk317.sesame.task

import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ModelTaskResultTest {
    @Test
    fun `reports success and increments the run count once`() = runBlocking {
        val task = TestTask { }
        task.enable()

        try {
            val result = task.startTaskWithResult(rounds = 1).await()

            assertEquals(TaskExecutionResult.Status.SUCCESS, result.status)
            assertEquals(1, task.runCents)
        } finally {
            task.stopTask()
        }
    }

    @Test
    fun `reports skipped when task is disabled`() = runBlocking {
        val task = TestTask { error("disabled task must not run") }

        try {
            val result = task.startTaskWithResult(rounds = 1).await()

            assertEquals(TaskExecutionResult.Status.SKIPPED, result.status)
            assertEquals(0, task.runCents)
        } finally {
            task.stopTask()
        }
    }

    @Test
    fun `reports failure instead of completing successfully`() = runBlocking {
        val task = TestTask { error("expected failure") }
        task.enable()

        try {
            val result = task.startTaskWithResult(rounds = 1).await()

            assertEquals(TaskExecutionResult.Status.FAILED, result.status)
            assertTrue(result.errorMessage.orEmpty().contains("expected failure"))
        } finally {
            task.stopTask()
        }
    }

    @Test
    fun `propagates cancellation`() = runBlocking {
        val task = TestTask {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
        }
        task.enable()
        val deferred = task.startTaskWithResult(rounds = 1)

        try {
            deferred.cancel()
            var cancelled = false
            try {
                deferred.await()
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        } finally {
            task.stopTask()
        }
    }

    @Test
    fun `does not report success when cancellation arrives during synchronous work`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val task = TestTask {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "test task was not released" }
        }
        task.enable()
        val deferred = task.startTaskWithResult(rounds = 1)

        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            deferred.cancel()
            release.countDown()

            var cancelled = false
            try {
                deferred.await()
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
        } finally {
            release.countDown()
            task.stopTask()
        }
    }

    @Test
    fun `stop keeps synchronous task gated until its execution exits`() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val task = TestTask {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "test task was not released" }
        }
        task.enable()
        val running = task.startTaskWithResult(rounds = 1)

        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            task.stopTask()
            assertTrue(task.isRunning, "blocking execution must retain the re-entry gate")

            val retry = task.startTaskWithResult(rounds = 1).await()
            assertEquals(TaskExecutionResult.Status.SKIPPED, retry.status)

            release.countDown()
            running.join()
            assertFalse(task.isRunning)
        } finally {
            release.countDown()
            task.stopTask()
        }
    }

    private class TestTask(
        private val action: suspend () -> Unit,
    ) : ModelTask() {
        override fun getName(): String = "结果测试任务"

        override fun getGroup(): ModelGroup = ModelGroup.OTHER

        override fun getIcon(): String = ""

        override fun getFields(): ModelFields = ModelFields()

        override fun check(): Boolean = true

        override suspend fun runSuspend() {
            action()
        }

        fun enable() {
            getEnableField().value = true
        }
    }
}
