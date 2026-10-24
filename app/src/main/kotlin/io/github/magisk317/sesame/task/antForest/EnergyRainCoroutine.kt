package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.hook.Toast
import io.github.magisk317.sesame.task.GameTask
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.model.ModelRegistry
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlin.random.Random

/**
 * 能量雨功能 - Kotlin协程版本
 *
 * 这是EnergyRain.java的协程版本重构，提供更好的性能和可维护性
 */
object EnergyRainCoroutine {
    private const val TAG = "EnergyRain"

    /**
     * 上次执行能量雨的时间戳
     */
    @Volatile
    private var lastExecuteTime: Long = 0

    /**
     * 随机延迟，增加随机性避免风控检测
     * @param min 最小延迟（毫秒）
     * @param max 最大延迟（毫秒）
     */
    private suspend fun randomDelay(min: Int, max: Int) {
        val delayTime = Random.nextInt(min, max + 1).toLong()
        delay(delayTime)
    }

    /**
     * 执行能量雨功能
     */
    suspend fun execEnergyRain() {
        try {
            // 执行频率检查：防止短时间内重复执行
            val currentTime = System.currentTimeMillis()
            val timeSinceLastExec = currentTime - lastExecuteTime
            val cooldownSeconds = 3 // 冷却时间：3秒

            if (timeSinceLastExec < cooldownSeconds * 1000) {
                // 粗放点，delay 3秒
                delay(cooldownSeconds * 1000.toLong())
            }

            energyRain()

            // 更新最后执行时间
            lastExecuteTime = System.currentTimeMillis()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消是正常现象，不记录为错误
            StructuredLog.record(TAG, "execEnergyRain 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (th: Throwable) {
            StructuredLog.printStackTrace(TAG, "执行能量雨出错:", th)
        }
    }

    /**
     * 能量雨主逻辑（协程版本）
     */
    private suspend fun energyRain() {
        try {
            var playedCount = 0
            val maxPlayLimit = 10

            do {
                val joEnergyRainHome = ForestEnergyRainRpc.queryHome().toJsonObject()
                randomDelay(250, 400) // 随机延迟 300-400ms
                if (!ResChecker.checkRes(TAG, joEnergyRainHome)) {
                    StructuredLog.record(TAG, "查询能量雨状态失败")
                    break
                }
                val canPlayToday = joEnergyRainHome.optBoolean("canPlayToday", false)
                val canPlayGame = joEnergyRainHome.optBoolean("canPlayGame", false)
                val canGrantStatus = joEnergyRainHome.optBoolean("canGrantStatus", false)

                // 1️⃣ 检查是否可以开始能量雨
                if (canPlayToday) {
                    startEnergyRain()
                    playedCount++
                    randomDelay(3000, 5000) // 随机延迟3-5秒
                    continue
                }

                // 2️⃣ 检查是否可以赠送能量雨
                if (canGrantStatus) {
                    StructuredLog.record(TAG, "有送能量雨的机会")
                    val joEnergyRainCanGrantList = ForestEnergyRainRpc.queryCanGrantList().toJsonObject()
                    val grantInfos = joEnergyRainCanGrantList.optJSONArray("grantInfos") ?: buildJsonArray { }
                    val giveEnergyRainSet = ModelRegistry.getModel(AntForest::class.java)
                        ?.giveEnergyRainList?.value.orEmpty()
                    var granted = false

                    for (j in 0 until grantInfos.length()) {
                        val grantInfo = grantInfos.getJSONObject(j)
                        if (grantInfo.optBoolean("canGrantedStatus", false)) {
                            val uid = grantInfo.getString("userId")
                            if (giveEnergyRainSet.contains(uid)) {
                                val rainJsonObj = ForestEnergyRainRpc.grantChance(uid).toJsonObject()
                                StructuredLog.record(TAG, "尝试送能量雨给【${UserMap.getMaskName(uid)}】")
                                if (ResChecker.checkRes(TAG, rainJsonObj)) {
                                    StructuredLog.forest(
                                        "赠送能量雨机会给🌧️[${UserMap.getMaskName(uid)}]#${
                                            UserMap.getMaskName(
                                                UserMap.currentUid
                                            )
                                        }"
                                    )
                                    randomDelay(300, 400) // 随机延迟 300-400ms
                                    granted = true
                                    break
                                } else {
                                    StructuredLog.error(TAG, "送能量雨失败 $rainJsonObj")
                                }
                            }
                        }
                    }
                    if (granted) {
                        continue
                    } else {
                        StructuredLog.record(TAG, "今日无可送能量雨好友或已达到赠送上限")
                    }
                }

                // 3️⃣ 检查是否可以能量雨游戏
                // canPlayGame 好像一直是true        注意：能量雨游戏只能执行一次，执行后会设置标记防止重复
                StructuredLog.record(TAG, "是否可以能量雨游戏: $canPlayGame")

                if (canPlayGame) {
                    // 防止能量雨游戏重复执行
                    val energyRainGameFlag = "EnergyRain::能量雨游戏任务"
                    if (Status.hasFlagToday(energyRainGameFlag)) {
                        break
                    }
                    val hasTaskToProcess = checkAndDoEndGameTask()//检查能量雨 游戏任务 并接取
                    val hasVerifiedListTask = ForestEnergyRainTask.process()
                    randomDelay(3000, 5000) // 随机延迟3-5秒
                    playedCount++
                    // 只有当有实际任务需要处理时才继续循环
                    if (hasTaskToProcess || hasVerifiedListTask) {
                        continue
                    } else {
                        // 没有任务需要处理，跳出循环
                        Status.setFlagToday(energyRainGameFlag)
                        break
                    }
                }

            /*
                // 3️⃣ 检查能量雨游戏任务
                val energyRainGameFlag = "EnergyRain::能量雨游戏任务"
                if (!Status.hasFlagToday(energyRainGameFlag)) {
                    StructuredLog.record(TAG, "检查能量雨游戏任务")
                    val hasTaskToProcess = checkAndDoEndGameTask()//检查能量雨 游戏任务
                    randomDelay(3000, 5000) // 随机延迟3-5秒
                    playedCount++
                    // 只有当有实际任务需要处理时才继续循环
                    if (hasTaskToProcess) {
                        continue
                    } else {
                        // 设置能量雨游戏已执行标志
                        Status.setFlagToday(energyRainGameFlag)
                        break
                    }
                } else {
                    // 今天已经执行过能量雨游戏任务，跳出循环
                    break
                }
            */

                break
            } while (playedCount < maxPlayLimit)
            if (playedCount >= maxPlayLimit) {
                StructuredLog.record(TAG, "能量雨执行达到单次任务上限($maxPlayLimit)，停止执行")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消是正常现象，不记录为错误
            StructuredLog.record(TAG, "energyRain 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (th: Throwable) {
            StructuredLog.record(TAG, "energyRain err:")
            StructuredLog.printStackTrace(TAG, th)
        }
    }

    /**
     * 开始能量雨（协程版本）
     */
    private suspend fun startEnergyRain() {
        try {
            val joStart = ForestEnergyRainRpc.start().toJsonObject()

            if (ResChecker.checkRes(TAG, joStart)) {
                val token = joStart.getString("token")
                val bubbleEnergyList = joStart.getJSONObject("difficultyInfo").getJSONArray("bubbleEnergyList")
                var sum = 0

                for (i in 0 until bubbleEnergyList.length()) {
                    sum += bubbleEnergyList.getString(i).toInt()
                }

                randomDelay(5000, 5200) // 随机延迟 5-5.2秒，模拟真人玩游戏
                val resultJson = ForestEnergyRainRpc.settle(sum, token).toJsonObject()

                if (ResChecker.checkRes(TAG, resultJson)) {
                    val s = "收获能量雨🌧️[${sum}g]"
                    Toast.show(s)
                    StructuredLog.forest(s)
                }
                randomDelay(300, 400) // 随机延迟 300-400ms
            } else {
                StructuredLog.record(TAG, "startEnergyRain: $joStart")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消是正常现象，不记录为错误
            StructuredLog.record(TAG, "startEnergyRain 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (th: Throwable) {
            StructuredLog.record(TAG, "startEnergyRain err:")
            StructuredLog.printStackTrace(TAG, th)
        }
    }

    /**
     * 检查并领取能量雨后的额外游戏任务
     * @return Boolean 是否还有待处理的任务
     */
    @JvmStatic
    private fun checkAndDoEndGameTask(): Boolean {
        try {
            // 1. 查询当前是否有可接或已接的游戏任务
            var jo = ForestEnergyRainRpc.queryEndGameList().toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) {
                //StructuredLog.error(TAG, "查询能量雨游戏任务失败 $jo")
                return false
            }

            // 2. 需要接入新任务时先初始化，再重新读取列表；初始化前的
            //    taskInfoList 可能仍是旧快照。
            if (jo.optBoolean("needInitTask", false)) {
                val initRes = ForestTaskRpc.initTask("GAME_DONE_SLJYD").toJsonObject()
                if (!ResChecker.checkRes(TAG, initRes)) {
                    return false
                }
                jo = ForestEnergyRainRpc.queryEndGameList().toJsonObject()
                if (!ResChecker.checkRes(TAG, jo)) return false
            }

            // 3. needInitTask=false 也可能返回已经接入的 TODO（现场抓包即为此
            //    形态），不能因为没有初始化标志而漏掉它。
            if (!hasPendingForestRescueTask(jo)) return false

            StructuredLog.record(TAG, "能量雨🌧️ 待完成附加任务[GAME_DONE_SLJYD]，执行上报")
            GameTask.Forest_sljyd.report(1)
            return true
        } catch (th: Throwable) {
            StructuredLog.printStackTrace(TAG, "执行能量雨后续任务出错:", th)
            return false
        }
    }

    /** Whether the response contains the only game task with a supported report flow. */
    internal fun hasPendingForestRescueTask(response: JsonObject): Boolean {
        val taskInfoList = response
            .optJSONObject("energyRainEndGameGroupTask")
            ?.optJSONArray("taskInfoList")
            ?: return false

        for (index in 0 until taskInfoList.length()) {
            val baseInfo = taskInfoList.optJSONObject(index)
                ?.optJSONObject("taskBaseInfo")
                ?: continue
            if (baseInfo.optString("taskType") == "GAME_DONE_SLJYD" &&
                baseInfo.optString("taskStatus") in setOf("TODO", "NOT_TRIGGER")
            ) {
                return true
            }
        }
        return false
    }

    /**
     * 兼容Java调用的包装方法
     */
    @JvmStatic
    fun execEnergyRainCompat() {
        kotlinx.coroutines.runBlocking {
            execEnergyRain()
        }
    }
}
