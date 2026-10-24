@file:Suppress("ClassName")

package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.entity.AlipayUser
import io.github.magisk317.sesame.entity.MapperEntity
import io.github.magisk317.sesame.entity.OtherEntityProvider.farmFamilyOption
import io.github.magisk317.sesame.entity.ParadiseCoinBenefit
import io.github.magisk317.sesame.hook.Toast
import io.github.magisk317.sesame.hook.rpc.intervallimit.RpcIntervalLimit.addIntervalLimit
import io.github.magisk317.sesame.model.BaseModel
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.ChoiceModelField
import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField
import io.github.magisk317.sesame.model.modelFieldExt.ListModelField.ListJoinCommaToStringModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.model.modelFieldExt.StringModelField
import io.github.magisk317.sesame.task.AnswerAI.AnswerAI
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.task.antForest.TaskTimeChecker
import io.github.magisk317.sesame.util.CoroutineUtils
import io.github.magisk317.sesame.util.toJsonArray
import io.github.magisk317.sesame.util.TaskRuntimeStore
import io.github.magisk317.sesame.task.GameTask
import io.github.magisk317.sesame.util.JsonUtil
import io.github.magisk317.sesame.util.ListUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getDouble
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optDouble
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optLong
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toStringList
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.StringUtil
import io.github.magisk317.sesame.util.TaskBlacklist
import io.github.magisk317.sesame.util.TimeCounter
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.ParadiseCoinBenefitIdMap
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.util.maps.VipDataIdMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar
import java.util.Locale
import java.util.Random
import kotlin.math.min
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

@Suppress("unused", "EnumEntryName", "EnumEntryName", "EnumEntryName", "EnumEntryName")
class AntFarm : ModelTask() {
    private val farmGameService = FarmGameService(
        allowDonationTasks = { doChouChouLeDonationTask?.value == true },
        allowAutoExchange = { autoExchange?.value == true },
    )
    private val farmActivityRewardService = FarmActivityRewardService()
    private val farmHomeSnapshotService = FarmHomeSnapshotService()
    private val farmKitchenService = FarmKitchenService(
        foodStock = { foodStock },
        foodStockLimit = { foodStockLimit },
        ensureAnimalAtHome = ::ensureAnimalAtHomeForKitchen,
    )
    private val farmHomeWorkflowService = FarmHomeWorkflowService(
        snapshotService = farmHomeSnapshotService,
        applySnapshot = { snapshot ->
            rewardProductNum = snapshot.rewardProductNum
            val farm = snapshot.farm
            foodStock = farm.getInt("foodStock")
            foodStockLimit = farm.getInt("foodStockLimit")
            harvestBenevolenceScore = farm.getDouble("harvestBenevolenceScore")
            parseSyncAnimalStatusResponse(farm)
            farm.getJSONObject("masterUserInfoVO").getString("userId")
        },
        claimActivityRewards = farmActivityRewardService::claimAll,
        shouldUseSpecialFood = { useSpecialFood!!.value },
        useSpecialFood = { cuisine ->
            if (AnimalFeedStatus.SLEEPY.name != ownerAnimal.animalFeedStatus) {
                farmKitchenService.useSpecialFood(cuisine)
            }
        },
        drawLotteryPlus = farmKitchenService::drawLotteryPlus,
        shouldAcceptGift = { acceptGift!!.value },
        canAcceptGift = { foodStockLimit - foodStock >= 10 },
        acceptGift = ::acceptGift,
    )
    private val farmChickenDiaryService = FarmChickenDiaryService()
    private val farmDonationService = FarmDonationService { harvestBenevolenceScore = it }
    private val farmTaskRewardService = FarmTaskRewardService(
        foodStock = { foodStock },
        foodStockLimit = { foodStockLimit },
        syncAnimalStatus = { syncAnimalStatus(ownerFarmId) },
        addFood = ::add2FoodStock,
        recordFarmGame = { recordFarmGame?.value == true },
        gameRewardMax = { gameRewardMax!!.value },
        ignoreAccelerateLimit = { ignoreAcceLimit!!.value },
        signRegardless = { signRegardless!!.value },
        onUnreceived = { unreceiveTaskAward++ },
        onReceived = { if (unreceiveTaskAward > 0) unreceiveTaskAward-- },
    )
    private val farmFeedService = FarmFeedService(
        foodStock = { foodStock },
        feedStatus = { ownerAnimal.animalFeedStatus },
        scheduleSendBack = { scheduleSendBackAfterFeed(ownerFarmId, it) },
        sendBackEnabled = { sendBackAnimal?.value == true },
        sendBackDelayMinutes = { timeSendBack!!.value },
        checkIntervalMinutes = { BaseModel.checkInterval.value },
    )
    private val farmToolService = FarmToolService(
        onToolsLoaded = { farmTools = it.toTypedArray() },
        isFenceActive = { hasFence },
        markFenceActive = {
            hasFence = true
            fenceCountDown = 86400
        },
        markAccelerateLimit = { Status.setFlagToday("farm::accelerateLimit") },
    )
    private val farmToolRewardService = FarmToolRewardService { farmTools }
    private val farmProduceService = FarmProduceService { harvestBenevolenceScore = it }
    private val farmFriendRewardService = FarmFriendRewardService(
        currentScore = { benevolenceScore },
        updateScore = { benevolenceScore = it },
        displayName = { UserMap.getMaskName(it) },
    )
    private val farmFriendNotificationService = FarmFriendNotificationService(
        foodStock = { foodStock },
        foodStockLimit = { foodStockLimit },
        notifyList = { notifyFriendList!!.value },
        notifyMode = { notifyFriendType!!.value },
        notifyEnabled = { notifyFriend!!.value },
        ownerFarmId = { ownerFarmId },
        onFoodStock = { amount, refresh ->
            if (refresh) foodStock = amount else add2FoodStock(amount)
        },
    )
    private val farmSleepService = FarmSleepService()
    private val farmAnimalStateService = FarmAnimalStateService()
    private val farmFriendAnimalStateService = FarmFriendAnimalStateService()
    private val farmStealingAnimalStateService = FarmStealingAnimalStateService()
    private val farmAnimalVisitService = FarmAnimalVisitService()
    private val farmVisitService = FarmVisitService(
        friends = { visitFriendList!!.value },
        foodStock = { foodStock },
        setFoodStock = { foodStock = it },
    )

    private val farmAnswerCacheSerializer = MapSerializer(String.serializer(), String.serializer())
    var foodStock: Int = 0
    var foodStockLimit: Int = 0
    private var antFarmReferToken: String? = null
    private var antFarmReferTokenUid: String? = null
    private var ownerFarmId: String? = null
    private var animals: Array<Animal>? = null
    private var ownerAnimal = Animal()
    private var rewardProductNum: String? = null
    private var rewardList: Array<RewardFriend>? = null
    private var countdown: Long? = null
    /**
     * 慈善评分
     */
    private var benevolenceScore = 0.0
    private var harvestBenevolenceScore = 0.0

    /**
     * 未领取的饲料奖励
     */
    private var unreceiveTaskAward = 0

    /**
     * 小鸡心情值
     */
    private var finalScore = 0.0
    private var farmTools: Array<FarmTool> = emptyArray()

    // 服务端返回的“是否已使用加饭卡”状态（从 subFarmVO.useBigEaterTool 解析）
    private var serverUseBigEaterTool: Boolean = false

    // 当前食槽上限（从 subFarmVO.foodInTroughLimit 解析，默认 180；使用加饭卡后为 360）
    private var foodInTroughLimitCurrent: Int = 180

    /**
     * 标记农场是否已满（用于雇佣小鸡逻辑）
     */
    private var isFarmFull: Boolean = false

    /**
     * 将服务端的饲喂状态代码转换为可读中文
     */
    private fun toFeedStatusName(status: String?): String {
        return when (status) {
            AnimalFeedStatus.HUNGRY.name -> "饥饿"
            AnimalFeedStatus.EATING.name -> "进食中"
            AnimalFeedStatus.SLEEPY.name -> "睡觉中"
            else -> status ?: "未知"
        }
    }

    override fun getName(): String {
        return "蚂蚁庄园"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.FARM
    }

    override fun getIcon(): String {
        return "AntFarm.png"
    }

    /**
     * 小鸡睡觉时间
     */
    private var sleepTime: StringModelField? = null

    // 起床时间
    private var wakeUpTime: StringModelField? = null

    /**
     * 小鸡睡觉时长
     */
    private var sleepMinutes: IntegerModelField? = null

    /**
     * 自动喂鸡
     */
    private var feedAnimal: BooleanModelField? = null

    /**
     * 打赏好友
     */
    private var rewardFriend: BooleanModelField? = null

    /**
     * 遣返小鸡
     */
    private var sendBackAnimal: BooleanModelField? = null
    private var timeSendBack: IntegerModelField? = null

    /**
     * 遣返方式
     */
    private var sendBackAnimalWay: ChoiceModelField? = null

    /**
     * 遣返动作
     */
    private var sendBackAnimalType: ChoiceModelField? = null

    /**
     * 遣返好友列表
     */
    private var sendBackAnimalList: SelectModelField? = null

    /**
     * 召回小鸡
     */
    private var recallAnimalType: ChoiceModelField? = null

    /**
     * s收取道具奖励
     */
    private var receiveFarmToolReward: BooleanModelField? = null

    /**
     * 游戏改分
     */
    private var recordFarmGame: BooleanModelField? = null
    private var gameRewardMax: IntegerModelField? = null

    /**
     * 小鸡游戏时间
     */
    private var farmGameTime: ListJoinCommaToStringModelField? = null

    /**
     * 小鸡厨房
     */
    private var kitchen: BooleanModelField? = null

    /**
     * 使用特殊食品
     */
    private var useSpecialFood: BooleanModelField? = null
    private var useNewEggCard: BooleanModelField? = null
    private var harvestProduce: BooleanModelField? = null
    private var donation: BooleanModelField? = null
    private var donationCount: ChoiceModelField? = null

    /**
     * 饲料任务
     */
    private var doFarmTask: BooleanModelField? = null // 做饲料任务
    private var doFarmTaskTime: StringModelField? = null // 饲料任务执行时间

    // 签到
    private var signRegardless: BooleanModelField? =null

    /**
     * 收取饲料奖励（无时间限制）
     */
    private var receiveFarmTaskAward: BooleanModelField? = null
    private var useAccelerateTool: BooleanModelField? = null
    private var ignoreAcceLimit: BooleanModelField? = null
    private var useBigEaterTool: BooleanModelField? = null // ✓ 新增加饭卡
    private var useAccelerateToolContinue: BooleanModelField? = null
    private var useAccelerateToolWhenMaxEmotion: BooleanModelField? = null

    /**
     * 喂鸡列表
     */
    private var feedFriendAnimalList: SelectAndCountModelField? = null
    private var notifyFriend: BooleanModelField? = null
    private var notifyFriendType: ChoiceModelField? = null
    private var notifyFriendList: SelectModelField? = null
    private var acceptGift: BooleanModelField? = null
    private var visitFriendList: SelectAndCountModelField? = null
    private var chickenDiary: BooleanModelField? = null
    private var diaryTietie: BooleanModelField? = null
    private var collectChickenDiary: ChoiceModelField? = null
    private lateinit var remainingTime: IntegerModelField
    private var enableChouchoule: BooleanModelField? = null
    private var enableChouchouleTime: StringModelField? = null // 抽抽乐执行时间
    var autoExchange: BooleanModelField? = null
    var doChouChouLeDonationTask: BooleanModelField? = null
    private var listOrnaments: BooleanModelField? = null
    private var hireAnimal: BooleanModelField? = null
    private var hireAnimalType: ChoiceModelField? = null
    private var hireAnimalList: SelectModelField? = null
    private var enableDdrawGameCenterAward: BooleanModelField? = null
    private var getFeed: BooleanModelField? = null
    private var getFeedlList: SelectModelField? = null
    private var getFeedType: ChoiceModelField? = null
    private var family: BooleanModelField? = null
    private var familyOptions: SelectModelField? = null
    private var notInviteList: SelectModelField? = null
    private var paradiseCoinExchangeBenefit: BooleanModelField? = null
    private var paradiseCoinExchangeBenefitList: SelectModelField? = null

    private var visitAnimal: BooleanModelField? = null
    private var useSmartSchedulerManager: BooleanModelField? = null
    private var hasFence: Boolean = false       // 是否正在使用篱笆
    private var fenceCountDown: Int = 0
    // 雇佣NPC
    private var npcAnimalType: ChoiceModelField? = null
    private val npcService = FarmNpcService(::syncAnimalStatus)
    private val farmAutoFeedService = FarmAutoFeedService(
        currentState = { FarmAutoFeedService.State(ownerAnimal.animalInteractStatus, ownerAnimal.animalFeedStatus) },
        foodStock = { foodStock },
        receiveAwards = ::receiveFarmAwards,
        feed = { feedAnimal(ownerFarmId) },
        sync = { syncAnimalStatus(ownerFarmId) },
        useBigEater = ::useBigEaterTool,
        useAccelerate = {
            StructuredLog.record(
                TAG,
                "加速卡判断⏩[动物状态=${toFeedStatusName(ownerAnimal.animalFeedStatus)}, 今日封顶=${Status.hasFlagToday("farm::accelerateLimit")} ]",
            )
            useAccelerateTool()
        },
        runFarmGame = ::handleFarmGameLogic,
        runChouChouLe = ::handleChouChouLeLogic,
        rewardEnabled = { receiveFarmTaskAward?.value == true },
        feedEnabled = { feedAnimal?.value == true },
        bigEaterEnabled = { useBigEaterTool?.value == true },
        accelerateEnabled = { useAccelerateTool?.value == true },
        recordFarmGameEnabled = { recordFarmGame?.value == true },
        chouChouLeEnabled = { enableChouchoule?.value == true },
    )
    private val farmAccelerateService = FarmAccelerateService(
        state = {
            val currentAnimals = animals.orEmpty()
            FarmAccelerateService.State(
                countdownSeconds = countdown?.toDouble() ?: 0.0,
                totalConsumeSpeed = currentAnimals.sumOf { (it.consumeSpeed ?: 0.0) },
                ownFoodConsumePerHour = (ownerAnimal.consumeSpeed ?: 0.0) * 60 * 60,
                finalScore = finalScore,
                foodStock = foodStock,
                foodStockLimit = foodStockLimit,
                ownerFeedStatus = ownerAnimal.animalFeedStatus,
                ownerBuff = ownerAnimal.animalBuff,
            )
        },
        canUse = { Status.canUseAccelerateTool() },
        markLimit = { Status.setFlagToday("farm::accelerateLimit") },
        useTool = { useFarmTool(ownerFarmId, ToolType.ACCELERATETOOL) },
        recordUse = { Status.useAccelerateTool() },
        usedCount = { Status.INSTANCE.useAccelerateToolCount },
        sync = { syncAnimalStatus(ownerFarmId) },
        feed = { feedAnimal(ownerFarmId) },
        receiveAwards = ::receiveFarmAwards,
        continueEnabled = { useAccelerateToolContinue?.value == true },
        maxEmotionEnabled = { useAccelerateToolWhenMaxEmotion?.value == true },
        remainingTimeMinutes = { remainingTime.value },
        gameFinished = { Status.hasFlagToday("farm::farmGameFinished") },
        foodStock = { foodStock },
        foodStockLimit = { foodStockLimit },
        gameRewardMax = { gameRewardMax?.value ?: 0 },
    )
    private val farmGameWorkflowService = FarmGameWorkflowService(
        gameFinished = { Status.hasFlagToday("farm::farmGameFinished") },
        accelerateEnabled = { useAccelerateTool?.value == true },
        accelerateLimitReached = { Status.hasFlagToday("farm::accelerateLimit") || !Status.canUseAccelerateTool() },
        syncFarmState = { syncAnimalStatus(ownerFarmId) },
        insideGameTimeRange = { farmGameTime?.value?.any { TimeUtil.checkNowInTimeRange(it) } == true },
        ignoreAccelerateLimit = { ignoreAcceLimit?.value == true },
        accelerateCardCount = { accelerateToolCount },
        usedAccelerateCardCount = { Status.INSTANCE.useAccelerateToolCount },
        foodStock = { foodStock },
        foodStockLimit = { foodStockLimit },
        gameRewardMax = { gameRewardMax?.value ?: 0 },
        taskEnabled = { doFarmTask?.value == true },
        taskFinished = { Status.hasFlagToday("farm::farmTaskFinished") },
        receiveAwards = ::receiveFarmAwards,
        playGames = farmGameService::playAllGames,
        chouChouLeFinished = { Status.hasFlagToday("farm::chouChouLeFinished") },
        gameEnabledForChouChouLe = { recordFarmGame?.value == true },
        chouChouLeTimeReached = { TaskTimeChecker.isTimeReached(enableChouchouleTime?.value, "0900") },
        playChouChouLe = farmGameService::playChouChouLe,
    )
    private val farmFriendFeedService = FarmFriendFeedService(
        targets = { feedFriendAnimalList?.value ?: emptyMap() },
        currentUserId = { UserMap.currentUid },
        autoFeedEnabled = { feedAnimal?.value == true },
        canFeedToday = { userId, maxCount -> Status.canFeedFriendToday(userId, maxCount) },
        enterFarm = { userId -> FarmCoreRpc.enterFarm(userId, userId).toJsonObject() },
        parseTarget = farmFriendAnimalStateService::parse,
        foodStock = { foodStock },
        pendingAwardCount = { unreceiveTaskAward },
        receiveAwards = { receiveFarmAwards() },
        feed = { farmId -> FarmCoreRpc.feedFriendAnimal(farmId).toJsonObject() },
        setFoodStock = { foodStock = it },
        markFedToday = { farmId -> Status.feedFriendToday(FarmCoreRpc.farmId2UserId(farmId)) },
        hasReachedLimit = { Status.hasFlagToday("farm::feedFriendLimit") },
        markReachedLimit = { Status.setFlagToday("farm::feedFriendLimit") },
        displayName = { userId -> UserMap.getMaskName(userId) },
        warnSelfConflict = { maxCount ->
            Toast.show(
                "⚠️ 配置冲突提醒\n" +
                    "已开启「自动喂小鸡」，将使用蹲点机制（精准时间）\n" +
                    "好友列表中的自己（配置${maxCount}次）已被忽略\n" +
                    "建议：无需在好友列表中添加自己",
            )
        },
    )
    private val farmGameCenterService = FarmGameCenterService(
        queryList = { FarmGameMallRpc.queryGameList().toJsonObject() },
        drawAward = { FarmGameMallRpc.drawGameCenterAward(1).toJsonObject() },
        reportTask = GameTask.Farm_ddply::report,
    )
    private val farmHireService = FarmHireService(
        enterOwnFarm = ::enterFarm,
        foodStock = { foodStock },
        selectedUsers = { hireAnimalList?.value ?: emptySet() },
        excludeSelectedUsers = { hireAnimalType?.value == HireAnimalType.DONT_HIRE },
        currentUserId = { UserMap.currentUid },
        rankingPage = { pageStart -> FarmCoreRpc.rankingList(pageStart).toJsonObject() },
        hireAction = { userId -> hireAnimalAction(userId) },
        isFarmFull = { isFarmFull },
        resetFarmFull = { isFarmFull = false },
        scheduleExpiry = { animalId, endTime ->
            val taskId = "HIRE|$animalId"
            if (!hasChildTask(taskId)) {
                addChildTask(
                    ChildModelTask(
                        taskId,
                        "HIRE",
                        suspendRunnable = { this.hireAnimal() },
                        endTime,
                    ),
                )
            } else {
                addChildTask(
                    ChildModelTask(
                        taskId,
                        "HIRE",
                        suspendRunnable = { this.hireAnimal() },
                        endTime,
                    ),
                )
            }
            StructuredLog.record(TAG, "添加蹲点雇佣👷在[${TimeUtil.getCommonDate(endTime)}]执行")
        },
        showConfigurationWarning = {
            Toast.show(
                "⚠️ 雇佣小鸡配置错误\n" +
                    "已开启「雇佣小鸡」但未配置雇佣好友列表\n" +
                    "请在「雇佣小鸡 | 好友列表」中勾选好友",
            )
        },
    )

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(
            ChoiceModelField(
                "recallAnimalType",
                "召回小鸡",
                RecallAnimalType.ALWAYS,
                RecallAnimalType.nickNames
            ).also { recallAnimalType = it })
        modelFields.addField(
            BooleanModelField(
                "feedAnimal",
                "自动喂小鸡",
                false
            ).also { feedAnimal = it })
        modelFields.addField(
            BooleanModelField(
                "doFarmTask",
                "做饲料任务",
                false
            ).also { doFarmTask = it })
        modelFields.addField(
            StringModelField(
                "doFarmTaskTime",
                "饲料任务执行时间 | 默认8:30后执行",
                "0830"
            ).also { doFarmTaskTime = it })

        modelFields.addField(
            BooleanModelField(
                "receiveFarmTaskAward",
                "收取饲料奖励",
                false
            ).also { receiveFarmTaskAward = it })
        modelFields.addField(
            BooleanModelField(
                "useBigEaterTool",
                "加饭卡 | 使用",
                false
            ).also { useBigEaterTool = it })
        modelFields.addField(
            BooleanModelField(
                "useAccelerateTool",
                "加速卡 | 使用",
                false
            ).also { useAccelerateTool = it })
        modelFields.addField(
            BooleanModelField(
                "useAccelerateToolContinue",
                "加速卡 | 连续使用",
                false
            ).also { useAccelerateToolContinue = it })
        modelFields.addField(
            IntegerModelField("remainingTime", "饲料剩余时间大于多少时直接使用加速（分钟）（-1关闭）", 40).also { remainingTime = it }
        )
        modelFields.addField(
            BooleanModelField(
                "useAccelerateToolWhenMaxEmotion",
                "加速卡 | 仅在满状态时使用",
                false
            ).also { useAccelerateToolWhenMaxEmotion = it })
        modelFields.addField(
            BooleanModelField(
                "ignoreAcceLimit",
                "按设置的时间进行游戏改分和抽抽乐",
                false
            ).also { ignoreAcceLimit = it })
        modelFields.addField(
            BooleanModelField(
                "enableChouchoule",
                "开启小鸡抽抽乐",
                false
            ).also { enableChouchoule = it })
        modelFields.addField(
            BooleanModelField(
                "autoExchange",
                "IP抽抽乐自动从高到低兑换物品",
                false
            ).also { autoExchange = it })
        modelFields.addField(
            StringModelField(
                "enableChouchouleTime",
                "小鸡抽抽乐执行时间 | 默认9:00后执行",
                "0900"
            ).also { enableChouchouleTime = it })
        modelFields.addField(
            BooleanModelField(
                "recordFarmGame",
                "游戏改分(星星球、登山赛、飞行赛、揍小鸡)",
                false
            ).also { recordFarmGame = it })
        modelFields.addField(
            IntegerModelField("gameRewardMax", "游戏改分预计最大产出饲料量(g)", 180, 0, null).also { gameRewardMax = it }
        )
        modelFields.addField(
            ListJoinCommaToStringModelField(
                "farmGameTime",
                "小鸡游戏时间(范围)",
                ListUtil.newArrayList("2200-2400")
            ).also { farmGameTime = it })
        modelFields.addField(
            BooleanModelField(
                "enableDdrawGameCenterAward",
                "开宝箱",
                false
            ).also { enableDdrawGameCenterAward = it })
        modelFields.addField(
            StringModelField(
                "sleepTime",
                "小鸡睡觉时间(关闭:-1)",
                "2330"
            ).also { sleepTime = it })
        modelFields.addField(
            StringModelField(
                "wakeupTime",
                "小鸡起床时间(关闭:-1)",
                "0530"
            ).also { wakeUpTime = it })
        modelFields.addField(
            SelectAndCountModelField(
                "feedFriendAnimalList",
                "帮喂小鸡 | 好友列表",
                LinkedHashMap<String?, Int?>(),
                { AlipayUser.getList() },
                "记得设置帮喂次数.."
            ).also {
                feedFriendAnimalList = it
            })
        modelFields.addField(
            BooleanModelField(
                "rewardFriend",
                "打赏好友",
                false
            ).also { rewardFriend = it })
        modelFields.addField(BooleanModelField("getFeed", "一起拿饲料", false).also {
            getFeed = it
        })
        modelFields.addField(
            ChoiceModelField(
                "getFeedType",
                "一起拿饲料 | 动作",
                GetFeedType.GIVE,
                GetFeedType.nickNames
            ).also { getFeedType = it })
        modelFields.addField(
            SelectModelField(
                "getFeedlList",
                "一起拿饲料 | 好友列表",
                LinkedHashSet<String?>()
            ) { AlipayUser.getList() }.also {
                getFeedlList = it
            })
        modelFields.addField(BooleanModelField("acceptGift", "收麦子", false).also {
            acceptGift = it
        })
        modelFields.addField(
            SelectAndCountModelField(
                "visitFriendList",
                "送麦子好友列表",
                LinkedHashMap<String?, Int?>(),
                { AlipayUser.getList() },
                "设置赠送次数？？"
            ).also {
                visitFriendList = it
            })
        modelFields.addField(
            BooleanModelField(
                "hireAnimal",
                "雇佣小鸡 | 开启",
                false
            ).also { hireAnimal = it })
        modelFields.addField(
            ChoiceModelField(
                "hireAnimalType",
                "雇佣小鸡 | 动作",
                HireAnimalType.DONT_HIRE,
                HireAnimalType.nickNames
            ).also { hireAnimalType = it })
        modelFields.addField(
            SelectModelField(
                "hireAnimalList",
                "雇佣小鸡 | 好友列表",
                LinkedHashSet<String?>()
            ) { AlipayUser.getList() }.also {
                hireAnimalList = it
            })
        modelFields.addField(
            ChoiceModelField(
                "npcAnimalType",
                "雇佣NPC小鸡(满产自动重雇)",
                FarmNpcConfig.NONE.ordinal,
                FarmNpcConfig.nickNames
            ).also { npcAnimalType = it })
        modelFields.addField(
            BooleanModelField(
                "sendBackAnimal",
                "遣返 | 开启",
                false
            ).also { sendBackAnimal = it })
        modelFields.addField(
            IntegerModelField("timeSendBack", "投喂饲料后间隔时间赶鸡(分,<10关闭)", 0, 0, null).also { timeSendBack = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "sendBackAnimalWay",
                "遣返 | 方式",
                SendBackAnimalWay.NORMAL,
                SendBackAnimalWay.nickNames
            ).also { sendBackAnimalWay = it })
        modelFields.addField(
            ChoiceModelField(
                "sendBackAnimalType",
                "遣返 | 动作",
                SendBackAnimalType.NOT_BACK,
                SendBackAnimalType.nickNames
            ).also { sendBackAnimalType = it })
        modelFields.addField(
            SelectModelField(
                "dontSendFriendList",
                "遣返 | 好友列表",
                LinkedHashSet<String?>()
            ) { AlipayUser.getList() }.also {
                sendBackAnimalList = it
            })
        modelFields.addField(
            BooleanModelField(
                "notifyFriend",
                "通知赶鸡 | 开启",
                false
            ).also { notifyFriend = it })
        modelFields.addField(
            ChoiceModelField(
                "notifyFriendType",
                "通知赶鸡 | 动作",
                NotifyFriendType.NOTIFY,
                NotifyFriendType.nickNames
            ).also { notifyFriendType = it })
        modelFields.addField(
            SelectModelField(
                "notifyFriendList",
                "通知赶鸡 | 好友列表",
                LinkedHashSet<String?>()
            ) { AlipayUser.getList() }.also {
                notifyFriendList = it
            })
        modelFields.addField(
            BooleanModelField(
                "donation",
                "每日捐蛋 | 开启",
                false
            ).also { donation = it })
        modelFields.addField(
            ChoiceModelField(
                "donationCount",
                "每日捐蛋 | 次数",
                DonationCount.ONE,
                DonationCount.nickNames
            ).also { donationCount = it })
        modelFields.addField(
            BooleanModelField(
                "useSpecialFood",
                "使用特殊食品",
                false
            ).also { useSpecialFood = it })
        modelFields.addField(
            BooleanModelField(
                "useNewEggCard",
                "使用新蛋卡",
                false
            ).also { useNewEggCard = it })
        modelFields.addField(
            BooleanModelField(
                "signRegardless",
                "庄园签到忽略饲料余量",
                true
            ).also { signRegardless = it })
        modelFields.addField(
            BooleanModelField(
                "receiveFarmToolReward",
                "收取道具奖励",
                false
            ).also { receiveFarmToolReward = it })
        modelFields.addField(
            BooleanModelField(
                "harvestProduce",
                "收获爱心鸡蛋",
                false
            ).also { harvestProduce = it })
        modelFields.addField(BooleanModelField("kitchen", "小鸡厨房", false).also { kitchen = it })
        modelFields.addField(
            BooleanModelField(
                "chickenDiary",
                "小鸡日记",
                false
            ).also { chickenDiary = it })
        modelFields.addField(
            BooleanModelField(
                "diaryTietze",
                "小鸡日记 | 贴贴",
                false
            ).also { diaryTietie = it })
        modelFields.addField(
            ChoiceModelField(
                "collectChickenDiary",
                "小鸡日记 | 点赞",
                collectChickenDiaryType.ONCE,
                collectChickenDiaryType.nickNames
            ).also { collectChickenDiary = it })
        modelFields.addField(
            BooleanModelField(
                "listOrnaments",
                "小鸡每日换装",
                false
            ).also { listOrnaments = it })
        modelFields.addField(BooleanModelField("family", "家庭 | 开启", false).also { family = it })
        modelFields.addField(
            SelectModelField(
                "familyOptions",
                "家庭 | 选项",
                LinkedHashSet<String?>(),
                farmFamilyOption()
            ).also { familyOptions = it })
        modelFields.addField(
            SelectModelField(
                "notInviteList",
                "家庭 | 好友分享排除列表",
                LinkedHashSet<String?>()
            ) { AlipayUser.getList() }.also {
                notInviteList = it
            })
        modelFields.addField(
            BooleanModelField(
                "paradiseCoinExchangeBenefit",
                "小鸡乐园 | 兑换权益",
                false
            ).also { paradiseCoinExchangeBenefit = it })
        modelFields.addField(
            SelectModelField(
                "paradiseCoinExchangeBenefitList",
                "小鸡乐园 | 权益列表",
                LinkedHashSet<String?>()
            ) { ParadiseCoinBenefit.getList() }.also {
                paradiseCoinExchangeBenefitList = it
            })
        modelFields.addField(
            BooleanModelField(
                "visitAnimal",
                "到访小鸡送礼",
                false
            ).also { visitAnimal = it })
        modelFields.addField(
            BooleanModelField(
                "useSmartSchedulerManager",
                "使用SmartSchedulerManager定时蹲点任务",
                false
            ).also { useSmartSchedulerManager = it })
        modelFields.addField(
            BooleanModelField(
                "doChouChouLeDonationTask",
                "抽抽乐捐赠任务(禁止开启)",
                false
            ).also { doChouChouLeDonationTask = it })
        return modelFields
    }

    override fun boot(classLoader: ClassLoader?) {
        super.boot(classLoader)
        addIntervalLimit("com.alipay.antfarm.enterFarm", 2000)
    }

    override suspend fun runSuspend() {
        try {
            val tc = TimeCounter(TAG)
            val userId = UserMap.currentUid

            if (enterFarm() == null) {
                return
            }
            //先遣返，再雇佣，喂鸡
            if (sendBackAnimal!!.value) {
                sendBackAnimal()
                tc.countDebug("遣返")
            }
            // 雇佣小鸡
            if (hireAnimal!!.value && AnimalFeedStatus.SLEEPY.name != ownerAnimal.animalFeedStatus) {
                hireAnimal()
            }

            // 处理NPC小鸡逻辑 (大表鸽/黄金鸡/农场鸡)
            if (npcAnimalType!!.value != FarmNpcConfig.NONE.ordinal) {
                handleNpcAnimalLogic()
                tc.countDebug("NPC小鸡任务")
            }

            /* 为保证单次运行程序可以完成全部任务，而加速卡用完会消耗最多360g饲料，如果差360g满饲料，那肯定不能执行
                游戏改分了，需要先把饲料任务完成，方便在连续用加速卡逻辑中领取饲料。
             */
            if (doFarmTask!!.value) {
                // 这里设置判断，如果当日完成过一次饲料任务了，就不会在这个位置再进行饲料任务了。
                if(!Status.hasFlagToday("farm::farmTaskFinished")) {
                    // 检查是否到达执行时间
                    if (TaskTimeChecker.isTimeReached(doFarmTaskTime?.value, "0830")) {
                        doFarmTasks()
                        tc.countDebug("饲料任务")
                        Status.setFlagToday("farm::farmTaskFinished")
                    } else {
                        StructuredLog.record(TAG, "饲料任务未到执行时间，跳过")
                    }
                }
            }

            handleAutoFeedAnimal()
            tc.countDebug("喂食")

            recallAnimal()
            tc.countDebug("召回小鸡")

            listFarmTool() //装载道具信息
            tc.countDebug("装载道具信息")

            if (rewardFriend!!.value) {
                rewardFriend()
                tc.countDebug("打赏好友")
            }

            if (receiveFarmToolReward!!.value) {
                receiveToolTaskReward()
                tc.countDebug("收取道具奖励")
            }
            if (recordFarmGame!!.value) {
                tc.countDebug("游戏改分(星星球、登山赛、飞行赛、揍小鸡)")
                handleFarmGameLogic()
            }

            // 小鸡日记和贴贴
            if (chickenDiary!!.value) {
                farmChickenDiaryService.run(diaryTietie!!.value, collectChickenDiary!!.value)
                tc.countDebug("小鸡日记")
            }

            if (kitchen!!.value) {
                // 检查小鸡是否在睡觉，如果在睡觉则跳过厨房功能
                if (AnimalFeedStatus.SLEEPY.name == ownerAnimal.animalFeedStatus) {
                    StructuredLog.record(TAG, "小鸡厨房🐔[小鸡正在睡觉中，跳过厨房功能]")
                } else {
                    farmKitchenService.collectDailyFoodMaterial()
                    farmKitchenService.collectDailyLimitedFoodMaterial()
                    farmKitchenService.cook()
                }
                tc.countDebug("小鸡厨房")
            }

            if (useNewEggCard!!.value) {
                useFarmTool(ownerFarmId, ToolType.NEWEGGTOOL)
                syncAnimalStatus(ownerFarmId)
                tc.countDebug("使用新蛋卡")
            }
            if (harvestProduce!!.value && benevolenceScore >= 1) {
                StructuredLog.record(TAG, "有可收取的爱心鸡蛋")
                harvestProduce(ownerFarmId)
                tc.countDebug("收鸡蛋")
            }
            if (donation!!.value && Status.canDonationEgg(userId) && harvestBenevolenceScore >= 1) {
                handleDonation(donationCount!!.value)
                tc.countDebug("每日捐蛋")
                StructuredLog.farm("今日捐蛋完成")
            }

            // 收取饲料奖励（无时间限制）
            if (receiveFarmTaskAward!!.value) {
                receiveFarmAwards()
                tc.countDebug("收取饲料奖励")
            }

            // 到访小鸡送礼
            if (visitAnimal!!.value) {
                visitAnimal()
                tc.countDebug("到访小鸡送礼")
                // 送麦子
                visit()
                tc.countDebug("送麦子")
            }
            // 帮好友喂鸡
            feedFriend()
            tc.countDebug("帮好友喂鸡")
            // 通知好友赶鸡
            if (notifyFriend!!.value) {
                notifyFriend()
                tc.countDebug("通知好友赶鸡")
            }

            // 抽抽乐
            if (enableChouchoule!!.value) {
                tc.countDebug("抽抽乐")
                handleChouChouLeLogic()
            }

            if (getFeed!!.value) {
                letsGetChickenFeedTogether()
                tc.countDebug("一起拿饲料")
            }
            //家庭
            if (family!!.value) {
                AntFarmFamily.run(familyOptions!!, notInviteList!!)
                tc.countDebug("家庭任务")
            }
            // 开宝箱
            if (enableDdrawGameCenterAward!!.value) {
                drawGameCenterAward()
                tc.countDebug("开宝箱")
            }
            // 小鸡乐园道具兑换
            if (paradiseCoinExchangeBenefit!!.value) {
                FarmParadiseCoinService.run(paradiseCoinExchangeBenefitList!!.value)
                tc.countDebug("小鸡乐园道具兑换")
            }
            //小鸡睡觉&起床
            animalSleepAndWake()
            tc.countDebug("小鸡睡觉&起床")

            /* 小鸡睡觉后领取饲料，先同步小鸡状态，更新小鸡为SLEEPY状态，然后领取饲料。避免小鸡睡觉后软件异常，引起
                喂小鸡睡觉的饲料没有领取，而造成缺口
             */
            syncAnimalStatus(ownerFarmId)
            if (AnimalFeedStatus.SLEEPY.name == ownerAnimal.animalFeedStatus) {
                StructuredLog.record(TAG, "小鸡正在睡觉，领取饲料")
                receiveFarmAwards()
            }

            tc.stop()
        } catch (e: CancellationException) {
            // 协程取消是正常现象，不记录为错误
             StructuredLog.record(TAG, "AntFarm 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "AntFarm.start.run err:",t)
        }
    }

    /** Ensures kitchen RPCs only run after the owner's chicken is home. */
    private fun ensureAnimalAtHomeForKitchen(): Boolean {
        return try {
            if (AnimalInteractStatus.HOME.name == ownerAnimal.animalInteractStatus) return true
            refreshOwnerAnimalFromHome()
            if (AnimalInteractStatus.HOME.name == ownerAnimal.animalInteractStatus) return true

            val status = ownerAnimal.animalInteractStatus
            if (status == null) {
                // 仍然拿不到 interact 状态（精简同步响应不含 animalStatusVO）：
                // 不再阻塞厨房，交给 enterKitchen 的 312 召回重试兜底——此前 null
                // 会被当成"不在家"，导致厨房 RPC 被永久跳过、做菜停摆。
                StructuredLog.record(TAG, "小鸡在家状态未知，交由厨房RPC 312兜底")
                return true
            }

            recallAnimalForKitchen()
            // 召回在服务端异步生效，复查轮询数次再放弃。
            for (attempt in 1..KITCHEN_RECALL_POLL_TRIES) {
                TimeUtil.sleepCompat(KITCHEN_RECALL_POLL_INTERVAL_MS)
                refreshOwnerAnimalFromHome()
                if (AnimalInteractStatus.HOME.name == ownerAnimal.animalInteractStatus) return true
            }
            StructuredLog.record(
                TAG,
                "小鸡未回到家(interact=$status，" +
                    "feed=${ownerAnimal.animalFeedStatus ?: "null"}，" +
                    "subType=${ownerAnimal.subAnimalType ?: "null"})，跳过厨房 RPC",
            )
            false
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "确认小鸡在家失败，跳过厨房 RPC: ${t.javaClass.simpleName}")
            false
        }
    }

    /** enterFarm 全量响应携带 animalStatusVO；用它刷新小鸡状态，避免精简同步把已知状态洗成 null。 */
    private fun refreshOwnerAnimalFromHome(): Boolean {
        return try {
            val userId = UserMap.currentUid ?: return false
            val jo = FarmCoreRpc.enterFarm(userId, userId).toJsonObject()
            val farmVO = jo.optJSONObject("farmVO") ?: return false
            val state = farmAnimalStateService.parse(farmVO) ?: return false
            ownerAnimal = state.ownerAnimal
            true
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "refreshOwnerAnimalFromHome err:", t)
            false
        }
    }

    private fun recallAnimalForKitchen() {
        if ("ORCHARD" == ownerAnimal.locationType) {
            FarmPropRpc.orchardRecallAnimal(
                ownerAnimal.animalId,
                ownerAnimal.currentFarmMasterUserId,
            )
        } else {
            recallAnimal(
                ownerAnimal.animalId,
                ownerAnimal.currentFarmId,
                ownerFarmId,
                UserMap.getMaskName(FarmCoreRpc.farmId2UserId(ownerAnimal.currentFarmId)),
            )
        }
    }

    /**
     * 召回小鸡
     */
    private fun recallAnimal() {
        try {
            //召回小鸡相关操作
            if (AnimalInteractStatus.HOME.name != ownerAnimal.animalInteractStatus) { //如果小鸡不在家
                if ("ORCHARD" == ownerAnimal.locationType) {
                    StructuredLog.farm("庄园通知📣[你家的小鸡给拉去除草了！]")
                    val joRecallAnimal = FarmPropRpc.orchardRecallAnimal(
                        ownerAnimal.animalId,
                        ownerAnimal.currentFarmMasterUserId
                    ).toJsonObject()
                    val manureCount = joRecallAnimal.getInt("manureCount")
                    StructuredLog.farm("召回小鸡📣[收获:肥料" + manureCount + "g]")
                } else {
                    StructuredLog.record(TAG, "DEBUG:$ownerAnimal")

                    syncAnimalStatus(ownerFarmId)
                    var guest = false
                    when (SubAnimalType.valueOf(ownerAnimal.subAnimalType!!)) {
                        SubAnimalType.GUEST -> {
                            guest = true
                            StructuredLog.record(TAG, "小鸡到好友家去做客了")
                        }

                        SubAnimalType.NORMAL -> StructuredLog.record(TAG, "小鸡太饿，离家出走了")
                        SubAnimalType.PIRATE -> StructuredLog.record(TAG, "小鸡外出探险了")
                        SubAnimalType.WORK -> StructuredLog.record(TAG, "小鸡出去工作啦")
                    }
                    var hungry = false
                    val userName =
                        UserMap.getMaskName(FarmCoreRpc.farmId2UserId(ownerAnimal.currentFarmId))
                    val feedStatus = ownerAnimal.animalFeedStatus?.let {
                        runCatching { AnimalFeedStatus.valueOf(it) }.getOrNull()
                    }
                    when (feedStatus) {
                        AnimalFeedStatus.HUNGRY -> {
                            hungry = true
                            StructuredLog.record(TAG, "小鸡在[$userName]的庄园里挨饿")
                        }

                        AnimalFeedStatus.EATING -> StructuredLog.record(
                            TAG,
                            "小鸡在[$userName]的庄园里吃得津津有味"
                        )
                        AnimalFeedStatus.SLEEPY -> StructuredLog.record(TAG, "小鸡在[$userName]的庄园里睡觉")
                        AnimalFeedStatus.NONE -> StructuredLog.record(TAG, "小鸡在[$userName]的庄园里状态未知")
                        null -> StructuredLog.record(TAG, "小鸡在[$userName]的喂食状态缺失，按未知处理")
                    }
                    val recall = when (recallAnimalType!!.value) {
                        RecallAnimalType.ALWAYS -> true
                        RecallAnimalType.WHEN_THIEF -> !guest
                        RecallAnimalType.WHEN_HUNGRY -> hungry
                        else -> false
                    }
                    if (recall) {
                        recallAnimal(
                            ownerAnimal.animalId,
                            ownerAnimal.currentFarmId,
                            ownerFarmId,
                            userName
                        )
                        syncAnimalStatus(ownerFarmId)
                    }
                }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "recallAnimal err:", e)
        }
    }

    private fun animalSleepAndWake() {
        try {
            val sleepTimeStr = sleepTime!!.value
            if ("-1" == sleepTimeStr) {
                StructuredLog.record(TAG, "当前已关闭小鸡睡觉")
                return
            }
            val now = TimeUtil.getNow()
            val animalSleepTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(sleepTimeStr)
            if (animalSleepTimeCalendar == null) {
                StructuredLog.record(TAG, "小鸡睡觉时间格式错误，请重新设置")
                return
            }

            val wakeUpTimeStr = wakeUpTime!!.value
            if ("-1" == wakeUpTimeStr) {
                StructuredLog.record(TAG, "当前已关闭小鸡起床")
            }

            var animalWakeUpTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(wakeUpTimeStr)
            if(animalWakeUpTimeCalendar == null) {
                StructuredLog.record(TAG, "小鸡起床时间格式错误，请重新设置，否则默认关闭")
                animalWakeUpTimeCalendar = TimeUtil.getTodayCalendarByTimeStr("0600")
            }
            val sixAmToday = TimeUtil.getTodayCalendarByTimeStr("0600") ?: return
            animalWakeUpTimeCalendar ?: return
            if (now.after(sixAmToday)) {
                animalWakeUpTimeCalendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            val animalWakeUpTime = animalWakeUpTimeCalendar.timeInMillis
            val animalSleepTime = animalSleepTimeCalendar.timeInMillis
            val afterSleepTime = now > animalSleepTimeCalendar
            val afterWakeUpTime = now > animalWakeUpTimeCalendar
            val afterSixAm = now >= sixAmToday

            if (afterSleepTime && afterWakeUpTime) {
                if (!Status.canAnimalSleep()) {
                    return
                }
                StructuredLog.record(TAG, "已错过小鸡今日睡觉时间")
                return
            }
            val sleepTaskId = "AS|$animalSleepTime"
            val wakeUpTaskId = "AW|$animalWakeUpTime"
            if (!hasChildTask(sleepTaskId) && !afterSleepTime) {
                addChildTask(
                    ChildModelTask(
                        sleepTaskId,
                        "AS",
                        suspendRunnable = {
                            this.animalSleepNow()
                            syncAnimalStatus(ownerFarmId)
                            receiveFarmAwards()
                        },
                        animalSleepTime
                    )
                )
                StructuredLog.record(
                    TAG,
                    "添加定时睡觉🛌[" + UserMap.getCurrentMaskName() + "]在[" + TimeUtil.getCommonDate(
                        animalSleepTime
                    ) + "]执行"
                )
            }
            if (!hasChildTask(wakeUpTaskId) && !afterWakeUpTime) {
                addChildTask(
                    ChildModelTask(
                        wakeUpTaskId,
                        "AW",
                        suspendRunnable = { this.animalWakeUpNow() },
                        animalWakeUpTime
                    )
                )
                StructuredLog.record(
                    TAG,
                    "添加定时起床🛌[" + UserMap.getCurrentMaskName() + "]在[" + TimeUtil.getCommonDate(
                        animalWakeUpTime
                    ) + "]执行"
                )
            }
            if (afterSleepTime) {
                if (Status.canAnimalSleep()) {
                    animalSleepNow()
                }
            }
            if (afterWakeUpTime && !afterSixAm) {
                if (Status.canAnimalSleep()) {
                    animalWakeUpNow()
                }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG,"animalSleepAndWake err:",e)
        }
    }

    /**
     * 初始化庄园
     *
     * @return 庄园信息
     */
    private fun enterFarm(): JsonObject? {
        try {
            val userId = UserMap.currentUid
            val jo = FarmCoreRpc.enterFarm(userId, userId).toJsonObject()
            if (ResChecker.checkRes(TAG, jo)) {
                return if (farmHomeWorkflowService.process(jo)) jo else null
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
        return null
    }

    /**
     * 自动喂鸡
     */
    private suspend fun handleAutoFeedAnimal(isChildTask: Boolean = false) {
        farmAutoFeedService.run(isChildTask)

        // 5. 计算并安排下一次自动喂食任务（仅当小鸡不在睡觉时）
        if (AnimalFeedStatus.SLEEPY.name != ownerAnimal.animalFeedStatus) {
            try {
                /* 创建蹲点任务时间点前先同步countdown，因为可能因为好友小鸡在两次执行间隔间偷吃而引起蹲点时间变动。
                    比如投喂后程序第一次计算了剩余时间是4小时40分钟，那中间有小鸡偷吃，时间就少于4：40分钟了。再用原来
                    的时间显然有误,除非其他逻辑同步了小鸡状态才会修正，这里直接同步+修正
                 */
                syncAnimalStatus(ownerFarmId)
                // 直接使用服务器计算的权威倒计时（单位：秒）
                val remainingSec = countdown?.toDouble()?.coerceAtLeast(0.0)
                // 如果倒计时为0，跳过任务创建
                remainingSec?.let {
                    if (it > 0) {
                        // 计算下次执行时间（毫秒）
                        val nextFeedTime = System.currentTimeMillis() + (remainingSec * 1000).toLong()
                        // 调试日志：显示服务器倒计时详情
                        StructuredLog.record(
                            TAG, "服务器倒计时🕐[小鸡状态=" + toFeedStatusName(ownerAnimal.animalFeedStatus) +
                                    ", 剩余=${remainingSec.toInt()}秒" +
                                    ", 执行时间=" + TimeUtil.getCommonDate(nextFeedTime) + "]"
                        )
                        val taskId = "FA|$ownerFarmId"
                        addChildTask(
                            ChildModelTask(
                                id = taskId,
                                group = "FA",
                                suspendRunnable = {
                                    try {
                                        StructuredLog.record(TAG, "🔔 蹲点投喂任务触发")
                                        // 重新进入庄园，获取最新状态
                                        enterFarm()
                                        // 同步最新状态
                                        syncAnimalStatus(ownerFarmId)
                                        // 遣返
                                        if (sendBackAnimal!!.value) {
                                            sendBackAnimal()
                                        }
                                        // 雇佣小鸡
                                        if (hireAnimal!!.value) {
                                            hireAnimal()
                                        }
                                        // 喂鸡
                                        handleAutoFeedAnimal(true)
                                        StructuredLog.record(TAG, "🔄 下一次蹲点任务已创建")
                                    } catch (e: Exception) {
                                        StructuredLog.printStackTrace(TAG,"蹲点投喂任务执行失败", e)
                                    }
                                },
                                execTime = nextFeedTime,
                                useSmartScheduler = useSmartSchedulerManager!!.value
                            )
                        )
                        StructuredLog.record(UserMap.getCurrentMaskName() + "小鸡的蹲点投喂时间[" + TimeUtil.getCommonDate(nextFeedTime)+"]")
                    } else {
                        StructuredLog.record(TAG, "蹲点投喂🥣[倒计时为0，执行投喂]")
                        if (feedAnimal(ownerFarmId)) {
                            // 刷新状态
                            syncAnimalStatus(ownerFarmId)
                        }
                    }
                }
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, "创建蹲点任务失败: ${e.message}",e)
            }
        } else {
            // 小鸡在睡觉，跳过创建蹲点投喂任务
            // 注意：已存在的任务会在小鸡醒来时被新任务自动替换
            StructuredLog.record(TAG, "蹲点投喂🥣[小鸡正在睡觉，暂不安排投喂任务]")
        }

        // 6. 其他功能（换装、领取饲料）
        // 小鸡换装
        if (listOrnaments!!.value && Status.canOrnamentToday()) {
            listOrnaments()
        }
    }

    private suspend fun useBigEaterTool(): Boolean {
        val today = LocalDate.now().toString()
        val usedKey = "AF_BIG_EATER_USED_COUNT|${UserMap.currentUid}|$today"
        val usedCount = TaskRuntimeStore.getInt(usedKey) ?: 0
        if (serverUseBigEaterTool) {
            StructuredLog.record("服务端标记已使用加饭卡，跳过使用")
            if (usedCount == 0) TaskRuntimeStore.put(usedKey, 1)
            return false
        }
        if (usedCount >= 2) {
            StructuredLog.record("今日加饭卡已使用${usedCount}/2，跳过使用")
            return false
        }
        val result = useFarmTool(ownerFarmId, ToolType.BIG_EATER_TOOL)
        if (!result) {
            StructuredLog.record("⚠️使用道具🎭[加饭卡]失败，可能卡片不足或状态异常~")
            return false
        }
        StructuredLog.farm("使用道具🎭[加饭卡]！")
        TaskRuntimeStore.put(usedKey, usedCount + 1)
        delay(1000)
        return true
    }

    private fun animalSleepNow() = farmSleepService.sleepNow()

    private fun animalWakeUpNow() = farmSleepService.wakeUpNow()

    /**
     * 同步小鸡状态通用方法
     *
     * @param farmId 庄园id
     */
    private fun syncAnimalStatus(
        farmId: String?,
        operTag: String?,
        operateType: String?
    ): JsonObject? {
        try {
            return FarmCoreRpc.syncAnimalStatus(farmId, operTag, operateType).toJsonObject()
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, e)
            return null
        }
    }

    private fun syncAnimalStatus(farmId: String?) {
        try {
            val jo = syncAnimalStatus(farmId, "SYNC_RESUME", "QUERY_ALL")
            parseSyncAnimalStatusResponse(jo!!)
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "syncAnimalStatus err:", t)
        }
    }

    private fun syncAnimalStatusAfterFeedAnimal(farmId: String?): JsonObject? {
        try {
            return syncAnimalStatus(
                farmId,
                "SYNC_AFTER_FEED_ANIMAL",
                "QUERY_EMOTION_INFO|QUERY_ORCHARD_RIGHTS"
            )
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, e)
        }
        return null
    }

    private fun syncAnimalStatusQueryFamilyAnimals(farmId: String?): JsonObject? {
        try {
            return syncAnimalStatus(farmId, "SYNC_RESUME_FAMILY", "QUERY_ALL|QUERY_FAMILY_ANIMAL")
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, e)
        }
        return null
    }


    private fun syncAnimalStatusAtOtherFarm(userId: String?, friendUserId: String?) {
        try {
            val s = FarmCoreRpc.enterFarm(userId, friendUserId)
            var jo = s.toJsonObject()
            StructuredLog.record(TAG, "DEBUG$jo")
            jo = jo.getJSONObject("farmVO").getJSONObject("subFarmVO")
            val jaAnimals = jo.getJSONArray("animals")
            for (i in 0..<jaAnimals.length()) {
                val jaAnimaJson = jaAnimals.getJSONObject(i)
                if (jaAnimaJson.getString("masterFarmId") == ownerFarmId) { // 过滤出当前用户的小鸡
                    ownerAnimal = Animal.fromJson(jaAnimaJson)
                    break
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "syncAnimalStatusAtOtherFarm err:", t)
        }
    }

    private fun rewardFriend() {
        farmFriendRewardService.reward(rewardList, rewardProductNum)
        rewardList = null
    }

    private fun recallAnimal(
        animalId: String?,
        currentFarmId: String?,
        masterFarmId: String?,
        user: String?
    ) {
        try {
            val s = FarmCoreRpc.recallAnimal(animalId, currentFarmId, masterFarmId)
            val jo = s.toJsonObject()
            val memo = jo.getString("memo")
            if (ResChecker.checkRes(TAG, jo)) {
                val foodHaveStolen = jo.getDouble("foodHaveStolen")
                StructuredLog.farm("召回小鸡📣，偷吃[" + user + "]#" + foodHaveStolen + "g")
                // 这里不需要加
                // add2FoodStock((int)foodHaveStolen);
            } else {
                StructuredLog.record(memo)
                StructuredLog.record(s)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "recallAnimal err:",t)
        }
    }

    private fun sendBackAnimal() {
        if (animals == null) {
            return
        }
        try {
            for (animal in animals) {
                if (AnimalInteractStatus.STEALING.name == animal.animalInteractStatus && (SubAnimalType.GUEST.name != animal.subAnimalType) && (SubAnimalType.WORK.name != animal.subAnimalType)) {
                    // 赶鸡
                    var user = FarmCoreRpc.farmId2UserId(animal.masterFarmId)
                    var isSendBackAnimal = sendBackAnimalList!!.value.contains(user)
                    if (sendBackAnimalType!!.value == SendBackAnimalType.BACK) {
                        isSendBackAnimal = !isSendBackAnimal
                    }
                    if (isSendBackAnimal) {
                        continue
                    }
                    val sendTypeInt = sendBackAnimalWay!!.value
                    user = UserMap.getMaskName(user) ?: user
                    val s = FarmCoreRpc.sendBackAnimal(
                        SendBackAnimalWay.nickNames[sendTypeInt],
                        animal.animalId,
                        animal.currentFarmId,
                        animal.masterFarmId
                    )
                    val jo = s.toJsonObject()
                    val memo = jo.getString("memo")
                    if (ResChecker.checkRes(TAG, jo)) {
                        StructuredLog.farm("${UserMap.getCurrentMaskName()} 驱赶小鸡🧶[$user]")
                    } else {
                        StructuredLog.record(memo)
                        StructuredLog.record(s)
                    }
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "sendBackAnimal err:",t)
        }
    }

    private fun receiveToolTaskReward() {
        farmToolRewardService.receive()
    }

    private fun harvestProduce(farmId: String?) {
        farmProduceService.harvest(farmId)
    }

    /* 捐赠爱心鸡蛋 */
    private fun handleDonation(donationType: Int) {
        farmDonationService.donate(donationType == DonationCount.ONE)
    }

    @Suppress("SameParameterValue")
    private fun answerQuestion(activityId: String?) {
        try {
            val today = TimeUtil.getDateStr2()
            val tomorrow = TimeUtil.getDateStr2(1)
            val farmAnswerCache = TaskRuntimeStore.getOrCreate(
                FARM_ANSWER_CACHE_KEY,
                emptyMap<String, String>(),
                farmAnswerCacheSerializer,
            ).toMutableMap()
            cleanOldAnswers(farmAnswerCache, today)
            // 检查是否今天已经答过题
            if (Status.hasFlagToday(ANSWERED_FLAG)) {
                if (!Status.hasFlagToday(CACHED_FLAG)) {
                    val jo = DadaDailyRpcCall.home(activityId).toJsonObject()
                    if (ResChecker.checkRes(TAG + "查询答题活动失败:", jo)) {
                        val operationConfigList = jo.getJSONArray("operationConfigList")
                        updateTomorrowAnswerCache(operationConfigList, tomorrow)
                        Status.setFlagToday(CACHED_FLAG)
                    }
                }
                return
            }

            // 获取题目信息
            val jo = DadaDailyRpcCall.home(activityId).toJsonObject()
            if (!ResChecker.checkRes(TAG + "获取答题题目失败:", jo)) return

            val question = jo.getJSONObject("question")
            val questionId = question.getLong("questionId")
            val labels = question.getJSONArray("label")
            val title = question.getString("title")

            var answer: String? = null
            var cacheHit = false
            val cacheKey = "$title|$today"

            // 改进的缓存匹配逻辑
            if (farmAnswerCache.containsKey(cacheKey)) {
                val cachedAnswer = farmAnswerCache[cacheKey]
                StructuredLog.farm("🎉 缓存[$cachedAnswer] 🎯 题目：$cacheKey")

                // 1. 首先尝试精确匹配
                for (i in 0..<labels.length()) {
                    val option = labels.getString(i)
                    if (option == cachedAnswer) {
                        answer = option
                        cacheHit = true
                        break
                    }
                }

                // 2. 如果精确匹配失败，尝试模糊匹配
                if (!cacheHit && cachedAnswer != null) {
                    for (i in 0..<labels.length()) {
                        val option = labels.getString(i)
                        if (option.contains(cachedAnswer) || cachedAnswer.contains(option)) {
                            answer = option
                            cacheHit = true
                            StructuredLog.farm("⚠️ 缓存模糊匹配成功：$cachedAnswer → $option")
                            break
                        }
                    }
                }
            }

            // 缓存未命中时调用AI
            if (!cacheHit) {
                StructuredLog.record(TAG, "缓存未命中，尝试使用AI答题：$title")
                answer = AnswerAI.getAnswer(title, labels.toString().toJsonArray().toStringList(), "farm")
                if (answer.isEmpty()) {
                    answer = labels.getString(0) // 默认选择第一个选项
                }
            }

            // 提交答案
            val joDailySubmit = DadaDailyRpcCall.submit(activityId, answer, questionId).toJsonObject()
            Status.setFlagToday(ANSWERED_FLAG)
            if (ResChecker.checkRes(TAG + "提交答题答案失败:", joDailySubmit)) {
                val extInfo = joDailySubmit.getJSONObject("extInfo")
                val correct = joDailySubmit.getBoolean("correct")
                StructuredLog.farm("饲料任务答题：" + (if (correct) "正确" else "错误") + "领取饲料［" + extInfo.getString("award") + "g］")
                val operationConfigList = joDailySubmit.getJSONArray("operationConfigList")
                updateTomorrowAnswerCache(operationConfigList, tomorrow)
                Status.setFlagToday(CACHED_FLAG)
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "答题出错", e)
        }
    }

    /**
     * 更新明日答案缓存
     *
     * @param operationConfigList 操作配置列表
     * @param date                日期字符串，格式 "yyyy-MM-dd"
     */
    private fun updateTomorrowAnswerCache(operationConfigList: JsonArray, date: String?) {
        try {
            val farmAnswerCache = TaskRuntimeStore.getOrCreate(
                FARM_ANSWER_CACHE_KEY,
                emptyMap<String, String>(),
                farmAnswerCacheSerializer,
            ).toMutableMap()
            for (j in 0..<operationConfigList.length()) {
                val operationConfig = operationConfigList.getJSONObject(j)
                val type = operationConfig.getString("type")
                if ("PREVIEW_QUESTION" == type) {
                    val previewTitle = operationConfig.getString("title") + "|" + date
                        val actionTitle = operationConfig.getString("actionTitle").toJsonArray()
                    for (k in 0..<actionTitle.length()) {
                        val joActionTitle = actionTitle.getJSONObject(k)
                        val isCorrect = joActionTitle.getBoolean("correct")
                        if (isCorrect) {
                            val nextAnswer = joActionTitle.getString("title")
                            farmAnswerCache[previewTitle] = nextAnswer // 缓存下一个问题的答案
                        }
                    }
                }
            }
            TaskRuntimeStore.put(FARM_ANSWER_CACHE_KEY, farmAnswerCache.toMap(), farmAnswerCacheSerializer)
            StructuredLog.record(TAG, "updateTomorrowAnswerCache 缓存更新完毕")
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "updateTomorrowAnswerCache 错误:", e)
        }
    }


    /**
     * 清理缓存超过7天的B答案
     */
    private fun cleanOldAnswers(farmAnswerCache: MutableMap<String, String>?, today: String?) {
        try {
            if (farmAnswerCache == null || farmAnswerCache.isEmpty()) return
            // 将今天日期转为数字格式：20250405
            val todayInt = convertDateToInt(today) // 如 "2025-04-05" → 20250405
            // 设置保留天数（例如7天）
            val daysToKeep = 7
            val cleanedMap: MutableMap<String, String> = HashMap()
            for (entry in farmAnswerCache.entries) {
                val key: String = entry.key
                if (key.contains("|")) {
                    val parts: Array<String?> = key.split("\\|".toRegex(), limit = 2).toTypedArray()
                    if (parts.size == 2) {
                        val dateStr = parts[1] //获取日期部分 20
                        val dateInt = convertDateToInt(dateStr)
                        if (dateInt == -1) continue
                        if (todayInt - dateInt <= daysToKeep) {
                            cleanedMap[entry.key] = entry.value //保存7天内的答案
                            StructuredLog.record(TAG, "保留 日期：" + todayInt + "缓存日期：" + dateInt + " 题目：" + parts[0])
                        }
                    }
                }
            }
            TaskRuntimeStore.put(FARM_ANSWER_CACHE_KEY, cleanedMap.toMap(), farmAnswerCacheSerializer)
            StructuredLog.record(TAG, "cleanOldAnswers 清理缓存完毕")
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "cleanOldAnswers error:", e)
        }
    }


    /**
     * 将日期字符串转为数字格式
     *
     * @param dateStr 日期字符串，格式 "yyyy-MM-dd"
     * @return 日期数字格式，如 "2025-04-05" → 20250405
     */
    private fun convertDateToInt(dateStr: String?): Int {
        if (dateStr == null || dateStr.length != 10 || dateStr[4] != '-' || dateStr[7] != '-') {
            StructuredLog.error("日期格式错误：$dateStr")
            return -1 // 格式错误
        }
        try {
            val year = dateStr.take(4).toInt()
            val month = dateStr.substring(5, 7).toInt()
            val day = dateStr.substring(8, 10).toInt()
            if (month !in 1..12 || day < 1 || day > 31) {
                StructuredLog.error("日期无效：$dateStr")
                return -1 // 日期无效
            }
            return year * 10000 + month * 100 + day
        } catch (e: NumberFormatException) {
            StructuredLog.error(TAG, "日期转换失败：" + dateStr + e.message)
            return -1
        }
    }


    private suspend fun handleFarmGameLogic() = farmGameWorkflowService.runGames()

    private fun handleChouChouLeLogic() = farmGameWorkflowService.runChouChouLe()
    /**
     * 庄园任务，目前支持i
     * 视频，杂货铺，抽抽乐，家庭，618会场，芭芭农场，小鸡厨房
     * 添加组件，雇佣，会员签到，逛咸鱼，今日头条极速版，UC浏览器
     * 一起拿饲料，到店付款，线上支付，鲸探
     */
    private suspend fun doFarmTasks() {
        FarmTaskRunner.run { answerQuestion("100") }
    }

    private suspend fun receiveFarmAwards() = farmTaskRewardService.receive()

    /**
     * 喂鸡
     *
     * @param farmId 庄园ID
     * @return true: 喂鸡成功，false: 喂鸡失败
     */
    private fun feedAnimal(farmId: String?): Boolean = farmFeedService.feed(farmId)

    private fun scheduleSendBackAfterFeed(farmId: String?, delayMinutes: Int) {
        try {
            val taskId = "KC|$farmId"
            val executeAt = System.currentTimeMillis() + delayMinutes * 60 * 1000L
            val displayTime = TimeUtil.getCommonDate(executeAt)
            addChildTask(
                ChildModelTask(
                    id = taskId,
                    group = "KC",
                    suspendRunnable = {
                        try {
                            StructuredLog.record(TAG, "🔔 蹲点赶鸡任务触发")
                            enterFarm()
                            syncAnimalStatus(farmId)
                            sendBackAnimal()
                        } catch (error: Exception) {
                            StructuredLog.error(TAG, "蹲点赶鸡任务执行失败: ${error.message}")
                            StructuredLog.printStackTrace(TAG, error)
                        }
                    },
                    execTime = executeAt,
                    useSmartScheduler = useSmartSchedulerManager!!.value,
                ),
            )
            StructuredLog.record(UserMap.getCurrentMaskName() + "${TimeFormatter.formatDuration(delayMinutes * 60 * 1000L)}后${displayTime}蹲点赶小鸡")
        } catch (error: Exception) {
            StructuredLog.printStackTrace(TAG, "创建蹲点赶鸡失败: ${error.message}", error)
        }
    }


    private fun listFarmTool(): List<FarmTool>? = farmToolService.list()
    private val accelerateToolCount: Int
        get() = farmTools.find { it.toolType == ToolType.ACCELERATETOOL }?.toolCount ?: 0

    /**
     * 连续使用加速卡
     *
     * @return true: 使用成功，false: 使用失败
     */
    private suspend fun useAccelerateTool(): Boolean = farmAccelerateService.run()

    private fun useFarmTool(targetFarmId: String?, toolType: ToolType): Boolean =
        farmToolService.use(targetFarmId, toolType)

    private suspend fun feedFriend() {
        farmFriendFeedService.run()
    }


    private fun notifyFriend() {
        farmFriendNotificationService.notify()
    }

    /** Applies the parsed animal state to the task facade. */
    private fun parseSyncAnimalStatusResponse(response: JsonObject) {
        val state = farmAnimalStateService.parse(response) ?: return
        state.finalScore?.let { finalScore = it }
        serverUseBigEaterTool = state.serverUseBigEaterTool
        state.foodStock?.let { foodStock = it }
        foodInTroughLimitCurrent = state.foodInTroughLimit
        foodStockLimit = state.foodStockLimit
        ownerFarmId = state.ownerFarmId
        countdown = state.countdown
        benevolenceScore = state.benevolenceScore
        state.rewardList?.let { rewardList = it }
        hasFence = state.hasFence
        fenceCountDown = state.fenceCountDown
        animals = state.animals
        ownerAnimal = state.ownerAnimal
        if (hasFence) {
            StructuredLog.record(
                TAG,
                "🛡️ 篱笆生效中，剩余时间: ${fenceCountDown / 3600}小时${(fenceCountDown % 3600) / 60}分",
            )
        }
    }

    private fun add2FoodStock(i: Int) {
        foodStock += i
        if (foodStock > foodStockLimit) {
            foodStock = foodStockLimit
        }
        if (foodStock < 0) {
            foodStock = 0
        }
    }


    /**
     * 送麦子
     */
    private suspend fun visit() = farmVisitService.run()

    private fun acceptGift() {
        try {
            val jo = FarmCoreRpc.acceptGift().toJsonObject()
            if (ResChecker.checkRes(TAG, jo)) {
                val receiveFoodNum = jo.getInt("receiveFoodNum")
                StructuredLog.farm("收取麦子🌾[" + receiveFoodNum + "g]")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "acceptGift err:",t)
        }
    }

    /**
     * 贴贴小鸡
     *
     * @param queryDayStr 日期，格式：yyyy-MM-dd
     */
    private fun visitAnimal() {
        farmAnimalVisitService.run()
    }

    /* 雇佣好友小鸡 */
    private fun hireAnimal() {
        farmHireService.run()
    }

    /* Single-friend hire RPC remains a narrow facade callback for FarmHireService. */
    private fun hireAnimalAction(userId: String?): Boolean {
        try {
            val s = FarmCoreRpc.enterFarm(userId, userId)
            var jo = s.toJsonObject()
            if (ResChecker.checkRes(TAG, jo)) {
                val farmVO = jo.getJSONObject("farmVO")
                val subFarmVO = farmVO.getJSONObject("subFarmVO")
                val farmId = subFarmVO.getString("farmId")
                val animals = subFarmVO.getJSONArray("animals")
                var i = 0
                val len = animals.length()
                while (i < len) {
                    val animal = animals.getJSONObject(i)
                    if (animal.getJSONObject("masterUserInfoVO").getString("userId") == userId) {
                        val animalStatusVo = animal.getJSONObject("animalStatusVO")
                        if (AnimalInteractStatus.HOME.name != animalStatusVo.getString("animalInteractStatus")) {
                            StructuredLog.record(UserMap.getMaskName(userId) + "的小鸡不在家")
                            return false
                        }
                        val animalId = animal.getString("animalId")
                        jo = FarmPropRpc.hireAnimal(farmId, animalId).toJsonObject()
                        if (ResChecker.checkRes(TAG, jo)) {
                            StructuredLog.farm("雇佣小鸡👷[" + UserMap.getMaskName(userId) + "] 成功")
                            val newAnimals = jo.getJSONArray("animals")
                            var ii = 0
                            val newLen = newAnimals.length()
                            while (ii < newLen) {
                                val joo = newAnimals.getJSONObject(ii)
                                if (joo.getString("animalId") == animalId) {
                                    val beHiredEndTime = joo.getLong("beHiredEndTime")
                                    addChildTask(
                                        ChildModelTask(
                                            "HIRE|$animalId",
                                            "HIRE",
                                            suspendRunnable = { this.hireAnimal() },
                                            beHiredEndTime
                                        )
                                    )
                                    StructuredLog.record(
                                        TAG,
                                        "添加蹲点雇佣👷在[" + TimeUtil.getCommonDate(beHiredEndTime) + "]执行"
                                    )
                                    break
                                }
                                ii++
                            }
                            return true
                        } else {
                            val resultCode = jo.optString("resultCode", "")
                            val memo = jo.optString("memo", "")
                            // 如果庄园已满，设置标志并返回false
                            if (resultCode == "I07" || memo.contains("庄园的小鸡太多了")) {
                                isFarmFull = true
                                StructuredLog.record(TAG, "庄园小鸡已满，停止雇佣")
                                return false
                            }
                            StructuredLog.record(memo)
                            StructuredLog.record(s)
                        }
                        return false
                    }
                    i++
                }
            } else {
                StructuredLog.record(jo.getString("memo"))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "hireAnimal err:",t)
        }
        return false
    }

    /**
     * 统一处理NPC小鸡的雇佣、切换、领奖与任务
     */
    private suspend fun handleNpcAnimalLogic() {
        npcService.run(npcAnimalType?.value ?: 0, animals, ownerFarmId)
    }

    private suspend fun drawGameCenterAward() {
        farmGameCenterService.run()
    }

    // 小鸡换装
    private fun listOrnaments() {
        try {
            val s = FarmDrawRpc.queryLoveCabin(UserMap.currentUid)
            val jsonObject = s.toJsonObject()
            if ("SUCCESS" == jsonObject.getString("memo")) {
                val ownAnimal = jsonObject.getJSONObject("ownAnimal")
                val animalId = ownAnimal.getString("animalId")
                val farmId = ownAnimal.getString("farmId")
                val listResult = FarmPropRpc.listOrnaments()
                val jolistOrnaments = listResult.toJsonObject()
                // 检查是否有 achievementOrnaments 数组
                if (!jolistOrnaments.has("achievementOrnaments")) {
                    return  // 数组为空，直接返回
                }
                val achievementOrnaments = jolistOrnaments.getJSONArray("achievementOrnaments")
                val random = Random()
                val possibleOrnaments: MutableList<String> = ArrayList() // 收集所有可保存的套装组合
                for (i in 0..<achievementOrnaments.length()) {
                    val ornament = achievementOrnaments.getJSONObject(i)
                    if (ornament.getBoolean("acquired")) {
                        val sets = ornament.getJSONArray("sets")
                        val availableSets: MutableList<JsonObject> = ArrayList()
                        // 收集所有带有 cap 和 coat 的套装组合
                        for (j in 0..<sets.length()) {
                            val set = sets.getJSONObject(j)
                            if ("cap" == set.getString("subType") || "coat" == set.getString("subType")) {
                                availableSets.add(set)
                            }
                        }
                        // 如果有可用的帽子和外套套装组合
                        if (availableSets.size >= 2) {
                            // 将所有可保存的套装组合添加到 possibleOrnaments 列表中
                            for (j in 0..<availableSets.size - 1) {
                                val selectedCoat = availableSets[j]
                                val selectedCap = availableSets[j + 1]
                                val id1 = selectedCoat.getString("id") // 外套 ID
                                val id2 = selectedCap.getString("id") // 帽子 ID
                                val ornaments = "$id1,$id2"
                                possibleOrnaments.add(ornaments)
                            }
                        }
                    }
                }
                // 如果有可保存的套装组合，则随机选择一个进行保存
                if (!possibleOrnaments.isEmpty()) {
                    val ornamentsToSave =
                        possibleOrnaments[random.nextInt(possibleOrnaments.size)]
                    val saveResult = FarmPropRpc.saveOrnaments(animalId, farmId, ornamentsToSave)
                    val saveResultJson = saveResult.toJsonObject()
                    // 判断保存是否成功并输出日志
                    if (saveResultJson.optBoolean("success")) {
                        // 获取保存的整套服装名称
                        val ornamentIds: Array<String?> =
                            ornamentsToSave.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        var wholeSetName = "" // 整套服装名称
                        // 遍历 achievementOrnaments 查找对应的套装名称
                        for (i in 0..<achievementOrnaments.length()) {
                            val ornament = achievementOrnaments.getJSONObject(i)
                            val sets = ornament.getJSONArray("sets")
                            // 找到对应的整套服装名称
                            if (sets.length() == 2 && sets.getJSONObject(0)
                                    .getString("id") == ornamentIds[0]
                                && sets.getJSONObject(1).getString("id") == ornamentIds[1]
                            ) {
                                wholeSetName = ornament.getString("name")
                                break
                            }
                        }
                        // 输出日志
                        StructuredLog.farm("庄园小鸡💞[换装:$wholeSetName]")
                        Status.setOrnamentToday()
                    } else {
                        StructuredLog.record(TAG, "保存时装失败，错误码： $saveResultJson")
                    }
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "listOrnaments err: " + t.message,t)
        }
    }

    // 一起拿小鸡饲料
    private fun letsGetChickenFeedTogether() {
        try {
            var jo = FarmSocialRpc.letsGetChickenFeedTogether().toJsonObject()
            if (jo.optBoolean("success")) {
                val bizTraceId = jo.getString("bizTraceId")
                val p2pCanInvitePersonDetailList = jo.getJSONArray("p2pCanInvitePersonDetailList")
                var canInviteCount = 0
                var hasInvitedCount = 0
                val userIdList: MutableList<String?> = ArrayList() // 保存 userId
                for (i in 0..<p2pCanInvitePersonDetailList.length()) {
                    val personDetail = p2pCanInvitePersonDetailList.getJSONObject(i)
                    val inviteStatus = personDetail.getString("inviteStatus")
                    val userId = personDetail.getString("userId")
                    if (inviteStatus == "CAN_INVITE") {
                        userIdList.add(userId)
                        canInviteCount++
                    } else if (inviteStatus == "HAS_INVITED") {
                        hasInvitedCount++
                    }
                }
                val invitedToday = hasInvitedCount
                val remainingInvites = 5 - invitedToday
                var invitesToSend = min(canInviteCount, remainingInvites)
                if (invitesToSend == 0) {
                    return
                }
                val getFeedSet = getFeedlList!!.value
                if (getFeedType!!.value == GetFeedType.GIVE) {
                    for (userId in userIdList) {
                        if (invitesToSend <= 0) {
//                            StructuredLog.record(TAG,"已达到最大邀请次数限制，停止发送邀请。");
                            break
                        }
                        if (getFeedSet.contains(userId)) {
                            jo = FarmSocialRpc.giftOfFeed(bizTraceId, userId).toJsonObject()
                            if (jo.optBoolean("success")) {
                                StructuredLog.farm("一起拿小鸡饲料🥡 [送饲料：" + UserMap.getMaskName(userId) + "]")
                                invitesToSend-- // 每成功发送一次邀请，减少一次邀请次数
                            } else {
                                StructuredLog.record(TAG, "邀请失败：$jo")
                                break
                            }
                        }
                    }
                } else {
                    val random = Random()
                    for (j in 0..<invitesToSend) {
                        val randomIndex = random.nextInt(userIdList.size)
                        val userId = userIdList[randomIndex]
                        jo = FarmSocialRpc.giftOfFeed(bizTraceId, userId).toJsonObject()
                        if (jo.optBoolean("success")) {
                            StructuredLog.farm("一起拿小鸡饲料🥡 [送饲料：" + UserMap.getMaskName(userId) + "]")
                        } else {
                            StructuredLog.record(TAG, "邀请失败：$jo")
                            break
                        }
                        userIdList.removeAt(randomIndex)
                    }
                }
            }
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "letsGetChickenFeedTogether err:",e)
        }
    }

    interface DonationCount {
        companion object {
            const val ONE: Int = 0
            const val ALL: Int = 1
            val nickNames: Array<String?> = arrayOf<String?>("随机一次", "随机多次")
        }
    }

    interface RecallAnimalType {
        companion object {
            const val ALWAYS: Int = 0
            const val WHEN_THIEF: Int = 1
            const val WHEN_HUNGRY: Int = 2
            const val NEVER: Int = 3
            val nickNames: Array<String?> =
                arrayOf<String?>("始终召回", "偷吃召回", "饥饿召回", "暂不召回")
        }
    }

    interface SendBackAnimalWay {
        companion object {
            const val HIT: Int = 0
            const val NORMAL: Int = 1
            val nickNames: Array<String?> = arrayOf<String?>("攻击", "常规")
        }
    }

    interface SendBackAnimalType {
        companion object {
            const val BACK: Int = 0
            const val NOT_BACK: Int = 1
            val nickNames: Array<String?> = arrayOf<String?>("选中遣返", "选中不遣返")
        }
    }

    @Suppress("ClassName")
    interface collectChickenDiaryType {
        companion object {
            const val CLOSE: Int = 0
            const val ONCE: Int = 0
            const val MONTH: Int = 1
            const val ALL: Int = 2
            val nickNames: Array<String?> = arrayOf<String?>("不开启", "一次", "当月", "所有")
        }
    }

    enum class AnimalBuff {
        //小鸡buff
        ACCELERATING, INJURED, NONE
    }

    /**
     * 小鸡喂食状态枚举
     */
    enum class AnimalFeedStatus {
        HUNGRY,  // 饥饿状态：小鸡需要投喂，可以正常喂食
        EATING,  // 进食状态：小鸡正在吃饭，此时不能重复投喂，会返回"不要着急，还没吃完呢"
        SLEEPY,  // 睡觉状态：小鸡正在睡觉，不能投喂，需要等待醒来
        NONE // 无状态：未知或其他状态
    }

    /**
     * 小鸡互动状态枚举
     */
    enum class AnimalInteractStatus {
        HOME,  // 在家：小鸡在自己的庄园里，正常状态
        GOTOSTEAL,  // 去偷吃：小鸡离开庄园，准备去别的庄园偷吃
        STEALING // 偷吃中：小鸡正在别人的庄园里偷吃饲料
    }

    /**
     * 小鸡子类型枚举
     */
    enum class SubAnimalType {
        NORMAL,  // 普通：正常的小鸡状态
        GUEST,  // 客人：小鸡去好友家做客
        PIRATE,  // 海盗：小鸡外出探险
        WORK // 工作：小鸡被雇佣去工作
    }

    /**
     * 道具类型枚举
     * STEALTOOL：蹭饭卡
     * ACCELERATETOOL：加速卡
     * SHARETOOL：救济卡
     * FENCETOOL：篱笆卡
     * NEWEGGTOOL：新蛋卡
     * DOLLTOOL：公仔补签卡
     * ORDINARY_ORNAMENT_TOOL：普通装扮补签卡
     * ADVANCE_ORNAMENT_TOOL：高级装扮补签卡
     * BIG_EATER_TOOL：加饭卡
     * RARE_ORNAMENT_TOOL：稀有装扮补签卡
     */
    enum class ToolType {
        STEALTOOL,  // 蹭饭卡
        ACCELERATETOOL,  // 加速卡
        SHARETOOL,  // 救济卡
        FENCETOOL,  // 篱笆卡
        NEWEGGTOOL,  // 新蛋卡
        DOLLTOOL,  // 公仔补签卡
        ORDINARY_ORNAMENT_TOOL,  // 普通装扮补签卡
        ADVANCE_ORNAMENT_TOOL,  // 高级装扮补签卡
        BIG_EATER_TOOL,  // 加饭卡
        RARE_ORNAMENT_TOOL; // 稀有装扮补签卡

        /**
         * 获取道具类型的中文名称
         * @return 对应的中文名称
         */
        fun nickName(): CharSequence? {
            return nickNames[ordinal]
        }

        companion object {
            // 道具类型对应的中文名称
            val nickNames: Array<CharSequence?> = arrayOf<CharSequence?>(
                "蹭饭卡",
                "加速卡",
                "救济卡",
                "篱笆卡",
                "新蛋卡",
                "公仔补签卡",
                "普通装扮补签卡",
                "高级装扮补签卡",
                "加饭卡",
                "稀有装扮补签卡"
            )
        }
    }

    enum class GameType {
        starGame, jumpGame, flyGame, hitGame;

        fun gameName(): CharSequence? {
            return gameNames[ordinal]
        }

        companion object {
            val gameNames: Array<CharSequence?> =
                arrayOf<CharSequence?>("星星球", "登山赛", "飞行赛", "欢乐揍小鸡")
        }
    }


    @Serializable
    internal data class Animal(
        var animalId: String? = null,
        var currentFarmId: String? = null,
        var masterFarmId: String? = null,
        var animalBuff: String? = null,
        var subAnimalType: String? = null,
        var currentFarmMasterUserId: String? = null,
        var animalFeedStatus: String? = null,
        var animalInteractStatus: String? = null,
        var locationType: String? = null,
        var startEatTime: Long? = null,
        var consumeSpeed: Double? = null,
        var foodHaveEatten: Double? = null,
        var foodHaveStolen: Double? = null,
        var masterUserInfoVO: Map<String, JsonElement>? = null,
        /** 新版接口将 feed/interact 状态嵌在该对象里；kotlinx 默认只映射顶层字段 */
        var animalStatusVO: JsonObject? = null,
    ) {

        /** 把嵌套的 animalStatusVO 回填到扁平字段（顶层仍返回时保持原值优先）。 */
        fun applyAnimalStatusVO() {
            val vo = animalStatusVO ?: return
            vo.optString("animalFeedStatus").takeIf { it.isNotEmpty() }?.let { animalFeedStatus = it }
            vo.optString("animalInteractStatus").takeIf { it.isNotEmpty() }?.let { animalInteractStatus = it }
        }

        companion object {
            fun fromJson(animal: JsonObject): Animal =
                JsonUtil.decode(animal.toString(), serializer()).also { it.applyAnimalStatusVO() }
        }
    }

    internal data class RewardFriend(
        var consistencyKey: String? = null,
        var friendId: String? = null,
        var time: String? = null,
    )

    internal data class FarmTool(
        var toolType: ToolType? = null,
        var toolId: String? = null,
        var toolCount: Int = 0,
        var toolHoldLimit: Int = 0,
    )

    @Suppress("unused")
    interface HireAnimalType {
        companion object {
            const val HIRE: Int = 0
            const val DONT_HIRE: Int = 1
            val nickNames: Array<String?> = arrayOf<String?>("选中雇佣", "选中不雇佣")
        }
    }

    @Suppress("unused")
    interface GetFeedType {
        companion object {
            const val GIVE: Int = 0
            const val RANDOM: Int = 1
            val nickNames: Array<String?> = arrayOf<String?>("选中赠送", "随机赠送")
        }
    }

    interface NotifyFriendType {
        companion object {
            const val NOTIFY: Int = 0
            const val DONT_NOTIFY: Int = 1
            val nickNames: Array<String?> = arrayOf<String?>("选中通知", "选中不通知")
        }
    }

    internal class AntFarmFamilyOption(i: String, n: String) : MapperEntity() {
        init {
            id = i
            name = n
        }

        companion object {
            val antFarmFamilyOptions: MutableList<AntFarmFamilyOption?>
                get() {
                    val list: MutableList<AntFarmFamilyOption?> =
                        ArrayList()
                    list.add(AntFarmFamilyOption("familySign", "每日签到"))
                    list.add(AntFarmFamilyOption("eatTogetherConfig", "请吃美食"))
                    list.add(AntFarmFamilyOption("feedFamilyAnimal", "帮喂小鸡"))
                    list.add(AntFarmFamilyOption("deliverMsgSend", "道早安"))
                    list.add(AntFarmFamilyOption("familyClaimReward", "领取奖励"))
                    list.add(AntFarmFamilyOption("inviteFriendVisitFamily", "好友分享"))
                    list.add(AntFarmFamilyOption("assignRights", "使用顶梁柱特权"))
                    list.add(AntFarmFamilyOption("familyDrawInfo", "开扭蛋"))
                    list.add(AntFarmFamilyOption("batchInviteP2P", "串门送扭蛋"))
                    list.add(AntFarmFamilyOption("ExchangeFamilyDecoration", "兑换装修物品"))
                    return list
                }
        }
    }

    companion object {
        private val TAG: String = AntFarm::class.java.getSimpleName()
        /** 厨房守卫召回后复查次数与间隔：召回服务端异步生效，需轮询等待 */
        private const val KITCHEN_RECALL_POLL_TRIES = 3
        private const val KITCHEN_RECALL_POLL_INTERVAL_MS = 2_500L
        /**
         * 小鸡饲料g
         */
        private const val FARM_ANSWER_CACHE_KEY = "farmAnswerQuestionCache"
        private const val ANSWERED_FLAG = "farmQuestion::answered" // 今日是否已答题
        private const val CACHED_FLAG = "farmQuestion::cache" // 是否已缓存明日答案
        init {
            ModelOrder.register(ModelDefinition(AntFarm::class.java) { AntFarm() })
        }
    }

    @Synchronized
    fun loadAntFarmReferToken(): String? {
        val uid = UserMap.currentUid
        if (uid != antFarmReferTokenUid) {
            antFarmReferToken = null
            antFarmReferTokenUid = uid
        }
        if (!antFarmReferToken.isNullOrEmpty()) return antFarmReferToken
        val vipData = IdMapManager.getInstance(VipDataIdMap::class.java)
        vipData.load(uid)
        antFarmReferToken = vipData.get("AntFarmReferToken")
        return antFarmReferToken
    }

    /**
     * 手动触发遣返小鸡
     */
    fun manualSendBackAnimal() {
        try {
            StructuredLog.record(TAG, "庄园🐔 手动遣返小鸡")
            // 必须先进入农场获取最新 animal 数据
            if (enterFarm() != null) {
                sendBackAnimal()
                StructuredLog.farm("庄园🐔 遣返指令执行完毕")
            } else {
                StructuredLog.record(TAG, "庄园🐔 进入农场失败，无法执行遣返")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "manualSendBackAnimal 异常:", t)
        }
    }
    /**
     * 手动执行庄园游戏改分逻辑（供 ManualTask 调用）
     */
    suspend fun manualFarmGameLogic() {
        try {
            StructuredLog.record(TAG, "庄园🐔 手动游戏改分")
            if (enterFarm() != null) {
                // 同步最新状态后执行原有逻辑
                syncAnimalStatus(ownerFarmId)
                val foodStockThreshold = foodStockLimit - gameRewardMax!!.value
                if (foodStock < foodStockThreshold) {
                    receiveFarmAwards()
                }
                farmGameService.playAllGames()
                StructuredLog.record(TAG, "手动游戏改分任务处理完毕")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "manualFarmGameLogic err:", t)
        }
    }
    /**
     * 手动执行庄园抽抽乐逻辑（供 ManualTask 调用）
     */
    fun manualChouChouLeLogic() {
        try {
            StructuredLog.record(TAG, "庄园🐔 手动抽抽乐")
            if (enterFarm() != null) {
                farmGameService.playChouChouLe()
                StructuredLog.record(TAG, "庄园🐔 抽抽乐处理完毕")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "manualChouChouLeLogic 异常:", t)
        }
    }
    /**
     * 手动使用特殊美食
     * @param count 期望使用的总数量（必须 > 0）
     */
    fun manualUseSpecialFood(count: Int) {
        try {
            // 1. 严格校验：如果数量 <= 0，则不执行任何逻辑
            if (count <= 0) {
                StructuredLog.record(TAG, "⚠️ 手动使用特殊美食已拦截：必须指定大于0的使用次数")
                return
            }

            StructuredLog.record(TAG, "庄园🐔 手动使用特殊美食，目标数量: $count")
            val jo = enterFarm()
            if (jo != null) {
                val cuisineList = jo.getJSONArray("cuisineList")

                if (AnimalFeedStatus.SLEEPY.name == ownerAnimal.animalFeedStatus) {
                    StructuredLog.record(TAG, "庄园🐔 小鸡正在睡觉，无法使用美食")
                } else {
                    farmKitchenService.useSpecialFood(cuisineList, count)
                    StructuredLog.record(TAG, "庄园🐔 特殊美食处理完毕")
                }
            } else {
                StructuredLog.record(TAG, "庄园🐔 进入农场失败，无法执行任务")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "manualUseSpecialFood 异常:", t)
        }
    }

    /**
     * 手动使用庄园道具
     * @param toolType 道具类型：BIG_EATER_TOOL, NEWEGGTOOL, FENCETOOL
     * @param toolCount 使用数量（仅 NEWEGGTOOL 有效）
     */
    suspend fun manualUseFarmTool(toolType: String, toolCount: Int) {
        try {
            if (enterFarm() != null) {
                syncAnimalStatus(ownerFarmId)
                StructuredLog.record(TAG, "庄园🐔 手动使用道具: $toolType, 计划数量: $toolCount")
                val farmTools = listFarmTool()
                if (farmTools == null || farmTools.isEmpty()) {
                    StructuredLog.farm("庄园🐔 道具列表获取失败或道具库为空")
                    return
                }

                val tool = farmTools.find { it.toolType?.name == toolType }
                if (tool == null) {
                    StructuredLog.farm("庄园🐔 道具库无道具: $toolType")
                    return
                }
                if (toolType == "FENCETOOL" && hasFence) {
                    StructuredLog.farm("庄园🐔 手动执行拦截：篱笆卡效果正在生效中")
                    return
                }

                StructuredLog.record(TAG, "当前道具 [${tool.toolType?.nickName()}] 余量: ${tool.toolCount}")

                val actualCount = if (toolType == "NEWEGGTOOL") {
                    if (tool.toolCount < toolCount) {
                        StructuredLog.record(TAG, "⚠️ 道具余量不足，将用完剩余的 ${tool.toolCount} 个")
                        tool.toolCount
                    } else {
                        toolCount
                    }
                } else {
                    1 // 其他道具默认使用1次
                }

                if (actualCount <= 0) {
                    StructuredLog.farm("庄园🐔 可用数量为0，终止操作")
                    return
                }

                repeat(actualCount) { index ->
                    val res = FarmCoreRpc.useFarmTool(ownerFarmId, tool.toolId, tool.toolType?.name)
                    val jo = res.toJsonObject()
                    if (ResChecker.checkRes(TAG, jo)) {
                        StructuredLog.farm("手动使用道具 [${tool.toolType?.nickName()}] 成功 (${index + 1}/$actualCount)")
                    } else {
                        val msg = jo.optString("memo", "未知错误")
                        StructuredLog.farm("庄园🐔 使用道具失败: $msg")
                        return@repeat
                    }
                    // 使用多个时稍微延迟，避免过快（suspend delay 可响应协程取消）
                    if (actualCount > 1) delay(1000)
                }
            }
        } catch (t: Throwable) {
            StructuredLog.farm("庄园🐔 手动道具异常 出错: ${t.message}")
            StructuredLog.printStackTrace(t)
        }
    }
}
