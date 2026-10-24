package io.github.magisk317.sesame.task

/**
 * 任务执行检查策略
 *
 * 将 ModelTask.check() 中的阻塞判断逻辑抽取为可替换策略，
 * 解除基类对具体 task（如 AntForest）的硬依赖。
 */
fun interface CheckPolicy {
    /**
     * @param taskName 当前任务名称
     * @return true = 阻止任务执行, false = 允许执行
     */
    fun shouldBlock(taskName: String?): Boolean
}
