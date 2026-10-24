package io.github.magisk317.sesame.task.antSports

import io.github.magisk317.sesame.util.StructuredLog

import io.github.magisk317.sesame.hook.SesameHooks

import android.annotation.SuppressLint
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.data.StatusFlags
import io.github.magisk317.sesame.entity.AlipayUser
import io.github.magisk317.sesame.hook.ApplicationHook
import io.github.magisk317.sesame.model.BaseModel
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.ChoiceModelField
import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.model.modelFieldExt.StringModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.task.TaskCommon
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

import kotlin.math.max
import kotlin.math.min
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

private fun parseSportsJson(raw: String): JsonObject = raw.toJsonObject()

private sealed interface NeverlandMapSwitchResult {
    data class Switched(val map: JsonObject) : NeverlandMapSwitchResult
    data object AllComplete : NeverlandMapSwitchResult
    data class Failed(val reason: String) : NeverlandMapSwitchResult
}

/**
 * @file AntSports.kt
 * @brief 支付宝蚂蚁运动主任务逻辑（Kotlin 重构版）。
 *
 * @details
 * 负责统一调度蚂蚁运动相关的所有自动化逻辑，包括：
 * - 步数同步与行走路线（旧版 & 新版路线）
 * - 运动任务面板任务、首页能量球任务
 * - 首页金币收集、慈善捐步
 * - 文体中心任务 / 行走路线
 * - 抢好友大战（训练好友 + 抢购好友）
 * - 健康岛（Neverland）任务、泡泡、走路建造
 *
 * 所有 RPC 调用均通过 {@link AntSportsRpcCall} 与 {@link AntSportsRpcCall.NeverlandRpcCall} 完成。
 */
@SuppressLint("DefaultLocale")
class AntSports : ModelTask() {

    companion object {
        /** @brief 日志 TAG */
        private val TAG: String = AntSports::class.java.simpleName

        /** @brief 运动任务完成日期缓存键 */
        private const val SPORTS_TASKS_COMPLETED_DATE = "SPORTS_TASKS_COMPLETED_DATE"

        /** @brief 训练好友 0 金币达上限日期缓存键 */
        private const val TRAIN_FRIEND_ZERO_COIN_DATE = "TRAIN_FRIEND_ZERO_COIN_DATE"
        init {
            ModelOrder.register(ModelDefinition(AntSports::class.java) { AntSports() })
        }
    }

    /** @brief 临时步数缓存（-1 表示未初始化） */
    private var tmpStepCount: Int = -1

    // 配置字段
    private lateinit var walk: BooleanModelField
    private lateinit var walkPathTheme: ChoiceModelField
    private var walkPathThemeId: String? = null
    private lateinit var walkCustomPath: BooleanModelField
    private lateinit var walkCustomPathId: StringModelField
    private lateinit var openTreasureBox: BooleanModelField
    private lateinit var receiveCoinAssetField: BooleanModelField
    private lateinit var donateCharityCoin: BooleanModelField
    private lateinit var donateCharityCoinType: ChoiceModelField
    private lateinit var donateCharityCoinAmount: IntegerModelField
    private lateinit var minExchangeCount: IntegerModelField
    private lateinit var latestExchangeTime: IntegerModelField
    private lateinit var syncStepCount: IntegerModelField
    private lateinit var tiyubiz: BooleanModelField
    private lateinit var battleForFriends: BooleanModelField
    private lateinit var battleForFriendType: ChoiceModelField
    private lateinit var originBossIdList: SelectModelField
    private lateinit var sportsTasksField: BooleanModelField
    private lateinit var sportsEnergyBubble: BooleanModelField
    private val sportsTaskService = AntSportsTaskService()

    // 训练好友相关配置
    private lateinit var trainFriend: BooleanModelField
    private lateinit var zeroCoinLimit: IntegerModelField

    /** @brief 记录训练好友连续获得 0 金币的次数 */
    private var zeroTrainCoinCount: Int = 0

    // 健康岛任务
    private lateinit var neverlandTask: BooleanModelField
    private lateinit var neverlandGrid: BooleanModelField
    private lateinit var neverlandGridStepCount: IntegerModelField


    /**
     * @brief 任务名称
     */
    override fun getName(): String = "运动"

    /**
     * @brief 所属任务分组
     */
    override fun getGroup(): ModelGroup = ModelGroup.SPORTS

    /**
     * @brief 图标文件名
     */
    override fun getIcon(): String = "AntSports.png"

    internal fun isSportsTasksEnabled(): Boolean =
        ::sportsTasksField.isInitialized && sportsTasksField.value

    internal fun isSportsEnergyBubbleEnabled(): Boolean =
        ::sportsEnergyBubble.isInitialized && sportsEnergyBubble.value

    internal fun isDonationEnabled(): Boolean =
        ::donateCharityCoin.isInitialized &&
            ::donateCharityCoinType.isInitialized &&
            ::donateCharityCoinAmount.isInitialized &&
            donateCharityCoin.value

    internal fun isTiyubizEnabled(): Boolean =
        ::tiyubiz.isInitialized && tiyubiz.value

    internal fun isBattleForFriendsEnabled(): Boolean =
        ::battleForFriends.isInitialized &&
            ::battleForFriendType.isInitialized &&
            ::originBossIdList.isInitialized &&
            ::zeroCoinLimit.isInitialized &&
            battleForFriends.value

    internal fun isTrainFriendEnabled(): Boolean =
        ::trainFriend.isInitialized && trainFriend.value

    internal fun isReceiveCoinAssetEnabled(): Boolean =
        ::receiveCoinAssetField.isInitialized && receiveCoinAssetField.value

    internal fun isNeverlandTaskEnabled(): Boolean =
        ::neverlandTask.isInitialized && neverlandTask.value

    internal fun isNeverlandGridEnabled(): Boolean =
        ::neverlandGrid.isInitialized &&
            ::neverlandGridStepCount.isInitialized &&
            neverlandGrid.value

    /**
     * @brief 定义本任务所需的所有配置字段
     */
    override fun getFields(): ModelFields {
        val modelFields = ModelFields()

        // 行走路线
        modelFields.addField(BooleanModelField("walk", "行走路线 | 开启", false).also { walk = it })
        modelFields.addField(
            ChoiceModelField(
                "walkPathTheme",
                "行走路线 | 主题",
                WalkPathTheme.DA_MEI_ZHONG_GUO,
                WalkPathTheme.nickNames
            ).also { walkPathTheme = it }
        )
        modelFields.addField(
            BooleanModelField("walkCustomPath", "行走路线 | 开启自定义路线", false).also { walkCustomPath = it }
        )
        modelFields.addField(
            StringModelField(
                "walkCustomPathId",
                "行走路线 | 自定义路线代码(debug)",
                "p0002023122214520001"
            ).also { walkCustomPathId = it }
        )

        // 旧版路线相关
        modelFields.addField(
            BooleanModelField("openTreasureBox", "开启宝箱", false).also { openTreasureBox = it }
        )
        /*
        // 运动任务 & 能量球
        modelFields.addField(
            BooleanModelField("sportsTasks", "开启运动任务", false).also { sportsTasksField = it }
        )
        modelFields.addField(
            BooleanModelField(
                "sportsEnergyBubble",
                "运动球任务(开启后有概率出现滑块验证)",
                false
            ).also { sportsEnergyBubble = it }
        )
        // 首页金币 & 捐步
        modelFields.addField(
            BooleanModelField("receiveCoinAsset", "收能量🎈", false).also { receiveCoinAssetField = it }
        )
        modelFields.addField(
            BooleanModelField("donateCharityCoin", "捐能量🎈 | 开启", false).also { donateCharityCoin = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "donateCharityCoinType",
                "捐能量🎈 | 方式",
                DonateCharityCoinType.ONE,
                DonateCharityCoinType.nickNames
            ).also { donateCharityCoinType = it }
        )
        modelFields.addField(
            IntegerModelField("donateCharityCoinAmount", "捐能量🎈 | 数量(每次)", 100)
                .also { donateCharityCoinAmount = it }
        )
        */
        // 健康岛任务
        modelFields.addField(
            BooleanModelField("neverlandTask", "健康岛 | 任务", true).also { neverlandTask = it }
        )
        modelFields.addField(
            BooleanModelField("neverlandGrid", "健康岛 | 自动走路建造", true).also { neverlandGrid = it }
        )
        modelFields.addField(
            IntegerModelField("neverlandGridStepCount", "健康岛 | 今日走路最大次数", 20)
                .also { neverlandGridStepCount = it }
        )
        /*
        // 抢好友相关
        modelFields.addField(
            BooleanModelField("battleForFriends", "抢好友 | 开启", false).also { battleForFriends = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "battleForFriendType",
                "抢好友 | 动作",
                BattleForFriendType.ROB,
                BattleForFriendType.nickNames
            ).also { battleForFriendType = it }
        )
        modelFields.addField(
            SelectModelField(
                "originBossIdList",
                "抢好友 | 好友列表",
                LinkedHashSet(),
                AlipayUser::getList
            ).also { originBossIdList = it }
        )

        // 训练好友相关
        modelFields.addField(
            BooleanModelField("trainFriend", "训练好友 | 开启", false).also { trainFriend = it }
        )
        modelFields.addField(
            IntegerModelField("zeroCoinLimit", "训练好友 | 0金币上限次数当天关闭", 5)
                .also { zeroCoinLimit = it }
        )

        // 文体中心 & 捐步 & 步数同步
        modelFields.addField(BooleanModelField("tiyubiz", "文体中心", false).also { tiyubiz = it })
         */

        modelFields.addField(
            IntegerModelField("minExchangeCount", "最小捐步步数", 0).also { minExchangeCount = it }
        )
        modelFields.addField(
            IntegerModelField("latestExchangeTime", "最晚捐步时间(24小时制)", 22)
                .also { latestExchangeTime = it }
        )
        modelFields.addField(
            IntegerModelField("syncStepCount", "自定义同步步数", 22000).also { syncStepCount = it }
        )

        // 本地字段：能量兑换双击卡
        val coinExchangeDoubleCard = BooleanModelField(
            "coinExchangeDoubleCard",
            "能量🎈兑换限时能量双击卡",
            false
        )
        modelFields.addField(coinExchangeDoubleCard)

        return modelFields
    }

    /**
     * @brief Xposed 启动时 hook 步数读取逻辑，实现自定义步数同步
     */
    override fun boot(classLoader: ClassLoader?) {
        try {
            SesameHooks.findAndHookMethod(
                "com.alibaba.health.pedometer.core.datasource.PedometerAgent",
                classLoader,
                "readDailyStep",
                object : MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val originStep = param.result as Int
                        val step = tmpStepCount()
                        // 早于 8 点或步数小于自定义步数时进行 hook
                        if (TaskCommon.IS_AFTER_8AM && originStep < step) {
                            param.result = step
                        }
                    }
                }
            )
            StructuredLog.record(TAG, "hook readDailyStep successfully")
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "hook readDailyStep err:", t)
        }
    }

    /**
     * @brief 任务主入口
     */
    override suspend fun runSuspend() {
        try {
            val neverlandTaskInitialized = ::neverlandTask.isInitialized
            val neverlandGridInitialized = ::neverlandGrid.isInitialized
            val neverlandStepLimitInitialized = ::neverlandGridStepCount.isInitialized
            val neverlandTaskEnabled = isNeverlandTaskEnabled()
            val neverlandGridEnabled = isNeverlandGridEnabled()
            val neverlandStepLimit = if (neverlandStepLimitInitialized) {
                neverlandGridStepCount.value.toString()
            } else {
                "uninitialized"
            }
            StructuredLog.record(
                TAG,
                "健康岛🏝️ 配置: task=$neverlandTaskEnabled(initialized=$neverlandTaskInitialized), " +
                    "grid=$neverlandGridEnabled(initialized=$neverlandGridInitialized), " +
                    "stepLimit=$neverlandStepLimit"
            )

            val loader = ApplicationHook.classLoader
            if (loader == null) {
                StructuredLog.error(TAG, "ClassLoader is null, 跳过运动任务")
                return
            }

            // 健康岛整体任务（任务大厅 + 泡泡 + 走路建造）
            if (neverlandTaskEnabled || neverlandGridEnabled) {
                NeverlandTaskHandler().runNeverland()
            } else {
                StructuredLog.record(TAG, "健康岛🏝️ 功能已关闭，本轮跳过")
            }

            // 步数同步
            if (!Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE) &&
                TimeUtil.isNowAfterOrCompareTimeStr("0600")) {
                syncStepTask()
            }

            // 运动任务
            if (!Status.hasFlagToday(StatusFlags.FLAG_ANTSPORTS_DAILY_TASKS_DONE) &&
                isSportsTasksEnabled()) {
                sportsTasks()
            }

            // 运动球任务
            if (isSportsEnergyBubbleEnabled()) {
                sportsEnergyBubbleTask()
            }

            // 新版行走路线
            if (walk.value) {
                getWalkPathThemeIdOnConfig()
                walk()
            }

            // 旧版路线：只开宝箱
            if (openTreasureBox.value && !walk.value) {
                queryMyHomePage(loader)
            }

            // 捐能量
            if (isDonationEnabled() && Status.canDonateCharityCoin()) {
                queryProjectList(loader)
            }

            // 捐步
            val currentUid = UserMap.currentUid
            if (minExchangeCount.value > 0 &&
                currentUid != null &&
                Status.canExchangeToday(currentUid)) {
                queryWalkStep(loader)
            }

            // 文体中心
            if (isTiyubizEnabled()) {
                userTaskGroupQuery("SPORTS_DAILY_SIGN_GROUP")
                userTaskGroupQuery("SPORTS_DAILY_GROUP")
                userTaskRightsReceive()
                pathFeatureQuery()
                participate()
            }

            // 抢好友大战
            if (isBattleForFriendsEnabled()) {
                queryClubHome()
                queryTrainItem()
                buyMember()
            }

            // 首页金币
            if (isReceiveCoinAssetEnabled()) {
                receiveCoinAsset()
            }

        } catch (e: CancellationException) {
            StructuredLog.record(TAG, "运动任务被取消")
            throw e
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            StructuredLog.record(TAG, "runJava error: ${e.javaClass.simpleName}$message")
            StructuredLog.printStackTrace(TAG, e)
            throw e
        }
    }

    /**
     * 步数同步任务
     */
    private fun syncStepTask() {
        addChildTask(
            ChildModelTask(
                "syncStep",
                Runnable {
                    val step = tmpStepCount()
                    try {
                        val loader = ApplicationHook.classLoader
                        if (loader == null) {
                            StructuredLog.error(TAG, "ClassLoader is null, 跳过同步步数")
                            return@Runnable
                        }

                        val rpcManager = HookHelpers.callStaticMethod(
                            loader.loadClass("com.alibaba.health.pedometer.intergation.rpc.RpcManager"),
                            "a"
                        )

                        val success = HookHelpers.callMethod(
                            rpcManager,
                            "a",
                            step,
                            java.lang.Boolean.FALSE,
                            "system"
                        ) as Boolean

                        if (success) {
                            StructuredLog.other("同步步数🏃🏻‍♂️[$step 步]")
                            Status.setFlagToday(StatusFlags.FLAG_ANTSPORTS_SYNC_STEP_DONE)
                        } else {
                            StructuredLog.error(TAG, "同步运动步数失败:$step")
                        }
                    } catch (t: Throwable) {
                        // RpcManager.a 的签名随支付宝版本变化；未知签名时安全跳过，
                        // 不把兼容性问题当成运动任务运行时错误刷屏。
                        StructuredLog.record(
                            TAG,
                            "当前支付宝运动步数 RPC 不兼容，跳过同步: ${t.javaClass.simpleName}",
                        )
                    }
                }
            )
        )
    }

    /**
     * @brief 计算今日用于同步的随机步数
     *
     * @return 步数值（最大 100000）
     */
    fun tmpStepCount(): Int {
        if (tmpStepCount >= 0) {
            return tmpStepCount
        }
        tmpStepCount = syncStepCount.value
        if (tmpStepCount > 0) {
            tmpStepCount = RandomUtil.nextInt(tmpStepCount, tmpStepCount + 2000)
            if (tmpStepCount > 100_000) {
                tmpStepCount = 100_000
            }
        }
        return tmpStepCount
    }

    // ---------------------------------------------------------------------
    // 运动任务面板
    // ---------------------------------------------------------------------

    /**
     * @brief 处理运动任务面板中的任务（含签到、完成、领奖）
     */
    private suspend fun sportsTasks() = sportsTaskService.runTasks()

    private suspend fun sportsEnergyBubbleTask() = sportsTaskService.runEnergyBubbleTask()

    private suspend fun sportsCheckIn() = sportsTaskService.checkIn()

    private suspend fun receiveCoinAsset() = sportsTaskService.receiveCoinAsset()
    // 新版行走路线（SportsPlay）
    // ---------------------------------------------------------------------

    /**
     * @brief 新版行走路线主流程 主入口
     */
    private fun walk() {
        try {
            val user = parseSportsJson(AntSportsRpcCall.queryUser())
            if (!ResChecker.checkRes(TAG, user)) {
                StructuredLog.error(TAG, "查询用户失败: $user")
                return
            }

            val data = user.optJSONObject("data")
            val joinedPathId = data?.optString("joinedPathId") ?: ""
            if(joinedPathId.isEmpty()) {

                StructuredLog.error(TAG, "未找到有效线路: $user")
            }
            val path = queryPath(joinedPathId)

            if (path == null) {
                StructuredLog.error(TAG, "无法获取路线详情(PathId: $joinedPathId)")
                return
            }
            val userPathStep = path.getJSONObject("userPathStep")

            //如果是 JOIN 则还没走完
            if ("COMPLETED" == userPathStep.getString("pathCompleteStatus")) {
                StructuredLog.record(TAG, "行走路线🚶🏻‍♂️路线[${userPathStep.getString("pathName")}]已完成")
                // 获取新路线 ID
                val newPathId = queryJoinPath(walkPathThemeId)    //walkPathThemeId 在进入walk()之前已经获取了
                if (!newPathId.isNullOrEmpty()) {
                    StructuredLog.record(TAG, "发现新路线，准备加入: $newPathId")
                    joinPath(newPathId)
                } else {
                    StructuredLog.record(TAG, "未发现可加入的新路线，可能当前地图已全部走完")
                }
                return
            }

            val pathObj = path.getJSONObject("path")
            val minGoStepCount = pathObj.getInt("minGoStepCount")
            val pathStepCount = pathObj.getInt("pathStepCount")
            val forwardStepCount = userPathStep.getInt("forwardStepCount")
            val remainStepCount = userPathStep.getInt("remainStepCount")
            val needStepCount = pathStepCount - forwardStepCount

            if (remainStepCount >= minGoStepCount) {
                val useStepCount = min(remainStepCount, needStepCount)
                walkGo(userPathStep.getString("pathId"), useStepCount, userPathStep.getString("pathName"))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "walk err:", t)
        }
    }

    /**
     * @brief 新版路线行走一步
     */
    private fun walkGo(pathId: String, useStepCount: Int, pathName: String) {
        try {
            val date = Date()
            @SuppressLint("SimpleDateFormat") val sdf = SimpleDateFormat("yyyy-MM-dd")
            val jo = parseSportsJson(AntSportsRpcCall.walkGo(sdf.format(date), pathId, useStepCount))
            if (ResChecker.checkRes(TAG, jo)) {
                StructuredLog.other(TAG, "行走路线🚶🏻‍♂️路线[$pathName]#前进了${useStepCount}步")
                queryPath(pathId)
            } else {
                StructuredLog.error(TAG, "walkGo失败： [pathId: $pathId]: $jo")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "walkGo err:", t)
        }
    }

    /**
     * @brief 查询世界地图
     */
    private fun queryWorldMap(themeId: String?): JsonObject? {
        var theme: JsonObject? = null
        if (themeId.isNullOrEmpty()) return null
        try {
            val jo = parseSportsJson(AntSportsRpcCall.queryWorldMap(themeId))
            if (ResChecker.checkRes(TAG + "queryWorldMap失败： [ThemeID: $themeId]: ", jo)) {
                theme = jo.getJSONObject("data")
            } else {
                StructuredLog.error(TAG, "queryWorldMap失败： [ThemeID: $themeId]: $jo")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryWorldMap err:", t)
        }
        return theme
    }

    /**
     * @brief 查询指定城市的路线详情
     * @param cityId 城市 ID
     */
    private fun queryCityPath(cityId: String): JsonObject? {
        var city: JsonObject? = null
        try {
            val jo = parseSportsJson(AntSportsRpcCall.queryCityPath(cityId))
            if (ResChecker.checkRes(TAG, jo)) {
                city = jo.getJSONObject("data")
            } else {
                StructuredLog.error(TAG, "queryCityPath失败： [CityID: $cityId]$jo")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryCityPath err:", t)
        }
        return city
    }

    /**
     * @brief 查询路线详情（同时触发宝箱领取）
     */
    /*
    private fun queryPath(pathId: String): JsonObject? {
        var path: JsonObject? = null
        try {
            val date = Date()
            @SuppressLint("SimpleDateFormat") val sdf = SimpleDateFormat("yyyy-MM-dd")
            val jo = parseSportsJson(AntSportsRpcCall.queryPath(sdf.format(date), pathId))
            if (ResChecker.checkRes(TAG, jo)) {
                path = jo.getJSONObject("data")
                val ja = jo.getJSONObject("data").getJSONArray("treasureBoxList")
                for (i in 0 until ja.length()) {
                    val treasureBox = ja.getJSONObject(i)
                    receiveEvent(treasureBox.getString("boxNo"))
                }
            } else {
                StructuredLog.error(TAG, "queryPath失败： $jo")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryPath err:", t)
        }
        return path
    }*/


    //这里会返回路线详情
    private fun queryPath(pathId: String): JsonObject? {
        try {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val response = AntSportsRpcCall.queryPath(dateStr, pathId)
            val jo = parseSportsJson(response)

            if (!ResChecker.checkRes(TAG, jo)) {
                StructuredLog.error(TAG, "queryPath 请求失败: $response")
                return null
            }

            // 2. 检查数据节点是否存在
            val data = jo.optJSONObject("data")
            if (data == null) {
                StructuredLog.error(TAG, "queryPath 响应成功但 data 节点为空: $response")
                return null
            }

            // --- 逻辑处理 ---
            val userPath = data.optJSONObject("userPathStep")
            StructuredLog.record(TAG, "路线: ${userPath?.optString("pathName")}, 进度: ${userPath?.optInt("pathProgress")}%")

            val boxList = data.optJSONArray("treasureBoxList")
            if (boxList != null && boxList.length() > 0) {
                for (i in 0 until boxList.length()) {
                    val boxNo = boxList.optJSONObject(i)?.optString("boxNo")
                    if (!boxNo.isNullOrEmpty()) receiveEvent(boxNo)
                }
            }

            return data
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryPath 过程中发生崩溃", t)
        }
        return null
    }

    /**
     * @brief 新版路线开启宝箱并打印奖励
     */
    private fun receiveEvent(eventBillNo: String) {
        try {
            val jo = parseSportsJson(AntSportsRpcCall.receiveEvent(eventBillNo))
            if (!ResChecker.checkRes(TAG, jo)) return

            val ja = jo.getJSONObject("data").getJSONArray("rewards")
            for (i in 0 until ja.length()) {
                val reward = ja.getJSONObject(i)
                StructuredLog.record(
                    TAG,
                    "行走路线🎁开启宝箱[${reward.getString("rewardName")}]*${reward.getInt("count")}"
                )
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "receiveEvent err:", t)
        }
    }

    /**
     * @brief 根据主题 ID 挑选可加入的 pathId
     */
    private fun queryJoinPath(themeId: String?): String? {
        if (walkCustomPath.value) {
            return walkCustomPathId.value
        }
        var pathId: String? = null
        try {
            val theme = queryWorldMap(walkPathThemeId)
            if (theme == null) {
                StructuredLog.error(TAG, "queryJoinPath-> theme 失败：$theme")
                return null
            }
            val cityList = theme.getJSONArray("cityList")
            for (i in 0 until cityList.length()) {
                val cityId = cityList.optJSONObject(i)?.optString("cityId")?.trim().orEmpty()
                if (cityId.isEmpty() || cityId.all { it == '0' }) {
                    StructuredLog.record(TAG, "跳过无效城市ID: [$cityId]")
                    continue
                }
                val city = queryCityPath(cityId) ?: continue
                val cityPathList = city.getJSONArray("cityPathList")
                for (j in 0 until cityPathList.length()) {
                    val cityPath = cityPathList.getJSONObject(j)
                    pathId = cityPath.getString("pathId")
                    if ("COMPLETED" != cityPath.getString("pathCompleteStatus")) {
                        return pathId
                    }
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryJoinPath err:", t)
        }
        return pathId
    }

    /**
     * @brief 加入新版路线
     */
    private fun joinPath(pathId: String?) {
        var realPathId = pathId
        if (realPathId == null) {
            // 默认龙年祈福线
            realPathId = "p0002023122214520001"
        }
        try {
            val jo = parseSportsJson(AntSportsRpcCall.joinPath(realPathId))
            if (ResChecker.checkRes(TAG, jo)) {
                val path = queryPath(realPathId)
                StructuredLog.record(TAG, "行走路线🚶🏻‍♂️路线[${path?.getJSONObject("path")?.getString("name")}]已加入")
            } else {
                StructuredLog.error(TAG, "行走路线🚶🏻‍♂️路线[$realPathId]有误，无法加入！")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "joinPath err:", t)
        }
    }

    /**
     * @brief 根据配置索引同步更新路线主题 ID
     */
    private fun getWalkPathThemeIdOnConfig() {
        val index = walkPathTheme.value
        if (index >= 0 && index < WalkPathTheme.themeIds.size) {
            walkPathThemeId = WalkPathTheme.themeIds[index]
        } else {
            StructuredLog.error(TAG, "非法的路线主题索引: $index，已回退至默认主题")
            walkPathThemeId = WalkPathTheme.themeIds[WalkPathTheme.DA_MEI_ZHONG_GUO]
        }
    }

    // ---------------------------------------------------------------------
    // 旧版行走路线（保留兼容）
    // ---------------------------------------------------------------------

    /**
     * @brief 旧版行走路线首页逻辑（开宝箱 + 行走 + 加入路线）
     */
    private suspend fun queryMyHomePage(loader: ClassLoader) {
        try {
            var s = AntSportsRpcCall.queryMyHomePage()
            var jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val pathJoinStatus = jo.getString("pathJoinStatus")
                if ("GOING" == pathJoinStatus) {
                    if (jo.has("pathCompleteStatus")) {
                        if ("COMPLETED" == jo.getString("pathCompleteStatus")) {
                            jo = parseSportsJson(AntSportsRpcCall.queryBaseList())
                            if (ResChecker.checkRes(TAG, jo)) {
                                val allPathBaseInfoList = jo.getJSONArray("allPathBaseInfoList")
                                val otherAllPathBaseInfoList = jo.getJSONArray("otherAllPathBaseInfoList")
                                    .getJSONObject(0)
                                    .getJSONArray("allPathBaseInfoList")
                                join(loader, allPathBaseInfoList, otherAllPathBaseInfoList, "")
                            } else {
                                StructuredLog.record(TAG, jo.getString("resultDesc"))
                            }
                        }
                    } else {
                        val rankCacheKey = jo.getString("rankCacheKey")
                        val ja = jo.getJSONArray("treasureBoxModelList")
                        for (i in 0 until ja.length()) {
                            parseTreasureBoxModel(loader, ja.getJSONObject(i), rankCacheKey)
                        }
                        val joPathRender = jo.getJSONObject("pathRenderModel")
                        val title = joPathRender.getString("title")
                        val minGoStepCount = joPathRender.getInt("minGoStepCount")
                        jo = jo.getJSONObject("dailyStepModel")
                        val consumeQuantity = jo.getInt("consumeQuantity")
                        val produceQuantity = jo.getInt("produceQuantity")
                        val day = jo.getString("day")
                        val canMoveStepCount = produceQuantity - consumeQuantity
                        if (canMoveStepCount >= minGoStepCount) {
                            go(loader, day, rankCacheKey, canMoveStepCount, title)
                        }
                    }
                } else if ("NOT_JOIN" == pathJoinStatus) {
                    val firstJoinPathTitle = jo.getString("firstJoinPathTitle")
                    val allPathBaseInfoList = jo.getJSONArray("allPathBaseInfoList")
                    val otherAllPathBaseInfoList = jo.getJSONArray("otherAllPathBaseInfoList")
                        .getJSONObject(0)
                        .getJSONArray("allPathBaseInfoList")
                    join(loader, allPathBaseInfoList, otherAllPathBaseInfoList, firstJoinPathTitle)
                }
            } else {
                StructuredLog.record(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryMyHomePage err:", t)
        }
    }

    /**
     * @brief 旧版路线加入逻辑（根据可解锁路径列表）
     */
    private suspend fun join(
        loader: ClassLoader,
        allPathBaseInfoList: JsonArray,
        otherAllPathBaseInfoList: JsonArray,
        firstJoinPathTitle: String
    ) {
        try {
            var index = -1
            var title: String? = null
            var pathId: String? = null
            var jo: JsonObject

            for (i in allPathBaseInfoList.length() - 1 downTo 0) {
                jo = allPathBaseInfoList.getJSONObject(i)
                if (jo.getBoolean("unlocked")) {
                    title = jo.getString("title")
                    pathId = jo.getString("pathId")
                    index = i
                    break
                }
            }
            if (index < 0 || index == allPathBaseInfoList.length() - 1) {
                for (j in otherAllPathBaseInfoList.length() - 1 downTo 0) {
                    jo = otherAllPathBaseInfoList.getJSONObject(j)
                    if (jo.getBoolean("unlocked")) {
                        if (j != otherAllPathBaseInfoList.length() - 1 || index != allPathBaseInfoList.length() - 1) {
                            title = jo.getString("title")
                            pathId = jo.getString("pathId")
                            index = j
                        }
                        break
                    }
                }
            }
            if (index >= 0) {
                val s = if (title == firstJoinPathTitle) {
                    AntSportsRpcCall.openAndJoinFirst()
                } else {
                    AntSportsRpcCall.join(pathId ?: "")
                }
                jo = parseSportsJson(s)
                if (ResChecker.checkRes(TAG, jo)) {
                    StructuredLog.other("加入线路🚶🏻‍♂️[$title]")
                    queryMyHomePage(loader)
                } else {
                    StructuredLog.record(TAG, jo.getString("resultDesc"))
                }
            } else {
                StructuredLog.record(TAG, "好像没有可走的线路了！")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "join err:", t)
        }
    }

    /**
     * @brief 旧版路线行走逻辑
     */
    private suspend fun go(loader: ClassLoader, day: String, rankCacheKey: String, stepCount: Int, title: String) {
        try {
            val s = AntSportsRpcCall.go(day, rankCacheKey, stepCount)
            val jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                StructuredLog.other("行走线路🚶🏻‍♂️[$title]#前进了${jo.getInt("goStepCount")}步")
                val completed = "COMPLETED" == jo.getString("completeStatus")
                val ja = jo.getJSONArray("allTreasureBoxModelList")
                for (i in 0 until ja.length()) {
                    parseTreasureBoxModel(loader, ja.getJSONObject(i), rankCacheKey)
                }
                if (completed) {
                    StructuredLog.other("完成线路🚶🏻‍♂️[$title]")
                    queryMyHomePage(loader)
                }
            } else {
                StructuredLog.record(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "go err:", t)
        }
    }

    /**
     * @brief 解析旧版宝箱模型并按时间安排子任务开箱
     */
    private suspend fun parseTreasureBoxModel(loader: ClassLoader, jo: JsonObject, rankCacheKey: String) {
        try {
            val canOpenTime = jo.getString("canOpenTime")
            val issueTime = jo.getString("issueTime")
            val boxNo = jo.getString("boxNo")
            val userId = jo.getString("userId")
            if (canOpenTime == issueTime) {
                openTreasureBox(boxNo, userId)
            } else {
                val cot = canOpenTime.toLong()
                val now = rankCacheKey.toLong()
                val delay = cot - now
                if (delay <= 0) {
                    openTreasureBox(boxNo, userId)
                    return
                }
                if (delay < BaseModel.checkInterval.value) {
                    val taskId = "BX|$boxNo"
                    if (hasChildTask(taskId)) return
                    StructuredLog.forest("运动🎁 还有${TimeFormatter.formatDuration(delay)}开宝箱")
                    addChildTask(
                        ChildModelTask(
                            taskId,
                            "BX",
                            {
                                val startTime = System.currentTimeMillis()
                                while (System.currentTimeMillis() - startTime < 5_000) {
                                    if (openTreasureBox(boxNo, userId) > 0) {
                                        break
                                    }
                                    delay(200)
                                }
                            },
                            System.currentTimeMillis() + delay
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "parseTreasureBoxModel err:", t)
        }
    }

    /**
     * @brief 旧版宝箱开启
     * @return 获得的奖励数量
     */
    private fun openTreasureBox(boxNo: String, userId: String): Int {
        try {
            val s = AntSportsRpcCall.openTreasureBox(boxNo, userId)
            var jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val ja = jo.getJSONArray("treasureBoxAwards")
                var num = 0
                for (i in 0 until ja.length()) {
                    jo = ja.getJSONObject(i)
                    num += jo.getInt("num")
                    StructuredLog.other("运动宝箱🎁[$num${jo.getString("name")}]")
                }
                return num
            } else if ("TREASUREBOX_NOT_EXIST" == jo.getString("resultCode")) {
                StructuredLog.record(TAG, "运动宝箱🎁 已领取或不可用")
                return 1
            } else {
                StructuredLog.error(TAG, "运动宝箱🎁 ❌ 开启失败")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "openTreasureBox err:", t)
        }
        return 0
    }

    // ---------------------------------------------------------------------
    // 旧版捐步 & 慈善
    // ---------------------------------------------------------------------

    /**
     * @brief 查询慈善项目列表并执行捐赠
     */
    private fun queryProjectList(loader: ClassLoader) {
        try {
            var jo = parseSportsJson(AntSportsRpcCall.queryProjectList(0))
            if (ResChecker.checkRes(TAG, jo)) {
                var charityCoinCount = jo.getInt("charityCoinCount")
                if (charityCoinCount < donateCharityCoinAmount.value) return

                val ja = jo.getJSONObject("projectPage").getJSONArray("data")
                for (i in 0 until ja.length()) {
                    if (charityCoinCount < donateCharityCoinAmount.value) break
                    val basicModel = ja.getJSONObject(i).getJSONObject("basicModel")
                    if ("DONATE_COMPLETED" == basicModel.getString("footballFieldStatus")) break
                    donate(
                        loader,
                        donateCharityCoinAmount.value,
                        basicModel.getString("projectId"),
                        basicModel.getString("title")
                    )
                    Status.donateCharityCoin()
                    charityCoinCount -= donateCharityCoinAmount.value
                    if (donateCharityCoinType.value == DonateCharityCoinType.ONE) break
                }
            } else {
                StructuredLog.error(TAG, "运动公益💚 ❌ 项目查询失败")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryProjectList err:", t)
        }
    }

    /**
     * @brief 执行一次慈善捐赠
     */
    private fun donate(loader: ClassLoader, donateCharityCoin: Int, projectId: String, title: String) {
        try {
            val s = AntSportsRpcCall.donate(donateCharityCoin, projectId)
            val jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                StructuredLog.other("捐赠活动❤️[$title][$donateCharityCoin 能量🎈]")
            } else {
                StructuredLog.record(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "donate err:", t)
        }
    }

    /**
     * @brief 查询行走步数，并根据条件自动捐步
     */
    private fun queryWalkStep(loader: ClassLoader) {
        try {
            var s = AntSportsRpcCall.queryWalkStep()
            var jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                jo = jo.getJSONObject("dailyStepModel")
                val produceQuantity = jo.getInt("produceQuantity")
                val hour = TimeUtil.getFormatTime().split(":").first().toInt()

                if (produceQuantity >= minExchangeCount.value || hour >= latestExchangeTime.value) {
                    AntSportsRpcCall.walkDonateSignInfo(produceQuantity)
                    s = AntSportsRpcCall.donateWalkHome(produceQuantity)
                    jo = parseSportsJson(s)
                    if (!jo.getBoolean("isSuccess")) return
                    val walkDonateHomeModel = jo.getJSONObject("walkDonateHomeModel")
                    val walkUserInfoModel = walkDonateHomeModel.getJSONObject("walkUserInfoModel")
                    if (!walkUserInfoModel.has("exchangeFlag")) {
                        Status.exchangeToday(UserMap.currentUid ?: return)
                        return
                    }
                    val donateToken = walkDonateHomeModel.getString("donateToken")
                    val walkCharityActivityModel = walkDonateHomeModel.getJSONObject("walkCharityActivityModel")
                    val activityId = walkCharityActivityModel.getString("activityId")
                    s = AntSportsRpcCall.exchange(activityId, produceQuantity, donateToken)
                    jo = parseSportsJson(s)
                    if (jo.getBoolean("isSuccess")) {
                        val donateExchangeResultModel = jo.getJSONObject("donateExchangeResultModel")
                        val userCount = donateExchangeResultModel.getInt("userCount")
                        val amount = donateExchangeResultModel.getJSONObject("userAmount").getDouble("amount")
                        StructuredLog.other("捐出活动❤️[$userCount 步]#兑换$amount 元公益金")
                        Status.exchangeToday(UserMap.currentUid ?: return)
                    } else if (s.contains("已捐步")) {
                        Status.exchangeToday(UserMap.currentUid ?: return)
                    } else {
                        StructuredLog.record(TAG, jo.getString("resultDesc"))
                    }
                }
            } else {
                StructuredLog.record(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryWalkStep err:", t)
        }
    }

    // ---------------------------------------------------------------------
    // 文体中心
    // ---------------------------------------------------------------------

    /**
     * @brief 文体中心任务组查询并自动完成 TODO 状态任务
     */
    private fun userTaskGroupQuery(groupId: String) {
        try {
            val s = AntSportsRpcCall.userTaskGroupQuery(groupId)
            var jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                jo = jo.getJSONObject("group")
                val userTaskList = jo.getJSONArray("userTaskList")
                for (i in 0 until userTaskList.length()) {
                    jo = userTaskList.getJSONObject(i)
                    if ("TODO" != jo.getString("status")) continue
                    val taskInfo = jo.getJSONObject("taskInfo")
                    val bizType = taskInfo.getString("bizType")
                    val taskId = taskInfo.getString("taskId")
                    val res = parseSportsJson(AntSportsRpcCall.userTaskComplete(bizType, taskId))
                    if (ResChecker.checkRes(TAG, res)) {
                        val taskName = taskInfo.optString("taskName", taskId)
                        StructuredLog.other("完成任务🧾[$taskName]")
                    } else {
                        StructuredLog.record(TAG, "文体每日任务 $res")
                    }
                }
            } else {
                StructuredLog.record(TAG, "文体每日任务 $s")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "userTaskGroupQuery err:", t)
        }
    }

    /**
     * @brief 文体中心走路挑战报名
     */
    private fun participate() {
        try {
            val s = AntSportsRpcCall.queryAccount()
            var jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val balance = jo.getDouble("balance")
                if (balance < 100) return

                jo = parseSportsJson(AntSportsRpcCall.queryRoundList())
                if (ResChecker.checkRes(TAG, jo)) {
                    val dataList = jo.getJSONArray("dataList")
                    for (i in 0 until dataList.length()) {
                        jo = dataList.getJSONObject(i)
                        if ("P" != jo.getString("status")) continue
                        if (jo.has("userRecord")) continue
                        val instanceList = jo.getJSONArray("instanceList")
                        var pointOptions = 0
                        val roundId = jo.getString("id")
                        var instanceId: String? = null
                        var resultId: String? = null

                        for (j in instanceList.length() - 1 downTo 0) {
                            val inst = instanceList.getJSONObject(j)
                            if (inst.getInt("pointOptions") < pointOptions) continue
                            pointOptions = inst.getInt("pointOptions")
                            instanceId = inst.getString("id")
                            resultId = inst.getString("instanceResultId")
                        }
                        val res = parseSportsJson(
                            AntSportsRpcCall.participate(
                                pointOptions,
                                instanceId ?: continue,
                                resultId ?: continue,
                                roundId
                            )
                        )
                        if (ResChecker.checkRes(TAG, res)) {
                            val data = res.getJSONObject("data")
                            val roundDescription = data.getString("roundDescription")
                            val targetStepCount = data.getInt("targetStepCount")
                            StructuredLog.other("走路挑战🚶🏻‍♂️[$roundDescription]#$targetStepCount")
                        } else {
                            StructuredLog.record(TAG, "走路挑战赛 $res")
                        }
                    }
                } else {
                    StructuredLog.record(TAG, "queryRoundList $jo")
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "participate err:", t)
        }
    }

    /**
     * @brief 文体中心奖励领取
     */
    private fun userTaskRightsReceive() {
        try {
            val s = AntSportsRpcCall.userTaskGroupQuery("SPORTS_DAILY_GROUP")
            var jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                jo = jo.getJSONObject("group")
                val userTaskList = jo.getJSONArray("userTaskList")
                for (i in 0 until userTaskList.length()) {
                    jo = userTaskList.getJSONObject(i)
                    if ("COMPLETED" != jo.getString("status")) continue
                    val userTaskId = jo.getString("userTaskId")
                    val taskInfo = jo.getJSONObject("taskInfo")
                    val taskId = taskInfo.getString("taskId")
                    val res = parseSportsJson(AntSportsRpcCall.userTaskRightsReceive(taskId, userTaskId))
                    if (ResChecker.checkRes(TAG, res)) {
                        val taskName = taskInfo.optString("taskName", taskId)
                        val rightsRuleList = taskInfo.getJSONArray("rightsRuleList")
                        val award = StringBuilder()
                        for (j in 0 until rightsRuleList.length()) {
                            val r = rightsRuleList.getJSONObject(j)
                            award.append(r.getString("rightsName"))
                                .append("*")
                                .append(r.getInt("baseAwardCount"))
                        }
                        StructuredLog.other("领取奖励🎖️[$taskName]#$award")
                    } else {
                        StructuredLog.record(TAG, "文体中心领取奖励")
                        StructuredLog.record(res.toString())
                    }
                }
            } else {
                StructuredLog.record(TAG, "文体中心领取奖励")
                StructuredLog.record(s)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "userTaskRightsReceive err:", t)
        }
    }

    /**
     * @brief 文体中心路径特性查询 + 行走任务/加入路径
     */
    private fun pathFeatureQuery() {
        try {
            val s = AntSportsRpcCall.pathFeatureQuery()
            var jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                val path = jo.getJSONObject("path")
                val pathId = path.getString("pathId")
                val title = path.getString("title")
                val minGoStepCount = path.getInt("minGoStepCount")
                if (jo.has("userPath")) {
                    val userPath = jo.getJSONObject("userPath")
                    val userPathRecordStatus = userPath.getString("userPathRecordStatus")
                    if ("COMPLETED" == userPathRecordStatus) {
                        pathMapHomepage(pathId)
                        pathMapJoin(title, pathId)
                    } else if ("GOING" == userPathRecordStatus) {
                        pathMapHomepage(pathId)
                        val countDate = TimeUtil.getFormatDate()
                        jo = parseSportsJson(AntSportsRpcCall.stepQuery(countDate, pathId))
                        if (ResChecker.checkRes(TAG, jo)) {
                            val canGoStepCount = jo.getInt("canGoStepCount")
                            if (canGoStepCount >= minGoStepCount) {
                                val userPathRecordId = userPath.getString("userPathRecordId")
                                tiyubizGo(countDate, title, canGoStepCount, pathId, userPathRecordId)
                            }
                        }
                    }
                } else {
                    pathMapJoin(title, pathId)
                }
            } else {
                StructuredLog.record(TAG, jo.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "pathFeatureQuery err:", t)
        }
    }

    /**
     * @brief 文体中心地图首页 & 奖励领取
     */
    private fun pathMapHomepage(pathId: String) {
        try {
            val s = AntSportsRpcCall.pathMapHomepage(pathId)
            var jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                if (!jo.has("userPathGoRewardList")) return
                val userPathGoRewardList = jo.getJSONArray("userPathGoRewardList")
                for (i in 0 until userPathGoRewardList.length()) {
                    jo = userPathGoRewardList.getJSONObject(i)
                    if ("UNRECEIVED" != jo.getString("status")) continue
                    val userPathRewardId = jo.getString("userPathRewardId")
                    val res = parseSportsJson(AntSportsRpcCall.rewardReceive(pathId, userPathRewardId))
                    if (ResChecker.checkRes(TAG, res)) {
                        val detail = res.getJSONObject("userPathRewardDetail")
                        val rightsRuleList = detail.getJSONArray("userPathRewardRightsList")
                        val award = StringBuilder()
                        for (j in 0 until rightsRuleList.length()) {
                            val right = rightsRuleList.getJSONObject(j).getJSONObject("rightsContent")
                            award.append(right.getString("name"))
                                .append("*")
                                .append(right.getInt("count"))
                        }
                        StructuredLog.other("文体宝箱🎁[$award]")
                    } else {
                        StructuredLog.record(TAG, "文体中心开宝箱")
                        StructuredLog.record(res.toString())
                    }
                }
            } else {
                StructuredLog.record(TAG, "文体中心开宝箱")
                StructuredLog.record(s)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "pathMapHomepage err:", t)
        }
    }

    /**
     * @brief 文体中心加入路线
     */
    private fun pathMapJoin(title: String, pathId: String) {
        try {
            val jo = parseSportsJson(AntSportsRpcCall.pathMapJoin(pathId))
            if (ResChecker.checkRes(TAG, jo)) {
                StructuredLog.other("加入线路🚶🏻‍♂️[$title]")
                pathFeatureQuery()
            } else {
                StructuredLog.record(TAG, jo.toString())
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "pathMapJoin err:", t)
        }
    }

    /**
     * @brief 文体中心行走逻辑
     */
    private fun tiyubizGo(
        countDate: String,
        title: String,
        goStepCount: Int,
        pathId: String,
        userPathRecordId: String
    ) {
        try {
            val s = AntSportsRpcCall.tiyubizGo(countDate, goStepCount, pathId, userPathRecordId)
            var jo = parseSportsJson(s)
            if (ResChecker.checkRes(TAG, jo)) {
                jo = jo.getJSONObject("userPath")
                StructuredLog.other(
                    "行走线路🚶🏻‍♂️[$title]#前进了" +
                        jo.getInt("userPathRecordForwardStepCount") + "步"
                )
                pathMapHomepage(pathId)
                val completed = "COMPLETED" == jo.getString("userPathRecordStatus")
                if (completed) {
                    StructuredLog.other("完成线路🚶🏻‍♂️[$title]")
                    pathFeatureQuery()
                }
            } else {
                StructuredLog.record(TAG, s)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "tiyubizGo err:", t)
        }
    }

    // ---------------------------------------------------------------------
    // 抢好友大战
    // ---------------------------------------------------------------------

    /**
     * @brief 抢好友主页查询 + 训练好友收益泡泡收集
     */
    private suspend fun queryClubHome() {
        try {
            val maxCount = zeroCoinLimit.value
            if (zeroTrainCoinCount >= maxCount) {
                val today = TimeUtil.getDateStr2()
                TaskRuntimeStore.put(TRAIN_FRIEND_ZERO_COIN_DATE, today)
                StructuredLog.forest("运动🏋️ 训练好友0金币已达${maxCount}次上限，今日不再执行")
                return
            }
            val clubHomeData = parseSportsJson(AntSportsRpcCall.queryClubHome())
            processBubbleList(clubHomeData.optJSONObject("mainRoom"))
            val roomList = clubHomeData.optJSONArray("roomList")
            if (roomList != null) {
                for (i in 0 until roomList.length()) {
                    val room = roomList.optJSONObject(i)
                    processBubbleList(room)
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryClubHome err:", t)
        }
    }

    /**
     * @brief 训练好友收益泡泡收集逻辑
     */
    private suspend fun processBubbleList(obj: JsonObject?) {
        if (obj == null || !obj.has("bubbleList")) return
        try {
            val bubbleList = obj.getJSONArray("bubbleList")
            for (j in 0 until bubbleList.length()) {
                val bubble = bubbleList.getJSONObject(j)
                val bubbleId = bubble.optString("bubbleId")

                val responseStr = AntSportsRpcCall.pickBubbleTaskEnergy(bubbleId, false)
                val responseJson = parseSportsJson(responseStr)

                if (!ResChecker.checkRes(TAG, responseJson)) {
                    StructuredLog.error(TAG, "收取训练好友 失败: $responseStr")
                    continue
                }

                var amount = 0
                val data = responseJson.optJSONObject("data")
                if (data != null) {
                    val changeAmountStr = data.optString("changeAmount", "0")
                    amount = changeAmountStr.toIntOrNull() ?: 0
                }

                StructuredLog.other("训练好友💰️ [获得:$amount 金币]")

                if (amount <= 0) {
                    zeroTrainCoinCount++
                    val maxCount = zeroCoinLimit.value
                    if (zeroTrainCoinCount >= maxCount) {
                        val today = TimeUtil.getDateStr2()
                        TaskRuntimeStore.put(TRAIN_FRIEND_ZERO_COIN_DATE, today)
                        StructuredLog.forest("运动🏋️ 连续0金币已达${maxCount}次，今日停止执行")
                        return
                    } else {
                        StructuredLog.record(TAG, "训练好友0金币计数: $zeroTrainCoinCount/$maxCount")
                    }
                }

                delay(1000)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "processBubbleList 异常:", t)
        }
    }

    /**
     * @brief 训练好友：选取可训练好友并执行一次训练
     */
    private suspend fun queryTrainItem() {
        try {
            val clubHomeData = parseSportsJson(AntSportsRpcCall.queryClubHome())
            val roomList = clubHomeData.optJSONArray("roomList") ?: return

            for (i in 0 until roomList.length()) {
                val room = roomList.optJSONObject(i) ?: continue
                val memberList = room.optJSONArray("memberList") ?: continue

                for (j in 0 until memberList.length()) {
                    val member = memberList.optJSONObject(j) ?: continue
                    val trainInfo = member.optJSONObject("trainInfo")
                    if (trainInfo == null || trainInfo.optBoolean("training", false)) continue

                    val memberId = member.optString("memberId")
                    val originBossId = member.optString("originBossId")
                    val userName = UserMap.getMaskName(originBossId) ?: originBossId

                    val responseData = AntSportsRpcCall.queryTrainItem()
                    val responseJson = parseSportsJson(responseData)
                    if (!ResChecker.checkRes(TAG, responseJson)) {
                        StructuredLog.record(
                            TAG,
                            "queryTrainItem rpc failed: ${responseJson.optString("resultDesc")}"
                        )
                        return
                    }

                    var bizId = responseJson.optString("bizId", "")
                    if (bizId.isEmpty() && responseJson.has("taskDetail")) {
                        bizId = responseJson.getJSONObject("taskDetail").optString("taskId", "")
                    }

                    val trainItemList = responseJson.optJSONArray("trainItemList")
                    if (bizId.isEmpty() || trainItemList == null || trainItemList.length() == 0) {
                        StructuredLog.record(TAG, "queryTrainItem response missing bizId or trainItemList")
                        return
                    }

                    var bestItem: JsonObject? = null
                    var bestProduction = -1
                    for (k in 0 until trainItemList.length()) {
                        val item = trainItemList.optJSONObject(k) ?: continue
                        val production = item.optInt("production", 0)
                        if (production > bestProduction) {
                            bestProduction = production
                            bestItem = item
                        }
                    }

                    if (bestItem == null) return

                    val itemType = bestItem.optString("itemType")
                    val trainItemName = bestItem.optString("name")

                    val trainMemberResponse = AntSportsRpcCall.trainMember(
                        bizId,
                        itemType,
                        memberId,
                        originBossId
                    )
                    val trainMemberJson = parseSportsJson(trainMemberResponse)
                    if (!ResChecker.checkRes(TAG, trainMemberJson)) {
                        StructuredLog.record(
                            TAG,
                            "trainMember request failed: ${trainMemberJson.optString("resultDesc")}"
                        )
                        return
                    }

                    StructuredLog.other("训练好友🥋[训练:$userName $trainItemName]")
                    delay(1000)
                    return
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryTrainItem err:", t)
        }
    }

    /**
     * @brief 抢好友大战：抢购好友逻辑
     */
    private suspend fun buyMember() {
        try {
            val clubHomeResponse = AntSportsRpcCall.queryClubHome()
            delay(500)
            val clubHomeJson = parseSportsJson(clubHomeResponse)

            if ("ENABLE" != clubHomeJson.optString("clubAuth")) {
                StructuredLog.record(TAG, "抢好友大战🧑‍🤝‍🧑未授权开启")
                return
            }

            val assetsInfo = clubHomeJson.optJSONObject("assetsInfo") ?: return
            val coinBalance = assetsInfo.optInt("energyBalance", 0)
            if (coinBalance <= 0) {
                StructuredLog.record(TAG, "抢好友大战🧑‍🤝‍🧑当前能量为0，跳过抢好友")
                return
            }

            val roomList = clubHomeJson.optJSONArray("roomList") ?: return

            for (i in 0 until roomList.length()) {
                val room = roomList.optJSONObject(i) ?: continue
                val memberList = room.optJSONArray("memberList")

                if (memberList != null && memberList.length() > 0) continue

                val roomId = room.optString("roomId")
                if (roomId.isEmpty()) continue

                val memberPriceResult = AntSportsRpcCall.queryMemberPriceRanking(coinBalance)
                delay(500)
                val memberPriceJson = parseSportsJson(memberPriceResult)
                if (!memberPriceJson.optBoolean("success", true)) {
                    StructuredLog.error(TAG, "queryMemberPriceRanking err: ${memberPriceJson.optString("resultDesc")}")
                    continue
                }

                val memberDetailList = memberPriceJson.optJSONArray("memberDetailList") ?: run {
                    StructuredLog.record(TAG, "抢好友大战🧑‍🤝‍🧑暂无可抢好友")
                    continue
                }

                for (j in 0 until memberDetailList.length()) {
                    val detail = memberDetailList.optJSONObject(j) ?: continue
                    val memberModel = detail.optJSONObject("memberModel") ?: continue

                    val originBossId = memberModel.optString("originBossId")
                    val memberIdFromRank = memberModel.optString("memberId")
                    if (originBossId.isEmpty() || memberIdFromRank.isEmpty()) continue

                    var isTarget = originBossIdList.value.contains(originBossId)
                    if (battleForFriendType.value == BattleForFriendType.DONT_ROB) {
                        isTarget = !isTarget
                    }
                    if (!isTarget) continue

                    val priceInfoObj = memberModel.optJSONObject("priceInfo") ?: continue
                    val price = priceInfoObj.optInt("price", Int.MAX_VALUE)
                    if (price > coinBalance) continue

                    val clubMemberResult = AntSportsRpcCall.queryClubMember(memberIdFromRank, originBossId)
                    delay(500)
                    val clubMemberDetailJson = parseSportsJson(clubMemberResult)
                    if (!clubMemberDetailJson.optBoolean("success", true) ||
                        !clubMemberDetailJson.has("member")
                    ) continue

                    val memberObj = clubMemberDetailJson.getJSONObject("member")
                    val currentBossId = memberObj.optString("currentBossId")
                    val memberId = memberObj.optString("memberId")
                    val priceInfoFull = memberObj.optJSONObject("priceInfo") ?: continue

                    if (currentBossId.isEmpty() || memberId.isEmpty()) continue

                    val priceInfoStr = priceInfoFull.toString()

                    val buyMemberResult = AntSportsRpcCall.buyMember(
                        currentBossId,
                        memberId,
                        originBossId,
                        priceInfoStr,
                        roomId
                    )
                    delay(500)
                    val buyMemberResponse = parseSportsJson(buyMemberResult)

                    if (ResChecker.checkRes(TAG, buyMemberResponse)) {
                        val userName = UserMap.getMaskName(originBossId) ?: originBossId
                        StructuredLog.other("抢购好友🥋[成功:将 $userName 抢回来]")
                        if (isTrainFriendEnabled()) {
                            queryTrainItem()
                        }
                        return
                    } else if ("CLUB_AMOUNT_NOT_ENOUGH" == buyMemberResponse.optString("resultCode")) {
                        StructuredLog.record(TAG, "[能量🎈不足，无法完成抢购好友！]")
                        return
                    } else if ("CLUB_MEMBER_TRADE_PROTECT" == buyMemberResponse.optString("resultCode")) {
                        StructuredLog.record(TAG, "[暂时无法抢购好友，给Ta一段独处的时间吧！]")
                    }
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "buyMember err:", t)
        }
    }

    // ---------------------------------------------------------------------
    // 健康岛任务处理器（内部类）
    // ---------------------------------------------------------------------

    /**
     * @brief 健康岛任务处理器
     *
     * <p>整体流程：</p>
     * <ol>
     *   <li>签到（querySign + takeSign）</li>
     *   <li>任务大厅循环处理（queryTaskCenter + taskSend / adtask.finish）</li>
     *   <li>健康岛浏览任务（queryTaskInfo + energyReceive）</li>
     *   <li>捡泡泡（queryBubbleTask + pickBubbleTaskEnergy）</li>
     *   <li>走路建造 / 旧版行走（queryBaseinfo + queryMapInfo/Build/WalkGrid 等）</li>
     * </ol>
     */
    @Suppress("GrazieInspection")
    inner class NeverlandTaskHandler {

        private val TAG = "Neverland"

        /** @brief 最大失败次数（优先使用 BaseModel 配置，默认 5 次） */
        private val MAX_ERROR_COUNT: Int =
            if (BaseModel.setMaxErrorCount.value > 0) BaseModel.setMaxErrorCount.value else 5

        /** @brief 任务循环间隔（毫秒） */
        private val TASK_LOOP_DELAY: Long = 1000

        /** @brief 健康岛任务中心最大刷新轮数 */
        private val MAX_TASK_CENTER_REFRESH_ROUNDS = 20

        /** @brief 相同任务快照连续出现次数上限 */
        private val MAX_UNCHANGED_TASK_REFRESHES = 2

        /** @brief build 成功但服务端进度不变的连续次数上限 */
        private val MAX_UNCHANGED_BUILD_CALLS = 2

        /** @brief 单张地图每轮最多发起的 build 调用数 */
        private val MAX_BUILD_CALLS_PER_RUN = 100

        /** @brief 单次健康岛运行最多执行的建造/重扫闭环轮数 */
        private val MAX_NEVERLAND_FLOW_ROUNDS = 4

        /** @brief 浏览任务列表最大刷新轮数 */
        private val MAX_HEALTH_ISLAND_TASK_REFRESH_ROUNDS = 20

        /**
         * @brief 健康岛任务入口
         */
        suspend fun runNeverland() {
            try {
                val taskEnabled = isNeverlandTaskEnabled()
                val gridEnabled = isNeverlandGridEnabled()
                val taskExecutionGuard = NeverlandTaskExecutionGuard()
                val buildCallLimiter = NeverlandBuildCallLimiter(MAX_BUILD_CALLS_PER_RUN)

                if (taskEnabled) {
                    // 1. 签到
                    neverlandDoSign()
                    // 2. 任务大厅循环处理
                    loopHandleTaskCenter("initial", taskExecutionGuard)
                    // 3. 浏览任务
                    handleHealthIslandTask()
                    // 4. 捡泡泡
                    neverlandPickAllBubble()
                }

                if (gridEnabled) {
                    // 5. 自动走路建造；每次确认推进后重扫任务中心，形成有界闭环。
                    val workflow = NeverlandBuildCoordinator(
                        maxRounds = MAX_NEVERLAND_FLOW_ROUNDS,
                        buildRound = {
                            if (buildCallLimiter.remainingCalls <= 0) {
                                NeverlandBuildOutcome.skipped("本轮健康岛建造调用预算已耗尽")
                            } else {
                                neverlandAutoTask(buildCallLimiter.remainingCalls).also { outcome ->
                                    buildCallLimiter.record(outcome.calls)
                                }
                            }
                        },
                        rescanTaskCenter = {
                            StructuredLog.record(TAG, "建造已推进，重新扫描健康岛任务中心")
                            loopHandleTaskCenter("post-build", taskExecutionGuard)
                        },
                        pause = { delay(it) },
                    ).run(rescanEnabled = taskEnabled)

                    StructuredLog.record(
                        TAG,
                        "健康岛🏝️ 闭环: rounds=${workflow.rounds}, " +
                            "rescans=${workflow.postBuildRescans}, " +
                            "status=${workflow.lastOutcome.status}, " +
                            "reason=${workflow.lastOutcome.stopReason}"
                    )
                    if (workflow.reachedRoundLimit) {
                        StructuredLog.record(TAG, "健康岛🏝️ 闭环达到本轮上限，留待下次调度继续")
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, "runNeverland err:", e)
                throw e
            }
        }

        // ---------------------------------------------------------------
        // 1. 健康岛签到
        // ---------------------------------------------------------------

        /**
         * @brief 健康岛签到流程
         */
        private fun neverlandDoSign() {
            try {
                if (Status.hasFlagToday("AntSports::neverlandDoSign::已签到")) return

                StructuredLog.forest("健康岛🏝️ 检查签到状态")
                val jo = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.querySign(3, "jkdsportcard"))

                if (!ResChecker.checkRes(TAG + "查询签到失败:", jo) ||
                    !ResChecker.checkRes(TAG, jo) ||
                    jo.optJSONObject("data") == null
                ) {
                    val errorCode = jo.optString("errorCode", "")
                    if ("ALREADY_SIGN_IN" == errorCode ||
                        "已签到" == jo.optString("errorMsg", "")
                    ) {
                        Status.setFlagToday("AntSports::neverlandDoSign::已签到")
                    }
                    return
                }

                val data = jo.getJSONObject("data")
                val signInfo = data.optJSONObject("continuousSignInfo")
                if (signInfo != null && signInfo.optBoolean("signedToday", false)) {
                    StructuredLog.record(
                        TAG,
                        "今日已签到 ✓ 连续：${signInfo.optInt("continuitySignedDayCount")} 天"
                    )
                    Status.setFlagToday("AntSports::neverlandDoSign::已签到")
                    return
                }

                StructuredLog.forest("健康岛🏝️ 正在签到…")
                val signRes = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.takeSign(3, "jkdsportcard"))

                if (!ResChecker.checkRes(TAG + "签到失败:", signRes) ||
                    !ResChecker.checkRes(TAG, signRes) ||
                    signRes.optJSONObject("data") == null
                ) {
                    StructuredLog.error(TAG, "takeSign raw=$signRes")
                    val errorCode = signRes.optString("errorCode", "")
                    val errorMessage = signRes.optString("errorMsg", "")
                    if (errorCode == "ALREADY_SIGN_IN" || errorMessage == "已签到") {
                        StructuredLog.forest("运动🏋️ 今日已签到")
                        Status.setFlagToday("AntSports::neverlandDoSign::已签到")
                    }
                    return
                }

                val signData = signRes.getJSONObject("data")
                val reward = signData.optJSONObject("continuousDoSignInVO")
                val rewardAmount = reward?.optInt("rewardAmount", 0) ?: 0
                val rewardType = reward?.optString("rewardType", "") ?: ""
                val signInfoAfter = signData.optJSONObject("continuousSignInfo")
                val newContinuity = signInfoAfter?.optInt("continuitySignedDayCount", -1) ?: -1

                StructuredLog.other(
                    "健康岛签到成功 🎉 +" + rewardAmount + rewardType +
                        " 连续：" + newContinuity + " 天"
                )
                Status.setFlagToday("AntSports::neverlandDoSign::已签到")
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "neverlandDoSign err:$t", t)
            }
        }

        // ---------------------------------------------------------------
        // 2. 任务大厅循环处理
        // ---------------------------------------------------------------

        /**
         * @brief 循环处理健康岛任务大厅中的活动、浏览和 GAME_TASK 任务。
         */
        private suspend fun loopHandleTaskCenter(
            reason: String,
            executionGuard: NeverlandTaskExecutionGuard,
        ) {
            var errorCount = 0
            var refreshRound = 0
            var previousFingerprint: String? = null
            val noProgressGuard = NeverlandNoProgressGuard(MAX_UNCHANGED_TASK_REFRESHES)
            StructuredLog.record(
                TAG,
                "健康岛🏝️ 任务大厅（reason=$reason, " +
                    "刷新限制=$MAX_TASK_CENTER_REFRESH_ROUNDS, 失败限制=$MAX_ERROR_COUNT）"
            )

            while (refreshRound < MAX_TASK_CENTER_REFRESH_ROUNDS && errorCount < MAX_ERROR_COUNT) {
                try {
                    refreshRound++

                    val taskCenterResp = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.queryTaskCenter())
                    if (!ResChecker.checkRes(TAG, taskCenterResp) ||
                        taskCenterResp.optJSONObject("data") == null
                    ) {
                        errorCount++
                        delay(TASK_LOOP_DELAY)
                        continue
                    }

                    val taskList = taskCenterResp.getJSONObject("data").optJSONArray("taskCenterTaskVOS")
                    if (taskList == null || taskList.length() == 0) {
                        StructuredLog.other("任务中心为空，无任务可处理")
                        break
                    }

                    val taskStates = mutableListOf<NeverlandTaskState>()
                    val actionableTasks = mutableListOf<Triple<JsonObject, NeverlandTaskState, NeverlandTaskAction>>()
                    var unresolvedTasks = 0
                    for (i in 0 until taskList.length()) {
                        val task = taskList.optJSONObject(i) ?: continue

                        val title = task.optString("title", task.optString("taskName", "未知任务"))
                        val type = task.optString("taskType", "")
                        val status = task.optString("taskStatus", "")
                        val taskId = task.optString("taskId", task.optString("id", ""))
                        val state = NeverlandTaskState(
                            taskId = taskId.ifBlank { "$type:$title" },
                            taskType = type,
                            taskStatus = status,
                            progress = task.optString("progress", ""),
                            condition = task.optString("condition", ""),
                        )
                        taskStates.add(state)

                        val action = neverlandTaskAction(type, status)
                        if (neverlandTaskIsUnresolved(action)) {
                            unresolvedTasks++
                        }
                        when (action) {
                            NeverlandTaskAction.REQUIRES_SIGNUP -> {
                                StructuredLog.record(TAG, "任务 [$title] 需要手动报名，已自动拉黑并跳过")
                                if (taskId.isNotEmpty()) {
                                    TaskBlacklist.addToBlacklist(taskId, title)
                                }
                            }
                            NeverlandTaskAction.COMPLETE -> Unit
                            NeverlandTaskAction.WAIT -> {
                                StructuredLog.error(
                                    TAG,
                                    "任务 [$title] 状态暂不可处理: type=$type status=$status " +
                                        "progress=${state.progress}/${state.condition}"
                                )
                            }
                            NeverlandTaskAction.UNSUPPORTED -> {
                                StructuredLog.record(
                                    TAG,
                                    "任务 [$title] 类型暂不支持: type=$type status=$status"
                                )
                            }
                            NeverlandTaskAction.EXECUTE,
                            NeverlandTaskAction.RECEIVE -> {
                                if (taskId.isNotEmpty() && TaskBlacklist.isTaskInBlacklist(taskId)) {
                                    if (neverlandTaskIsUnresolved(action, blocked = true)) {
                                        unresolvedTasks++
                                    }
                                    StructuredLog.record(TAG, "任务 [$title] 位于黑名单，保持未确认状态")
                                    continue
                                }
                                if (executionGuard.wasSuccessfullyRequested(state, action)) {
                                    if (neverlandTaskIsUnresolved(action, blocked = true)) {
                                        unresolvedTasks++
                                    }
                                    StructuredLog.record(
                                        TAG,
                                        "任务 [$title] 已提交过相同状态请求，等待服务端刷新"
                                    )
                                    continue
                                }
                                actionableTasks.add(Triple(task, state, action))
                            }
                        }
                    }

                    val fingerprint = neverlandTaskFingerprint(taskStates)
                    val previous = previousFingerprint
                    if (previous != null) {
                        val changed = fingerprint != previous
                        if (noProgressGuard.record(changed)) {
                            StructuredLog.error(
                                TAG,
                                "任务中心连续 $MAX_UNCHANGED_TASK_REFRESHES 轮状态无变化，" +
                                    "停止重复提交；snapshot=$fingerprint"
                            )
                            return
                        }
                    }
                    previousFingerprint = fingerprint

                    if (actionableTasks.isEmpty()) {
                        if (unresolvedTasks > 0) {
                            StructuredLog.record(TAG, "任务中心无可自动处理项，仍有 $unresolvedTasks 个未确认任务")
                        } else {
                            StructuredLog.record(TAG, "任务中心状态已闭环，无待提交或待领取任务")
                        }
                        break
                    }

                    StructuredLog.record(
                        TAG,
                        "第 $refreshRound 轮发现 ${actionableTasks.size} 个可处理任务（含待领取）"
                    )

                    var currentBatchError = 0
                    for ((task, state, action) in actionableTasks) {
                        if (executionGuard.wasSuccessfullyRequested(state, action)) {
                            continue
                        }
                        val ok = handleSingleTask(task, action)
                        if (ok) {
                            executionGuard.recordSuccessfulRequest(state, action)
                        } else {
                            currentBatchError++
                        }
                        delay(3000)
                    }

                    errorCount += currentBatchError
                    StructuredLog.record(TAG, "当前批次执行完毕，准备下一次刷新检查")
                    delay(TASK_LOOP_DELAY)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    errorCount++
                    StructuredLog.printStackTrace(TAG, "循环异常", t)
                }
            }

            if (errorCount >= MAX_ERROR_COUNT) {
                StructuredLog.error(TAG, "任务处理失败次数达到上限，保留未确认状态等待下次执行")
            } else if (refreshRound >= MAX_TASK_CENTER_REFRESH_ROUNDS) {
                StructuredLog.record(TAG, "任务中心达到刷新上限，保留当前状态等待下次执行")
            }
        }

        /**
         * @brief 处理单个大厅任务
         */
        private fun handleSingleTask(task: JsonObject, action: NeverlandTaskAction): Boolean {
            return try {
                val title = task.optString("title", "未知任务")
                val type = task.optString("taskType", "")
                val status = task.optString("taskStatus", "")
                val jumpLink = task.optString("jumpLink", "")

                StructuredLog.record(TAG, "任务：[$title] 状态：$status 类型：$type")

                if (action == NeverlandTaskAction.RECEIVE) {
                    try {
                        val taskRequest = JsonObject(
                            task + mapOf(
                                "scene" to JsonPrimitive("MED_TASK_HALL"),
                                "source" to (task["source"] ?: JsonPrimitive("jkdsportcard")),
                            )
                        )

                        val res = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.taskReceive(taskRequest))
                        if (res.optBoolean("success", false)) {
                            val data = res.optJSONObject("data")
                            var rewardDetail = ""
                            if (data != null && data.has("userItems")) {
                                val items = data.getJSONArray("userItems")
                                val sb = StringBuilder()
                                for (i in 0 until items.length()) {
                                    val item = items.getJSONObject(i)
                                    val name = item.optString("name", "未知奖励")
                                    val amount = item.optInt("modifyCount", 0)
                                    val total = item.optInt("count", 0)
                                    sb.append("[").append(name)
                                        .append(" +").append(amount)
                                        .append(" (余:").append(total).append(")] ")
                                }
                                rewardDetail = sb.toString()
                            }
                            StructuredLog.record(TAG, "奖励领取请求成功 [$title]$rewardDetail，等待刷新确认终态")
                            return true
                        } else {
                            val errorMsg = res.optString("errorMsg", "未知错误")
                            val errorCode = res.optString("errorCode", "UNKNOWN")
                            StructuredLog.error(TAG, "❌ 奖励领取失败 [$errorCode]: $errorMsg")
                            return false
                        }
                    } catch (e: Exception) {
                        StructuredLog.error(TAG, "领取流程异常: ${e.message}")
                        return false
                    }
                }

                if (action == NeverlandTaskAction.EXECUTE) {
                    return when (type) {
                        "PROMOKERNEL_TASK" -> handlePromoKernelTask(task, title)
                        "LIGHT_TASK" -> handleLightTask(task, title, jumpLink)
                        "GAME_TASK" -> handleGameTask(task, title)
                        else -> {
                            StructuredLog.error(TAG, "未处理的任务类型：$type")
                            false
                        }
                    }
                }

                StructuredLog.record(TAG, "任务动作 $action 无需执行 RPC，保持状态等待刷新")
                action == NeverlandTaskAction.COMPLETE
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, "handleSingleTask 异常", e)
                false
            }
        }

        /**
         * GAME_TASK 由健康岛服务端根据任务对象结算事件；taskSend 成功后任务中心会刷新。
         */
        private fun handleGameTask(task: JsonObject, title: String): Boolean {
            return try {
                val taskRequest = JsonObject(task + mapOf("scene" to JsonPrimitive("MED_TASK_HALL")))
                val res = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.taskSend(taskRequest))
                if (ResChecker.checkRes(TAG, res)) {
                    StructuredLog.forest("运动🏋️ 游戏任务提交成功：$title，等待任务中心刷新确认")
                    true
                } else {
                    StructuredLog.error(TAG, "GAME_TASK taskSend 失败：$title 响应：$res")
                    false
                }
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, "处理 GAME_TASK 异常（$title）", e)
                false
            }
        }

        // ---------------------------------------------------------------
        // 3. 健康岛浏览任务
        // ---------------------------------------------------------------

        /**
         * @brief 处理健康岛浏览任务（LIGHT_FEEDS_TASK）
         */
        private suspend fun handleHealthIslandTask() {
            try {
                StructuredLog.forest("健康岛🏝️ 检查浏览任务")
                val attemptedEncryptValues = mutableSetOf<String>()
                var refreshRound = 0
                while (refreshRound < MAX_HEALTH_ISLAND_TASK_REFRESH_ROUNDS) {
                    refreshRound++
                    val taskInfoResp = parseSportsJson(
                        AntSportsRpcCall.NeverlandRpcCall.queryTaskInfo(
                            "health-island",
                            "LIGHT_FEEDS_TASK"
                        )
                    )

                    if (!ResChecker.checkRes(TAG + "查询健康岛浏览任务失败:", taskInfoResp) ||
                        taskInfoResp.optJSONObject("data") == null
                    ) {
                        StructuredLog.error(TAG, "健康岛浏览任务查询失败 [$taskInfoResp] 请关闭此功能")
                        return
                    }

                    val taskInfos = taskInfoResp.getJSONObject("data").optJSONArray("taskInfos")
                    if (taskInfos == null || taskInfos.length() == 0) {
                        StructuredLog.record(TAG, "健康岛浏览任务列表为空")
                        return
                    }

                    var attemptedThisRound = 0
                    for (i in 0 until taskInfos.length()) {
                        val taskInfo = taskInfos.getJSONObject(i)
                        val encryptValue = taskInfo.optString("encryptValue")
                        val energyNum = taskInfo.optInt("energyNum", 0)
                        val viewSec = taskInfo.optInt("viewSec", 15)

                        if (encryptValue.isEmpty()) {
                            StructuredLog.error(TAG, "健康岛任务 encryptValue 为空，跳过")
                            continue
                        }
                        if (!attemptedEncryptValues.add(encryptValue)) {
                            continue
                        }
                        attemptedThisRound++

                        StructuredLog.record(TAG, "健康岛浏览任务：能量+$energyNum，需等待${viewSec}秒")
                        delay(neverlandViewDelayMillis(viewSec))

                        val receiveResp = parseSportsJson(
                            AntSportsRpcCall.NeverlandRpcCall.energyReceive(
                                encryptValue,
                                energyNum,
                                "LIGHT_FEEDS_TASK",
                                null
                            )
                        )
                        if (ResChecker.checkRes(TAG + "领取健康岛任务奖励:", receiveResp) &&
                            ResChecker.checkRes(TAG, receiveResp)
                        ) {
                            StructuredLog.forest("健康岛🏝️ 浏览任务完成，获得能量+$energyNum")
                        } else {
                            StructuredLog.error(TAG, "健康岛任务领取失败: $receiveResp")
                        }

                        delay(1000)
                    }

                    if (attemptedThisRound == 0) {
                        StructuredLog.record(TAG, "健康岛浏览任务状态未刷新，停止重复领取")
                        return
                    }
                }
                StructuredLog.record(TAG, "健康岛浏览任务达到刷新上限，留待下次调度继续")
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "handleHealthIslandTask err", t)
            }
        }

        // ---------------------------------------------------------------
        // 4. PROMOKERNEL_TASK / LIGHT_TASK 处理
        // ---------------------------------------------------------------

        /**
         * @brief 处理 PROMOKERNEL_TASK（活动类任务）
         */
        private fun handlePromoKernelTask(task: JsonObject, title: String): Boolean {
            return try {
                val taskRequest = JsonObject(task + mapOf("scene" to JsonPrimitive("MED_TASK_HALL")))
                val res = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.taskSend(taskRequest))
                if (ResChecker.checkRes(TAG, res)) {
                    StructuredLog.forest("健康岛🏝️ 活动任务提交成功：$title，等待任务中心刷新确认")
                    true
                } else {
                    StructuredLog.error(TAG, "taskSend 失败: $task 响应：$res")
                    false
                }
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, "handlePromoKernelTask 处理 PROMOKERNEL_TASK 异常（$title）", e)
                false
            }
        }

        /**
         * @brief 处理 LIGHT_TASK（浏览类任务）
         */
        private fun handleLightTask(task: JsonObject, title: String, jumpLink: String): Boolean {
            return try {
                var bizId = task.optString("bizId", "")
                if (bizId.isEmpty()) {
                    val logExtMap = task.optJSONObject("logExtMap")
                    if (logExtMap != null) {
                        bizId = logExtMap.optString("bizId", "")
                    }
                }

                if (bizId.isEmpty()) {
                    StructuredLog.error(TAG, "LIGHT_TASK 未找到 bizId：$title jumpLink=$jumpLink")
                    return false
                }

                val res = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.finish(bizId))
                if (res.optBoolean("success", false) ||
                    "0" == res.optString("errCode", "")
                ) {
                    var rewardMsg = ""
                    val extendInfo = res.optJSONObject("extendInfo")
                    if (extendInfo != null) {
                        val rewardInfo = extendInfo.optJSONObject("rewardInfo")
                        if (rewardInfo != null) {
                            val amount = rewardInfo.optString("rewardAmount", "0")
                            rewardMsg = " (获得奖励: $amount 能量)"
                        }
                    }
                    StructuredLog.forest("健康岛🏝️ 浏览任务完成：$title$rewardMsg")
                    true
                } else {
                    StructuredLog.error(TAG, "完成 LIGHT_TASK 失败: $title 返回: $res")
                    false
                }
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, "handleLightTask 处理 LIGHT_TASK 异常（$title）", e)
                false
            }
        }

        // ---------------------------------------------------------------
        // 5. 捡泡泡
        // ---------------------------------------------------------------

        /**
         * @brief 健康岛捡泡泡 + 浏览类泡泡任务
         */
        private suspend fun neverlandPickAllBubble() {
            try {
                StructuredLog.record(TAG, "健康岛🏝️ 检查可领取泡泡")

                val jo = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.queryBubbleTask())

                if (!ResChecker.checkRes(TAG + "查询泡泡失败:", jo) ||
                    jo.optJSONObject("data") == null
                ) {
                    StructuredLog.error(TAG, "queryBubbleTask raw=$jo")
                    return
                }

                val arr = jo.getJSONObject("data").optJSONArray("bubbleTaskVOS")
                if (arr == null || arr.length() == 0) {
                    StructuredLog.other("无泡泡可领取")
                    return
                }

                val ids = mutableListOf<String>()
                val encryptValues = mutableListOf<String>()

                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val bubbleTaskStatus = item.optString("bubbleTaskStatus")
                    val encryptValue = item.optString("encryptValue")
                    val energyNum = item.optInt("energyNum", 0)
                    val viewSec = item.optInt("viewSec", 15)

                    if ("INIT" == bubbleTaskStatus && encryptValue.isNotEmpty()) {
                        encryptValues.add(encryptValue)
                        StructuredLog.record(
                            TAG,
                            "找到可浏览任务： ${item.optString("title")}，能量+$energyNum，需等待${viewSec}秒"
                        )
                    } else if ("RECEIVE_SUCCESS" != bubbleTaskStatus &&
                        !item.optBoolean("initState") &&
                        item.optString("medEnergyBallInfoRecordId").isNotEmpty()
                    ) {
                        ids.add(item.getString("medEnergyBallInfoRecordId"))
                    }
                }

                if (ids.isEmpty() && encryptValues.isEmpty()) {
                    StructuredLog.record(TAG, "没有可领取的泡泡任务")
                    return
                }

                if (ids.isNotEmpty()) {
                    StructuredLog.record(TAG, "健康岛🏝️ 正在领取 ${ids.size} 个泡泡…")
                    val pick = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.pickBubbleTaskEnergy(ids))

                    if (!ResChecker.checkRes(TAG + "领取泡泡失败:", pick) ||
                        pick.optJSONObject("data") == null
                    ) {
                        StructuredLog.error(TAG, "pickBubbleTaskEnergy raw=$pick")
                        return
                    }

                    val data = pick.getJSONObject("data")
                    val changeAmount = data.optString("changeAmount", "0")
                    val balance = data.optString("balance", "0")
                    if (changeAmount == "0") {
                        StructuredLog.record(TAG, "健康岛🏝️ 本次未获得任何能量")
                    } else {
                        StructuredLog.other("捡泡泡成功 🎈 +$changeAmount 余额：$balance")
                    }
                }

                for (encryptValue in encryptValues) {

                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        if (encryptValue == item.optString("encryptValue")) {
                            val energyNum = item.optInt("energyNum", 0)
                            val viewSec = item.optInt("viewSec", 15)
                            val title = item.optString("title")

                            delay(viewSec * 1000L)

                            val receiveResp = parseSportsJson(
                                AntSportsRpcCall.NeverlandRpcCall.energyReceive(
                                    encryptValue,
                                    energyNum,
                                    "LIGHT_FEEDS_TASK",
                                    "adBubble"
                                )
                            )

                            if (ResChecker.checkRes(TAG + "领取泡泡任务奖励:", receiveResp)) {
                                StructuredLog.forest("健康岛🏝️ 浏览任务[$title]完成，获得能量+$energyNum")
                            } else {
                                StructuredLog.error(TAG, "浏览任务领取失败: $receiveResp")
                            }

                            delay((1000 + Math.random() * 1000).toLong())
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "neverlandPickAllBubble err:", t)
            }
        }

        // ---------------------------------------------------------------
        // 6. 自动走路建造（步数限制 + 能量限制）
        // ---------------------------------------------------------------

        /**
         * @brief 检查今日步数是否达到上限
         * @return 剩余可走步数（<=0 表示已达上限）
         */
        private fun checkDailyStepLimit(): Int {
            var stepCount = Status.getIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT) ?: 0
            val maxStepLimit = neverlandGridStepCount.value
            val remainSteps = maxStepLimit - stepCount

            StructuredLog.record(
                TAG,
                String.format(
                    "今日步数统计: 已走 %d/%d 步, 剩余 %d 步",
                    stepCount,
                    maxStepLimit,
                    max(0, remainSteps)
                )
            )
            return remainSteps
        }

        /**
         * @brief 记录步数增加
         * @param addedSteps 本次增加的步数
         * @return 更新后的总步数
         */
        private fun recordStepIncrease(addedSteps: Int): Int {
            if (addedSteps <= 0) {
                return Status.getIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT) ?: 0
            }
            var currentSteps = Status.getIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT) ?: 0
            val newSteps = currentSteps + addedSteps
            Status.setIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT, newSteps)
            val maxLimit = neverlandGridStepCount.value
            StructuredLog.record(
                TAG,
                String.format(
                    "步数增加: +%d 步, 当前总计 %d/%d 步",
                    addedSteps,
                    newSteps,
                    maxLimit
                )
            )
            return newSteps
        }

        /**
         * @brief 健康岛走路建造任务入口
         */
        private suspend fun neverlandAutoTask(maxBuildCalls: Int): NeverlandBuildOutcome {
            return try {
                StructuredLog.record(TAG, "健康岛🏝️ 启动走路建造任务")

                val baseInfo = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.queryBaseinfo())
                if (!ResChecker.checkRes(TAG + " 查询基础信息失败:", baseInfo) ||
                    baseInfo.optJSONObject("data") == null
                ) {
                    StructuredLog.error(TAG, "queryBaseinfo 失败, 响应数据: $baseInfo")
                    return NeverlandBuildOutcome.failed("queryBaseinfo 失败")
                }

                val baseData = baseInfo.getJSONObject("data")
                val isNewGame = baseData.optBoolean("newGame", false)
                val branchId = baseData.optString("branchId", "MASTER")
                val mapId = baseData.optString("mapId", "")
                val mapName = baseData.optString("mapName", "未知地图")
                if (mapId.isBlank()) {
                    StructuredLog.error(TAG, "queryBaseinfo 未返回有效 mapId")
                    return NeverlandBuildOutcome.failed("缺少 mapId")
                }

                StructuredLog.record(
                    TAG,
                    String.format(
                        "当前地图: [%s](%s) | 模式: %s",
                        mapName,
                        mapId,
                        if (isNewGame) "新游戏建造" else "旧版行走"
                    )
                )

                val remainSteps = if (isNewGame) null else checkDailyStepLimit()
                if (remainSteps != null && remainSteps <= 0) {
                    StructuredLog.record(TAG, "健康岛🏝️ 今日步数已达上限，本轮跳过")
                    return NeverlandBuildOutcome.skipped("旧版地图已达每日步数上限")
                }

                val leftEnergy = queryUserEnergy()
                if (leftEnergy < 5) {
                    StructuredLog.record(TAG, "剩余能量不足(< 5), 无法执行任务")
                    return NeverlandBuildOutcome.skipped("能量不足")
                }

                val outcome = if (isNewGame) {
                    executeAutoBuild(branchId, mapId, leftEnergy, mapName, maxBuildCalls)
                } else {
                    executeAutoWalk(branchId, mapId, requireNotNull(remainSteps), leftEnergy, mapName)
                }

                StructuredLog.record(
                    TAG,
                    "健康岛🏝️ 自动走路建造: status=${outcome.status}, " +
                        "calls=${outcome.calls}, reason=${outcome.stopReason}"
                )
                outcome
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "neverlandAutoTask 发生异常$t", t)
                NeverlandBuildOutcome.failed("neverlandAutoTask 异常: ${t.message}")
            }
        }

        /**
         * @brief 查询用户剩余能量
         */
        private fun queryUserEnergy(): Int {
            return try {
                val energyResp = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.queryUserEnergy())
                if (!ResChecker.checkRes(TAG + " 查询用户能量失败:", energyResp) ||
                    energyResp.optJSONObject("data") == null
                ) {
                    StructuredLog.error(TAG, "queryUserEnergy 失败, 响应数据: $energyResp")
                    0
                } else {
                    val balance = energyResp.getJSONObject("data").optInt("balance", 0)
                    StructuredLog.record(TAG, "当前剩余能量: $balance")
                    balance
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "queryUserEnergy err", t)
                0
            }
        }

        /**
         * @brief 执行旧版行走任务（能量泵走路模式）
         */
        private suspend fun executeAutoWalk(
            branchId: String,
            mapId: String,
            remainSteps: Int,
            leftEnergyInit: Int,
            mapName: String
        ): NeverlandBuildOutcome {
            var leftEnergy = leftEnergyInit
            var attemptedCalls = 0
            var successfulCalls = 0
            return try {
                val mapInfoResp = parseSportsJson(
                    AntSportsRpcCall.NeverlandRpcCall.queryMapInfo(mapId, branchId)
                )

                if (!ResChecker.checkRes(TAG + " queryMapInfo 失败:", mapInfoResp) ||
                    mapInfoResp.optJSONObject("data") == null
                ) {
                    StructuredLog.error(TAG, "queryMapInfo 失败，终止走路任务")
                    return NeverlandBuildOutcome.failed("queryMapInfo 失败")
                }

                val mapInfo = mapInfoResp.getJSONObject("data")
                if (!mapInfo.optBoolean("canWalk", false)) {
                    StructuredLog.record(TAG, "当前地图不可走(canWalk=false)，跳过走路任务")
                    return NeverlandBuildOutcome.skipped("当前旧版地图不可走")
                }

                val mapStarData = mapInfo.optJSONObject("starData")
                var lastCurrStar = mapStarData?.optInt("curr", 0) ?: 0

                for (i in 0 until remainSteps) {
                    if (leftEnergy < 5) {
                        StructuredLog.record(TAG, "[$mapName] 能量不足(< 5), 停止走路任务")
                        break
                    }

                    attemptedCalls++
                    val walkResp = parseSportsJson(
                        AntSportsRpcCall.NeverlandRpcCall.walkGrid(branchId, mapId, false)
                    )

                    if (!ResChecker.checkRes(TAG + " walkGrid 失败:", walkResp) ||
                        walkResp.optJSONObject("data") == null
                    ) {
                        val errorCode = walkResp.optString("errorCode", "")
                        StructuredLog.error(
                            TAG,
                            String.format(
                                "walkGrid 失败, 错误码: %s, 响应数据: %s",
                                errorCode,
                                walkResp
                            )
                        )
                        return if (successfulCalls > 0) {
                            NeverlandBuildOutcome(
                                status = NeverlandBuildStatus.ADVANCED,
                                calls = attemptedCalls,
                                stopReason = "旧版行走部分成功后 walkGrid 失败",
                            )
                        } else {
                            NeverlandBuildOutcome(
                                status = NeverlandBuildStatus.FAILED,
                                calls = attemptedCalls,
                                stopReason = "walkGrid RPC 失败",
                            )
                        }
                    }

                    val walkData = walkResp.getJSONObject("data")
                    leftEnergy = walkData.optInt("leftCount", leftEnergy)
                    successfulCalls++

                    recordStepIncrease(1)
                    val stepThisTime = extractStepIncrease(walkData)

                    val starData = walkData.optJSONObject("starData")
                    val currStar = starData?.optInt("curr", lastCurrStar) ?: lastCurrStar
                    val maxStar = starData?.optInt("count", 0) ?: Int.MAX_VALUE
                    val starIncreased = currStar > lastCurrStar
                    lastCurrStar = currStar

                    var redPocketAdd = 0
                    val userItems = walkData.optJSONArray("userItems")
                    if (userItems != null && userItems.length() > 0) {
                        val item = userItems.optJSONObject(0)
                        if (item != null) {
                            redPocketAdd = item.optInt("modifyCount", item.optInt("count", 0))
                        }
                    }

                    val sb = StringBuilder()
                    sb.append("[").append(mapName).append("] 前进 ")
                        .append(stepThisTime).append(" 步，")

                    if (starIncreased) {
                        sb.append("获得 🌟")
                    } else if (redPocketAdd > 0) {
                        sb.append("获得 🧧 +").append(redPocketAdd)
                    } else {
                        sb.append("啥也没有")
                    }

                    StructuredLog.other(sb.toString())

                    tryReceiveStageReward(branchId, mapId, starData)

                    if (currStar >= maxStar) {
                        StructuredLog.other("[$mapName] 当前地图已完成星星，准备切换地图")
                        chooseAvailableMap()
                        break
                    }
                    // 切换地图前稍作等待（suspend delay 可响应协程取消，避免 withTimeout 下线程空转）
                    delay(888)
                }
                if (successfulCalls > 0) {
                    StructuredLog.forest("健康岛🏝️ 自动走路任务完成")
                    NeverlandBuildOutcome(
                        status = NeverlandBuildStatus.ADVANCED,
                        calls = attemptedCalls,
                        stopReason = "旧版地图已成功行走",
                    )
                } else {
                    StructuredLog.record(TAG, "健康岛🏝️ 自动走路：本轮未产生有效行走")
                    NeverlandBuildOutcome.skipped("旧版地图本轮未产生行走")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "executeAutoWalk err", t)
                if (successfulCalls > 0) {
                    NeverlandBuildOutcome(
                        status = NeverlandBuildStatus.ADVANCED,
                        calls = attemptedCalls,
                        stopReason = "旧版行走部分成功后异常: ${t.message}",
                    )
                } else {
                    NeverlandBuildOutcome.failed("executeAutoWalk 异常: ${t.message}", attemptedCalls)
                }
            }
        }

        /**
         * @brief 若有未领取的关卡奖励则尝试领取
         */
        private fun tryReceiveStageReward(branchId: String, mapId: String, starData: JsonObject?) {
            if (starData == null) return

            val rewardLevel = starData.optInt("rewardLevel", -1)
            if (rewardLevel <= 0) return

            val recordArr = starData.optJSONArray("stageRewardRecord")
            if (recordArr != null) {
                for (i in 0 until recordArr.length()) {
                    if (recordArr.optString(i).toIntOrNull() == rewardLevel) return
                }
            }

            StructuredLog.other(String.format("检测到未领取关卡奖励 🎁 map=%s 等级: %d，尝试领取…", mapId, rewardLevel))

            val rewardStr = try {
                AntSportsRpcCall.NeverlandRpcCall.mapStageReward(branchId, rewardLevel, mapId)
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "mapStageReward RPC 调用异常", t)
                return
            }.trim()

            if (rewardStr.isEmpty()) {
                StructuredLog.error(TAG, "mapStageReward 返回空字符串")
                return
            }
            if (!rewardStr.startsWith("{")) {
                StructuredLog.error(TAG, "mapStageReward 返回非 JSON: $rewardStr")
                return
            }

            val rewardResp = try {
                parseSportsJson(rewardStr)
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "mapStageReward JSON 解析失败", t)
                return
            }

            if (!ResChecker.checkRes(TAG, rewardResp)) {
                val errCode = rewardResp.optString("errorCode", "")
                if ("ASSET_ITEM_NOT_EXISTED" == errCode) {
                    StructuredLog.other("关卡奖励已被领取或不存在（可忽略）")
                } else {
                    StructuredLog.error(TAG, "领取关卡奖励失败: $rewardResp")
                }
                return
            }

            val data = rewardResp.optJSONObject("data")
            val receiveResult = data?.optJSONObject("receiveResult")
            if (receiveResult == null) {
                StructuredLog.record(TAG, "关卡奖励领取成功 🎉（无奖励详情）")
                return
            }

            val prizes = receiveResult.optJSONArray("prizes")
            val balance = receiveResult.optString("balance", "")

            if (prizes != null && prizes.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until prizes.length()) {
                    val p = prizes.optJSONObject(i) ?: continue
                    sb.append(p.optString("title", "未知奖励"))
                        .append(" x")
                        .append(p.optString("modifyCount", "1"))
                    if (i != prizes.length() - 1) sb.append("，")
                }
                StructuredLog.other(
                    String.format(
                        "Lv.%s 奖励领取成功 🎉 %s | 当前余额: %s",
                        rewardLevel,
                        sb.toString(),
                        balance
                    )
                )
            } else {
                StructuredLog.other("关卡奖励领取成功 🎉（无可展示奖励）")
            }
        }

        /**
         * @brief 查询地图列表，区分可切换、全部完成和真实失败。
         */
        private fun chooseAvailableMap(): NeverlandMapSwitchResult {
            return try {
                val mapResp = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.queryMapList())
                if (!ResChecker.checkRes(TAG + " 查询地图失败:", mapResp)) {
                    StructuredLog.error(TAG, "queryMapList 失败: $mapResp")
                    return NeverlandMapSwitchResult.Failed("queryMapList 失败")
                }

                val mapList = mapResp.optJSONObject("data")?.optJSONArray("mapList")
                val summaries = neverlandMapSummaries(mapList)
                val availability = neverlandMapAvailability(summaries)
                if (mapList == null || availability == NeverlandMapAvailability.INVALID) {
                    StructuredLog.error(TAG, "地图列表为空或包含未知状态")
                    return NeverlandMapSwitchResult.Failed("地图列表为空或状态未知")
                }
                if (availability == NeverlandMapAvailability.ALL_COMPLETE) {
                    StructuredLog.record(TAG, "健康岛🏝️ 所有地图已完成，无新地图可切换")
                    return NeverlandMapSwitchResult.AllComplete
                }

                val maps = (0 until mapList.length()).mapNotNull(mapList::optJSONObject)
                val candidates = when (availability) {
                    NeverlandMapAvailability.DOING -> maps.filter {
                        it.optString("status").equals("DOING", ignoreCase = true)
                    }
                    NeverlandMapAvailability.AVAILABLE -> maps.filter {
                        neverlandMapIsBuildable(it.optString("status")) &&
                            !it.optString("status").equals("DOING", ignoreCase = true)
                    }
                    NeverlandMapAvailability.ALL_COMPLETE,
                    NeverlandMapAvailability.INVALID -> emptyList()
                }
                val selected = candidates.randomOrNull()
                    ?: return NeverlandMapSwitchResult.Failed("未找到可切换地图")

                StructuredLog.other(
                    "选择可建造地图: ${selected.optString("mapName")}" +
                        "${selected.optString("mapId")} status=${selected.optString("status")}"
                )
                val switched = chooseMap(selected)
                    ?: return NeverlandMapSwitchResult.Failed("地图切换 RPC 失败")
                NeverlandMapSwitchResult.Switched(switched)
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "chooseAvailableMap err", t)
                NeverlandMapSwitchResult.Failed("地图切换异常: ${t.message}")
            }
        }

        /**
         * @brief 切换当前地图
         */
        private fun chooseMap(map: JsonObject): JsonObject? {
            return try {
                val mapId = map.optString("mapId")
                val branchId = map.optString("branchId")
                val resp = parseSportsJson(
                    AntSportsRpcCall.NeverlandRpcCall.chooseMap(branchId, mapId)
                )
                if (ResChecker.checkRes(TAG, resp)) {
                    StructuredLog.record(TAG, "切换地图成功: $mapId")
                    map
                } else {
                    StructuredLog.error(TAG, "切换地图失败: $resp")
                    null
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "chooseMap err", t)
                null
            }
        }

        /**
         * @brief 从 walkData 中提取步数增量
         */
        private fun extractStepIncrease(walkData: JsonObject): Int {
            return try {
                val mapAwards = walkData.optJSONArray("mapAwards")
                if (mapAwards != null && mapAwards.length() > 0) {
                    mapAwards.getJSONObject(0).optInt("step", 0)
                } else {
                    0
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, t)
                0
            }
        }

        private fun neverlandBuildSnapshot(
            mapId: String,
            branchId: String,
            data: JsonObject,
            fallbackMapEnergy: Int? = if (data.has("mapEnergyProcess")) {
                data.optInt("mapEnergyProcess")
            } else {
                null
            },
        ): NeverlandBuildSnapshot {
            val stage = data.optJSONObject("stageInfo") ?: data
            return NeverlandBuildSnapshot(
                mapId = data.optString("mapId", mapId).ifEmpty { mapId },
                branchId = data.optString("branchId", branchId).ifEmpty { branchId },
                mapEnergyProcess = fallbackMapEnergy,
                buildingIndex = if (stage.has("buildingIndex")) stage.optInt("buildingIndex") else null,
                buildingEnergyProcess = if (stage.has("buildingEnergyProcess")) {
                    stage.optInt("buildingEnergyProcess")
                } else {
                    null
                },
            )
        }

        private fun queryBuildSnapshot(mapId: String, branchId: String): NeverlandBuildSnapshot? {
            return try {
                val response = parseSportsJson(AntSportsRpcCall.NeverlandRpcCall.queryMapInfoNew(mapId))
                if (!ResChecker.checkRes(TAG + " 建造后查询地图失败:", response)) return null
                val data = response.optJSONObject("data") ?: return null
                val snapshot = neverlandBuildSnapshot(mapId, branchId, data)
                StructuredLog.record(
                    TAG,
                    "建造后地图确认: map=${snapshot.mapId}, branch=${snapshot.branchId}, " +
                        "mapProgress=${snapshot.mapEnergyProcess}, building=${snapshot.buildingIndex}, " +
                        "buildingProgress=${snapshot.buildingEnergyProcess}"
                )
                snapshot
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "建造后查询地图异常", t)
                null
            }
        }

        private fun calculateActualBuildEnergy(before: JsonObject?, end: JsonObject?): Int {
            if (before == null || end == null) return 0
            if (!before.has("buildingIndex") || !before.has("buildingEnergyProcess") ||
                !end.has("buildingIndex") || !end.has("buildingEnergyProcess")
            ) {
                return 0
            }
            val beforeIndex = before.optInt("buildingIndex", -1)
            val endIndex = end.optInt("buildingIndex", -1)
            if (beforeIndex < 0 || endIndex < 0 || endIndex < beforeIndex) return 0

            return if (endIndex > beforeIndex) {
                if (!before.has("buildingEnergyFinal")) return 0
                (before.optInt("buildingEnergyFinal") - before.optInt("buildingEnergyProcess") +
                    end.optInt("buildingEnergyProcess")).coerceAtLeast(0)
            } else {
                (end.optInt("buildingEnergyProcess") - before.optInt("buildingEnergyProcess"))
                    .coerceAtLeast(0)
            }
        }

        /**
         * @brief 执行自动建造任务（新游戏模式）
         */
        private suspend fun executeAutoBuild(
            branchIdInit: String,
            mapIdInit: String,
            leftEnergyInit: Int,
            mapName: String,
            hardCallLimit: Int,
        ): NeverlandBuildOutcome {
            var branchId = branchIdInit
            var mapId = mapIdInit
            var leftEnergy = leftEnergyInit
            var calls = 0
            var progressedCalls = 0
            return try {

                val resp = AntSportsRpcCall.NeverlandRpcCall.queryMapInfoNew(mapId)
                val mapInfo = parseSportsJson(resp)

                if (!ResChecker.checkRes(TAG + " 查询建造地图失败", mapInfo)) {
                    StructuredLog.error(TAG, "查询建造地图失败 $mapInfo")
                    return NeverlandBuildOutcome.failed("queryMapInfoNew 失败")
                }
                val data = mapInfo.optJSONObject("data")
                if (data == null) {
                    StructuredLog.error(TAG, "地图Data 为空，无法解析")
                    return NeverlandBuildOutcome.failed("地图 data 为空")
                }

                // 新游戏接口返回的分支才是 build 实际接受的分支（baseinfo 可能仍是旧分支）。
                branchId = data.optString("branchId", branchId).ifEmpty { branchId }

                if (!data.has("mapEnergyFinal") || !data.has("mapEnergyProcess")) {
                    StructuredLog.error(TAG, "地图缺少 mapEnergyFinal/mapEnergyProcess，禁止盲目建造: $data")
                    return NeverlandBuildOutcome.failed("地图缺少完整能量进度")
                }
                val mapEnergyFinal = data.optInt("mapEnergyFinal", -1)
                val mapEnergyProcess = data.optInt("mapEnergyProcess", -1)
                if (mapEnergyFinal <= 0 || mapEnergyProcess < 0 || mapEnergyProcess > mapEnergyFinal) {
                    StructuredLog.error(
                        TAG,
                        "地图能量进度非法，禁止建造: process=$mapEnergyProcess final=$mapEnergyFinal"
                    )
                    return NeverlandBuildOutcome.failed("地图能量进度非法")
                }
                val currentSnapshot = neverlandBuildSnapshot(mapId, branchId, data)

                if (mapEnergyProcess == mapEnergyFinal) {
                    StructuredLog.record(TAG, "当前地图已建造完成，准备切换地图...")
                    when (val switchResult = chooseAvailableMap()) {
                        NeverlandMapSwitchResult.AllComplete -> {
                            StructuredLog.record(TAG, "健康岛🏝️ 所有地图已完成建造，本轮正常收敛")
                            return NeverlandBuildOutcome.skipped("所有地图已完成")
                        }
                        is NeverlandMapSwitchResult.Failed -> {
                            return NeverlandBuildOutcome.failed(switchResult.reason)
                        }
                        is NeverlandMapSwitchResult.Switched -> {
                            val choiceMapInfo = switchResult.map
                            if (choiceMapInfo.optBoolean("newIsLandFlg", true)) {
                                val nextMapId = choiceMapInfo.optString("mapId")
                                StructuredLog.record(TAG, "成功切换到可建造的新地图: $nextMapId，下一闭环轮次继续建造。")
                                return NeverlandBuildOutcome(
                                    status = NeverlandBuildStatus.ADVANCED,
                                    stopReason = "当前地图完成并已切换地图",
                                )
                            }
                            StructuredLog.record(TAG, "已切换至走路地图，将在下次运行时执行，任务终止。")
                            return NeverlandBuildOutcome.skipped("已切换至旧版走路地图")
                        }
                    }
                }

                val mapRemainingEnergy = mapEnergyFinal - mapEnergyProcess
                val mapConfig = data.optJSONObject("mapConfig")
                val oneBuildEnergy = mapConfig?.optInt("oneBuildEnergy", 5)?.coerceAtLeast(1) ?: 5
                val allowedMultipliers = mapConfig?.optJSONArray("buildMulti")
                    ?.let { values -> (0 until values.length()).mapNotNull { values.optString(it).toIntOrNull() } }
                    ?.ifEmpty { listOf(1) }
                    ?: listOf(1)
                val budget = neverlandBuildBudget(
                    mapEnergyRemaining = mapRemainingEnergy,
                    oneBuildEnergy = oneBuildEnergy,
                    allowedMultipliers = allowedMultipliers,
                    availableEnergy = leftEnergy,
                    hardCallLimit = hardCallLimit,
                )
                if (budget.remainingUnits <= 0) {
                    StructuredLog.record(TAG, "当前地图无可建造的剩余进度或能量")
                    return NeverlandBuildOutcome.skipped("地图进度或能量不足")
                }
                if (budget.maxCalls <= 0) {
                    StructuredLog.error(TAG, "建造倍数无法精确覆盖剩余单位=${budget.remainingUnits}")
                    return NeverlandBuildOutcome.failed("建造倍数无法覆盖剩余进度")
                }
                StructuredLog.record(
                    TAG,
                    "建造预算: 剩余单位=${budget.remainingUnits}, " +
                        "最大倍数=${budget.maxMultiplier}, 最多调用=${budget.maxCalls}"
                )

                var remainingMapUnits = budget.remainingUnits
                var previousSnapshot = currentSnapshot
                val noProgressGuard = NeverlandNoProgressGuard(MAX_UNCHANGED_BUILD_CALLS)

                while (calls < budget.maxCalls && remainingMapUnits > 0 && leftEnergy >= oneBuildEnergy) {
                    val multiNum = neverlandBuildMultiplier(
                        remainingUnits = remainingMapUnits,
                        availableEnergy = leftEnergy,
                        oneBuildEnergy = oneBuildEnergy,
                        allowedMultipliers = allowedMultipliers,
                        callsRemaining = budget.maxCalls - calls,
                    )
                    if (multiNum <= 0) break
                    calls++

                    val buildResp = parseSportsJson(
                        AntSportsRpcCall.NeverlandRpcCall.build(branchId, mapId, multiNum)
                    )
                    if (!ResChecker.checkRes(TAG + " build 失败:", buildResp)) {
                        StructuredLog.error(
                            TAG,
                            String.format(
                                "build 失败, multiNum=%d, 响应: %s",
                                multiNum,
                                buildResp
                            )
                        )
                        return if (progressedCalls > 0) {
                            NeverlandBuildOutcome(
                                status = NeverlandBuildStatus.ADVANCED,
                                calls = calls,
                                stopReason = "build 失败但已有部分推进",
                            )
                        } else {
                            NeverlandBuildOutcome.failed("build RPC 失败", calls)
                        }
                    }

                    val buildData = buildResp.optJSONObject("data")
                    if (buildData == null || buildData.isEmpty()) {
                        StructuredLog.record(TAG, "⚠️ build响应数据为空，停止本轮建造并等待下一轮地图刷新。")
                        return if (progressedCalls > 0) {
                            NeverlandBuildOutcome(
                                status = NeverlandBuildStatus.ADVANCED,
                                calls = calls,
                                stopReason = "build 响应为空但已有部分推进",
                            )
                        } else {
                            NeverlandBuildOutcome.skipped("build 响应为空", calls)
                        }
                    }

                    val before = buildData.optJSONObject("beforeStageInfo")
                    val end = buildData.optJSONObject("endStageInfo")
                    val responseSnapshot = if (before != null && end != null) {
                        neverlandBuildSnapshot(mapId, branchId, end)
                    } else {
                        null
                    }
                    val confirmedSnapshot = queryBuildSnapshot(mapId, branchId)
                    val snapshotCandidates = listOf(responseSnapshot, confirmedSnapshot)
                    val advancedSnapshot = latestAdvancedNeverlandSnapshot(
                        previous = previousSnapshot,
                        candidates = snapshotCandidates,
                    )
                    val progressed = advancedSnapshot != null

                    if (!progressed) {
                        val shouldStop = noProgressGuard.record(progressed = false)
                        StructuredLog.error(
                            TAG,
                            "build 返回成功但进度未变化（连续=${noProgressGuard.consecutiveUnchanged}）: $buildResp"
                        )
                        if (shouldStop) {
                            return if (progressedCalls > 0) {
                                NeverlandBuildOutcome(
                                    status = NeverlandBuildStatus.ADVANCED,
                                    calls = calls,
                                    stopReason = "连续建造成功但进度未变化",
                                )
                            } else {
                                NeverlandBuildOutcome(
                                    status = NeverlandBuildStatus.UNCHANGED,
                                    calls = calls,
                                    stopReason = "连续建造成功但进度未变化",
                                )
                            }
                        }
                        delay(500)
                        continue
                    }

                    noProgressGuard.record(progressed = true)
                    progressedCalls++
                    val resultCount = buildData.optJSONArray("buildResults")
                        ?.length()
                        ?.takeIf { it > 0 }
                        ?: 0
                    val stageEnergyIncrease = calculateActualBuildEnergy(before, end)
                    val mapEnergyIncrease = confirmedNeverlandMapEnergyIncrease(
                        previous = previousSnapshot,
                        candidates = snapshotCandidates,
                    )
                    val confirmedEnergyIncrease = maxOf(
                        stageEnergyIncrease,
                        mapEnergyIncrease,
                        resultCount * oneBuildEnergy,
                    )
                    val stepIncrease = calculateBuildSteps(resultCount, confirmedEnergyIncrease, oneBuildEnergy)
                    if (stepIncrease <= 0) {
                        StructuredLog.record(
                            TAG,
                            "build 已确认推进但无法量化实际增量；停止本轮并重新查询地图与能量，" +
                                "不使用请求倍数 x$multiNum 代替真实进度"
                        )
                        return NeverlandBuildOutcome(
                            status = NeverlandBuildStatus.ADVANCED,
                            calls = calls,
                            stopReason = "建造已推进但实际增量未知",
                        )
                    }
                    leftEnergy = (leftEnergy - confirmedEnergyIncrease).coerceAtLeast(0)
                    val totalSteps = if (stepIncrease > 0) recordStepIncrease(stepIncrease) else {
                        Status.getIntFlagToday(StatusFlags.FLAG_NEVERLAND_STEP_COUNT) ?: 0
                    }
                    remainingMapUnits = (remainingMapUnits - stepIncrease).coerceAtLeast(0)
                    previousSnapshot = advancedSnapshot

                    val awardInfo = extractAwardInfo(buildData)
                    StructuredLog.other(
                        String.format(
                            "建造进度 🏗️ 倍数: x%d | 能量: %d | 本次: +%d | 今日: %d/%d%s",
                            multiNum,
                            leftEnergy,
                            stepIncrease,
                            totalSteps,
                            neverlandGridStepCount.value,
                            awardInfo
                        )
                    )
                    if (previousSnapshot.mapEnergyProcess != null &&
                        mapEnergyFinal > 0 && previousSnapshot.mapEnergyProcess >= mapEnergyFinal
                    ) {
                        StructuredLog.record(TAG, "当前地图建造进度已达到服务端上限")
                        break
                    }
                    delay(1000)
                }
                if (calls >= budget.maxCalls) {
                    StructuredLog.record(TAG, "达到单地图建造调用上限，下一闭环轮次重新查询地图")
                }
                if (progressedCalls > 0) {
                    NeverlandBuildOutcome(
                        status = NeverlandBuildStatus.ADVANCED,
                        calls = calls,
                        stopReason = "建造进度已确认",
                    )
                } else {
                    NeverlandBuildOutcome(
                        status = NeverlandBuildStatus.UNCHANGED,
                        calls = calls,
                        stopReason = "本轮没有确认到建造进度",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "executeAutoBuild err", t)
                if (progressedCalls > 0) {
                    NeverlandBuildOutcome(
                        status = NeverlandBuildStatus.ADVANCED,
                        calls = calls,
                        stopReason = "建造部分成功后异常: ${t.message}",
                    )
                } else {
                    NeverlandBuildOutcome.failed("executeAutoBuild 异常: ${t.message}", calls)
                }
            }
        }

        /**
         * @brief 计算建造实际产生的步数
         */
        private fun calculateBuildSteps(resultCount: Int, actualUsedEnergy: Int, oneBuildEnergy: Int): Int {
            return try {
                if (resultCount > 0) {
                    resultCount
                } else if (actualUsedEnergy > 0) {
                    val energyPerStep = oneBuildEnergy.coerceAtLeast(1)
                    ((actualUsedEnergy.toLong() + energyPerStep - 1L) / energyPerStep)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt()
                } else {
                    0
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, t)
                0
            }
        }

        /**
         * @brief 从建造数据中提取奖励信息
         */
        private fun extractAwardInfo(buildData: JsonObject?): String {
            return try {
                val awards = buildData?.optJSONArray("rewards") ?: buildData?.optJSONArray("awards")
                if (awards != null && awards.length() > 0) {
                    String.format(" | 获得奖励: %d 项", awards.length())
                } else {
                    ""
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, t)
                ""
            }
        }
    }

    // ---------------------------------------------------------------------
    // 配置用枚举/常量
    // ---------------------------------------------------------------------

    /**
     * @brief 蚂蚁运动路线主题常量与映射表
     */
    interface WalkPathTheme {
        companion object {
            const val DA_MEI_ZHONG_GUO = 0  ///< 大美中国 (默认)
            const val GONG_YI_YI_XIAO_BU = 1  ///< 公益一小步
            const val DENG_DING_ZHI_MA_SHAN = 2  ///< 登顶芝麻山
            const val WEI_C_DA_TIAO_ZHAN = 3  ///< 维C大挑战
            const val LONG_NIAN_QI_FU = 4  ///< 龙年祈福
            const val SHOU_HU_TI_YU_MENG = 5  ///< 守护体育梦

            /** @brief 界面显示的名称列表 */
            val nickNames = arrayOf(
                "大美中国",
                "公益一小步",
                "登顶芝麻山",
                "维C大挑战",
                "龙年祈福",
                "守护体育梦"
            )

            /**
             * @brief 对应目标应用接口的 ThemeID 映射表
             * @note 数组顺序必须与上方常量定义保持严格一致
             */
            val themeIds = arrayOf(
                "M202308082226",  ///< [0] 大美中国
                "M202401042147",  ///< [1] 公益一小步
                "V202405271625",  ///< [2] 登顶芝麻山
                "202404221422",   ///< [3] 维C大挑战
                "WF202312050200", ///< [4] 龙年祈福
                "V202409061650"   ///< [5] 守护体育梦
            )
        }
    }

    /**
     * @brief 慈善捐能量模式
     */
    interface DonateCharityCoinType {
        companion object {
            const val ONE = 0
            // 保留原 ALL 选项的文案，方便以后扩充
            val nickNames = arrayOf("捐赠一个项目", "捐赠所有项目")
        }
    }

    /**
     * @brief 抢好友模式
     */
    interface BattleForFriendType {
        companion object {
            const val ROB = 0
            const val DONT_ROB = 1
            val nickNames = arrayOf("选中抢", "选中不抢")
        }
    }
}
