package io.github.magisk317.sesame.task.youthPrivilege

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.toJsonObject

/**
 * 青春豆业务服务
 *
 * 青春特权已升级为"青春豆"系统。提供签到查询、兑换查询、任务查询功能。
 *
 * 实测结果（2026-08-22）：
 * - queryExchangeModel: ✅ 正常，返回可兑换权益列表
 * - queryTaskModel: ✅ 正常，返回任务列表
 * - queryCheckInModel: ⚠️ 需要 H5 会话上下文，独立调用返回 6004
 * - checkIn: ⚠️ 方法存在但账号不在目标组（NOT_TARGET_USER）
 * - queryDetailFlow: ✅ 正常，无数据时返回 DETAIL_FLOW_NOT_EXIST
 *
 * 注意：当前账号 crowdType=NOT_TARGET，无法执行签到。
 */
class YouthPrivilegeService(
    private val rpc: YouthPrivilegeRpc = YouthPrivilegeRpc,
) {

    private companion object {
        const val TAG = "YouthPrivilege"
        const val FLAG_CHECKIN_DONE = "youth_privilege_checkin_done"
    }

    // ==================== 签到查询 ====================

    /**
     * 查询签到状态
     * @return 签到信息，查询失败返回 null
     * 注意：独立调用可能返回 6004，建议在 H5 页面上下文触发
     */
    fun queryCheckIn(): CheckInInfo? {
        try {
            val response = rpc.queryCheckInModel().toJsonObject()
            val resultCode = response.optString("resultCode")
            if (resultCode != "SUCCESS") {
                StructuredLog.record(TAG, "签到查询异常: code=$resultCode msg=${response.optString("resultMessage")}")
                return null
            }

            val checkInInfo = response.optJSONObject("studentCheckInInfo")
            val amountInfo = response.optJSONObject("studentCheckInAmountInfo")

            return CheckInInfo(
                action = checkInInfo?.optString("action") ?: "",
                status = checkInInfo?.optString("status") ?: "",
                desc = checkInInfo?.optString("desc") ?: "",
                consecutiveNum = checkInInfo?.optInt("consecutiveCheckInNum") ?: 0,
                morningNum = checkInInfo?.optInt("morningCheckInNum") ?: 0,
                todayAmount = amountInfo?.optString("todayAmount") ?: "0",
                totalAmount = amountInfo?.optString("totalAmount") ?: "0",
                yesterdayAmount = amountInfo?.optString("yesterdayAmount") ?: "0",
                enableNewPointCheckIn = response.optBoolean("enableNewPointCheckIn"),
                enableNewPrivilege = response.optBoolean("enableNewPrivilege"),
                crowdType = response.optString("crowdType"),
            )
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "签到查询异常", e)
            return null
        }
    }

    /**
     * 检查今天是否已签到
     */
    fun hasCheckedInToday(): Boolean = Status.hasFlagToday(FLAG_CHECKIN_DONE)

    /**
     * 标记今天已签到
     */
    fun markCheckedInToday() = Status.setFlagToday(FLAG_CHECKIN_DONE)

    // ==================== 兑换查询 ====================

    /**
     * 查询可兑换权益列表
     */
    fun queryExchangeItems(): List<ExchangeItem> {
        try {
            val response = rpc.queryExchangeModel().toJsonObject()
            if (response.optString("resultCode") != "SUCCESS") {
                StructuredLog.record(TAG, "兑换查询失败: ${response.optString("resultMessage")}")
                return emptyList()
            }

            val benefitList = response.optJSONArray("exchangeBenefitInfoList") ?: return emptyList()
            val items = mutableListOf<ExchangeItem>()
            for (i in 0 until benefitList.length()) {
                val item = benefitList.optJSONObject(i) ?: continue
                items.add(
                    ExchangeItem(
                        benefitId = item.optString("benefitId"),
                        title = item.optString("title"),
                        value = item.optString("value"),
                        tag = item.optString("tag"),
                        status = item.optString("benefitInfoStatus"),
                        subText = item.optString("subText"),
                        actionUrl = item.optString("actionUrl"),
                    )
                )
            }
            return items
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "兑换查询异常", e)
            return emptyList()
        }
    }

    // ==================== 任务查询 ====================

    /**
     * 查询任务列表
     */
    fun queryTasks(): List<YouthTask> {
        try {
            val response = rpc.queryTaskModel().toJsonObject()
            if (response.optString("resultCode") != "SUCCESS") {
                StructuredLog.record(TAG, "任务查询失败: ${response.optString("resultMessage")}")
                return emptyList()
            }

            val taskModule = response.optJSONObject("studentTaskModule") ?: return emptyList()
            val taskGroups = taskModule.optJSONArray("taskGroupList") ?: return emptyList()

            val tasks = mutableListOf<YouthTask>()
            for (i in 0 until taskGroups.length()) {
                val group = taskGroups.optJSONObject(i) ?: continue
                val taskList = group.optJSONArray("taskList") ?: continue
                for (j in 0 until taskList.length()) {
                    val task = taskList.optJSONObject(j) ?: continue
                    tasks.add(
                        YouthTask(
                            taskCode = task.optString("taskCode"),
                            taskName = task.optString("taskName"),
                            taskSubName = task.optString("taskSubName"),
                            taskType = task.optString("taskType"),
                            taskStatus = task.optString("taskStatus"),
                            taskAction = task.optString("taskAction"),
                            prizeAmount = task.optString("prizeAmount"),
                            currentCount = task.optInt("currentCount"),
                            totalCount = task.optInt("totalCount"),
                            taskUrl = task.optString("taskUrl"),
                        )
                    )
                }
            }
            return tasks
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "任务查询异常", e)
            return emptyList()
        }
    }

    // ==================== 明细查询 ====================

    /**
     * 查询明细流水
     * @param pageNo 页码（从 1 开始）
     * @param pageSize 每页条数
     */
    fun queryDetailFlow(pageNo: Int = 1, pageSize: Int = 10): DetailFlowResult? {
        try {
            val response = rpc.queryDetailFlow(pageNo, pageSize).toJsonObject()
            val resultCode = response.optString("resultCode")
            if (resultCode != "SUCCESS") {
                if (resultCode != "DETAIL_FLOW_NOT_EXIST") {
                    StructuredLog.record(TAG, "明细查询异常: code=$resultCode msg=${response.optString("resultMessage")}")
                }
                return null
            }

            return DetailFlowResult(
                hasMore = response.optBoolean("hasMore"),
                enableNewPointCheckIn = response.optBoolean("enableNewPointCheckIn"),
            )
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "明细查询异常", e)
            return null
        }
    }
}
