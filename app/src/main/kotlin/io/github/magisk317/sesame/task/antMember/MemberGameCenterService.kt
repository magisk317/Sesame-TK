package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.StructuredLog.record
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.length
import kotlinx.coroutines.delay

/**
 * Runs game-center sign-in, platform tasks, and pending point-ball claims.
 *
 * TODO (2026-08-09): the "play games for coins" task was verified on-device.
 * A real session reaches `com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallComplete`
 * after enterGame and genuine duration reports, and the page advanced from 1/9 to 2/9.
 * Do not invoke that completion RPC from the background executor without a real game
 * session; keep this task in observation/manual status until a supported, server-approved
 * workflow is identified.
 */
class MemberGameCenterService {
    suspend fun run() {
        try {
            // 1. 查询签到状态并尝试签到
            try {
            val resp = GameCenterRpc.querySignInBall()
                val root = resp.toJsonObject()
                if (!ResChecker.checkRes(TAG, root)) {
                    val msg = root.optString("errorMsg", root.optString("resultView", resp))
                    StructuredLog.error("$TAG.enableGameCenter.signIn", "游戏中心🎮[签到查询失败]#$msg")
                } else {
                    val data = root.optJSONObject("data")

                    // data 为空表示服务端没有可签到内容，按今日已签到处理。
                    if (data == null || data.isEmpty()) {
                        record("$TAG.enableGameCenter.signIn", "游戏中心🎮[今日已签到](data为空)")
                        return
                    }
                    val signModule = data.optJSONObject("signInBallModule")
                    val signed = signModule != null && signModule.optBoolean("signInStatus", false)
                    if (signed) {
                        record("$TAG.enableGameCenter.signIn", "游戏中心🎮[今日已签到]")
                    } else {
                        val signResp = GameCenterRpc.continueSignIn()
                        delay(300)
                        val signJo = signResp.toJsonObject()
                        if (!ResChecker.checkRes(TAG, signJo)) {
                            val msg = signJo.optString(
                                "errorMsg", signJo.optString("resultView", signResp)
                            )
                            StructuredLog.error("$TAG.enableGameCenter.signIn", "游戏中心🎮[签到失败]#$msg")
                        } else {
                            val signData = signJo.optJSONObject("data")
                            var title = ""
                            var desc = ""
                            var type = ""
                            if (signData != null) {
                                val toast = signData.optJSONObject("autoSignInToastModule")
                                if (toast != null) {
                                    title = toast.optString("title", "")
                                    desc = toast.optString("desc", "")
                                    type = toast.optString("type", "")
                                }
                            }
                            val toastSuccess = "SUCCESS".equals(type, ignoreCase = true) && !title.contains("失败") && !desc.contains("失败")
                            if (toastSuccess) {
                                val sb = StringBuilder()
                                sb.append("游戏中心🎮[每日签到成功]")
                                if (!title.isEmpty()) {
                                    sb.append("#").append(title)
                                }
                                if (!desc.isEmpty()) {
                                    sb.append("#").append(desc)
                                }
                                StructuredLog.other(sb.toString())
                            } else {
                                val sb = StringBuilder()
                                if (!title.isEmpty()) {
                                    sb.append(title)
                                }
                                if (!desc.isEmpty()) {
                                    if (sb.isNotEmpty()) sb.append(" ")
                                    sb.append(desc)
                                }
                                StructuredLog.error(
                                    "$TAG.enableGameCenter.signIn", "游戏中心🎮[签到失败]#" + (if (sb.isNotEmpty()) sb.toString() else signResp)
                                )
                            }
                        }
                    }
                }
            } catch (th: Throwable) {
                StructuredLog.printStackTrace(TAG, "enableGameCenter.signIn err:", th)
            }

            // 2. 查询任务列表,完成平台任务
            try {
                val resp = GameCenterRpc.queryTaskList()
                val root = resp.toJsonObject()
                if (!ResChecker.checkRes(TAG, root)) {
                    val msg = root.optString("errorMsg", root.optString("resultView", resp))
                    StructuredLog.error("$TAG.enableGameCenter.tasks", "游戏中心🎮[任务列表查询失败]#$msg")
                } else {
                    val data = root.optJSONObject("data")
                    if (data != null) {
                        val platformTaskModule = data.optJSONObject("platformTaskModule")
                        if (platformTaskModule != null) {
                            val platformTaskList = platformTaskModule.optJSONArray("platformTaskList")
                            if (platformTaskList != null && platformTaskList.length() > 0) {
                                var total = 0
                                var finished = 0
                                var failed = 0
                                var lastFailedTaskId = ""
                                var lastFailedCount = 0

                                for (i in 0..<platformTaskList.length()) {
                                    val task = platformTaskList.optJSONObject(i) ?: continue

                                    val taskId = task.optString("taskId")
                                    val status = task.optString("taskStatus")

                                    if (taskId.isEmpty()) continue
                                    if ("NOT_DONE" != status && "SIGNUP_COMPLETE" != status) {
                                        continue
                                    }

                                    // 如果是上次失败的任务,计数加1
                                    if (taskId == lastFailedTaskId) {
                                        lastFailedCount++
                                        if (lastFailedCount >= 2) {
                                            record(
                                                "$TAG.enableGameCenter.tasks", "游戏中心🎮任务[" + task.optString("title") + "]连续失败2次,跳过"
                                            )
                                            continue
                                        }
                                    } else {
                                        // 新任务,重置计数
                                        lastFailedTaskId = taskId
                                        lastFailedCount = 0
                                    }

                                    total++
                                    val title = task.optString("title")
                                    val subTitle = task.optString("subTitle")
                                    val needSignUp = task.optBoolean("needSignUp", false)
                                    val pointAmount = task.optInt("pointAmount", 0)

                                    try {
                                        // needSignUp 为 true 且是首次状态 NOT_DONE:先报名
                                        if (needSignUp && "NOT_DONE" == status) {
                                            val signUpResp = GameCenterRpc.doTaskSignup(taskId)
                                            delay(300)
                                            val signUpJo = signUpResp.toJsonObject()
                                            if (!ResChecker.checkRes(TAG, signUpJo)) {
                                                val msg = signUpJo.optString(
                                                    "errorMsg", signUpJo.optString("resultView", signUpResp)
                                                )
                                                StructuredLog.error(
                                                    "$TAG.enableGameCenter.tasks", "游戏中心🎮任务[$title]报名失败#$msg"
                                                )
                                                failed++
                                                continue
                                            }
                                        }

                                        // 完成任务
                                        val doResp = GameCenterRpc.doTaskSend(taskId)
                                        delay(300)
                                        val doJo = doResp.toJsonObject()

                                        if (ResChecker.checkRes(TAG, doJo)) {
                                            // 检查返回的任务状态
                                            val doData = doJo.optJSONObject("data")
                                            val resultStatus = if (doData != null) doData.optString(
                                                "taskStatus", ""
                                            ) else ""

                                            if ("SIGNUP_COMPLETE" == resultStatus || "NOT_DONE" == resultStatus) {
                                                // 状态未变更,记为失败
                                                StructuredLog.error(
                                                    "$TAG.enableGameCenter.tasks", "游戏中心🎮任务[$title]状态未变更,可能无法完成"
                                                )
                                                failed++
                                            } else {
                                                // 真正完成,重置失败计数
                                                StructuredLog.other(
                                                    "游戏中心🎮任务[" + (subTitle.ifEmpty { title }) + "]#完成,奖励" + pointAmount + "玩乐豆" + (if (needSignUp) "(签到任务)" else "")
                                                )
                                                finished++
                                                lastFailedTaskId = ""
                                                lastFailedCount = 0
                                            }
                                        } else {
                                            val msg = doJo.optString(
                                                "errorMsg", doJo.optString("resultView", doResp)
                                            )
                                            StructuredLog.error(
                                                "$TAG.enableGameCenter.tasks", "游戏中心🎮任务[$title]完成失败#$msg"
                                            )
                                            failed++
                                        }
                                    } catch (e: Throwable) {
                                        StructuredLog.printStackTrace("$TAG.enableGameCenter.tasks.doTask", e)
                                        failed++
                                    }
                                }

                                if (total > 0) {
                                    record(
                                        "$TAG.enableGameCenter.tasks", "游戏中心🎮[平台任务处理完成]#待做:$total 完成:$finished 失败:$failed"
                                    )
                                } else {
                                    record(
                                        "$TAG.enableGameCenter.tasks", "游戏中心🎮[无待处理的平台任务]"
                                    )
                                }
                            } else {
                                record("$TAG.enableGameCenter.tasks", "游戏中心🎮[平台任务列表为空]")
                            }
                        }
                    }
                }
            } catch (th: Throwable) {
                StructuredLog.printStackTrace(TAG, "enableGameCenter.tasks err:", th)
            }

            // 3. 查询待收乐豆并使用一键收取接口
            try {
                val resp = GameCenterRpc.queryPointBallList()
                val root = resp.toJsonObject()
                if (!ResChecker.checkRes(TAG, root)) {
                    val msg = root.optString("errorMsg", root.optString("resultView", resp))
                    StructuredLog.error("$TAG.enableGameCenter.point", "游戏中心🎮[查询待收乐豆失败]#$msg")
                } else {
                    val data = root.optJSONObject("data")
                    val pointBallList = data?.optJSONArray("pointBallList")
                    if (pointBallList == null || pointBallList.length() == 0) {
                        record("$TAG.enableGameCenter.point", "游戏中心🎮[暂无可领取乐豆]")
                    } else {
                        val batchResp = GameCenterRpc.batchReceivePointBall()
                        delay(300)
                        val batchJo = batchResp.toJsonObject()
                        if (ResChecker.checkRes(TAG, batchJo)) {
                            val batchData = batchJo.optJSONObject("data")
                            val receiveAmount = batchData?.optInt("receiveAmount", 0) ?: 0
                            val totalAmount = batchData?.optInt("totalAmount", receiveAmount) ?: receiveAmount
                            if (receiveAmount > 0) {
                                StructuredLog.other("游戏中心🎮[一键领取乐豆成功]#本次领取" + receiveAmount + " | 当前累计" + totalAmount + "玩乐豆")
                            } else {
                                record("$TAG.enableGameCenter.point", "游戏中心🎮[暂无可领取乐豆]")
                            }
                        } else {
                            val msg = batchJo.optString(
                                "errorMsg", batchJo.optString("resultView", batchResp)
                            )
                            StructuredLog.error(
                                "$TAG.enableGameCenter.point", "游戏中心🎮[一键领取乐豆失败]#$msg"
                            )
                        }
                    }
                }
            } catch (th: Throwable) {
                StructuredLog.printStackTrace(TAG, "enableGameCenter.point err:", th)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
        }
    }


    private companion object {
        const val TAG = "MemberGameCenterService"
    }
}
