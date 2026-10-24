package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.task.antMember.AnnualReviewRpc
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.StructuredLog.record
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Executes the annual-review query/apply/process/reward protocol. */
class MemberAnnualReviewService {
    private val TAG = "AntMember"
    private val tag = "MemberAnnualReviewService"
        suspend fun run(): Unit = CoroutineUtils.run {
            try {

                val resp = AnnualReviewRpc.queryTasks()
                if (resp == null || resp.isEmpty()) {
                    record("$TAG.doAnnualReview", "年度回顾[查询返回空]")
                    return
                }

                val root: JsonObject?
                try {
                    root = resp.toJsonObject()
                } catch (e: Throwable) {
                    StructuredLog.printStackTrace("$TAG.doAnnualReview.parseRoot", e)
                    return
                }

                if (!root.optBoolean("isSuccess", false)) {
                    record("$TAG.doAnnualReview", "年度回顾[查询失败]#$resp")
                    return
                }

                val components = root.optJSONObject("components")
                if (components == null || components.isEmpty()) {
                    record("$TAG.doAnnualReview", "年度回顾[components 为空]")
                    return
                }

                var queryComp = components.optJSONObject(AnnualReviewRpc.QUERY_COMPONENT)
                if (queryComp == null) {
                    // 兜底：取第一个组件
                    try {
                        for (key in components.keys) {
                            queryComp = components.optJSONObject(key)
                            if (queryComp != null) break
                        }
                    } catch (_: Throwable) {
                    }
                }
                if (queryComp == null) {
                    record("$TAG.doAnnualReview", "年度回顾[未找到查询组件]")
                    return
                }
                if (!queryComp.optBoolean("isSuccess", true)) {
                    record("$TAG.doAnnualReview", "年度回顾[查询组件返回失败]")
                    return
                }

                val content = queryComp.optJSONObject("content")
                if (content == null) {
                    record("$TAG.doAnnualReview", "年度回顾[content 为空]")
                    return
                }

                val taskList = content.optJSONArray("playTaskOrderInfoList")
                if (taskList == null || taskList.length() == 0) {
                    record("$TAG.doAnnualReview", "年度回顾[当前无可处理任务]")
                    return
                }

                var candidate = 0
                var applied = 0
                var processed = 0
                var failed = 0

                for (i in 0..<taskList.length()) {
                    val task = taskList.optJSONObject(i) ?: continue

                    val taskStatus = task.optString("taskStatus", "")
                    if ("init" != taskStatus) {
                        // 已完成/已领奖等状态直接跳过
                        continue
                    }
                    candidate++

                    var code = task.optString("code", "")
                    if (code.isEmpty()) {
                        val extInfo = task.optJSONObject("extInfo")
                        if (extInfo != null) {
                            code = extInfo.optString("taskId", "")
                        }
                    }
                    if (code.isEmpty()) {
                        failed++
                        continue
                    }

                    var taskName = code
                    val displayInfo = task.optJSONObject("displayInfo")
                    if (displayInfo != null) {
                        val name = displayInfo.optString(
                            "taskName", displayInfo.optString("activityName", code)
                        )
                        if (!name.isEmpty()) {
                            taskName = name
                        }
                    }

                    // ========== Step 1: 领取任务 (apply) ==========
                    val applyResp = AnnualReviewRpc.applyTask(code)
                    if (applyResp == null || applyResp.isEmpty()) {
                        record("$TAG.doAnnualReview", "年度回顾[领任务失败]$taskName#响应为空")
                        failed++
                        continue
                    }

                    val applyRoot: JsonObject?
                    try {
                        applyRoot = applyResp.toJsonObject()
                    } catch (e: Throwable) {
                        StructuredLog.printStackTrace("$TAG.doAnnualReview.parseApply", e)
                        failed++
                        continue
                    }
                    if (!applyRoot.optBoolean("isSuccess", false)) {
                        record("$TAG.doAnnualReview", "年度回顾[领任务失败]$taskName#$applyResp")
                        failed++
                        continue
                    }
                    val applyComps = applyRoot.optJSONObject("components")
                    if (applyComps == null) {
                        failed++
                        continue
                    }
                    var applyComp = applyComps.optJSONObject(AnnualReviewRpc.APPLY_COMPONENT)
                    if (applyComp == null) {
                        try {
                            for (key in applyComps.keys) {
                                applyComp = applyComps.optJSONObject(key)
                                if (applyComp != null) break
                            }
                        } catch (_: Throwable) {
                        }
                    }
                    if (applyComp == null || !applyComp.optBoolean("isSuccess", true)) {
                        failed++
                        continue
                    }
                    val applyContent = applyComp.optJSONObject("content")
                    if (applyContent == null) {
                        failed++
                        continue
                    }
                    val claimedTask = applyContent.optJSONObject("claimedTask")
                    if (claimedTask == null) {
                        failed++
                        continue
                    }
                    val recordNo = claimedTask.optString("recordNo", "")
                    if (recordNo.isEmpty()) {
                        failed++
                        continue
                    }
                    applied++

                    delay(500)

                    // ========== Step 2: 提交任务完成 (process) ==========
                    val processResp = AnnualReviewRpc.processTask(code, recordNo)
                    if (processResp == null || processResp.isEmpty()) {
                        record("$TAG.doAnnualReview", "年度回顾[提交任务失败]$taskName#响应为空")
                        failed++
                        continue
                    }

                    val processRoot: JsonObject?
                    try {
                        processRoot = processResp.toJsonObject()
                    } catch (e: Throwable) {
                        StructuredLog.printStackTrace("$TAG.doAnnualReview.parseProcess", e)
                        failed++
                        continue
                    }
                    if (!processRoot.optBoolean("isSuccess", false)) {
                        record("$TAG.doAnnualReview", "年度回顾[提交任务失败]$taskName#$processResp")
                        failed++
                        continue
                    }
                    val processComps = processRoot.optJSONObject("components")
                    if (processComps == null) {
                        failed++
                        continue
                    }
                    var processComp = processComps.optJSONObject(AnnualReviewRpc.PROCESS_COMPONENT)
                    if (processComp == null) {
                        try {
                            for (key in processComps.keys) {
                                processComp = processComps.optJSONObject(key)
                                if (processComp != null) break
                            }
                        } catch (_: Throwable) {
                        }
                    }
                    if (processComp == null || !processComp.optBoolean("isSuccess", true)) {
                        failed++
                        continue
                    }
                    val processContent = processComp.optJSONObject("content")
                    if (processContent == null) {
                        failed++
                        continue
                    }
                    val processedTask = processContent.optJSONObject("processedTask")
                    if (processedTask == null) {
                        failed++
                        continue
                    }
                    val newStatus = processedTask.optString("taskStatus", "")
                    var rewardStatus = processedTask.optString("rewardStatus", "")

                    // ========== Step 3: 如仍未发奖，则调用 get_reward 领取奖励 ==========
                    if (!"success".equals(rewardStatus, ignoreCase = true)) {
                        try {
                            val rewardResp = AnnualReviewRpc.getReward(code, recordNo)
                            if (rewardResp != null && !rewardResp.isEmpty()) {
                                val rewardRoot = rewardResp.toJsonObject()
                                if (rewardRoot.optBoolean("isSuccess", false)) {
                                    val rewardComps = rewardRoot.optJSONObject("components")
                                    if (rewardComps != null) {
                                        var rewardComp = rewardComps.optJSONObject(AnnualReviewRpc.GET_REWARD_COMPONENT)
                                        if (rewardComp == null) {
                                            try {
                                                for (key in rewardComps.keys) {
                                                    rewardComp = rewardComps.optJSONObject(key)
                                                    if (rewardComp != null) break
                                                }
                                            } catch (_: Throwable) {
                                            }
                                        }
                                        if (rewardComp != null && rewardComp.optBoolean(
                                                "isSuccess", true
                                            )
                                        ) {
                                            val rewardContent = rewardComp.optJSONObject("content")
                                            if (rewardContent != null) {
                                                var rewardTask = rewardContent.optJSONObject("processedTask")
                                                if (rewardTask == null) {
                                                    rewardTask = rewardContent.optJSONObject("claimedTask")
                                                }
                                                if (rewardTask != null) {
                                                    val rs = rewardTask.optString("rewardStatus", "")
                                                    if (!rs.isEmpty()) {
                                                        rewardStatus = rs
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            StructuredLog.printStackTrace("$TAG.doAnnualReview.getReward", e)
                        }
                    }

                    processed++
                    StructuredLog.other("年度回顾🎞[任务完成]$taskName#状态=$newStatus 奖励状态=$rewardStatus")
                }

                record(
                    "$TAG.doAnnualReview", "年度回顾🎞[执行汇总] 待处理=$candidate 已领取=$applied 已提交=$processed 失败=$failed"
                )
            } catch (t: Throwable) {
                StructuredLog.printStackTrace("$TAG.doAnnualReview", t)
            }
        }

        /**
         * 会员签到
         */
        /**
         * 会员签到
         */

}
