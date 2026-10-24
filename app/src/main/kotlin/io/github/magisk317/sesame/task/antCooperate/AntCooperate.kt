package io.github.magisk317.sesame.task.antCooperate

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.data.StatusFlags
import io.github.magisk317.sesame.entity.CooperateEntity.Companion.getList
import io.github.magisk317.sesame.model.BaseModel
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.task.TaskCommon
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.maps.CooperateMap
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

private fun parseJsonObject(raw: String): JsonObject = raw.toJsonObject()

class AntCooperate : ModelTask() {
    private fun parseJsonObject(raw: String): JsonObject = raw.toJsonObject()

    /**
     * 获取任务名称
     *
     * @return 合种任务名称
     */
    override fun getName(): String {
        return "蚂蚁森林合种" //保留这个全称
    }

    /**
     * 获取任务分组
     *
     * @return 森林分组
     */
    override fun getGroup(): ModelGroup {
        return ModelGroup.FOREST
    }

    /**
     * 获取任务图标
     *
     * @return 合种任务图标文件名
     */
    override fun getIcon(): String {
        return "AntCooperate.png"
    }

    private val cooperateWater = BooleanModelField("cooperateWater", "合种浇水|开启", false)
    private val cooperateWaterList = SelectAndCountModelField(
        "cooperateWaterList",
        "合种浇水列表",
        LinkedHashMap<String?, Int?>(),
        { getList() },
        "打开上面的开关后执行一次后再重新回来应该能加载出来"
    )
    private val cooperateWaterTotalLimitList = SelectAndCountModelField(
        "cooperateWaterTotalLimitList",
        "浇水总量限制列表",
        LinkedHashMap<String?, Int?>(),
        { getList() },
        "当浇满后理论不会再浇了"
    )
    private val cooperateSendCooperateBeckon = BooleanModelField("cooperateSendCooperateBeckon", "合种 | 召唤队友浇水| 仅队长 ", false)
    private val loveCooperateWater = BooleanModelField("loveCooperateWater", "真爱合种 | 浇水", false)
    private val loveCooperateWaterNum = IntegerModelField("loveCooperateWaterNum", "真爱合种 | 浇水克数(默认1g)", 1)
    private val teamCooperateWaterNum = IntegerModelField("teamCooperateWaterNum", "组队合种 | 浇水克数(0为关闭，10-5000)", 0)
    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(cooperateWater)
        modelFields.addField(cooperateWaterList)
        modelFields.addField(cooperateWaterTotalLimitList)
        modelFields.addField(cooperateSendCooperateBeckon)
        // 真爱合种配置
        modelFields.addField(loveCooperateWater)
        modelFields.addField(loveCooperateWaterNum)
        // 组队合种配置
        modelFields.addField(teamCooperateWaterNum)
        return modelFields
    }

    /**
     * 执行合种任务的主要逻辑
     */
    override suspend fun runSuspend() {
        try {
            // 1. 真爱合种
            if (loveCooperateWater.value) {
                loveCooperateWater()
            }

            // 2. 组队合种
            if (teamCooperateWaterNum.value > 0) {
                teamCooperateWater()

            }
            // 3. 普通合种
            if (cooperateWater.value) {
                val queryUserCooperatePlantList = parseJsonObject(AntCooperateRpcCall.queryUserCooperatePlantList())
                if (ResChecker.checkRes(TAG, queryUserCooperatePlantList)) {
                    // 1. 获取当前能量，设为 var，因为浇水后本地需要扣减，否则下一个合种会误判能量充足
                    var userCurrentEnergy = queryUserCooperatePlantList.getInt("userCurrentEnergy")
                    val cooperatePlants = queryUserCooperatePlantList.getJSONArray("cooperatePlants")
                    StructuredLog.record(TAG, "获取合种列表成功: ${cooperatePlants.length()} 颗合种")
                    for (i in 0 until cooperatePlants.length()) {
                        var plant = cooperatePlants.getJSONObject(i)
                        val cooperationId = plant.getString("cooperationId")
                        // 补全缺失的合种名称信息
                        if (!plant.has("name")) {
                            val detailResp = AntCooperateRpcCall.queryCooperatePlant(cooperationId)
                            plant = parseJsonObject(detailResp).getJSONObject("cooperatePlant")
                        }

                        val name = plant.getString("name")
                        val admin = plant.getString("admin")

                        // 2. 合种打招呼逻辑 (独立判断，不影响浇水主流程)
                        if (cooperateSendCooperateBeckon.value && UserMap.currentUid == admin) {
                            cooperateSendCooperateBeckon(cooperationId, name)
                        }

                        // 3. 记录合种信息到本地 Map
                        IdMapManager.getInstance(CooperateMap::class.java).add(cooperationId, name)

                        // 4. 检查是否满足“今日是否可浇水”的本地状态缓存
                        if (!Status.canCooperateWaterToday(UserMap.currentUid, cooperationId)) {
                            // StructuredLog.runtime(TAG, "$name 今日已标记为不可浇水/已浇完")
                            continue
                        }

                        // 获取服务端限制
                        val waterDayLimit = plant.getInt("waterDayLimit") // 今日剩余可浇水量
                        val waterLimit = plant.getJSONObject("cooperateTemplate").getInt("waterLimit") // 每日总上限
                        // val watered = waterLimit - waterDayLimit
                        StructuredLog.record(TAG, "获取合种[$name] 浇水信息: 剩余可浇 $waterDayLimit g / 总限制 $waterLimit g")

                        // 5. 获取配置
                        val configPerRound = cooperateWaterList.value[cooperationId] // 本轮配置浇水量
                        val configTotalLimit = cooperateWaterTotalLimitList.value[cooperationId] // 配置的总浇水上限(累计)

                        if (configPerRound == null) {
                            StructuredLog.record(TAG, "浇水列表中没有为[$name]配置，跳过")
                            continue
                        }

                        // 6. 计算本轮目标浇水量 (Target Water)
                        var planToWater: Int

                        if (configTotalLimit == null) {
                            // 逻辑保持原意：如果没有配置总限制，则直接把今日剩余额度拉满
                            StructuredLog.record(TAG, "未配置 $name 限制总浇水，目标为填满今日可浇水量（服务端或本地限制）")
                            planToWater = waterDayLimit
                        } else {
                            StructuredLog.record(TAG, "载入配置 $name 限制总浇水[$configTotalLimit]g")
                            val totalWatered = getTotalWatering(cooperationId) // 获取已累计浇水

                            if (totalWatered < 0) {
                                StructuredLog.record(TAG, "无法获取用户[${UserMap.currentUid}]的累计浇水数据，跳过 $name")
                                continue
                            }

                            val remainingQuota = configTotalLimit - totalWatered
                            if (remainingQuota <= 0) {
                                StructuredLog.forest("$name 累计浇水已达标($totalWatered/$configTotalLimit)，跳过")
                                continue
                            }

                            planToWater = remainingQuota
                        }

                        // 7. 最终数值修正 (核心优化：统一使用 min 逻辑)
                        // 实际浇水量 = Min(计划量, 今日剩余可浇量, 当前背包能量)
                        var actualWater = planToWater

                        if (actualWater > waterDayLimit) actualWater = waterDayLimit
                        if (actualWater > configPerRound) actualWater = configPerRound
                        if (actualWater > userCurrentEnergy) actualWater = userCurrentEnergy

                        StructuredLog.record(TAG, "[$name] 结算: 计划 $planToWater, 剩余限额 $waterDayLimit, 背包 $userCurrentEnergy -> 实际: $actualWater")

                        // 8. 执行浇水
                        if (actualWater > 0) {
                            cooperateWater(cooperationId, actualWater, name)
                            // !!! 关键修正：本地扣除能量，供下一次循环判断使用 !!!
                            userCurrentEnergy -= actualWater
                        } else {
                            StructuredLog.record(TAG, "浇水列表中没有为[$name]配置")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
        } finally {
                    IdMapManager.getInstance(CooperateMap::class.java).save(UserMap.currentUid)
        }
    }

    // 真爱合种逻辑
    private fun loveCooperateWater() {
        try {
            // 1. 本地状态检查 (快速失败)
            if (Status.hasFlagToday("love::teamWater")) {
                StructuredLog.record(TAG, "真爱合种今日已浇过水")
                return
            }

            // 2. 查询首页数据
            val queryResult = AntCooperateRpcCall.queryLoveHome()
            val queryLoveHome = try {
                parseJsonObject(queryResult)
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, "真爱合种响应JSON解析失败", e)
                return
            }

            if (!ResChecker.checkRes(TAG, queryLoveHome)) {
                // ResChecker 内部通常已经打印了错误日志
                return
            }

            // 3. 解析队伍信息
            val teamInfo = queryLoveHome.optJSONObject("teamInfo")
            if (teamInfo == null) {
                StructuredLog.error(TAG, "未找到真爱合种队伍信息，可能是未开启或结构变更")
                // 如果确认是未开启，可以考虑自动关闭开关
                // loveCooperateWater.value = false
                return
            }

            val teamId = teamInfo.optString("teamId")
            val teamStatus = teamInfo.optString("teamStatus")

            // 4. 检查服务端记录的今日浇水状态
            // 结构通常是: waterInfo -> todayWaterMap -> {"uid": waterAmount}
            val myWateredAmount = teamInfo.optJSONObject("waterInfo")
                ?.optJSONObject("todayWaterMap")
                ?.optInt(UserMap.currentUid ?: "", 0) ?: 0

            if (myWateredAmount > 0) {
                StructuredLog.forest("真爱合种今日已浇水(${myWateredAmount}g)")
                // 既然服务端说浇过了，更新本地状态并退出
                Status.setFlagToday("love::teamWater")
                return
            }

            // 5. 校验队伍状态是否允许浇水
            if (teamId.isEmpty() || "ACTIVATED" != teamStatus) {
                StructuredLog.record(TAG, "真爱合种队伍不可用 (状态: $teamStatus, ID: $teamId)")
                return
            }

            // 6. 执行浇水
            val waterAmount = loveCooperateWaterNum.value
            if (waterAmount <= 0) {
                StructuredLog.error(TAG, "配置的浇水数值无效: $waterAmount")
                return
            }

            val waterResult = AntCooperateRpcCall.loveTeamWater(teamId, waterAmount)
            val waterJo = parseJsonObject(waterResult)

            if (ResChecker.checkRes(TAG, waterJo)) {
                StructuredLog.forest("真爱合种💖[浇水成功]#${waterAmount}g")
                Status.setFlagToday("love::teamWater")
            } else {
                StructuredLog.error(TAG, "真爱合种浇水失败: " + waterJo.optString("resultDesc"))
            }

        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "loveCooperateWater 异常:", t)
        }
    }

    // 组队合种浇水逻辑
    private fun teamCooperateWater() {
        try {
            // --- 1. 基础配置与本地校验 ---
            // 用户设置的“每日目标浇水量”
            val userDailyTarget = teamCooperateWaterNum.value.coerceIn(10, 5000)

            // 获取今日已浇水量
            val todayUsed = Status.getIntFlagToday(StatusFlags.FLAG_TEAM_WATER_DAILY_COUNT) ?: 0

            // 计算用户视角的今日剩余额度
            val userRemainingQuota = userDailyTarget - todayUsed

            // 如果剩余额度小于最小浇水单位(10g)，直接结束
            if (userRemainingQuota < 10) {
                StructuredLog.record(TAG, "组队合种今日已达标 (已浇${todayUsed}g / 目标${userDailyTarget}g)，跳过")
                return
            }

            // --- 2. 获取服务端数据 (TeamID & 能量) ---
            val homePageStr = AntCooperateRpcCall.queryHomePage()
            val homeJo = parseJsonObject(homePageStr)
            if (!ResChecker.checkRes(TAG, homeJo)) {
                StructuredLog.record(TAG, "queryHomePage 返回异常")
                return
            }

            val teamId = homeJo.optJSONObject("teamHomeResult")
                ?.optJSONObject("teamBaseInfo")
                ?.optString("teamId")
                ?.takeIf { it.isNotBlank() }

            if (teamId == null) {
                StructuredLog.record(TAG, "未获取到组队合种 TeamID")
                return
            }

            val currentEnergy = homeJo.optJSONObject("userBaseInfo")?.optInt("currentEnergy") ?: 0
            if (currentEnergy < 10) {
                StructuredLog.record(TAG, "当前能量不足10g (${currentEnergy}g)，无法浇水")
                return
            }

            var needReturn = false //判断是否要返回个人
            if (!isTeam(homeJo)) {

                val updateUserConfigStr = AntCooperateRpcCall.updateUserConfig(true)
                val userConfigJo = parseJsonObject(updateUserConfigStr)
                if (!ResChecker.checkRes(TAG, userConfigJo)) {
                    StructuredLog.record(TAG, "updateUserConfig 返回异常")
                    return
                }
                needReturn = true
                StructuredLog.record(TAG, "不在队伍模式,已为您切换至组队浇水")

            }

            // --- 3. 获取服务端限制 (剩余可浇水量) ---
            val miscInfoStr = AntCooperateRpcCall.queryMiscInfo("teamCanWaterCount", teamId)
            val miscJo = parseJsonObject(miscInfoStr)
            if (!ResChecker.checkRes(TAG, miscJo)) {
                StructuredLog.record(TAG, "queryMiscInfo 查询失败")
                return
            }

            // serverRemaining: 服务端返回的今日剩余可浇水额度
            val serverRemaining = miscJo.optJSONObject("combineHandlerVOMap")
                ?.optJSONObject("teamCanWaterCount")
                ?.optInt("waterCount", 0) ?: 0

            StructuredLog.record(TAG, "组队状态检查: 目标剩余${userRemainingQuota}g | 官方剩余${serverRemaining}g | 背包能量${currentEnergy}g")

            if (serverRemaining < 10) {
                StructuredLog.record(TAG, "官方限制今日无可浇水额度，跳过")
                return
            }

            // --- 4. 核心计算 (取交集/最小值) ---
            // 最终浇水量 = Min(用户剩余配额, 官方剩余配额, 当前背包能量)
            val finalWaterAmount = userRemainingQuota
                .coerceAtMost(serverRemaining)
                .coerceAtMost(currentEnergy)

            // --- 5. 最终校验与执行 ---
            if (finalWaterAmount < 10) {
                StructuredLog.record(TAG, "计算后浇水量(${finalWaterAmount}g)低于最小限制10g，不执行")
                return
            }

            StructuredLog.record(TAG, "执行浇水: ${finalWaterAmount}g")
            val waterResStr = AntCooperateRpcCall.teamWater(teamId, finalWaterAmount)
            val waterJo = parseJsonObject(waterResStr)

            if (ResChecker.checkRes(TAG, waterJo)) {
                StructuredLog.forest("组队合种🌲[浇水成功] #${finalWaterAmount}g")
                // 更新本地统计
                val newTotal = todayUsed + finalWaterAmount
                Status.setIntFlagToday(StatusFlags.FLAG_TEAM_WATER_DAILY_COUNT, newTotal)
                StructuredLog.record(TAG, "今日累计: ${newTotal}g / ${userDailyTarget}g")
            }
            //如果从个人来的就回到个人
            if (needReturn) {

                val updateUserConfigStr = AntCooperateRpcCall.updateUserConfig(false)
                val userConfigJo = parseJsonObject(updateUserConfigStr)
                if (!ResChecker.checkRes(TAG, userConfigJo)) {
                    StructuredLog.record(TAG, "updateUserConfig 返回异常")
                    return
                }
                StructuredLog.record(TAG, "已返回个人模式")

            }

        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "teamCooperateWater 异常:", t)
        }
    }

    companion object {
        private val TAG: String = AntCooperate::class.java.getSimpleName()


        /**
         * 判断是否为团队
         *
         * @param homeObj 用户主页的JSON对象
         * @return 是否为团队
         */
        private fun isTeam(homeObj: JsonObject): Boolean {
            // 修复逻辑：
            // 如果 nextAction 是 "Team"，说明当前在个人主页（显示去组队的入口），因此不是团队模式，应返回 false
            // 如果 nextAction 是 "Cultivate"，说明当前在团队主页（显示去种树的入口），是团队模式，应返回 true
            return "Team" != homeObj.optString("nextAction", "")
        }

        /**
         * 合种浇水
         */
        private fun cooperateWater(coopId: String, count: Int, name: String) {
            try {
                val jo = parseJsonObject(AntCooperateRpcCall.cooperateWater(UserMap.currentUid, coopId, count))
                if (ResChecker.checkRes(TAG, jo)) {
                    StructuredLog.forest("合种浇水🚿[" + name + "]" + jo.getString("barrageText"))
                    Status.cooperateWaterToday(UserMap.currentUid, coopId)
                } else {
                    StructuredLog.error(TAG, "浇水失败[" + name + "]: " + jo.getString("resultDesc"))
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "cooperateWater err:", t)
            }
        }

        /**
         * 计算合种需要浇水的克数
         */
        private fun getTotalWatering(coopId: String?): Int {
            try {
                val jo = parseJsonObject(AntCooperateRpcCall.queryCooperateRank("A", coopId))
                if (ResChecker.checkRes(TAG, jo)) {
                    val jaList = jo.getJSONArray("cooperateRankInfos")
                    for (i in 0..<jaList.length()) {
                        val joItem = jaList.getJSONObject(i)
                        val userId = joItem.getString("userId")
                        if (userId == UserMap.currentUid) {
                            // 未获取到累计浇水量 返回 -1 不执行浇水
                            val energySummation = joItem.optInt("energySummation", -1)
                            if (energySummation >= 0) {
                                StructuredLog.record(TAG, "当前用户[$userId]的累计浇水能量: $energySummation")
                            }
                            return energySummation
                        }
                    }
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "计算合种需要浇水的克数err", t)
            }
            StructuredLog.error(TAG, "合种获取累计浇水量失败")
            return -1 // 未获取到累计浇水量，停止浇水
        }

        /**
         * 召唤队友浇水（仅队长）
         */
        private fun cooperateSendCooperateBeckon(cooperationId: String, name: String) {
            try {
                if (TimeUtil.isNowBeforeTimeStr("1800")) {
                    return
                }
                var jo = parseJsonObject(AntCooperateRpcCall.queryCooperateRank("D", cooperationId))
                if (ResChecker.checkRes(TAG, jo)) {
                    val cooperateRankInfos = jo.getJSONArray("cooperateRankInfos")
                    for (i in 0..<cooperateRankInfos.length()) {
                        val rankInfo = cooperateRankInfos.getJSONObject(i)
                        if (rankInfo.getBoolean("canBeckon")) {
                            jo = parseJsonObject(AntCooperateRpcCall.sendCooperateBeckon(rankInfo.getString("userId"), cooperationId))
                            if (ResChecker.checkRes(TAG, jo)) {
                                StructuredLog.forest("合种🚿[" + name + "]#召唤队友[" + rankInfo.getString("displayName") + "]成功")
                            }
                            TimeUtil.sleepCompat(300)
                        }
                    }
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "召唤队友和种错误：", t)
            }
        }
        init {
            ModelOrder.register(ModelDefinition(AntCooperate::class.java) { AntCooperate() })
        }
    }
}
