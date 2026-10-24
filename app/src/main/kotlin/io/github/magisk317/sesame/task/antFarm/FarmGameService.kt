package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Owns farm mini-game score changes and the draw-play workflow. */
class FarmGameService(
    private val allowDonationTasks: () -> Boolean,
    private val allowAutoExchange: () -> Boolean,
) {
    private val TAG = "FarmGameService"

    /**
     * 庄园游戏改分逻辑
     */
    private suspend fun recordFarmGame(gameType: AntFarm.GameType) {
        try {
            while (true) {
                val initRes = FarmCoreRpc.initFarmGame(gameType.name)
                val joInit = initRes.toJsonObject()
                if (!ResChecker.checkRes(TAG, joInit)) break

                val gameAward = joInit.optJSONObject("gameAward")
                if (gameAward?.optBoolean("level3Get") == true) {
                    StructuredLog.record(TAG, "庄园游戏🎮[${gameType.gameName()}]#今日奖励已领满")
                    break
                }

                val remainingCount = joInit.optInt("remainingGameCount", 1)
                if (remainingCount > 0) {
                    val recordResult = FarmCoreRpc.recordFarmGame(gameType.name)
                        val joRecord = recordResult.toJsonObject()
                    if (ResChecker.checkRes(TAG, joRecord)) {
                        val awardStr = parseGameAward(joRecord)
                        StructuredLog.farm("庄园游戏🎮[${gameType.gameName()}]#$awardStr")

                        if (joRecord.optInt("remainingGameCount", 0) > 0) {
                            delay(3000)
                            continue
                        }
                    } else {
                        StructuredLog.record(TAG, "庄园游戏提交失败: $joRecord")
                    }
                }

                // 次数用完后，尝试获取额外任务机会
                if (handleGameTasks(gameType)) {
                    delay(3000)
                    continue // 任务处理成功（如领完奖励或做完任务），重新进入初始化检查次数
                }

                break
            }
        } catch (e: CancellationException) {
            // 协程取消异常必须重新抛出，不能吞掉
             StructuredLog.record(TAG, "recordFarmGame 协程被取消")
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "recordFarmGame err:",t)
        }
    }

    /**
     解析游戏奖励信息
     */
    private fun parseGameAward(jo: JsonObject): String {
        val award = StringBuilder()
        val awardInfos = jo.optJSONArray("awardInfos")
        if (awardInfos != null) {
            for (i in 0 until awardInfos.length()) {
                val info = awardInfos.getJSONObject(i)
                if (award.isNotEmpty()) award.append(",")
                award.append(info.optString("awardName")).append("*").append(info.optInt("awardCount"))
            }
        }
        // 统一处理饲料奖励
        val foodCount = jo.optString("receiveFoodCount", "")
        if (foodCount.isNotEmpty()) {
            if (award.isNotEmpty()) award.append(";")
            award.append("饲料*").append(foodCount)
        }
        return award.toString()
    }

    /**
     * 处理飞行赛和揍小鸡的额外次数任务
     */
    private fun handleGameTasks(gameType: AntFarm.GameType): Boolean {
        // 仅飞行赛和揍小鸡有独立任务列表
        val listResponse = when (gameType) {
            AntFarm.GameType.flyGame -> FarmGameMallRpc.flyGameListFarmTask()
            AntFarm.GameType.hitGame -> FarmGameMallRpc.hitGameListFarmTask()
            else -> return false
        }

        if (listResponse.isNullOrEmpty()) return false
        val taskJo = listResponse.toJsonObject()
        val farmTaskList = taskJo.optJSONArray("farmTaskList") ?: return false

        for (i in 0 until farmTaskList.length()) {
            val task = farmTaskList.getJSONObject(i)
            val taskStatus = task.optString("taskStatus")
            val taskId = task.optString("taskId")
            val bizKey = task.optString("bizKey")

            if (TaskStatus.RECEIVED.name == taskStatus) continue

            if (TaskStatus.FINISHED.name == taskStatus) {
                FarmCoreRpc.receiveFarmTaskAward(taskId)
                return true
            }

            if (TaskStatus.TODO.name == taskStatus) {
                val outBizNo = "${bizKey}_${System.currentTimeMillis()}_${Integer.toHexString((Math.random() * 0xFFFFFF).toInt())}"
                FarmDrawRpc.finishTask(bizKey, "ANTFARM_GAME_TIMES_TASK", outBizNo)
                return true
            }
        }
        return false
    }

    // 庄园游戏
    suspend fun playAllGames() {
        recordFarmGame(AntFarm.GameType.flyGame)
        recordFarmGame(AntFarm.GameType.hitGame)
        recordFarmGame(AntFarm.GameType.starGame)
        recordFarmGame(AntFarm.GameType.jumpGame)
        Status.setFlagToday("farm::farmGameFinished")
        StructuredLog.farm("今日庄园游戏改分已完成")
    }
    // 抽抽乐执行
    fun playChouChouLe() {
        val ccl = ChouChouLe(
            allowDonationTasks = allowDonationTasks,
            allowAutoExchange = allowAutoExchange,
        )
        if (ccl.chouchoule()) {
            Status.setFlagToday("farm::chouChouLeFinished")
            StructuredLog.farm("今日抽抽乐已完成")
        } else {
            StructuredLog.record(TAG, "抽抽乐尚有未完成项（请检查是否需要验证）")
        }
    }
}
