package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.data.Status.Companion.setFlagToday
import io.github.magisk317.sesame.data.StatusFlags
import io.github.magisk317.sesame.hook.internal.SecurityBodyHelper.getSecurityBodyData
import io.github.magisk317.sesame.task.antOrchard.AntOrchardRpcCall.orchardSpreadManure
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.StructuredLog.record
import io.github.magisk317.sesame.util.TaskBlacklist.autoAddToBlacklist
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.Objects

/** Owns Sesame credit tasks, growth guidance tasks, and credit collection. */
class MemberSesameService {
    private val TAG = "MemberSesameService"

    // 2026-08-09 真机通过 queryListV3(空参数) -> joinActivity -> pushActivity 验证。
    // 这些任务返回 SUCCESS，并在随后列表中变为 finishFlag=true。
    private val verifiedRpcTemplates = setOf(
        "zml_wsyh_fuli_erfang",
        "zml_zmzl_cyz_erfang",
        "zml_mybx_bzjrenwu_erfang",
        "zml_eleme_diaoyu_erfang",
        "zml_zmes_shangcheng_erfang",
        "zml_cx_liulan_erfang",
        "zml_zmzl_liulan_erfang",
        "zml_mybx_chexian_erfang",
        "zml_shiguangzahuodian_liulan",
    )

    // 2026-08-09 逐项 RPC 探测结果。失败项不再反复触发同一个已知错误，
    // 但仍保留在服务端任务列表中，待协议变化后可再次复核。
    private val rpcProbeFailures = mapOf(
        "zmxy_zml_wannengxiaozujian" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_zfbyouxi_erfang" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_tbdoudizhu_renwu_sanfang" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_zfbfeizhu_xiadan_sanfang" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_cainiao_guojiang_sanfang" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_langmancanting_renwu" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_qingyunjuezhifumo_renwu" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_zmzl_xdrw_erfang" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_eduka_renwu" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_tbxiaoxiaole_renwu_sanfang" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_tbjb_lingjinbi_erfang" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_kuaishou_jisuban_sanfang" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_tbbbnc_shifei_sanfang" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_wdhysj_7dacu_chongzhi" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_lieyanjuexing_renwu" to "ILLEGAL_ARGUMENT: promiseActivityExtCheck 参数无效",
        "zml_shangoulm_renwu3_erfang" to "OP_REPEAT_CHECK: 操作太频繁，请稍后再试",
        "zml_zfbcaifu_shipin_erfang" to "OP_REPEAT_CHECK: 操作太频繁，请稍后再试",
        "zml_leyouji_liulan_erfang" to "OP_REPEAT_CHECK: 操作太频繁，请稍后再试",
        "zml_chuxingxiaotao_shengtai_2" to "OP_REPEAT_CHECK: 操作太频繁，请稍后再试",
        "zml_zfbwanzhuan_renwu_erfang" to "OP_REPEAT_CHECK: 操作太频繁，请稍后再试",
        "zml_sjfw_jifen_erfang" to "OP_REPEAT_CHECK: 操作太频繁，请稍后再试",
    )

    fun runGrowthGuideTasks() {
        try {
            var resp: String?
            try {
                resp = ZmxyRpc.queryGrowthGuideToDoList("yuebao_7d", "1.0.2025.10.27")
            } catch (e: Throwable) {
                StructuredLog.printStackTrace("$TAG.handleGrowthGuideTasks.queryGrowthGuideToDoList", e)
                return
            }

            if (resp.isEmpty()) {
                record("$TAG.handleGrowthGuideTasks", "信誉任务列表返回空")
                return
            }

            val root: JsonObject?
            try {
                root = resp.toJsonObject()
            } catch (e: Throwable) {
                StructuredLog.printStackTrace("$TAG.handleGrowthGuideTasks.parseRootJson", e)
                return
            }

            if (!ResChecker.checkRes(TAG, root)) {
                record(
                    "$TAG.handleGrowthGuideTasks", "信誉任务列表获取失败: " + root.optString("resultView", resp)
                )
                return
            }
            // 成长引导列表（不会用，只做计数）
            val growthGuideList = root.optJSONArray("growthGuideList")
            growthGuideList?.length() ?: 0

            // 待处理任务列表
            val toDoList = root.optJSONArray("toDoList")
            val toDoCount = toDoList?.length() ?: 0
            if (toDoList == null || toDoCount == 0) {
                return
            }

            for (i in 0..<toDoList.length()) {
                var task: JsonObject? = null
                try {
                    task = toDoList.optJSONObject(i)
                } catch (_: Throwable) {
                }

                if (task == null) continue

                val behaviorId = task.optString("behaviorId", "")
                val title = task.optString("title", "")
                val status = task.optString("status", "")
                val subTitle = task.optString("subTitle", "")

                // ===== 2.1 公益类任务 =====
                if ("wait_receive" == status) {
                    val openResp: String?
                    try {
                        openResp = ZmxyRpc.openBehaviorCollect(behaviorId)
                    } catch (e: Throwable) {
                        StructuredLog.printStackTrace("$TAG.handleGrowthGuideTasks.openBehaviorCollect", e)
                        continue
                    }

                    try {
                        val openJo = openResp.toJsonObject()
                        if (ResChecker.checkRes(TAG, openJo)) {
                            StructuredLog.other("信誉任务[领取成功] $title")
                        } else {
                            record(
                                "$TAG.handleGrowthGuideTasks", ("信誉任务[领取失败] behaviorId=" + behaviorId + " title=" + title + " resp=" + openResp)
                            )
                        }
                    } catch (e: Throwable) {
                        StructuredLog.printStackTrace(
                            "$TAG.handleGrowthGuideTasks.parseOpenBehaviorCollect", e
                        )
                    }
                    continue
                }

                // ===== 2.2 每日问答 =====
                if ("meiriwenda" == behaviorId && "wait_doing" == status) { //如果等待去做才执行，一般不会进入下面的今日已参与判断

                    if (subTitle.contains("今日已参与")) {
                        StructuredLog.other("信誉任务[每日问答] $subTitle（跳过答题）")
                        continue
                    }

                    try {
                        // ① 查询题目
                        val quizResp = ZmxyRpc.queryDailyQuiz(behaviorId)
                        val quizJo: JsonObject?
                        try {
                            quizJo = quizResp.toJsonObject()
                        } catch (e: Throwable) {
                            StructuredLog.printStackTrace(
                                "$TAG.handleGrowthGuideTasks.parseDailyQuiz 每日问答[解析失败]$quizResp", e
                            )
                            continue
                        }

                        if (!ResChecker.checkRes(TAG, quizJo)) {
                            continue
                        }

                        val data = quizJo.optJSONObject("data")
                        if (data == null) {
                            StructuredLog.error("$TAG.handleGrowthGuideTasks", "每日问答[返回缺少data]")
                            continue
                        }

                        val qVo = data.optJSONObject("questionVo")
                        if (qVo == null) {
                            StructuredLog.error("$TAG.handleGrowthGuideTasks", "每日问答[缺少questionVo]")
                            continue
                        }

                        val rightAnswer = qVo.optJSONObject("rightAnswer")
                        if (rightAnswer == null) {
                            StructuredLog.error("$TAG.handleGrowthGuideTasks", "每日问答[缺少rightAnswer]")
                            continue
                        }

                        val bizDate = data.optLong("bizDate", 0L)
                        val questionId = qVo.optString("questionId", "")
                        val questionContent = qVo.optString("questionContent", "")
                        val answerId = rightAnswer.optString("answerId", "")
                        val answerContent = rightAnswer.optString("answerContent", "")

                        if (bizDate <= 0 || questionId.isEmpty() || answerId.isEmpty()) {
                            StructuredLog.error("$TAG.handleGrowthGuideTasks", "每日问答[关键字段缺失]")
                            continue
                        }

                        // ② 提交答案
                        val pushResp = ZmxyRpc.pushDailyTask(
                            behaviorId, bizDate, answerId, questionId, "RIGHT"
                        )

                        val pushJo: JsonObject?
                        try {
                            pushJo = pushResp.toJsonObject()
                        } catch (e: Throwable) {
                            StructuredLog.printStackTrace(
                                "$TAG.handleGrowthGuideTasks.parsePushDailyTask 每日问答[提交解析失败]$quizResp", e
                            )
                            continue
                        }

                        if (ResChecker.checkRes(TAG, pushJo)) {
                            StructuredLog.other(
                                TAG,
                                ("信誉任务[每日答题成功] " + questionContent + " | 答案=" + answerContent + "(" + answerId + ")" + (if (subTitle.isEmpty()) "" else " | $subTitle"))
                            )
                        } else {
                            StructuredLog.error(
                                "$TAG.handleGrowthGuideTasks", "每日问答[提交失败] resp=$pushResp"
                            )
                        }
                    } catch (e: Throwable) {
                        StructuredLog.printStackTrace("$TAG.handleGrowthGuideTasks.meiriwenda", e)
                    }
                }

                // ===== 2.3 视频问答 =====
                if ("shipingwenda" == behaviorId && "wait_doing" == status) {
                    val bizDate = System.currentTimeMillis()
                    val questionId = "question3"
                    val answerId = "A"
                    val answerType = "RIGHT"

                    val pushResp = ZmxyRpc.pushDailyTask(
                        behaviorId, bizDate, answerId, questionId, answerType
                    )

                    val jo: JsonObject?
                    try {
                        jo = pushResp.toJsonObject()
                    } catch (e: Throwable) {
                        StructuredLog.printStackTrace(
                            "$TAG.handleGrowthGuideTasks.parsePushDailyTask 视频问答[提交解析失败]$pushResp", e
                        )
                        continue  // 改为continue，避免return影响循环
                    }

                    if (ResChecker.checkRes(TAG, jo)) {
                        StructuredLog.other("信誉任务[视频问答提交成功] → ")
                    } else {
                        StructuredLog.error("$TAG.handleGrowthGuideTasks", "视频问答[提交失败] → $pushResp")
                    }
                }

                // ===== 2.4 芭芭农场施肥 =====
                if ("babanongchang_7d" == behaviorId && "wait_doing" == status) {
                    try {
                        // 假设getWua()方法存在，返回wua（为空即可）
                        val wua = getSecurityBodyData(4) // 传入空字符串
                        val source = "DNHZ_NC_zhimajingnangSF" // 从buttonUrl提取的source
                        record(TAG, "set Wua $wua")

                        val spreadManureDataStr = orchardSpreadManure(
                            Objects.requireNonNull(wua).toString(), source, false
                        )
                        val spreadManureData: JsonObject?
                        try {
                            spreadManureData = spreadManureDataStr.toJsonObject()
                        } catch (e: Throwable) {
                            StructuredLog.printStackTrace(
                                "$TAG.handleGrowthGuideTasks.parsePushDailyTask 芭芭农场[提交解析失败]$spreadManureDataStr", e
                            )
                            continue
                        }

                        if ("100" != spreadManureData.optString("resultCode")) {
                            record(
                                TAG, "农场 orchardSpreadManure 错误：" + spreadManureData.optString("resultDesc")
                            )
                            continue
                        }

                        val taobaoDataStr = spreadManureData.optString("taobaoData", "")
                        if (taobaoDataStr.isEmpty()) {
                            StructuredLog.error("$TAG.handleGrowthGuideTasks", "芭芭农场[缺少taobaoData]")
                            continue
                        }

                        val spreadTaobaoData: JsonObject?
                        try {
                            spreadTaobaoData = taobaoDataStr.toJsonObject()
                        } catch (e: Throwable) {
                            StructuredLog.printStackTrace(
                                "$TAG.handleGrowthGuideTasks.parsePushDailyTask 芭芭农场[taobaoData解析失败]$taobaoDataStr", e
                            )
                            continue
                        }

                        val currentStage = spreadTaobaoData.optJSONObject("currentStage")
                        if (currentStage == null) {
                            StructuredLog.error("$TAG.handleGrowthGuideTasks", "芭芭农场[缺少currentStage]")
                            continue
                        }

                        val stageText = currentStage.optString("stageText", "")
                        val statistics = spreadTaobaoData.optJSONObject("statistics")
                        val dailyAppWateringCount = statistics?.optInt("dailyAppWateringCount", 0) ?: 0

                        StructuredLog.forest("今日农场已施肥💩 $dailyAppWateringCount 次 [$stageText]")

                        StructuredLog.other(
                            TAG, "信誉任务[芭芭农场施肥成功] $title | 已施肥 $dailyAppWateringCount 次"
                        )
                    } catch (e: Throwable) {
                        StructuredLog.printStackTrace("$TAG.handleGrowthGuideTasks.babanongchang", e)
                    }
                }
            }
        } catch (e: Throwable) {
            StructuredLog.printStackTrace("$TAG.handleGrowthGuideTasks.Fatal", e)
        }
    }

    /**
     * 年度回顾任务：通过 programInvoke 查询并自动完成任务

     * 芝麻信用任务
     */
    suspend fun runTasks(): Unit = CoroutineUtils.run {
        try {
            val s = ZmxyLegacyRpc.queryAvailableTask()
            delay(500)
            var jo = s.toJsonObject()
            if (jo.has("resData")) {
                jo = jo.getJSONObject("resData")
            }
            if (!ResChecker.checkRes(TAG, jo)) {
                StructuredLog.error(
                    "$TAG.doAllAvailableSesameTask.queryAvailableSesameTask", "芝麻信用💳[查询任务响应失败]#$s"
                )
                return
            }

            val taskObj = jo.getJSONObject("data")
            var totalTasks = 0
            var completedTasks = 0
            var skippedTasks = 0

            // 处理日常任务
            if (taskObj.has("dailyTaskListVO")) {
                val dailyTaskListVO = taskObj.getJSONObject("dailyTaskListVO")

                if (dailyTaskListVO.has("waitCompleteTaskVOS")) {
                    val waitCompleteTaskVOS = dailyTaskListVO.getJSONArray("waitCompleteTaskVOS")
                    totalTasks += waitCompleteTaskVOS.length()
                    record(
                        TAG, "芝麻信用💳[待完成任务]#处理(" + waitCompleteTaskVOS.length() + "个)"
                    )
                    val results: IntArray = joinAndFinishSesameTaskWithResult(waitCompleteTaskVOS)
                    completedTasks += results[0]
                    skippedTasks += results[1]
                }

                if (dailyTaskListVO.has("waitJoinTaskVOS")) {
                    val waitJoinTaskVOS = dailyTaskListVO.getJSONArray("waitJoinTaskVOS")
                    totalTasks += waitJoinTaskVOS.length()
                    record(
                        TAG, "芝麻信用💳[待加入任务]#处理(" + waitJoinTaskVOS.length() + "个)"
                    )
                    val results: IntArray = joinAndFinishSesameTaskWithResult(waitJoinTaskVOS)
                    completedTasks += results[0]
                    skippedTasks += results[1]
                }
            }

            // 处理toCompleteVOS任务
            if (taskObj.has("toCompleteVOS")) {
                val toCompleteVOS = taskObj.getJSONArray("toCompleteVOS")
                totalTasks += toCompleteVOS.length()
                record(
                    TAG, "芝麻信用💳[toCompleteVOS任务]#处理(" + toCompleteVOS.length() + "个)"
                )
                val results: IntArray = joinAndFinishSesameTaskWithResult(toCompleteVOS)
                completedTasks += results[0]
                skippedTasks += results[1]
            }

            // 统计结果并决定是否关闭开关
            record(
                TAG, "芝麻信用💳[任务处理完成]#总任务:" + totalTasks + "个, 完成:" + completedTasks + "个, 跳过:" + skippedTasks + "个"
            )

            // 如果所有任务都已完成或跳过（没有剩余可完成任务），关闭开关
            if (totalTasks > 0 && (completedTasks + skippedTasks) >= totalTasks) {
                setFlagToday(StatusFlags.FLAG_ANTMEMBER_DO_ALL_SESAME_TASK)
                record(TAG, "芝麻信用💳[已全部完成任务，临时关闭]")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG + "doAllAvailableSesameTask err", t)
        }
    }

    /**
     * 芝麻粒收取
     * @param withOneClick 启用一键收取
     */
    suspend fun collect(withOneClick: Boolean): Unit = CoroutineUtils.run {
        try {
            var jo = ZmxyLegacyRpc.queryCreditFeedback().toJsonObject()
            delay(500)
            if (!ResChecker.checkRes(TAG, jo)) {
                StructuredLog.error(
                    "$TAG.collectSesame.queryCreditFeedback", "芝麻信用💳[查询未领取芝麻粒响应失败]#$jo"
                )
                return
            }
            val availableCollectList = jo.getJSONArray("creditFeedbackVOS")
            if (withOneClick) {
                delay(2000)
                jo = ZmxyLegacyRpc.collectAllCreditFeedback().toJsonObject()
                delay(2000)
                if (!ResChecker.checkRes(TAG, jo)) {
                    StructuredLog.error(
                        "$TAG.collectSesame.collectAllCreditFeedback", "芝麻信用💳[一键收取芝麻粒响应失败]#$jo"
                    )
                    return
                }
            }
            for (i in 0..<availableCollectList.length()) {
                jo = availableCollectList.getJSONObject(i)
                if ("UNCLAIMED" != jo.getString("status")) {
                    continue
                }
                val title = jo.getString("title")
                val creditFeedbackId = jo.getString("creditFeedbackId")
                val potentialSize = jo.getString("potentialSize")
                if (!withOneClick) {
                        jo = ZmxyLegacyRpc.collectCreditFeedback(creditFeedbackId).toJsonObject()
                    delay(2000)
                    if (!ResChecker.checkRes(TAG, jo)) {
                        StructuredLog.error(
                            "$TAG.collectSesame.collectCreditFeedback", "芝麻信用💳[收取芝麻粒响应失败]#$jo"
                        )
                        continue
                    }
                }
                StructuredLog.other("芝麻信用💳[" + title + "]#" + potentialSize + "粒" + (if (withOneClick) "(一键收取)" else ""))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("$TAG.collectSesame", t)
        }
    }

    private fun isTaskInBlacklist(taskTitle: String?): Boolean {
        return TaskBlacklist.isTaskInBlacklist(taskTitle)
    }

    /**
     * 芝麻信用-领取并完成任务（带结果统计）
     * @param taskList 任务列表
     * @return int数组 [完成数量, 跳过数量]
     * @throws IllegalArgumentException JSON解析异常，上抛处理
     */
    @Throws(IllegalArgumentException::class)
    private suspend fun joinAndFinishSesameTaskWithResult(taskList: JsonArray): IntArray {
        var completedCount = 0
        var skippedCount = 0

        for (i in 0..<taskList.length()) {
            val task = taskList.getJSONObject(i)
            val taskTitle = if (task.has("title")) task.getString("title") else "未知任务"

            // 打印任务状态信息用于调试
            val finishFlag = task.optBoolean("finishFlag", false)
            val actionText = task.optString("actionText", "")

            //   record(TAG, "芝麻信用💳[任务状态调试]#" + taskTitle + " - finishFlag:" + finishFlag + ", actionText:" + actionText);

            // 检查任务是否已完成
            if (finishFlag || "已完成" == actionText) {
                record(TAG, "芝麻信用💳[跳过已完成任务]#$taskTitle")
                skippedCount++
                continue
            }

            // 检查黑名单
            if (isTaskInBlacklist(taskTitle)) {
                record(TAG, "芝麻信用💳[跳过黑名单任务]#$taskTitle")
                skippedCount++
                continue
            }

            // 添加检查，确保templateId存在
            if (!task.has("templateId")) {
                record(TAG, "芝麻信用💳[跳过缺少templateId任务]#$taskTitle")
                skippedCount++
                continue
            }

            val taskTemplateId = task.getString("templateId")

            rpcProbeFailures[taskTemplateId]?.let { reason ->
                record(
                    TAG,
                    "芝麻信用💳[跳过已探测失败任务]#$taskTitle template=$taskTemplateId " +
                        "[2026-08-09 $reason]",
                )
                skippedCount++
                continue
            }
            if (taskTemplateId in verifiedRpcTemplates) {
                record(TAG, "芝麻信用💳[已验证后台RPC任务]#$taskTitle template=$taskTemplateId")
            }

            val needCompleteNum = if (task.has("needCompleteNum")) task.getInt("needCompleteNum") else 1
            val completedNum = task.optInt("completedNum", 0)
            var s: String?
            val recordId: String?
            var responseObj: JsonObject?


            if (task.has("actionUrl") && task.getString("actionUrl").contains("jumpAction")) {
                // 跳转APP任务 依赖跳转的APP发送请求鉴别任务完成 仅靠hook目标应用无法完成
                record(TAG, "芝麻信用💳[跳过跳转APP任务]#$taskTitle")
                skippedCount++
                continue
            }

            var taskCompleted = false
            if (task.has("recordId") && task.optString("recordId").isNotBlank()) {
                // 当前 queryListV3 会在任务加入后返回 recordId，但不一定返回 todayFinish。
                // 优先复用进行中的生活记录，避免重复 joinActivity。
                recordId = task.getString("recordId")
            } else {
                // 尚未加入任务时才领取任务
                s = ZmxyLegacyRpc.joinTask(taskTemplateId)
                delay(200)
                responseObj = s.toJsonObject()
                if (!ResChecker.checkRes(TAG, responseObj)) {
                    StructuredLog.error(TAG, "芝麻信用💳[领取任务" + taskTitle + "失败]#" + s)
                    // 自动添加到黑名单
                    val errorCode = responseObj.optString("errorCode", "")
                    if (!errorCode.isEmpty()) {
                        autoAddToBlacklist(taskTitle, taskTitle, errorCode)
                    }
                    skippedCount++
                    continue
                }
                recordId = responseObj.getJSONObject("data").getString("recordId")
            }

            // 完成任务
            for (j in completedNum..<needCompleteNum) {
                s = ZmxyLegacyRpc.finishTask(recordId)
                delay(200)
                responseObj = s.toJsonObject()
                if (ResChecker.checkRes(TAG, responseObj)) {
                    record(
                        TAG, "芝麻信用💳[完成任务" + taskTitle + "]#(" + (j + 1) + "/" + needCompleteNum + "天)"
                    )
                    taskCompleted = true
                } else {
                    StructuredLog.error(TAG, "芝麻信用💳[完成任务" + taskTitle + "失败]#" + s)
                    // 自动添加到黑名单
                    val errorCode = responseObj.optString("errorCode", "")
                    if (!errorCode.isEmpty()) {
                        autoAddToBlacklist(taskTitle, taskTitle, errorCode)
                    }
                    break
                }
            }

            if (taskCompleted) {
                completedCount++
            } else {
                skippedCount++
            }
        }

        return intArrayOf(completedCount, skippedCount)
    }

}
