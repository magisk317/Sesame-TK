package io.github.magisk317.sesame.task.youthPrivilege

/**
 * 青春豆系统数据模型
 *
 * 从 H5 页面 RPC 响应解析的数据结构。
 * 所有字段名称与 Alipay 服务端返回的 JSON 字段保持一致。
 */

/** 签到状态信息 */
data class CheckInInfo(
    val action: String,
    val status: String,
    val desc: String,
    val consecutiveNum: Int,
    val morningNum: Int,
    val todayAmount: String,
    val totalAmount: String,
    val yesterdayAmount: String,
    val enableNewPointCheckIn: Boolean,
    val enableNewPrivilege: Boolean,
    val crowdType: String,
) {
    val canCheckIn: Boolean get() = action == "CHECK_IN"
    val isTargetUser: Boolean get() = crowdType != "NOT_TARGET" && crowdType.isNotEmpty()
    val isMorningPeriod: Boolean get() = desc.contains("5:00-10:00") || desc.contains("早起")
}

/** 可兑换权益 */
data class ExchangeItem(
    val benefitId: String,
    val title: String,
    val value: String,
    val tag: String,
    val status: String,
    val subText: String,
    val actionUrl: String,
) {
    val isAvailable: Boolean get() = status == "AVAILABLE" || status == "UNDER_THRESHOLD"
}

/** 青春豆任务 */
data class YouthTask(
    val taskCode: String,
    val taskName: String,
    val taskSubName: String,
    val taskType: String,
    val taskStatus: String,
    val taskAction: String,
    val prizeAmount: String,
    val currentCount: Int,
    val totalCount: Int,
    val taskUrl: String,
) {
    val isCompleted: Boolean get() = taskStatus == "COMPLETED" || taskStatus == "RECEIVED"
    val isApplyable: Boolean get() = taskAction == "SIGNUP" && taskStatus == "TO_APPLY"
}

/** 明细流水查询结果 */
data class DetailFlowResult(
    val hasMore: Boolean,
    val enableNewPointCheckIn: Boolean,
)
