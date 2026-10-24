package io.github.magisk317.sesame.task.antOcean

import io.github.magisk317.sesame.util.StructuredLog

/** Ocean model options and protocol enums kept outside the orchestration class. */
object OceanOptions {
    enum class ApplyAction(val code: Int, val description: String) {
        AVAILABLE(0, "可用"),
        NO_STOCK(1, "无库存"),
        ENERGY_LACK(2, "能量不足");

        companion object {
            @JvmStatic
            fun fromString(value: String?): ApplyAction? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: run {
                    StructuredLog.error("ApplyAction", "Unknown applyAction: $value")
                    null
                }
        }
    }

    const val DONT_PROTECT = 0
    const val PROTECT_ALL = 1
    const val PROTECT_BEACH = 2
    @JvmField val protectNames = arrayOf("不保护", "保护全部", "仅保护沙滩")

    const val CLEAN = 0
    const val DONT_CLEAN = 1
    @JvmField val cleanNames = arrayOf("选中清理", "选中不清理")
}
