package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TaskBlacklist
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Owns the farm task-list protocol; AntFarm only schedules this service. */
object FarmTaskRunner {
    private const val TAG = "AntFarm"
    private const val ANSWER_CACHE_FLAG = "farmQuestion::cache"

    suspend fun run(answerQuestion: () -> Unit) {
        try {
            var needsAnotherPass: Boolean
            var pass = 0
            val attemptedBizKeys = mutableSetOf<String>()
            do {
                needsAnotherPass = false
                val response = FarmCoreRpc.listFarmTask().toJsonObject()
                if (!ResChecker.checkRes(TAG, response)) return
                val farmTaskList = response.getJSONArray("farmTaskList")

                for (index in 0 until farmTaskList.length()) {
                    val task = farmTaskList.getJSONObject(index)
                    val title = task.optString("title", "未知任务")
                    val taskStatus = task.getString("taskStatus")
                    val bizKey = task.getString("bizKey")

                    if (TaskBlacklist.isTaskInBlacklist(title) ||
                        TaskBlacklist.isTaskInBlacklist(bizKey)
                    ) {
                        StructuredLog.record(TAG, "跳过黑名单任务: $title ($bizKey)")
                        continue
                    }
                    if (Status.hasFlagToday("farm::task::limit::$bizKey")) continue

                    when (taskStatus) {
                        TaskStatus.TODO.name -> {
                            val isMultiStage = task.optInt("rightsTimes", 0) + 1 <
                                task.optInt("rightsTimesLimit", 0)
                            if (bizKey in attemptedBizKeys && !isMultiStage) continue
                            attemptedBizKeys += bizKey
                            val succeeded = when (bizKey) {
                                "VIDEO_TASK" -> {
                                    StructuredLog.record(TAG, "庄园任务🐔 处理视频: $title ($bizKey)")
                                    handleVideoTask(title)
                                }

                                "ANSWER" -> if (!Status.hasFlagToday(ANSWER_CACHE_FLAG)) {
                                    answerQuestion()
                                    true
                                } else {
                                    false
                                }

                                else -> {
                                    StructuredLog.record(TAG, "庄园任务🐔 处理任务: $title ($bizKey)")
                                    handleGeneralTask(bizKey, title)
                                }
                            }
                            if (succeeded && !isMultiStage) {
                                markTaskDoneToday(bizKey)
                            }
                            // 完成普通任务后也刷新一次：后端可能在完成前置任务后才投放新任务。
                            // attemptedBizKeys 防止刷新期间重复触发同一个普通任务。
                            if (succeeded) needsAnotherPass = true
                        }

                        TaskStatus.FINISHED.name -> {
                            // 尝试领取已完成任务的奖励（饲料任务需要 awardType）
                            val claimed = FarmDrawRpc.receiveFeedTaskAward(bizKey).toJsonObject()
                            if (ResChecker.checkRes(TAG, claimed)) {
                                StructuredLog.farm("庄园任务🧾[$title] 奖励已领取")
                            }
                            markTaskDoneToday(bizKey)
                            needsAnotherPass = true
                            delay(500L)
                            continue
                        }

                        TaskStatus.RECEIVED.name -> {
                            markTaskDoneToday(bizKey)
                            if (bizKey != "ANSWER") {
                                delay(1500L)
                                continue
                            }
                        }

                        else -> StructuredLog.record(
                            TAG,
                            "跳过非TODO任务: $title ($bizKey) 状态: $taskStatus",
                        )
                    }

                    if (bizKey == "ANSWER" && !Status.hasFlagToday(ANSWER_CACHE_FLAG)) {
                        answerQuestion()
                    }
                    delay(2000L)
                }
                pass++
                if (needsAnotherPass && pass < MAX_MULTI_STAGE_PASSES) {
                    StructuredLog.record(TAG, "检测到多阶段饲料任务未完成，刷新任务列表继续执行（第${pass + 1}轮）")
                    delay(MULTI_STAGE_REFRESH_DELAY_MS)
                }
            } while (needsAnotherPass && pass < MAX_MULTI_STAGE_PASSES)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "FarmTaskRunner 执行异常:", t)
        }
    }

    private suspend fun handleVideoTask(title: String): Boolean {
        val response = FarmDrawRpc.queryTabVideoUrl().toJsonObject()
        if (!ResChecker.checkRes(TAG, response)) return false

        val videoUrl = response.optString("videoUrl")
        val contentId = videoUrl.substringAfter("&contentId=", "").substringBefore("&refer")
        if (contentId.isBlank()) {
            StructuredLog.error(TAG, "解析视频ID失败: $title")
            return false
        }

        if (!ResChecker.checkRes(TAG, FarmDrawRpc.videoDeliverModule(contentId).toJsonObject())) return false
        delay(15_000L)
        if (ResChecker.checkRes(TAG, FarmDrawRpc.videoTrigger(contentId).toJsonObject())) {
            StructuredLog.farm("庄园视频任务确认成功[$title]")
            return true
        }
        return false
    }

    private fun handleGeneralTask(bizKey: String, title: String): Boolean {
        val raw = FarmCoreRpc.doFarmTask(bizKey)
        if (!raw.isNullOrEmpty()) {
            val response = raw.toJsonObject()
            if (ResChecker.checkRes(TAG, response)) {
                StructuredLog.farm("庄园任务完成[$title]")
                return true
            }
            val resultCode = response.optString("resultCode")
            if (resultCode == "309") {
                Status.setFlagToday("farm::task::limit::$bizKey")
                StructuredLog.record(TAG, "庄园任务[$title]已达上限")
                return false
            }
        }

        // doFarmTask 失败时，用 antiep.finishTask 兜底（饲料小游戏等任务）
        val fallback = FarmDrawRpc.finishFeedTask(bizKey).toJsonObject()
        val code = fallback.optString("code")
        if (code == "100000000") {
            StructuredLog.farm("庄园任务完成[$title] (via antiep)")
            return true
        }
        if (code == "B000000008") {
            // 无状态转换处理（已完成）
            Status.setFlagToday("farm::task::limit::$bizKey")
            return false
        }
        // 400000040 = 不支持rpc调用，加入黑名单避免重复尝试
        if (code == "400000040") {
            TaskBlacklist.autoAddToBlacklist(bizKey, title, code)
        }
        return false
    }

    private fun markTaskDoneToday(bizKey: String) {
        val flag = "farm::task::limit::$bizKey"
        if (!Status.hasFlagToday(flag)) {
            Status.setFlagToday(flag)
            StructuredLog.record(TAG, "庄园一次性任务[$bizKey]今日已完成，后续轮训跳过")
        }
    }

    private const val MAX_MULTI_STAGE_PASSES = 16
    private const val MULTI_STAGE_REFRESH_DELAY_MS = 2500L
}
