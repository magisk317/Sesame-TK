package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.TimeUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Owns farm task sign-in and food reward claiming, including capacity guards. */
class FarmTaskRewardService(
    private val foodStock: () -> Int,
    private val foodStockLimit: () -> Int,
    private val syncAnimalStatus: () -> Unit,
    private val addFood: (Int) -> Unit,
    private val recordFarmGame: () -> Boolean,
    private val gameRewardMax: () -> Int,
    private val ignoreAccelerateLimit: () -> Boolean,
    private val signRegardless: () -> Boolean,
    private val onUnreceived: () -> Unit,
    private val onReceived: () -> Unit,
) {
    suspend fun receive() {
        try {
            var doubleCheck: Boolean
            var isFeedFull = false
            do {
                doubleCheck = false
                val response = FarmCoreRpc.listFarmTask()
                if (response.isNullOrEmpty()) {
                    StructuredLog.record(TAG, "receiveFarmAwards: 收到空响应，跳过本次执行")
                    return
                }
                val result = response.toJsonObject()
                if (ResChecker.checkRes(TAG + "查询庄园任务失败:", result)) {
                    val farmTaskList = result.getJSONArray("farmTaskList")
                    val signList = result.getJSONObject("signList")
                    val needFarmGame = recordFarmGame() &&
                        !Status.hasFlagToday("farm::farmGameFinished")

                    if (!Status.hasFlagToday("farm::signed")) {
                        syncAnimalStatus()
                        val timeReached = TimeUtil.isNowAfterOrCompareTimeStr("1400")
                        val space = foodStockLimit() - foodStock()
                        var signAward = 180
                        try {
                            val signItems = signList.optJSONArray("signList")
                            val currentKey = signList.optString("currentSignKey")
                            if (signItems != null && !currentKey.isNullOrEmpty()) {
                                for (index in signItems.indices) {
                                    val sign = signItems.getJSONObject(index)
                                    if (sign.optString("signKey") == currentKey) {
                                        signAward = sign.optString("awardCount", "180")
                                            .toIntOrNull() ?: 180
                                        break
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        }

                        val enoughSpace = if (needFarmGame) {
                            space > gameRewardMax()
                        } else {
                            space >= signAward
                        }
                        if (signRegardless() || timeReached || enoughSpace) {
                            if (sign(signList) && space < signAward) {
                                StructuredLog.farm("签到实际获得饲料: ${space}g (因饲料空间不足)")
                            }
                        } else {
                            val message = if (needFarmGame) {
                                "预留游戏改分的饲料空间，庄园暂不执行签到"
                            } else {
                                "饲料空间不足${signAward}g，庄园暂不签到"
                            }
                            StructuredLog.record(
                                TAG,
                                "${message}。14点后会强制签到；如已签到请忽略",
                            )
                        }
                    }

                    for (index in farmTaskList.indices) {
                        val task = farmTaskList.getJSONObject(index)
                        if (task.getString("taskStatus") != TaskStatus.FINISHED.name) {
                            delay(1000)
                            continue
                        }
                        val title = task.optString("title", "未知任务")
                        val award = task.optInt("awardCount", 0)
                        val taskId = task.optString("taskId")
                        syncAnimalStatus()

                        val stockAfter = foodStock() + award
                        val isNight = TimeUtil.isNowAfterOrCompareTimeStr("2000")
                        val space = foodStockLimit() - foodStock()
                        if (task.optString("awardType") == "ALLPURPOSE") {
                            if (foodStock() >= foodStockLimit()) {
                                StructuredLog.record(TAG, "饲料[已满],暂不领取")
                                onUnreceived()
                                isFeedFull = true
                                break
                            }
                            if (!ignoreAccelerateLimit() && needFarmGame &&
                                foodStock() >= foodStockLimit() - gameRewardMax()
                            ) {
                                onUnreceived()
                                StructuredLog.record(
                                    "当日游戏改分未完成，预留最多${gameRewardMax()}饲料空间，现有饲料${foodStock()}g",
                                )
                                isFeedFull = true
                                break
                            }
                            if (award > space && !isNight) {
                                if (award > 90 && space >= 90) {
                                    onUnreceived()
                                    continue
                                }
                                StructuredLog.record(
                                    TAG,
                                    "领取任务：${title} 的饲料奖励 ${award}g后将超过[${foodStockLimit()}g]上限!" +
                                        "终止领取。现有饲料${foodStock()}g",
                                )
                                onUnreceived()
                                isFeedFull = true
                                break
                            }
                            if (award > space && isNight) {
                                StructuredLog.record(
                                    "20点后领取任务：${title} 的饲料奖励 ${award}g后饲料将超过上限，" +
                                        "现有饲料${foodStock()}g，溢出${award - space}g",
                                )
                            }
                        }

                        val claim = FarmCoreRpc.receiveFarmTaskAward(taskId).toJsonObject()
                        if (ResChecker.checkRes(TAG + "领取庄园任务奖励失败:", claim)) {
                            addFood(award)
                            StructuredLog.farm(
                                "收取庄园任务奖励[$title] # ${award}g (剩余容量: ${foodStockLimit() - foodStock()}g)",
                            )
                            if (stockAfter >= foodStockLimit()) {
                                StructuredLog.farm("领取饲料后饲料[已满]${foodStock()}g，停止后续领取")
                                isFeedFull = true
                                break
                            }
                            doubleCheck = true
                            onReceived()
                        } else {
                            val code = claim.optString("resultCode", "")
                            val memo = claim.optString("memo", "")
                            if (code == "331" || memo.contains("饲料槽已满")) {
                                StructuredLog.record(TAG, "领取失败：饲料槽已满，停止后续领取")
                                isFeedFull = true
                                break
                            }
                            StructuredLog.error(TAG, "领取庄园任务奖励失败：$claim")
                        }
                        delay(1000)
                    }
                }
            } while (doubleCheck && !isFeedFull)
        } catch (cancelled: CancellationException) {
            StructuredLog.record(TAG, "receiveFarmAwards 协程被取消")
            throw cancelled
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "receiveFarmAwards 错误:", error)
        }
    }

    private fun sign(signList: JsonObject): Boolean {
        return try {
            val flag = "farm::signed"
            if (Status.hasFlagToday(flag)) return false
            val signs = signList.getJSONArray("signList")
            val currentKey = signList.getString("currentSignKey")
            for (index in signs.indices) {
                val item = signs.getJSONObject(index)
                if (item.getString("signKey") != currentKey) continue
                if (!item.getBoolean("signed")) {
                    val response = FarmCoreRpc.sign()
                    if (ResChecker.checkRes(TAG, response)) {
                        StructuredLog.farm(
                            "庄园签到📅获得饲料${item.getString("awardCount")}g," +
                                "签到天数${item.getInt("currentContinuousCount")}",
                        )
                        Status.setFlagToday(flag)
                        return true
                    }
                    StructuredLog.farm("签到失败")
                    return false
                }
                StructuredLog.record(TAG, "今日已经签到了")
                Status.setFlagToday(flag)
                return false
            }
            false
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "庄园签到 JSON解析错误:", error)
            false
        }
    }

    private companion object {
        const val TAG = "FarmTaskRewardService"
    }
}
