package io.github.magisk317.sesame.task

import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.task.antForest.AntForest
import io.github.magisk317.sesame.util.StructuredLog

/**
 * 默认任务检查策略
 *
 * 包含能量时间判断逻辑，依赖 AntForest 配置状态。
 * 此策略可被替换以实现测试隔离或自定义调度规则。
 */
object DefaultCheckPolicy : CheckPolicy {

    @Volatile
    var energyTimeBlockingEnabled: Boolean = true

    override fun shouldBlock(taskName: String?): Boolean {
        if (!energyTimeBlockingEnabled) return false

        // 只有蚂蚁森林启用且当前不是蚂蚁森林任务时，才拦截能量时间
        if (taskName != "蚂蚁森林") {
            val antForest = Model.getModel(AntForest::class.java)
            if (antForest != null && antForest.isEnable()) {
                if (TaskCommon.IS_ENERGY_TIME) {
                    StructuredLog.record(
                        taskName ?: "Task",
                        "⏸ 当前为只收能量时间【${io.github.magisk317.sesame.model.BaseModel.energyTime.value}】，停止执行${taskName}任务！",
                    )
                    return true
                }
            }
        }
        return false
    }
}
