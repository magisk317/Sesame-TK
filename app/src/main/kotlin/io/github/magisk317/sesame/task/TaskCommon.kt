package io.github.magisk317.sesame.task

import io.github.magisk317.sesame.model.BaseModel
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeUtil

object TaskCommon {
    @JvmField
    @Volatile
    var IS_ENERGY_TIME: Boolean = false

    @JvmField
    @Volatile
    var IS_AFTER_8AM: Boolean = false

    @JvmField
    @Volatile
    var IS_MODULE_SLEEP_TIME: Boolean = false

    @JvmStatic
    @Synchronized
    fun update() {
        val now = System.currentTimeMillis()
        val energyTime = checkTimeRangeConfig(BaseModel.energyTime.value.filterNotNull(), "只收能量时间", now)
        val moduleSleep = checkTimeRangeConfig(BaseModel.modelSleepTime.value.filterNotNull(), "模块休眠时间", now)
        val after8am = TimeUtil.isAfterOrCompareTimeStr(now, "0800")
        // 写入顺序：先写依赖项，最后写 IS_AFTER_8AM（最常被读取的哨兵）
        IS_ENERGY_TIME = energyTime
        IS_MODULE_SLEEP_TIME = moduleSleep
        IS_AFTER_8AM = after8am
    }

    @JvmStatic
    fun isConfigDisabled(config: List<String>?): Boolean =
        config.isNullOrEmpty() || config.first().trim() == "-1"

    private fun checkTimeRangeConfig(config: List<String>?, label: String, now: Long): Boolean {
        if (isConfigDisabled(config)) {
            StructuredLog.record("$label 配置已关闭")
            return false
        }
        StructuredLog.record("获取 $label 配置: $config")
        return TimeUtil.checkInTimeRange(now, config)
    }
}
