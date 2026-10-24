package io.github.magisk317.sesame.task.antSports

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.data.StatusFlags
import io.github.magisk317.sesame.util.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import io.github.magisk317.sesame.util.toJsonObject

private fun parseSportsTaskJson(raw: String): JsonObject = raw.toJsonObject()

/** Coordinates sports sign-in, task completion, task rewards and home assets. */
internal class AntSportsTaskService {
    companion object {
        private const val TAG = "AntSportsTaskService"
        private const val SPORTS_TASKS_COMPLETED_DATE = "SPORTS_TASKS_COMPLETED_DATE"
    }

    internal suspend fun runTasks() {
        try {
            checkIn()
            val jo = parseSportsTaskJson(AntSportsRpcCall.queryCoinTaskPanel())

            if (ResChecker.checkRes(TAG, jo)) {
                val data = jo.getJSONObject("data")
                val taskList = data.getJSONArray("taskList")

                var totalTasks = 0
                var completedTasks = 0
                var availableTasks = 0

                for (i in 0 until taskList.length()) {
                    val taskDetail = taskList.getJSONObject(i)
                    val taskId = taskDetail.getString("taskId")
                    val taskName = taskDetail.getString("taskName")
                    val taskStatus = taskDetail.getString("taskStatus")
                    val taskType = taskDetail.optString("taskType", "")

                    // 排除自动结算任务
                    if (taskType == "SETTLEMENT") continue

                    // 黑名单过滤
                    if (TaskBlacklist.isTaskInBlacklist(taskId) || TaskBlacklist.isTaskInBlacklist(taskName)) {
                        continue
                    }

                    totalTasks++

                    when (taskStatus) {
                        "HAS_RECEIVED" -> {
                            completedTasks++
                        }
                        "WAIT_RECEIVE" -> {
                            if (receiveTaskReward(taskDetail, taskName)) {
                                completedTasks++
                            }
                        }
                        "WAIT_COMPLETE" -> {
                            availableTasks++
                            if (completeTask(taskDetail, taskName)) {
                                completedTasks++
                            }
                        }
                        else -> {
                            StructuredLog.error(TAG, "做任务得能量🎈[未知状态：$taskName，状态：$taskStatus]")
                        }
                    }
                }

                // completeTask changes the server-side status to WAIT_RECEIVE.
                // Re-query before deciding that the task center is complete.
                claimPendingAwards()

                StructuredLog.record(TAG, "运动任务完成情况：$completedTasks/$totalTasks，可执行任务：$availableTasks")

                // 所有任务完成后标记
                if (totalTasks > 0 && completedTasks >= totalTasks && availableTasks == 0) {
                    val today = TimeUtil.getDateStr2()
                    TaskRuntimeStore.put(SPORTS_TASKS_COMPLETED_DATE, today)
                    Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_DAILY_TASKS_DONE)
                    StructuredLog.record(TAG, "运动任务 全部完成，今日不再执行")
                }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }

    /**
     * @brief 领取单个任务奖励
     *
     * @param taskDetail 任务详情 JSON
     * @param taskName   任务名称
     * @return 是否视为成功
     */
    private fun receiveTaskReward(taskDetail: JsonObject, taskName: String): Boolean {
        return try {
            val assetId = taskDetail.getString("assetId")
            val prizeAmount = taskDetail.getInt("prizeAmount").toString()

            val result = AntSportsRpcCall.pickBubbleTaskEnergy(assetId)
            val resultData = parseSportsTaskJson(result)

            if (ResChecker.checkRes(TAG, result)) {
                StructuredLog.other("做任务得能量🎈[$taskName] +$prizeAmount 能量")
                true
            } else {
                val errorMsg = resultData.optString("errorMsg", "未知错误")
                val errorCode = resultData.optString("errorCode", "")
                StructuredLog.error(TAG, "做任务得能量🎈[领取失败：$taskName，错误：$errorCode - $errorMsg]")
                if (!resultData.optBoolean("retryable", true) || errorCode == "CAMP_TRIGGER_ERROR") {
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            StructuredLog.error(TAG, "做任务得能量🎈[领取异常：$taskName，错误：${e.message}]")
            false
        }
    }

    /**
     * @brief 执行任务（可能包含多次完成）
     */
    private suspend fun completeTask(taskDetail: JsonObject, taskName: String): Boolean {
        return try {
            val taskId = taskDetail.getString("taskId")
            val prizeAmount = taskDetail.getString("prizeAmount")
            val currentNum = taskDetail.getInt("currentNum")
            val limitConfigNum = taskDetail.getInt("limitConfigNum")
            val remainingNum = limitConfigNum - currentNum
            val needSignUp = taskDetail.optBoolean("needSignUp", false)

            if (remainingNum <= 0) {
                return true
            }

            // 需要先签到
            if (needSignUp) {
                if (!signUpForTask(taskId, taskName)) {
                    return false
                }
                delay(2000)
            }

            var success = true
            for (i in 0 until remainingNum) {
                val result = parseSportsTaskJson(AntSportsRpcCall.completeExerciseTasks(taskId))
                if (ResChecker.checkRes(TAG, result)) {
                    StructuredLog.record(
                        TAG,
                        "做任务得能量🎈[完成任务：$taskName，得$prizeAmount💰]#(${i + 1}/$remainingNum)"
                    )

                    if (i == remainingNum - 1) {
                        delay(2000)
                        receiveCoinAsset()
                    }
                } else {
                    val errorMsg = result.optString("errorMsg", "未知错误")
                    StructuredLog.error(
                        TAG,
                        "做任务得能量🎈[任务失败：$taskName，错误：$errorMsg]#(${i + 1}/$remainingNum)"
                    )
                    val errorCode = result.optString("errorCode", "")
                    if (errorCode.isNotEmpty()) {
                        TaskBlacklist.autoAddToBlacklist(taskId, taskName, errorCode)
                    }
                    success = false
                    break
                }

                if (remainingNum > 1 && i < remainingNum - 1) {
                    delay(10000)
                }
            }
            success
        } catch (e: Exception) {
            StructuredLog.error(TAG, "做任务得能量🎈[执行异常：$taskName，错误：${e.message}]")
            false
        }
    }

    /** Claim task-center rewards that became available after a finish RPC. */
    internal fun claimPendingAwards() {
        try {
            val response = parseSportsTaskJson(AntSportsRpcCall.queryCoinTaskPanel())
            if (!ResChecker.checkRes(TAG, response)) return
            val tasks = response.optJSONObject("data")?.optJSONArray("taskList") ?: return
            for (index in 0 until tasks.length()) {
                val task = tasks.optJSONObject(index) ?: continue
                if (task.optString("taskStatus") != "WAIT_RECEIVE") continue
                val taskId = task.optString("taskId")
                val taskName = task.optString("taskName", taskId)
                if (taskId.isEmpty() || TaskBlacklist.isTaskInBlacklist(taskId) ||
                    TaskBlacklist.isTaskInBlacklist(taskName)
                ) continue
                receiveTaskReward(task, taskName)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "运动任务领奖轮询异常", t)
        }
    }

    /**
     * @brief 为任务执行报名
     */
    private fun signUpForTask(taskId: String, taskName: String): Boolean {
        return try {
            val result = AntSportsRpcCall.signUpTask(taskId)
            val resultData = parseSportsTaskJson(result)

            if (ResChecker.checkRes(TAG, resultData)) {
                val data = resultData.optJSONObject("data")
                val taskOrderId = data?.optString("taskOrderId", "") ?: ""
                StructuredLog.other("做任务得能量🎈[签到成功：$taskName，订单：$taskOrderId]")
                true
            } else {
                val errorMsg = resultData.optString("errorMsg", "未知错误")
                StructuredLog.error(TAG, "做任务得能量🎈[签到失败：$taskName，错误：$errorMsg]")
                false
            }
        } catch (e: Exception) {
            StructuredLog.error(TAG, "做任务得能量🎈[签到异常：$taskName，错误：${e.message}]")
            false
        }
    }

    /**
     * @brief 运动首页推荐能量球任务
     *
     * @details
     * - 使用 {@link AntSportsRpcCall#queryEnergyBubbleModule} 获取 recBubbleList
     * - 对有 channel 的记录执行任务
     * - 成功后统一调用 pickBubbleTaskEnergy 领取奖励
     */
    internal suspend fun runEnergyBubbleTask() {
        try {
            val jo = parseSportsTaskJson(AntSportsRpcCall.queryEnergyBubbleModule())
            if (!ResChecker.checkRes(TAG, jo)) {
                StructuredLog.error(TAG, "queryEnergyBubbleModule fail: $jo")
                return
            }

            val data = jo.optJSONObject("data") ?: return
            if (!data.has("recBubbleList")) return

            val recBubbleList = data.optJSONArray("recBubbleList") ?: return
            if (recBubbleList.length() == 0) return

            var hasCompletedTask = false

            for (i in 0 until recBubbleList.length()) {
                val bubble = recBubbleList.optJSONObject(i) ?: continue

                val id = bubble.optString("id")
                val taskId = bubble.optString("channel", "")
                if (taskId.isEmpty()) continue
                if (TaskBlacklist.isTaskInBlacklist(id)) continue

                val sourceName = bubble.optString("simpleSourceName", "")
                val coinAmount = bubble.optInt("coinAmount", 0)
                StructuredLog.record(TAG, "运动首页任务🏃[完成请求：$sourceName，taskId=$taskId，coin=$coinAmount]")

                val completeRes = parseSportsTaskJson(AntSportsRpcCall.completeExerciseTasks(taskId))
                if (ResChecker.checkRes(TAG, completeRes)) {
                    hasCompletedTask = true
                    val dataObj = completeRes.optJSONObject("data")
                    val assetCoinAmount = dataObj?.optInt("assetCoinAmount", 0) ?: 0
                    StructuredLog.other("运动球任务[$sourceName]#奖励$assetCoinAmount💰")
                } else {
                    val errorCode = completeRes.optString("errorCode", "")
                    val errorMsg = completeRes.optString("errorMsg", "")
                    StructuredLog.error(TAG, "运动球任务❌[$sourceName]#$completeRes 任务：$bubble")

                    if (id.isNotEmpty()) {
                        TaskBlacklist.addToBlacklist(id, sourceName)
                    }
                }

                val sleepMs = RandomUtil.nextInt(10000, 30000)
                delay(sleepMs.toLong())
            }

            if (hasCompletedTask) {
                val result = AntSportsRpcCall.pickBubbleTaskEnergy()
                val resultJson = parseSportsTaskJson(result)
                if (ResChecker.checkRes(TAG, resultJson)) {
                    val dataObj = resultJson.optJSONObject("data")
                    val balance = dataObj?.optString("balance", "0") ?: "0"
                    StructuredLog.other("拾取能量球成功  当前余额: $balance💰")
                } else {
                    StructuredLog.error(TAG, "领取能量球任务失败: ${resultJson.optString("errorMsg", "未知错误")}")
                }
            } else {
                StructuredLog.record(TAG, "未完成任何任务，跳过领取能量球")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "sportsEnergyBubbleTask err:", t)
        }
    }

    /**
     * @brief 运动签到：先 query 再 signIn
     */
    internal suspend fun checkIn() {
        try {
            val queryJo = parseSportsTaskJson(AntSportsRpcCall.signInCoinTask("query"))
            if (ResChecker.checkRes(TAG, queryJo)) {
                val data = queryJo.getJSONObject("data")
                val isSigned = data.getBoolean("signed")

                if (!isSigned) {
                    val signConfigList = data.getJSONArray("signConfigList")
                    for (i in 0 until signConfigList.length()) {
                        val configItem = signConfigList.getJSONObject(i)
                        val toDay = configItem.getBoolean("toDay")
                        val itemSigned = configItem.getBoolean("signed")

                        if (toDay && !itemSigned) {
                            val coinAmount = configItem.getInt("coinAmount")
                            val signJo = parseSportsTaskJson(AntSportsRpcCall.signInCoinTask("signIn"))
                            if (ResChecker.checkRes(TAG, signJo)) {
                                val signData = signJo.getJSONObject("data")
                                val subscribeConfig = if (signData.has("subscribeConfig"))
                                    signData.getJSONObject("subscribeConfig")
                                else io.github.magisk317.sesame.util.jsonObject()

                                val expireDays = if (subscribeConfig.has("subscribeExpireDays"))
                                    subscribeConfig.getString("subscribeExpireDays")
                                else "未知"
                                val toast = if (signData.has("toast")) signData.getString("toast") else ""

                                StructuredLog.other(
                                    "做任务得能量🎈[签到${expireDays}天|" +
                                        coinAmount + "能量，" + toast + "💰]"
                                )
                            } else {
                                StructuredLog.record(TAG, "签到接口调用失败：$signJo")
                            }
                            break
                        }
                    }
                } else {
                    StructuredLog.record(TAG, "运动签到今日已签到")
                }
            } else {
                StructuredLog.record(TAG, "查询签到状态失败：$queryJo")
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "sportsCheck_in err", e)
        }
    }

    /**
     * @brief 首页金币收集逻辑
     */
    internal suspend fun receiveCoinAsset() {
        try {
            val s = AntSportsRpcCall.queryCoinBubbleModule()
            var jo = parseSportsTaskJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val data = jo.getJSONObject("data")
                if (!data.has("receiveCoinBubbleList")) return

                val ja = data.getJSONArray("receiveCoinBubbleList")
                for (i in 0 until ja.length()) {
                    jo = ja.getJSONObject(i)
                    val assetId = jo.getString("assetId")
                    val coinAmount = jo.getInt("coinAmount")
                    val res = parseSportsTaskJson(AntSportsRpcCall.receiveCoinAsset(assetId, coinAmount))
                    if (ResChecker.checkRes(TAG, res)) {
                        StructuredLog.other("收集金币💰[$coinAmount 个]")
                    } else {
                        StructuredLog.record(TAG, "首页收集金币 $res")
                    }
                }
            } else {
                StructuredLog.record(TAG, s)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "receiveCoinAsset err:", t)
        }
    }

    // ---------------------------------------------------------------------
}
