package io.github.magisk317.sesame.data

/**
 * 每日标志位存储
 *
 * 封装 Status 中的 flagList 和 intFlagMap 操作，提供统一的每日状态追踪 API。
 * 所有方法均为纯函数，操作传入的集合引用，便于测试和复用。
 *
 * 标志位命名约定：`{task}::{action}` 或 `{task}::{action}::{id}`
 * 示例：`forest::VitalityExchangeLimit::xxx`、`youth_privilege_checkin_done`
 */
object FlagStore {

    /**
     * 检查今日是否已设置某标志
     * @param flagList 标志集合（通常为 Status.INSTANCE.flagList）
     * @param flag 标志名称
     */
    @JvmStatic
    fun hasFlagToday(flagList: MutableSet<String>, flag: String): Boolean =
        flagList.contains(flag)

    /**
     * 设置今日标志
     * @return true 如果标志是新添加的，false 如果已存在
     */
    @JvmStatic
    fun setFlagToday(flagList: MutableSet<String>, flag: String): Boolean =
        flagList.add(flag)

    /**
     * 获取自定义整数标志
     */
    @JvmStatic
    fun getIntFlag(intFlagMap: MutableMap<String, Int>, key: String): Int? =
        intFlagMap[key]

    /**
     * 设置自定义整数标志
     */
    @JvmStatic
    fun setIntFlag(intFlagMap: MutableMap<String, Int>, key: String, value: Int) {
        intFlagMap[key] = value
    }

    /**
     * 检查某 benefitId 今日是否已兑换（通用去重检查）
     * @return true 如果尚未兑换（可执行），false 如果已兑换
     */
    @JvmStatic
    fun canExchangeBenefitToday(logList: MutableSet<String>, benefitId: String): Boolean =
        !logList.contains(benefitId)

    /**
     * 标记某 benefitId 今日已兑换
     * @return true 如果是新添加的
     */
    @JvmStatic
    fun markBenefitExchanged(logList: MutableSet<String>, benefitId: String): Boolean =
        logList.add(benefitId)
}
