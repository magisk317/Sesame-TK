package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.data.Status.Companion.setFlagToday
import io.github.magisk317.sesame.data.StatusFlags
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.StructuredLog.record
import io.github.magisk317.sesame.util.TaskBlacklist.autoAddToBlacklist
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray

/** Owns the Zhima Alchemy workflow and its reward collection. */
class MemberAlchemyService {

    /**
     * 芝麻粒信用福利签到  与芝麻粒炼金的签到方法都一样 alchemyQueryCheckIn 只不过scenecode不一样
     * 基于 HomeV8RpcManager.queryServiceCard 返回的 serviceCardVOList
     * 通过 itemAttrs.checkInModuleVO.currentDateCheckInTaskVO 判断今日是否可签到
     */
    fun checkInZml() {
        try {
            val checkInRes = AlchemyRpc.alchemyQueryCheckIn(AlchemyRpc.SCENE_ZML)
            val checkInJo = checkInRes.toJsonObject()
            if (ResChecker.checkRes(TAG, checkInJo)) {
                val data = checkInJo.optJSONObject("data")
                if (data != null) {
                    val currentDay = data.optJSONObject("currentDateCheckInTaskVO")
                    if (currentDay != null) {
                        val status = currentDay.optString("status")
                        val checkInDate = currentDay.optString("checkInDate")
                        if ("CAN_COMPLETE" == status && !checkInDate.isEmpty()) {
                            // 信誉主页签到
                            val completeRes = ZmxyLegacyRpc.completeCheckIn(checkInDate, "zml")
                            try {
                                val completeJo = completeRes.toJsonObject()
                                if (ResChecker.checkRes(TAG, completeJo)) {
                                    val prize = completeJo.optJSONObject("data")
                                    val num = prize?.let { data ->
                                        data.optInt(
                                            "zmlNum",
                                            data.optJSONObject("prize")?.optInt("num", 0) ?: 0,
                                        )
                                    } ?: 0
                                    StructuredLog.other("芝麻炼金⚗️[每日签到成功]#获得" + num + "粒")
                                } else {
                                    StructuredLog.error("$TAG.doSesameAlchemy", "炼金签到失败:$completeRes")
                                }
                            } catch (e: Throwable) {
                                StructuredLog.printStackTrace(
                                    "$TAG.doSesameAlchemy.alchemyCheckInComplete", e
                                )
                            }
                        } // status 为 COMPLETED 时不再重复签到
                    }
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("$TAG.doSesameZmlCheckIn", t)
        }
    }

    fun claimNextDayAward() = CoroutineUtils.run {
        try {
            // ===== 调用领取奖励 RPC =====

            val awardRes = AlchemyRpc.claimAward()

            val jo = awardRes.toJsonObject()

            if (!ResChecker.checkRes(TAG, jo)) {
                StructuredLog.error("芝麻炼金⚗️[次日奖励失败]：$awardRes")
                // 即使失败也要设 flag，避免卡死重复调用
                setFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)
                return@run
            }

            val data = jo.optJSONObject("data")
            var gotNum = 0

            if (data != null) {
                // 解析奖励数组
                val arr = data.optJSONArray("alchemyAwardSendResultVOS")
                if (arr != null && arr.length() > 0) {
                    val item = arr.optJSONObject(0)
                    if (item != null) {
                        gotNum = item.optInt("pointNum", 0)
                    }
                }
            }

            if (gotNum > 0) {
                StructuredLog.other("芝麻炼金⚗️[次日奖励领取成功]#获得" + gotNum + "粒")
            } else {
                record("芝麻炼金⚗️[次日奖励无奖励] 已领取或无可领奖励")
            }

            // ★★★★★ 不论有无奖励都标记今日完成 ★★★★★
            setFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("doSesameAlchemyNextDayAward", t)
            // 异常也要标记，否则会无限尝试
            setFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)
        }
    }


    /**
     * 芝麻炼金
     */
    suspend fun run(): Unit = CoroutineUtils.run {
        try {

            // ================= Step 1: 自动炼金 (消耗芝麻粒升级) =================
            val homeRes = AlchemyRpc.alchemyQueryHome()
            val homeJo = homeRes.toJsonObject()
            if (ResChecker.checkRes(TAG, homeJo)) {
                val data = homeJo.optJSONObject("data")
                if (data != null) {
                    var zmlBalance = data.optInt("zmlBalance", 0) // 当前芝麻粒
                    val cost = data.optInt("alchemyCostZml", 5) // 单次消耗
                    var capReached = data.optBoolean("capReached", false) // 是否达到上限
                    var currentLevel = data.optInt("currentLevel", 0)
                    val staminaCurrent = data.optInt("staminaCurrent", 0)
                    val staminaStatus = data.optString("staminaStatus", "")
                    val staminaExhausted = data.optBoolean("staminaExhausted", false)
                    val staminaReported = data.has("staminaCurrent") ||
                        data.has("staminaStatus") || data.has("staminaExhausted")
                    val staminaUnavailable = staminaReported && (staminaExhausted ||
                        staminaStatus.equals("EXHAUSTED", ignoreCase = true) || staminaCurrent <= 0)

                    record(
                        TAG,
                        "芝麻炼金⚗️[体力状态] current=$staminaCurrent status=$staminaStatus " +
                            "exhausted=$staminaExhausted",
                    )

                    // 服务端曾返回 staminaExhausted=false 但 staminaStatus=EXHAUSTED，
                    // 因此不能只依赖布尔字段。
                    if (staminaUnavailable) {
                        record(TAG, "芝麻炼金⚗️[体力不足] 暂不执行炼金，先处理可补体力任务")
                    }

                    // 循环炼金逻辑
                    while (zmlBalance >= cost && !capReached && !staminaUnavailable) {
                        delay(1500)
                        val alchemyRes = AlchemyRpc.alchemyExecute()
                        val alchemyJo = alchemyRes.toJsonObject()

                        if (ResChecker.checkRes(TAG, alchemyJo)) {
                            val alData = alchemyJo.optJSONObject("data")
                            if (alData != null) {
                                val levelUp = alData.optBoolean("levelUp", false)
                                val levelFull = alData.optBoolean("levelFull", false)
                                val goldNum = alData.optInt("goldNum", 0)


                                if (levelUp) currentLevel++
                                if (levelFull) capReached = true

                                StructuredLog.other(
                                    ("芝麻炼金⚗️[炼金成功]" + "#消耗" + cost + "粒" + " | 获得" + goldNum + "金" + " | 当前等级Lv." + currentLevel + (if (levelUp) "（升级🎉）" else "") + (if (levelFull) "（满级🏆）" else ""))
                                )
                                zmlBalance -= cost
                            } else {
                                break
                            }
                        } else {
                            StructuredLog.error(TAG, "芝麻炼金失败: " + alchemyJo.optString("resultView"))
                            break
                        }
                    }
                }
            } else {
                StructuredLog.error(TAG, "芝麻炼金首页查询失败")
            }

            // ================= Step 2: 自动签到 & 时段奖励 =================
            // 芝麻炼金和芝麻粒信用福利共用会员签到接口，但设置页已移除
            // sesameTask/collectSesame 开关；炼金独立启用时也必须执行 zml 签到。
            checkInZml()

            val checkInRes = AlchemyRpc.alchemyQueryCheckIn()
            val checkInJo = checkInRes.toJsonObject()
            if (ResChecker.checkRes(TAG, checkInJo)) {
                val data = checkInJo.optJSONObject("data")
                if (data != null) {
                    val currentDay = data.optJSONObject("currentDateCheckInTaskVO")
                    if (currentDay != null) {
                        val status = currentDay.optString("status")
                        val checkInDate = currentDay.optString("checkInDate")
                        if ("CAN_COMPLETE" == status && !checkInDate.isEmpty()) {
                            // 炼金签到
                            val completeRes = ZmxyLegacyRpc.completeCheckIn(checkInDate, "alchemy")
                            try {
                                val completeJo = completeRes.toJsonObject()
                                if (ResChecker.checkRes(TAG, completeJo)) {
                                    val prize = completeJo.optJSONObject("data")
                                    val num = prize?.optInt(
                                        "zmlNum", prize.optJSONObject("prize")?.optInt("num", 0) ?: 0
                                    ) ?: 0
                                    StructuredLog.other("芝麻炼金⚗️[每日签到成功]#获得" + num + "粒")
                                } else {
                                    StructuredLog.error("$TAG.doSesameAlchemy", "炼金签到失败:$completeRes")
                                }
                            } catch (e: Throwable) {
                                StructuredLog.printStackTrace(
                                    "$TAG.doSesameAlchemy.alchemyCheckInComplete", e
                                )
                            }
                        } // status 为 COMPLETED 时不再重复签到
                    }
                }
            }

            // 1. 查询时段任务
            val queryRespStr = AlchemyRpc.alchemyQueryTimeLimitedTask()
            record(TAG, "芝麻炼金⚗️[检查时段奖励]")

            val queryResp = queryRespStr.toJsonObject()
            val timeLimitedTaskVO = if (ResChecker.checkRes(TAG + "查询时段任务失败:", queryResp)) {
                queryResp.optJSONObject("data")?.optJSONObject("timeLimitedTaskVO")
            } else {
                StructuredLog.error(
                    TAG, "芝麻炼金⚗️[检查时段奖励错误] alchemyQueryTimeLimitedTask raw=$queryResp"
                )
                null
            }

            if (timeLimitedTaskVO == null) {
                record(TAG, "芝麻炼金⚗️[当前没有时段奖励任务]")
            } else {
                // 2. 获取任务信息
                val taskName = timeLimitedTaskVO.optString("longTitle", "未知任务")
                val templateId = timeLimitedTaskVO.optString("templateId", "")
                val state = timeLimitedTaskVO.optInt("state", 0) // 1: 可领取, 2: 未到时间
                val tomorrow = timeLimitedTaskVO.optBoolean("tomorrow", false)
                val rewardAmount = timeLimitedTaskVO.optInt("rewardAmount", 0)

                record(
                    TAG, "芝麻炼金⚗️[任务检查] 任务=$taskName 状态=$state 奖励=$rewardAmount 明天=$tomorrow"
                )

                // 3. 如果是明天任务，跳过；否则尝试领取当前奖励
                if (tomorrow) {
                    record(TAG, "芝麻炼金⚗️[任务跳过] 任务=$taskName 是明天的奖励")
                } else if (state == 1 && templateId.isNotEmpty()) {
                    record(TAG, "芝麻炼金⚗️[领取任务奖励] 任务=$taskName")

                    val collectResp = AlchemyRpc.alchemyCompleteTimeLimitedTask(templateId).toJsonObject()
                    if (!ResChecker.checkRes(TAG, collectResp) || collectResp.optJSONObject("data") == null) {
                        StructuredLog.error(TAG, "领取任务奖励失败 raw=$collectResp")
                    } else {
                        val data = collectResp.getJSONObject("data")
                        val zmlNum = data.optInt("zmlNum", 0)
                        val toast = data.optString("toast", "")
                        record(TAG, "芝麻炼金⚗️[领取成功] 获得芝麻粒=$zmlNum 提示=$toast")
                    }
                } else {
                    record(TAG, "芝麻炼金⚗️[当前不可领取] 任务=$taskName")
                }
            }


            // ================= Step 3: 自动做任务 =================
            record(TAG, "芝麻炼金⚗️[扫描任务列表]")
            val listRes = AlchemyRpc.alchemyQueryListV3()
            val listJo = listRes.toJsonObject()

            if (ResChecker.checkRes(TAG, listJo)) {
                val data = listJo.optJSONObject("data")
                if (data != null) {
                    // 用于记录所有已处理的黑名单任务，避免在不同任务组间重复记录
                    val allProcessedBlacklistTasks = mutableSetOf<String>()

                    val toComplete = data.optJSONArray("toCompleteVOS")
                    if (toComplete != null) {
                        processAlchemyTasks(toComplete, allProcessedBlacklistTasks)
                    }
                    val dailyTaskVO = data.optJSONObject("dailyTaskListVO")
                    if (dailyTaskVO != null) {
                        processAlchemyTasks(
                            dailyTaskVO.optJSONArray("waitJoinTaskVOS"), allProcessedBlacklistTasks
                        )
                        processAlchemyTasks(
                            dailyTaskVO.optJSONArray("waitCompleteTaskVOS"), allProcessedBlacklistTasks
                        )
                    }
                }
            }

            // 主页面任务池（zml_* 模板，不带 version=alchemy）
            record(TAG, "芝麻炼金⚗️[扫描主页面任务]")
            val mainListRes = AlchemyRpc.mainPageQueryListV3()
            val mainListJo = mainListRes.toJsonObject()
            if (ResChecker.checkRes(TAG, mainListJo)) {
                val mainData = mainListJo.optJSONObject("data")
                if (mainData != null) {
                    val mainProcessed = mutableSetOf<String>()
                    val mainToComplete = mainData.optJSONArray("toCompleteVOS")
                    if (mainToComplete != null) {
                        processAlchemyTasks(mainToComplete, mainProcessed)
                    }
                    val mainDaily = mainData.optJSONObject("dailyTaskListVO")
                    if (mainDaily != null) {
                        processAlchemyTasks(mainDaily.optJSONArray("waitJoinTaskVOS"), mainProcessed)
                        processAlchemyTasks(mainDaily.optJSONArray("waitCompleteTaskVOS"), mainProcessed)
                    }
                }
            }

            // 任务可能在外部小游戏中补充炼金体力；完成任务后重新查询，
            // 让本次运行也能使用刚到账的体力，而不是等到下一轮定时任务。
            val refreshedHome = AlchemyRpc.alchemyQueryHome().toJsonObject()
            if (ResChecker.checkRes(TAG, refreshedHome)) {
                val refreshedData = refreshedHome.optJSONObject("data")
                if (refreshedData != null) {
                    val refreshedStamina = refreshedData.optInt("staminaCurrent", 0)
                    val refreshedStatus = refreshedData.optString("staminaStatus", "")
                    val refreshedExhausted = refreshedData.optBoolean("staminaExhausted", false)
                    val refreshedReported = refreshedData.has("staminaCurrent") ||
                        refreshedData.has("staminaStatus") || refreshedData.has("staminaExhausted")
                    val refreshedUnavailable = refreshedReported && (refreshedExhausted ||
                        refreshedStatus.equals("EXHAUSTED", ignoreCase = true) || refreshedStamina <= 0)
                    if (!refreshedUnavailable) {
                        val refreshedZml = refreshedData.optInt("zmlBalance", 0)
                        val refreshedCost = refreshedData.optInt("alchemyCostZml", 5)
                        val refreshedCap = refreshedData.optBoolean("capReached", false)
                        if (refreshedZml >= refreshedCost && !refreshedCap) {
                            record(TAG, "芝麻炼金⚗️[体力已恢复] current=$refreshedStamina status=$refreshedStatus，继续炼金")
                            delay(1500)
                            val alchemyRes = AlchemyRpc.alchemyExecute().toJsonObject()
                            if (ResChecker.checkRes(TAG, alchemyRes)) {
                                record(TAG, "芝麻炼金⚗️[补体力后炼金成功]")
                            } else {
                                StructuredLog.error(TAG, "补体力后炼金失败: ${alchemyRes.optString("resultView")}")
                            }
                        }
                    }
                }
            }

            // ================= Step 4: [新增] 任务完成后一键收取芝麻粒 =================
            record(TAG, "芝麻炼金⚗️[任务处理完毕，准备收取芝麻粒]")
            delay(2000) // 稍作等待，确保任务奖励到账

            // 4.1 查询是否有可收取的芝麻粒
            val queryFeedbackRes = ZmxyLegacyRpc.queryCreditFeedback()
            val feedbackJo = queryFeedbackRes.toJsonObject()
            if (ResChecker.checkRes(TAG, feedbackJo)) {
                val feedbackList = feedbackJo.optJSONArray("creditFeedbackVOS")
                if (feedbackList != null && feedbackList.length() > 0) {
                    record(
                        TAG, "芝麻炼金⚗️[发现" + feedbackList.length() + "个待收取项，执行一键收取]"
                    )

                    // 4.2 执行一键收取
                    val collectRes = ZmxyLegacyRpc.collectAllCreditFeedback()
                    val collectJo = collectRes.toJsonObject()
                    if (ResChecker.checkRes(TAG, collectJo)) {
                        StructuredLog.other("芝麻炼金⚗️[一键收取成功]#收割完毕")
                    } else {
                        record(TAG, "芝麻炼金⚗️[一键收取失败]#" + collectJo.optString("resultView"))
                    }
                } else {
                    record(TAG, "芝麻炼金⚗️[当前无待收取芝麻粒]")
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("$TAG.doSesameAlchemy", t)
        }
    }

    /**
     * 处理芝麻炼金任务列表
     * @param taskList 任务列表
     * @param processedBlacklistTasks 已处理的黑名单任务集合（用于避免重复日志）

     */
    @Throws(IllegalArgumentException::class)
    private suspend fun processAlchemyTasks(
        taskList: JsonArray?, processedBlacklistTasks: MutableSet<String>
    ) {
        if (taskList == null || taskList.length() == 0) return

        for (i in 0..<taskList.length()) {
            val task = taskList.getJSONObject(i)
            val title = task.optString("title")
            val templateId = task.optString("templateId")
            val finishFlag = task.optBoolean("finishFlag", false)
            val bizType = task.optString("bizType", "")

            if (finishFlag) continue

            if (TaskBlacklist.isTaskInBlacklist(templateId) || TaskBlacklist.isTaskInBlacklist(title)) {
                continue
            }

            // These game templates were verified on-device to complete through
            // the Promise RPC flow without opening the game. Other game
            // templates use a different promiseActivityExtCheck contract.
            if (task.optString("gameAppId").isNotBlank() &&
                templateId !in VERIFIED_RPC_GAME_TEMPLATES
            ) {
                continue
            }

            // 特殊处理：广告浏览任务（逛15秒商品橱窗 / 浏览15秒视频广告 等）
            // 这类任务没有有效 templateId，需要用 logExtMap.bizId 走 com.alipay.adtask.biz.mobilegw.service.task.finish
            if ("AD_TASK" == bizType) {
                val logExtMap = task.optJSONObject("logExtMap")
                if (logExtMap == null) {
                    record(TAG, "芝麻炼金广告任务缺少logExtMap, 跳过: $title")
                    continue
                }
                val bizId = logExtMap.optString("bizId", "")
                if (bizId.isEmpty()) {
                    record(TAG, "芝麻炼金广告任务缺少bizId, 跳过: $title")
                    continue
                }

                record(TAG, "芝麻炼金广告任务: $title 准备执行") //(bizId=" + bizId + ")

                var sleepTime = 8000
                if (title.contains("15秒") || title.contains("15s")) {
                    // 抓包规则里写明“每次浏览不少于15秒”
                    sleepTime = 10000
                }
                delay(sleepTime.toLong())

                try {
                    val adFinishRes = MerchantTaskRpc.finishTask(bizId)
                    val adFinishJo = adFinishRes.toJsonObject()
                    // 兼容返回中只有 errCode=0 的情况
                    if (ResChecker.checkRes(
                            TAG, adFinishJo
                        ) || "0" == adFinishJo.optString("errCode")
                    ) {
                        val reward = task.optInt("rewardAmount", 0)
                        StructuredLog.other("芝麻炼金⚗️[广告任务完成: " + title + "]#获得" + reward + "粒")
                    } else {
                        StructuredLog.error(TAG, "芝麻炼金广告任务上报失败: $title - $adFinishRes")
                    }
                } catch (e: Throwable) {
                    StructuredLog.printStackTrace("$TAG.processAlchemyTasks.adTask", e)
                }
                // 广告任务不再走 templateId / recordId 这套逻辑
                continue
            }

            // 普通任务：仍然使用模板+recordId 的 Promise 流程
            if (templateId.contains("invite") || templateId.contains("upload") || templateId.contains("auth") || templateId.contains("banli")) {
                continue
            }
            val actionUrl = task.optString("actionUrl", "")
            if (actionUrl.startsWith("alipays://") && !actionUrl.contains("chInfo")) {
                // 需要外部 App，无法仅靠 hook 完成
                continue
            }

            record(TAG, "芝麻炼金任务: $title 准备执行")

            var recordId = task.optString("recordId", "")

            if (recordId.isEmpty()) {
                // templateId 为空或无效时，直接跳过，避免 "参数[templateId]不是有效的入参"
                if (templateId.trim().isEmpty()) {
                    record(TAG, "芝麻炼金任务: 模板为空，跳过 $title")
                    continue
                }
                val joinRes = ZmxyLegacyRpc.joinTask(templateId)
                val joinJo = joinRes.toJsonObject()
                val joinErrorCode = joinJo.optString("resultCode", joinJo.optString("code", ""))
                if (joinErrorCode == "PROMISE_TEMPLATE_NOT_EXIST") {
                    // The template is permanently unavailable; do not retry it every run.
                    autoAddToBlacklist(templateId, title, joinErrorCode)
                    continue
                }
                if (ResChecker.checkRes(TAG, joinJo)) {
                    val joinData = joinJo.optJSONObject("data")
                    if (joinData != null) {
                        recordId = joinData.optString("recordId")
                    }
                    record(TAG, "任务领取成功: $title")
                    delay(1000)
                } else {
                    StructuredLog.error(
                        TAG, "任务领取失败: " + title + " - " + joinJo.optString("resultView", joinRes)
                    )
                    continue
                }
            }

            ZmxyLegacyRpc.feedbackTask(templateId)

            var sleepTime = 3000
            if (title.contains("浏览") || title.contains("逛")) {
                sleepTime = 15000
            }
            delay(sleepTime.toLong())

            if (!recordId.isEmpty()) {
                val finishRes = ZmxyLegacyRpc.finishTask(recordId)
                val finishJo = finishRes.toJsonObject()
                if (ResChecker.checkRes(TAG, finishJo)) {
                    val reward = task.optInt("rewardAmount", 0)
                    StructuredLog.other("芝麻炼金⚗️[任务完成: " + title + "]#获得" + reward + "粒")
                } else {
                    val errorCode = finishJo.optString("resultCode", "")
                    //  val errorMsg = finishJo.optString("resultView", finishRes)
                    //  StructuredLog.error(TAG, "任务提交失败: $title - $errorMsg")
                    if (errorCode == "PROMISE_TEMPLATE_NOT_EXIST") {
                        // 仅将已确认永久不可执行的 Promise 模板加入黑名单。
                        autoAddToBlacklist(templateId, title, errorCode)
                    } else {
                        StructuredLog.error(
                            TAG,
                            "炼金任务完成失败: $title - ${finishJo.optString("resultView", finishRes)}",
                        )
                    }
                }
            }
            delay(2000)
        }
    }

    private companion object {
        const val TAG = "MemberAlchemyService"
        val VERIFIED_RPC_GAME_TEMPLATES = setOf(
            // _baojika_2 series (alchemy page)
            "hjwf_bwxrk_baojika_2",
            "hjwf_xjskp_baojika_2",
            "hjwf_lmct_baojika_2",
            "hjwf_fkssj_baojika_2",
            "hjwf_wdhysj_baojika_2",
            // hjwf_*_liulan30s series (alchemy page, 2026-08-19 verified)
            "hjwf_bwxrk_liulan30s",
            "hjwf_xjskp_liulan30s",
            // zml_*_liulan30s / zml_*_liulan series (main page, 2026-08-19 verified)
            "zml_wumingzhibei_liulan30s",
            "zml_sanguobingheshidai_liulan",
            "zml_jihewangguo_liulan",
        )
    }
}
