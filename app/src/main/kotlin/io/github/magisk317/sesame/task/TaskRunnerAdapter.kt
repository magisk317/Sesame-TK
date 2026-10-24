package io.github.magisk317.sesame.task

import io.github.magisk317.sesame.model.BaseModel
import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.util.GlobalThreadPools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/** Java-facing adapter around the coroutine task runner. */
class TaskRunnerAdapter @JvmOverloads constructor(
    models: List<Model> = Model.getModelArray().toList(),
    private val scope: CoroutineScope = GlobalThreadPools.currentScope(),
) {
    private val coroutineTaskRunner = CoroutineTaskRunner(models)
    @Volatile
    private var currentJob: Job? = null

    fun run() {
        run(true, null)
    }

    fun run(isFirst: Boolean, mode: ModelTask.TaskExecutionMode?) {
        run(isFirst, mode, BaseModel.taskExecutionRounds.value)
    }

    fun run(isFirst: Boolean, mode: ModelTask.TaskExecutionMode?, rounds: Int) {
        stop()
        currentJob = coroutineTaskRunner.launchIn(scope, isFirst, rounds)
    }

    fun stop() {
        currentJob?.let { job ->
            currentJob = null
            if (job.isActive) job.cancel()
        }
    }

    companion object {
        @JvmStatic
        fun runAllTasks() {
            runAllTasks(null)
        }

        @JvmStatic
        fun runAllTasks(mode: ModelTask.TaskExecutionMode?) {
            TaskRunnerAdapter().run(true, mode)
        }
    }
}
