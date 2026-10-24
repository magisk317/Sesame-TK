package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.CoroutineUtils
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.toJsonObject

/** Owns merchant-service availability, check-in, sign-up, and point tasks. */
class MerchantTaskService {
    suspend fun run(openDoor: Boolean, sign: Boolean, moreTask: Boolean) {
        val response = MerchantTaskRpc.transcodeCheck().toJsonObject()
        if (!ResChecker.checkRes(TAG, response)) return
        val data = response.getJSONObject("data")
        if (!data.optBoolean("isOpened")) {
            StructuredLog.record(TAG, "商家服务👪未开通")
            return
        }
        if (openDoor) {
            if (TimeUtil.isNowAfterTimeStr("0600") && TimeUtil.isNowBeforeTimeStr("1200")) {
                signIn()
            }
            signUp()
        }
        if (sign) merchantSign()
        if (moreTask) merchantMoreTask()
    }

    private fun signIn() = CoroutineUtils.run {
        try {
            val response = MerchantTaskRpc.queryActivity().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) {
                record("queryActivity $response")
                return@run
            }
            if (response.getString("signInStatus") != "SIGN_IN_ENABLE") return@run
            val result = MerchantTaskRpc.signIn(response.getString("activityNo")).toJsonObject()
            if (ResChecker.checkRes(TAG, result)) {
                StructuredLog.other("商家服务🏬[开门打卡签到成功]")
            } else {
                record(result.getString("errorMsg"))
                record(result.toString())
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "kmdkSignIn err:", error)
        }
    }

    private suspend fun signUp() = CoroutineUtils.run {
        try {
            repeat(5) {
                val response = MerchantTaskRpc.queryActivity().toJsonObject()
                if (ResChecker.checkRes(TAG, response)) {
                    val activityNo = response.getString("activityNo")
                    val datePart = activityNo.split("_").getOrNull(2)
                    if (datePart != TimeUtil.getFormatDate().replace("-", "")) return@run
                    when (response.getString("signUpStatus")) {
                        "SIGN_UP" -> return@run
                        "UN_SIGN_UP" -> {
                            val result = MerchantTaskRpc.signUp(activityNo).toJsonObject()
                            if (ResChecker.checkRes(TAG, result)) {
                                StructuredLog.other(
                                    "商家服务🏬[${response.getString("activityPeriodName")}开门打卡报名]",
                                )
                                return@run
                            }
                            record(result.getString("errorMsg"))
                            record(result.toString())
                        }
                    }
                } else {
                    record("queryActivity")
                    record(response.toString())
                }
                CoroutineUtils.delayCompat(500)
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "kmdkSignUp err:", error)
        }
    }

    private fun merchantSign() = CoroutineUtils.run {
        try {
            val raw = MerchantTaskRpc.merchantSign()
            var response = raw.toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) {
                record("doMerchantSign err:$raw")
                return@run
            }
            response = response.getJSONObject("data")
            if (response.getString("signInResult") == "SUCCESS") {
                StructuredLog.other("商家服务🏬[每日签到]#获得积分${response.getString("todayReward")}")
            } else {
                record(raw)
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "doMerchantSign err:", error)
        }
    }

    private suspend fun merchantMoreTask(): Unit = CoroutineUtils.run {
        try {
            var response: kotlinx.serialization.json.JsonObject = MerchantTaskRpc.taskList().toJsonObject()
            var needsSecondScan = false
            if (ResChecker.checkRes(TAG, response)) {
                val tasks = response.getJSONObject("data").getJSONArray("taskList")
                for (index in tasks.indices) {
                    val task = tasks.getJSONObject(index)
                    if (!task.has("status")) continue
                    val title = task.getString("title")
                    val reward = task.getString("reward")
                    when (task.getString("status")) {
                        "NEED_RECEIVE" -> if (task.has("pointBallId")) {
                            response = MerchantTaskRpc.receiveBall(task.getString("pointBallId")).toJsonObject()
                            if (ResChecker.checkRes(TAG, response)) {
                                StructuredLog.other("商家服务🏬[$title]#领取积分$reward")
                            }
                        }
                        "PROCESSING", "UNRECEIVED" -> {
                            val extendLog = task.optJSONObject("extendLog")
                            if (extendLog != null) {
                                val bizId = extendLog.getJSONObject("bizExtMap").getString("bizId")
                                response = MerchantTaskRpc.finishTask(bizId).toJsonObject()
                                if (ResChecker.checkRes(TAG, response)) {
                                    StructuredLog.other("商家服务🏬[$title]#领取积分$reward")
                                }
                                needsSecondScan = true
                            } else {
                                receiveTask(task.getString("taskCode"), title)
                            }
                        }
                    }
                }
                if (needsSecondScan) merchantMoreTask()
            } else {
                record("taskListQuery err: $response")
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "taskListQuery err:", error)
        } finally {
            CoroutineUtils.delayCompat(1000)
        }
    }

    private suspend fun receiveTask(taskCode: String, title: String) = CoroutineUtils.run {
        try {
            val response = MerchantTaskRpc.receiveTask(taskCode).toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) {
                record("taskReceive $response")
                return@run
            }
            CoroutineUtils.delayCompat(500)
            val actionCode = actionCode(taskCode) ?: return@run
            if (!ResChecker.checkRes(TAG, MerchantTaskRpc.queryAction(actionCode).toJsonObject())) return@run
            CoroutineUtils.delayCompat(16000)
            if (ResChecker.checkRes(TAG, MerchantTaskRpc.produce(actionCode).toJsonObject())) {
                StructuredLog.other("商家服务🏬[完成任务$title]")
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "taskReceive err:", error)
        }
    }

    private fun actionCode(taskCode: String): String? = when (taskCode) {
        "SYH_CPC_DYNAMIC" -> "SYH_CPC_DYNAMIC_VIEWED"
        "CYLLRW_TASK" -> "CYLLRW_VIEWED"
        "JFLLRW_TASK" -> "JFLL_VIEWED"
        "ZFBHYLLRW_TASK" -> "ZFBHYLL_VIEWED"
        "QQKLLRW_TASK" -> "QQKLL_VIEWED"
        "SSLLRW_TASK" -> "SSLL_VIEWED"
        "ELMGYLLRW2_TASK" -> "ELMGYLL_VIEWED"
        "ZMXYLLRW_TASK" -> "ZMXYLL_VIEWED"
        "GXYKPDDYH_TASK" -> "xykhkzd_VIEWED"
        "HHKLLRW_TASK" -> "HHKLLX_VIEWED"
        "TBNCLLRW_TASK" -> "TBNCLLRW_TASK_VIEWED"
        else -> null
    }

    private fun record(message: String) = StructuredLog.record(TAG, message)

    private companion object {
        const val TAG = "MerchantTaskService"
    }
}
