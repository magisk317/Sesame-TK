package io.github.magisk317.sesame.task.youthPrivilege

import io.github.magisk317.sesame.hook.RequestManager

/**
 * 青春豆 RPC 网关
 *
 * 青春特权已升级为"青春豆"系统，通过 H5 页面调用。
 * 所有方法参数从抓包数据提取，与 H5 页面实际调用一致。
 *
 * 实测结果（2026-08-22）：
 * - queryExchangeModel: ✅ [{"chInfo":"searchxsth"}] 返回兑换列表
 * - queryTaskModel: ✅ [{"chInfo":"searchxsth","skipTaskList":false}] 返回任务列表
 * - queryCheckInModel: ✅ [{"chInfo":"searchxsth","queryAd":true,"skipTaskModule":false}] 返回签到状态
 * - checkIn: ⚠️ [{}] 返回 NOT_TARGET_USER（账号不在目标组）
 * - queryDetailFlow: ✅ [{"pageNo":1,"pageSize":10,...}] 返回明细流水
 * - queryLotteryEntrance: ⚠️ [{"sceneCode":"STUDENT_MONEY_CHECK_IN"}] 返回 CONFIG_DATA_NOT_EXISTS
 * - queryPrivilegeEnableInfo: ⚠️ [{"sceneCode":"STUDENT_MONEY_CHECK_IN"}] 返回 PARAM_ILLEGAL
 */
object YouthPrivilegeRpc {

    private const val PREFIX = "com.alipay.mobileopl.youthprivilege.rpc.mgw"
    private const val CH_INFO = "searchxsth"
    private const val SCENE_CODE = "STUDENT_MONEY_CHECK_IN"

    /** 查询签到状态 */
    fun queryCheckInModel(): String = request(
        "$PREFIX.queryCheckInModel",
        "[{\"chInfo\":\"$CH_INFO\",\"queryAd\":true,\"skipTaskModule\":false}]",
    )

    /** 查询可兑换权益列表 */
    fun queryExchangeModel(): String = request(
        "$PREFIX.queryExchangeModel",
        "[{\"chInfo\":\"$CH_INFO\"}]",
    )

    /** 查询任务列表 */
    fun queryTaskModel(): String = request(
        "$PREFIX.queryTaskModel",
        "[{\"chInfo\":\"$CH_INFO\",\"skipTaskList\":false}]",
    )

    /** 查询抽奖入口 */
    fun queryLotteryEntrance(): String = request(
        "$PREFIX.queryLotteryEntrance",
        "[{\"sceneCode\":\"$SCENE_CODE\"}]",
    )

    /** 查询特权启用信息 */
    fun queryPrivilegeEnableInfo(): String = request(
        "$PREFIX.queryPrivilegeEnableInfo",
        "[{\"sceneCode\":\"$SCENE_CODE\"}]",
    )

    /** 查询明细流水（需要 pageNo 和 pageSize 参数） */
    fun queryDetailFlow(pageNo: Int, pageSize: Int): String = request(
        "$PREFIX.queryDetailFlow",
        "[{\"pageNo\":$pageNo,\"pageSize\":$pageSize}]",
    )

    /** 执行签到（账号需在目标组） */
    fun checkIn(): String = request(
        "$PREFIX.checkIn",
        "[{}]",
    )

    private fun request(method: String, payload: String): String =
        RequestManager.requestString(method, payload)
}
