package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.StructuredLog.record
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Owns the legacy forest task query, completion, and reward claim workflow. */
class ForestTaskService(
    private val taskTryCount: ConcurrentHashMap<String, AtomicInteger>,
    private val waitForGame: suspend (String) -> Boolean,
    private val onError: (String, Throwable) -> Unit,
) {
    private val TAG = "ForestTaskService"
    private fun parseJsonObject(raw: String): JsonObject = raw.toJsonObject()

    /**
     * 执行当天森林签到任务
     *
     * @param forestSignVOList 森林签到列表
     * @return 获得的能量，如果签到失败或已签到则返回 0
     */
    private fun dailyTask(forestSignVOList: JsonArray): Int {
        try {
            val currentUid = UserMap.currentUid ?: return 0
            val forestSignVO = forestSignVOList.getJSONObject(0)
            val currentSignKey = forestSignVO.getString("currentSignKey") // 当前签到的 key
            val signId = forestSignVO.getString("signId") // 签到ID
            val sceneCode = forestSignVO.getString("sceneCode") // 场景代码
            val signRecords = forestSignVO.getJSONArray("signRecords") // 签到记录
            for (i in 0..<signRecords.length()) { //遍历签到记录
                val signRecord = signRecords.getJSONObject(i)
                val signKey = signRecord.getString("signKey")
                val awardCount = signRecord.optInt("awardCount", 0)
                if (signKey == currentSignKey && !signRecord.getBoolean("signed")) {
                    val joSign = parseJsonObject(
                        ForestTaskRpc.sign(
                            signId,
                            currentUid,
                            sceneCode
                        )
                    )
                    GlobalThreadPools.sleepCompat(300) // 等待300毫秒
                    if (ResChecker.checkRes(TAG + "森林签到失败:", joSign)) {
                        StructuredLog.forest("森林签到📆成功")
                        return awardCount
                    }
                    break
                }
            }
            return 0 // 如果没有签到，则返回 0
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
            return 0
        }
    }

    /**
     * 森林任务:
     * 逛目标应用会员,去森林寻宝抽1t能量
     * 防治荒漠化和干旱日,给随机好友一键浇水
     * 开通高德活动领,去吉祥林许个愿
     * 逛森林集市得能量,逛一逛618会场
     * 逛一逛点淘得红包,去一淘签到领红包
     */
    suspend fun run() {
        try {
            // 新版森林任务已迁移到 listTaskopengreen；保留下面的旧 queryTaskList
            // 路径用于旧版本支付宝仍返回 forestTasksNew 的情况。
            ForestVitalityTask.process()

            // 使用统一的任务黑名单管理器，包含默认黑名单和用户自定义黑名单
            while (true) {
                var doubleCheck = false // 标记是否需要再次检查任务
                val s = ForestTaskRpc.queryList() // 查询任务列表
                val jo = parseJsonObject(s) // 解析响应为 JSON 对象

                if (!ResChecker.checkRes(TAG + "查询森林任务失败:", jo)) {
                    StructuredLog.error(TAG, "森林任务🌿 ❌ 列表查询失败")
                    break
                }
                // 提取森林任务列表
                val forestSignVOList = jo.getJSONArray("forestSignVOList")
                var sumawardCount = 0
                val dailyawardCount = dailyTask(forestSignVOList) // 执行每日任务
                sumawardCount += dailyawardCount

                // 提取森林任务
                val forestTasksNew = jo.optJSONArray("forestTasksNew")
                if (forestTasksNew == null || forestTasksNew.length() == 0) {
                    break // 如果没有新任务，则返回
                }

                // 遍历任务
                for (i in 0..<forestTasksNew.length()) {
                    val forestTask = forestTasksNew.getJSONObject(i)
                    val taskInfoList = forestTask.getJSONArray("taskInfoList") // 获取任务信息列表

                    for (j in 0..<taskInfoList.length()) {
                        val taskInfo = taskInfoList.getJSONObject(j)
                        val taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo") // 获取任务基本信息
                        val taskType = taskBaseInfo.getString("taskType") // 获取任务类型
                        val sceneCode = taskBaseInfo.getString("sceneCode") // 获取场景代码
                        val taskStatus = taskBaseInfo.getString("taskStatus") // 获取任务状态

                        val bizInfo = parseJsonObject(taskBaseInfo.getString("bizInfo")) // 获取业务信息
                        val taskTitle = bizInfo.optString("taskTitle", taskType) // 获取任务标题

                        val taskRights = parseJsonObject(taskInfo.getString("taskRights")) // 获取任务权益
                        val awardCount = taskRights.optInt("awardCount", 0) // 获取奖励数量

                        // 判断任务状态
                        if (TaskStatus.FINISHED.name == taskStatus) {
                            // 领取任务奖励
                            val joAward = parseJsonObject(
                                ForestTaskRpc.receiveAward(
                                    sceneCode,
                                    taskType
                                )
                            ) // 领取奖励请求
                            if (ResChecker.checkRes(TAG + "领取森林任务奖励失败:", joAward)) {
                                StructuredLog.forest("森林奖励🎖️[" + taskTitle + "]# " + awardCount + "活力值")
                                sumawardCount += awardCount
                                doubleCheck = true // 标记需要重新检查任务
                            } else {
                                StructuredLog.error(TAG, "领取失败: $taskTitle") // 记录领取失败信息
                                StructuredLog.record(joAward.toString()) // 打印奖励响应
                            }
                            GlobalThreadPools.sleepCompat(500)
                        } else if (TaskStatus.TODO.name == taskStatus) {
                            // 跳过已在黑名单中的任务
                            if (TaskBlacklist.isTaskInBlacklist(taskType)) continue
                            // 执行待完成任务
                            val bizKey = sceneCode + "_" + taskType
                            val count = taskTryCount
                                .computeIfAbsent(bizKey) { _: String? ->
                                    AtomicInteger(0)
                                }
                                .incrementAndGet()
                            if (HuaLingShiTask.TASK_TYPE == taskType) {
                                val gameUrl = bizInfo.optString("taskJumpUrl")
                                val started = HuaLingShiTask.execute(gameUrl)
                                if (started && waitForGame(taskType)) {
                                    StructuredLog.record(TAG, "灵画师子任务已完成，等待森林任务状态刷新")
                                    doubleCheck = true
                                } else {
                                    StructuredLog.error(TAG, "灵画师子任务未完成，不调用通用 finishTask")
                                }
                            } else if (
                                sceneCode == ForestVitalityTask.SCENE_CODE &&
                                !ForestVitalityTask.isVerifiedFinishTask(taskType)
                            ) {
                                // The new listTaskopengreen path has an explicit
                                // whitelist. Do not let the legacy queryTaskList
                                // fallback send the same unsupported task again.
                                StructuredLog.record(
                                    TAG,
                                    "跳过未验证的森林活力任务: $taskTitle [$taskType]",
                                )
                            } else {
                                // 普通 RPC 任务才允许直接调用通用完成接口。
                                val joFinishTask = parseJsonObject(
                                    ForestTaskRpc.finish(sceneCode, taskType)
                                )
                                if (!ResChecker.checkRes(TAG + "完成森林任务失败:", joFinishTask)) {
                                    val errorCode = joFinishTask.optString("code", "")
                                    TaskBlacklist.autoAddToBlacklist(taskType, taskTitle, errorCode)
                                    if (count > 1) {
                                        TaskBlacklist.addToBlacklist(taskType, taskTitle)
                                    }
                                } else {
                                    StructuredLog.forest("森林任务🧾️[$taskTitle]")
                                    doubleCheck = true
                                }
                            }
                        }
                    }
                }
                if (!doubleCheck) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            onError("receiveTaskAward", t)
        }
    }
}
