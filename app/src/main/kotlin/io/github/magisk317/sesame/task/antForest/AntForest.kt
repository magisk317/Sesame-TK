package io.github.magisk317.sesame.task.antForest

import android.annotation.SuppressLint
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.sesame.data.RuntimeInfo
import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.entity.AlipayUser
import io.github.magisk317.sesame.entity.CollectEnergyEntity
import io.github.magisk317.sesame.entity.KVMap
import io.github.magisk317.sesame.entity.OtherEntityProvider.listEcoLifeOptions
import io.github.magisk317.sesame.entity.OtherEntityProvider.listHealthcareOptions
import io.github.magisk317.sesame.entity.VitalityStore
import io.github.magisk317.sesame.entity.VitalityStore.Companion.getNameById
import io.github.magisk317.sesame.task.GameTask
import io.github.magisk317.sesame.hook.RequestManager.requestString
import io.github.magisk317.sesame.hook.Toast
import io.github.magisk317.sesame.hook.rpc.intervallimit.FixedOrRangeIntervalLimit
import io.github.magisk317.sesame.hook.rpc.intervallimit.IntervalLimit
import io.github.magisk317.sesame.hook.rpc.intervallimit.RpcIntervalLimit.addIntervalLimit
import io.github.magisk317.sesame.model.BaseModel
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.ChoiceModelField
import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField
import io.github.magisk317.sesame.model.modelFieldExt.ListModelField
import io.github.magisk317.sesame.model.modelFieldExt.ListModelField.ListJoinCommaToStringModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.model.modelFieldExt.StringModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.task.TaskCommon
import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.task.antForest.ForestUtil.hasBombCard
import io.github.magisk317.sesame.task.antForest.ForestUtil.hasShield
import io.github.magisk317.sesame.ui.ObjReference
import io.github.magisk317.sesame.util.Average
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.ListUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.Notify.updateLastExecText
import io.github.magisk317.sesame.util.Notify.updateStatusText
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.TaskBlacklist
import io.github.magisk317.sesame.util.TimeCounter
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.telemetry.SesameTelemetry
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optLong
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.min
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

/**
 * 蚂蚁森林V2
 */
class AntForest : ModelTask(), EnergyCollectCallback {
    private fun parseJsonObject(raw: String): JsonObject = raw.toJsonObject()

    @Volatile
    private var errorWait = false
    @Volatile
    private var canConsumeAnimalProp = false
    private val totalCollected = AtomicInteger(0)

    var giveEnergyRainList: SelectModelField? = null
    var medicalHealthOption: SelectModelField? = null
    var ecoLifeOption: SelectModelField? = null
    var ecoLifeOpen: BooleanModelField? = null

    private val taskCount = AtomicInteger(0)
    private val isEnergyLoopRunning = AtomicBoolean(false)
    private var selfId: String? = null
    private var tryCountInt: Int? = null
    private var retryIntervalInt: Int? = null
    private var collectIntervalEntity: IntervalLimit? = null
    private var doubleCollectIntervalEntity: IntervalLimit? = null

    /**
     * 双击卡结束时间
     */
    @Volatile
    private var doubleEndTime: Long = 0

    /**
     * 隐身卡结束时间
     */
    @Volatile
    private var stealthEndTime: Long = 0

    /**
     * 保护罩结束时间
     */
    @Volatile
    private var shieldEndTime: Long = 0

    /**
     * 炸弹卡结束时间
     */
    @Volatile
    private var energyBombCardEndTime: Long = 0

    /**
     * 1.1倍能量卡结束时间
     */
    @Volatile
    private var robExpandCardEndTime: Long = 0

    /** lzw add begin */
    @Volatile
    private var monday = false

    /** lzw add end */
    private val delayTimeMath = Average(5)
    private val collectEnergyLockLimit = ObjReference(0L)
    private val doubleCardLockObj = Any()
    private val forestPropUsagePlanner = ForestPropUsagePlanner()
    private val forestEnergyRainChanceService = ForestEnergyRainChanceService(
        hasRunToday = { Status.hasFlagToday("AntForest::useEnergyRainChanceCard") },
        findProp = ::findPropBag,
        queryBag = ::queryPropList,
        useProp = { usePropBag(it) },
        exchangeChance = {
            val skuInfo = Vitality.findSkuInfoBySkuName("能量雨次卡")
            skuInfo != null && Status.canVitalityExchangeToday(skuInfo.getString("skuId"), 1) &&
                Vitality.VitalityExchange(
                    skuInfo.getString("spuId"),
                    skuInfo.getString("skuId"),
                    "限时能量雨机会",
                )
        },
        markRunToday = { Status.setFlagToday("AntForest::useEnergyRainChanceCard") },
    )
    private val forestGameCenterService = ForestGameCenterService(
        queryList = ForestGameCenterRpc::queryList,
        drawAward = ForestGameCenterRpc::drawAward,
        reportTask = GameTask.Forest_slxcc::report,
    )
    private val forestRobExpandCardService = ForestRobExpandCardService(
        findProp = ::findPropBag,
        useProp = { usePropBag(it) },
        onActivated = {
            robExpandCardEndTime = System.currentTimeMillis() + 5 * TimeFormatter.ONE_MINUTE_MS
        },
    )
    private val forestTakeLookService = ForestTakeLookService(
        currentUserId = { selfId },
        takeLook = { skipUsers -> ForestCoreRpc.takeLook(skipUsers).toJsonObject() },
        queryFriendHome = { userId -> queryFriendHome(userId, "TAKE_LOOK") },
        collectEnergy = { userId, home, fromTag -> collectEnergy(userId, home, fromTag) },
    )
    private val forestBubbleBoostService = ForestBubbleBoostService(
        queryHome = ::querySelfHome,
        findProp = ::findPropBag,
        queryBag = { queryPropList() },
        useProp = { prop -> usePropBag(prop, needRefreshHome = false) },
        collectSelf = ::collectSelfEnergyImmediately,
    )
    private val forestRankingCollectionService = ForestRankingCollectionService(
        currentUserId = { selfId },
        processFriends = { source, flag, sourceName ->
            processFriendsEnergyCoroutine(source, flag, sourceName)
        },
    )
    private val forestProtectionCardService = ForestProtectionCardService(
        findProp = ::findPropBag,
        queryBag = { forestPropService.query() },
        queryHome = ::querySelfHome,
        useProp = { forestPropService.use(it) },
        exchangeShield = ::exchangeEnergyShield,
        exchangeBomb = {
            val sku = Vitality.findSkuInfoBySkuName("能量炸弹卡")
            sku != null && Status.canVitalityExchangeToday(sku.getString("skuId"), 1) &&
                Vitality.VitalityExchange(sku.getString("spuId"), sku.getString("skuId"), "能量炸弹卡")
        },
        updateHome = ::updateSelfHomePage,
    )
    private val forestDoubleCardService = ForestDoubleCardService(
        choice = { doubleCard!!.value },
        constantRenewal = { doubleCardConstant!!.value },
        canUseToday = { AntForest.canDoubleToday() },
        queryBag = { forestPropService.query() },
        exchange = ::exchangeDoubleCard,
        isWithinUseTime = ::hasDoubleCardTime,
        useProp = { forestPropService.use(it) },
        onActivated = {
            doubleEndTime = System.currentTimeMillis() + 5 * TimeFormatter.ONE_MINUTE_MS
            AntForest.doubleToday()
        },
    )
    private val forestPropPreparationService = ForestPropPreparationService(
        planner = forestPropUsagePlanner,
        queryBag = { forestPropService.query() },
        useDouble = ::useDoubleCard,
        useRobExpand = ::userobExpandCard,
        useStealth = ::useStealthCard,
        useBubbleBoost = { useCardBoot(bubbleBoostTime!!.value, "加速卡") { useBubbleBoostCard() } },
        useShield = ::useShieldCard,
        useBomb = ::useEnergyBombCard,
    )

    // 并发控制信号量，限制同时处理的好友数量，避免过多并发导致性能问题
    // 设置为60
    private val forestFriendBatchService = ForestFriendBatchService(
        errorWait = { errorWait },
        fillUserRobFlag = { userIds, needFillUserInfo ->
            if (needFillUserInfo) {
                ForestCoreRpc.fillUserRobFlag(userIds, true)
            } else {
                ForestCoreRpc.fillUserRobFlag(userIds)
            }
        },
        processFriend = ::processEnergyInternal,
        displayName = { userId -> UserMap.getMaskName(userId) },
    )

    private var collectEnergy: BooleanModelField? = null // 收集能量开关
    private var pkEnergy: BooleanModelField? = null // PK能量开关
    private var energyRain: BooleanModelField? = null // 能量雨开关
    private var advanceTime: IntegerModelField? = null // 提前时间（毫秒）
    private var tryCount: IntegerModelField? = null // 尝试收取次数
    private var retryInterval: IntegerModelField? = null // 重试间隔（毫秒）
    private var dontCollectList: SelectModelField? = null // 不收取能量的用户列表
    private var collectWateringBubble: BooleanModelField? = null // 收取浇水金球开关
    private var batchRobEnergy: BooleanModelField? = null // 批量收取能量开关
    private var collectSelfEnergyType: ChoiceModelField? = null // 收自己能量方式
    private var collectSelfEnergyThreshold: IntegerModelField? = null // 收自己能量阈值
    private var robExpandCardLimt: IntegerModelField? = null//收取翻倍能量阈值
    private var collectBombEnergyLimit: IntegerModelField? = null // 炸弹能量收取阈值
    private var balanceNetworkDelay: BooleanModelField? = null // 平衡网络延迟开关
    var whackMoleMode: ChoiceModelField? = null // 6秒拼手速开关

    /** 6秒拼手速游戏局数配置 */
    var whackMoleGames: IntegerModelField? = null
    var whackMoleMoleCount: IntegerModelField? = null
    var whackMoleTime: StringModelField? = null // 6秒拼手速执行时间

    // 6秒拼手速模式选择
    val whackMoleModeNames = arrayOf("关闭", "兼容", "激进")
    private var collectProp: BooleanModelField? = null // 收集道具开关
    private var queryInterval: StringModelField? = null // 查询间隔时间
    private var collectInterval: StringModelField? = null // 收取间隔时间
    private var doubleCollectInterval: StringModelField? = null // 双击间隔时间
    private var doubleCard: ChoiceModelField? = null // 双击卡类型选择
    private var doubleCardTime: ListJoinCommaToStringModelField? = null // 双击卡使用时间列表
    var doubleCountLimit: IntegerModelField? = null // 双击卡使用次数限制

    private var doubleCardConstant: BooleanModelField? = null // 双击卡永动机
    // 精简版不展示这些旧配置，但运行路径仍会读取它们。保留显式的关闭态，
    // 避免隐藏设置变成 null 并中断整个森林任务。
    private var stealthCard: ChoiceModelField = ChoiceModelField(
        "stealthCard",
        "隐身卡开关 | 消耗类型",
        ApplyPropType.CLOSE,
        ApplyPropType.nickNames
    )
    private var stealthCardConstant: BooleanModelField =
        BooleanModelField("stealthCardConstant", "限时隐身永动机 | 开关", false)
    private var shieldCard: ChoiceModelField? = null // 保护罩
    private var shieldCardConstant: BooleanModelField? = null // 限时保护永动机
    private var helpFriendCollectType: ChoiceModelField = ChoiceModelField(
        "helpFriendCollectType",
        "复活能量 | 选项",
        HelpFriendCollectType.NONE,
        HelpFriendCollectType.nickNames
    )
    private var helpFriendCollectList: SelectModelField = SelectModelField(
        "helpFriendCollectList",
        "复活能量 | 好友列表",
        LinkedHashSet<String?>()
    ) { AlipayUser.getList() }

    private var alternativeAccountList: SelectModelField = SelectModelField(
        "alternativeAccountList",
        "小号列表",
        LinkedHashSet<String?>()
    ) { AlipayUser.getList() }

    // 显示背包内容
    private var showBagList: BooleanModelField =
        BooleanModelField("showBagList", "显示背包内容", false)

    private var vitalityExchangeList: SelectAndCountModelField? = null
    private var returnWater33: IntegerModelField? = null
    private var returnWater18: IntegerModelField? = null
    private var returnWater10: IntegerModelField? = null
    private var receiveForestTaskAward: BooleanModelField? = null
    private var waterFriendList: SelectAndCountModelField? = null
    private var waterFriendCount: IntegerModelField? = null
    private var notifyFriend: BooleanModelField? = null
    private var vitalityExchange: BooleanModelField? = null
    private var userPatrol: BooleanModelField? = null
    private var collectGiftBox: BooleanModelField? = null
    private var medicalHealth: BooleanModelField? = null //医疗健康开关
    private var forestMarket: BooleanModelField? = null
    private var combineAnimalPiece: BooleanModelField? = null
    private var consumeAnimalProp: BooleanModelField? = null
    private var whoYouWantToGiveTo: SelectModelField = SelectModelField(
        "whoYouWantToGiveTo",
        "赠送 | 道具",
        LinkedHashSet<String?>()
    ) { AlipayUser.getList() }
    private var bubbleBoostCard: ChoiceModelField? = null //加速卡
    private var ecoLife: BooleanModelField? = null
    private var ecoLifeTime: StringModelField? = null // 绿色行动执行时间
    private var giveProp: BooleanModelField =
        BooleanModelField("giveProp", "赠送道具", false)

    private var robExpandCard: ChoiceModelField? = null //1.1倍能量卡
    private var robExpandCardTime: ListModelField? = null //1.1倍能量卡时间

    private var cycleinterval: IntegerModelField? = null
    private var energyRainChance: BooleanModelField? = null
    private var energyRainTime: StringModelField? = null // 能量雨执行时间

    /**
     * 能量炸弹卡
     */
    private var energyBombCardType: ChoiceModelField? = null

    /**
     * 用户名缓存：userId -> userName 的映射
     */
    private val forestUserNameService = ForestUserNameService()

    /**
     * 已处理用户缓存：记录本轮已处理过的用户ID，避免重复处理
     */
    private val processedUsersCache: ConcurrentHashMap.KeySetView<String, Boolean> = ConcurrentHashMap.newKeySet()

    /**
     * 空森林缓存，用于记录在本轮任务中已经确认没有能量的好友。
     * 在每轮蚂蚁森林任务开始时清空（见run方法finally块）。
     * “一轮任务”通常指由"执行间隔"触发的一次完整的好友遍历。
     */
    private val emptyForestCache: ConcurrentHashMap<String, Long> = ConcurrentHashMap<String, Long>()

    /**
     * 跳过用户缓存，用于记录有保护罩或其他需要跳过的用户
     * Key: 用户ID，Value: 跳过原因（如"baohuzhao"表示有保护罩）
     */
    private val skipUsersCache: ConcurrentHashMap<String, String> = ConcurrentHashMap<String, String>()

    private var forestChouChouLe: BooleanModelField? = null //森林抽抽乐

    /**
     * 加速器定时
     */
    private var bubbleBoostTime: ListJoinCommaToStringModelField? = null

    private val forestTaskTryCount: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap<String, AtomicInteger>()
    private val forestTaskService = ForestTaskService(
        taskTryCount = forestTaskTryCount,
        waitForGame = ::waitForHuaLingShiCompletion,
        onError = ::handleException,
    )
    private val forestPatrolService = ForestPatrolService()
    private val forestAnimalService = ForestAnimalService()
    private val forestFriendSupportService = ForestFriendSupportService { selfId }
    private val forestFriendEnergyService = ForestFriendEnergyService(
        currentUserId = { selfId },
        errorWaiting = { errorWait },
        isEmptyForest = { emptyForestCache.containsKey(it) },
        collectEnabled = { collectEnergy?.value == true },
        pkCollectEnabled = { pkEnergy?.value == true },
        excluded = { jsonCollectMap.contains(it) },
        shouldProtect = {
            helpFriendCollectType.value != HelpFriendCollectType.NONE &&
                it.optBoolean("canProtectBubble")
        },
        shouldCollectGift = { collectGiftBox?.value == true && it.optBoolean("canCollectGiftBox") },
        isProtected = ::isIsProtected,
        queryHome = ::queryFriendHome,
        collectEnergy = ::collectEnergy,
        protect = forestFriendSupportService::protectFriendEnergy,
        collectGift = forestFriendSupportService::collectGiftBox,
    )
    private val forestHomeRewardService = ForestHomeRewardService({ selfId }, ::querySelfHome)
    private val forestSelfPropRewardService = ForestSelfPropRewardService(
        isTeam = ::isTeam,
        onCollected = {
            totalCollected.addAndGet(it)
            SesameTelemetry.recordEnergyCollected(it)
        },
        refreshHome = ::updateSelfHomePage,
    )
    private val forestWaterService = ForestWaterService()
    private val forestEnergyCollectionResultService = ForestEnergyCollectionResultService(
        emoji = { emojiList[random.nextInt(emojiList.size)] },
        onCollected = {
            totalCollected.addAndGet(it)
            SesameTelemetry.recordEnergyCollected(it)
        },
        showToast = Toast::show,
        water = { userId, bizNo, count ->
            forestWaterService.water(userId, bizNo, 1, count, notifyFriend!!.value)
        },
    )
    private val forestEnergyDispatchService = ForestEnergyDispatchService(
        batchEnabled = { batchRobEnergy?.value == true },
        dispatch = ::collectEnergy,
    )
    private val forestEnergyResultService = ForestEnergyResultService()
    private val forestEnergyRpcExecutor = ForestEnergyRpcExecutor(
        collectLock = collectEnergyLockLimit,
        collectInterval = { collectIntervalEntity!! },
        doubleCollectInterval = { doubleCollectIntervalEntity!! },
        retryInterval = { retryIntervalInt!! },
        request = { requestString(it) },
        balanceNetworkDelay = { balanceNetworkDelay!!.value },
        recordNetworkDelay = { delayTimeMath.nextInteger(it) },
    )
    private val forestEnergyCollectionService = ForestEnergyCollectionService(
        errorWait = { errorWait },
        setErrorWait = { errorWait = it },
        prepareProps = ::usePropBeforeCollectEnergy,
        executor = forestEnergyRpcExecutor,
        resultService = forestEnergyResultService,
        collectionResultService = forestEnergyCollectionResultService,
        tryCount = { tryCountInt ?: 0 },
        userName = ::getAndCacheUserName,
        checkFrequencyError = { userId, response -> ForestUtil.checkAndRecordFrequencyError(userId, response) },
        returnCount = ::getReturnCount,
        onTaskStarted = { taskCount.incrementAndGet() },
        onFinished = {
            updateLastExecText("本次总 收:${totalCollected.get()}g 帮:${TOTAL_HELP_COLLECTED}g 浇:${TOTAL_WATERED}g")
            taskCount.decrementAndGet()
        },
    )
    private val forestActivePropService = ForestActivePropService(
        isTeam = ::isTeam,
        onActiveProp = { group, endTime ->
            when (group) {
                "doubleClick" -> doubleEndTime = endTime
                "stealthCard" -> stealthEndTime = endTime
                "shield" -> shieldEndTime = endTime
                "energyBombCard" -> energyBombCardEndTime = endTime
                "robExpandCard" -> robExpandCardEndTime = endTime
            }
        },
        onRobExpandEnergy = { prop, propName ->
            val extInfo = parseJsonObject(prop.getString("extInfo"))
            val leftEnergy = extInfo.optString("leftEnergy", "0").toDouble()
            if (leftEnergy > robExpandCardLimt!!.value ||
                (extInfo.optString("overLimitToday", "false") == "true" && leftEnergy >= 1)
            ) {
                val result = parseJsonObject(
                    ForestPropRpc.collectRobExpandEnergy(prop.getString("propId"), prop.getString("propType"))
                )
                if (ResChecker.checkRes(TAG, result)) {
                    StructuredLog.forest("翻倍能量🌳[${result.optInt("collectEnergy")}g][$propName]")
                }
            }
        },
    )
    private val forestPropService = ForestPropService(
        refreshHome = ::updateSelfHomePage,
        onPropName = ::propEmoji,
        onError = ::handleException,
    )
    private val forestBubbleService = ForestBubbleService(
        isTeam = ::isTeam,
        selfId = { selfId },
        userName = ::getAndCacheUserName,
        shouldCollectSelfBubble = ::shouldCollectSelfBubble,
    )
    private val forestHomeQueryService = ForestHomeQueryService(::updateSelfHomePage)

    private var jsonCollectMap: MutableSet<String?> = HashSet()

    var emojiList: ArrayList<String> = ArrayList(
        listOf(
            "🍅", "🍓", "🥓", "🍂", "🍚", "🌰", "🟢", "🌴",
            "🥗", "🧀", "🥩", "🍍", "🌶️", "🍲", "🍆", "🥕",
            "✨", "🍑", "🍘", "🍀", "🥞", "🍈", "🥝", "🧅",
            "🌵", "🌾", "🥜", "🍇", "🌭", "🥑", "🥐", "🥖",
            "🍊", "🌽", "🍉", "🍖", "🍄", "🥚", "🥙", "🥦",
            "🍌", "🍱", "🍏", "🍎", "🌲", "🌿", "🍁", "🍒",
            "🥔", "🌯", "🌱", "🍐", "🍞", "🍳", "🍙", "🍋",
            "🍗", "🌮", "🍃", "🥘", "🥒", "🧄", "🍠", "🥥"
        )
    )
    private val random = Random()

    // {{ 新增接口定义：收自己能量的方式 }}
    interface CollectSelfType {
        companion object {
            const val ALL = 0
            const val OVER_THRESHOLD = 1
            const val BELOW_THRESHOLD = 2
            val nickNames = arrayOf("所有", "大于阈值", "小于阈值")
        }
    }

    override fun getName(): String {
        return "蚂蚁森林"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.FOREST
    }

    override fun getIcon(): String {
        return "AntForest.png"
    }

    interface ApplyPropType {
        companion object {
            const val CLOSE: Int = 0
            const val ALL: Int = 1
            const val ONLY_LIMIT_TIME: Int = 2
            val nickNames: Array<String?> = arrayOf<String?>("关闭", "所有道具", "限时道具")
        }
    }

    interface HelpFriendCollectType {
        companion object {
            const val NONE: Int = 0
            const val HELP: Int = 1
            val nickNames: Array<String?> = arrayOf<String?>("关闭", "选中复活", "选中不复活")
        }
    }

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(
            BooleanModelField(
                "collectEnergy",
                "收集能量 | 开关",
                false
            ).also { collectEnergy = it })
        modelFields.addField(
            BooleanModelField(
                "batchRobEnergy",
                "一键收取 | 开关",
                false
            ).also { batchRobEnergy = it })
        modelFields.addField(
            BooleanModelField(
                "pkEnergy",
                "Pk榜收取 | 开关",
                false
            ).also { pkEnergy = it })
        // 在 ModelFields 定义中修改
        modelFields.addField(
            ChoiceModelField(
                "whackMoleMode",
                "🎮 6秒拼手速 | 运行模式",
                0, // 默认值为 0 (关闭)
                whackMoleModeNames
            ).also { whackMoleMode = it }
        )
        modelFields.addField(
            IntegerModelField(
                "whackMoleGames",
                "🎮 6秒拼手速 | 激进模式局数",
                5,
            ).also { whackMoleGames = it })
        modelFields.addField(
            IntegerModelField(
                "whackMoleMoleCount",
                "🎮 6秒拼手速 | 兼容模式击打数",
                15,
            ).also { whackMoleMoleCount = it })
        modelFields.addField(
            StringModelField(
                "whackMoleTime",
                "🎮 6秒拼手速 | 执行时间",
                "0820"
            ).also { whackMoleTime = it }
        )
        modelFields.addField(
            BooleanModelField(
                "energyRain",
                "能量雨 | 开关",
                false
            ).also { energyRain = it })
        modelFields.addField(
            StringModelField(
                "energyRainTime",
                "能量雨 | 默认8点10分后执行",
                "0810"
            ).also { energyRainTime = it })
        modelFields.addField(
            ChoiceModelField(
                "CollectSelfEnergyType",
                "收自己单个能量球 | 方式",
                CollectSelfType.ALL,
                CollectSelfType.nickNames
            ).also { collectSelfEnergyType = it }
        )
        modelFields.addField(
            IntegerModelField(
                "CollectSelfEnergyThreshold",
                "收自己单个能量球阈值",
                0,
                0,
                10000
            ).also { collectSelfEnergyThreshold = it }
        )
        modelFields.addField(
            IntegerModelField(
                "robExpandCardLimt",
                "收取翻倍能量阈值",
                20000,
                1,
                20000
            ).also { robExpandCardLimt = it }
        )

        modelFields.addField(
            IntegerModelField(
                "CollectBombEnergyLimit",
                "单个炸弹能量大于该值收取",
                0,
                0,
                100000
            ).also { collectBombEnergyLimit = it }
        )
        modelFields.addField(
            SelectModelField(
                "dontCollectList",
                "不收能量 | 配置列表",
                LinkedHashSet<String?>()
            ) { AlipayUser.getList() }.also {
                dontCollectList = it
            })
        modelFields.addField(
            SelectModelField(
                "giveEnergyRainList",
                "赠送能量雨 | 配置列表",
                LinkedHashSet<String?>()
            ) { AlipayUser.getList() }.also {
                giveEnergyRainList = it
            })
        modelFields.addField(
            BooleanModelField(
                "energyRainChance",
                "兑换使用能量雨次卡 | 开关",
                false
            ).also { energyRainChance = it })
        modelFields.addField(
            BooleanModelField(
                "collectWateringBubble",
                "收取浇水金球 | 开关",
                false
            ).also { collectWateringBubble = it })
        modelFields.addField(
            ChoiceModelField(
                "doubleCard",
                "双击卡开关 | 消耗类型",
                ApplyPropType.CLOSE,
                ApplyPropType.nickNames
            ).also { doubleCard = it })
        modelFields.addField(
            IntegerModelField(
                "doubleCountLimit",
                "双击卡 | 使用次数",
                6
            ).also { doubleCountLimit = it })
        modelFields.addField(
            ListJoinCommaToStringModelField(
                "doubleCardTime", "双击卡 | 使用时间/范围", ListUtil.newArrayList(
                    "0000", "0650", "0700","2355"
                )
            ).also { doubleCardTime = it })
        // 双击卡永动机
        modelFields.addField(
            BooleanModelField(
                "DoubleCardConstant", "限时双击永动机 | 开关", false
            ).also { doubleCardConstant = it }
        )
        modelFields.addField(
            ChoiceModelField(
                "bubbleBoostCard",
                "加速器开关 | 消耗类型",
                ApplyPropType.CLOSE,
                ApplyPropType.nickNames
            ).also { bubbleBoostCard = it })
        modelFields.addField(
            ListJoinCommaToStringModelField(
                "bubbleBoostTime", "加速器 | 使用时间/不能范围", ListUtil.newArrayList(
                    "0030,0630",
                    "0700",
                    "1200",
                    "1730",
                    "2359"
                )
            ).also { bubbleBoostTime = it })
        modelFields.addField(
            ChoiceModelField(
                "shieldCard",
                "保护罩开关 | 消耗类型",
                ApplyPropType.CLOSE,
                ApplyPropType.nickNames
            ).also { shieldCard = it })
        modelFields.addField(
            BooleanModelField(
                "shieldCardConstant",
                "限时保护永动机 | 开关",
                false
            ).also { shieldCardConstant = it })

        modelFields.addField(
            ChoiceModelField(
                "energyBombCardType", "炸弹卡开关 | 消耗类型", ApplyPropType.CLOSE,
                ApplyPropType.nickNames, "若开启了保护罩，则不会使用炸弹卡"
            ).also { energyBombCardType = it })
        modelFields.addField(
            ChoiceModelField(
                "robExpandCard",
                "1.1倍能量卡开关 | 消耗类型",
                ApplyPropType.CLOSE,
                ApplyPropType.nickNames
            ).also { robExpandCard = it })
        //1.1倍能量卡时间
        modelFields.addField(
            ListJoinCommaToStringModelField(
                "robExpandCardTime", "1.1倍能量卡 | 使用时间/不能范围",
                ListUtil.newArrayList(
                    "0700",
                    "0730",
                    "1200",
                    "1230",
                    "1700",
                    "1730",
                    "2000",
                    "2030",
                    "2359"
                )
            ).also { robExpandCardTime = it }
        )
        /*
        modelFields.addField(
            ChoiceModelField(
                "stealthCard",
                "隐身卡开关 | 消耗类型",
                ApplyPropType.CLOSE,
                ApplyPropType.nickNames
            ).also { stealthCard = it })
        modelFields.addField(
            BooleanModelField(
                "stealthCardConstant",
                "限时隐身永动机 | 开关",
                false
            ).also { stealthCardConstant = it })
         */
        modelFields.addField(
            IntegerModelField(
                "returnWater10",
                "返水 | 10克需收能量(关闭:0)",
                0
            ).also { returnWater10 = it })
        modelFields.addField(
            IntegerModelField(
                "returnWater18",
                "返水 | 18克需收能量(关闭:0)",
                0
            ).also { returnWater18 = it })
        modelFields.addField(
            IntegerModelField(
                "returnWater33",
                "返水 | 33克需收能量(关闭:0)",
                0
            ).also { returnWater33 = it })
        modelFields.addField(
            SelectAndCountModelField(
                "waterFriendList",
                "浇水 | 好友列表",
                LinkedHashMap<String?, Int?>(),
                { AlipayUser.getList() },
                "记得设置浇水次数"
            ).also { waterFriendList = it })
        modelFields.addField(
            IntegerModelField(
                "waterFriendCount",
                "浇水 | 克数(10 18 33 66)",
                66
            ).also { waterFriendCount = it })
        modelFields.addField(
            BooleanModelField(
                "notifyFriend",
                "浇水 | 通知好友",
                false
            ).also { notifyFriend = it })
        /*
        modelFields.addField(
            BooleanModelField(
                "giveProp",
                "赠送道具",
                false
            ).also { giveProp = it })
        modelFields.addField(
            SelectModelField(
                "whoYouWantToGiveTo",
                "赠送 | 道具",
                LinkedHashSet<String?>(),
                { AlipayUser.getList() },
                "所有可赠送的道具将全部赠"
            ).also { whoYouWantToGiveTo = it })
         */
        modelFields.addField(
            BooleanModelField(
                "collectProp",
                "收集道具",
                false
            ).also { collectProp = it })

        modelFields.addField(
            ChoiceModelField(
                "helpFriendCollectType",
                "复活能量 | 选项",
                HelpFriendCollectType.NONE,
                HelpFriendCollectType.nickNames
            ).also { helpFriendCollectType = it })
        modelFields.addField(
            SelectModelField(
                "helpFriendCollectList",
                "复活能量 | 好友列表",
                LinkedHashSet<String?>()
            ) { AlipayUser.getList() }.also {
                helpFriendCollectList = it
            })
        modelFields.addField(
            SelectModelField(
                "alternativeAccountList",
                "小号列表",
                LinkedHashSet<String?>()
            ) { AlipayUser.getList() }.also {
                alternativeAccountList = it
            })
        modelFields.addField(BooleanModelField("vitalityExchange", "活力值 | 兑换开关", false).also { vitalityExchange = it })
        modelFields.addField(
            SelectAndCountModelField(
                "vitalityExchangeList", "活力值 | 兑换列表", LinkedHashMap<String?, Int?>(),
                VitalityStore::list,
                "记得填兑换次数..亲爱的"
            ).also { vitalityExchangeList = it })
        modelFields.addField(BooleanModelField("userPatrol", "保护地巡护", false).also { userPatrol = it })
        modelFields.addField(BooleanModelField("combineAnimalPiece", "合成动物碎片", false).also { combineAnimalPiece = it })
        modelFields.addField(BooleanModelField("consumeAnimalProp", "派遣动物伙伴", false).also { consumeAnimalProp = it })
        modelFields.addField(BooleanModelField("receiveForestTaskAward", "森林任务", false).also { receiveForestTaskAward = it })

        modelFields.addField(BooleanModelField("forestChouChouLe", "森林寻宝任务", false).also { forestChouChouLe = it })

        modelFields.addField(BooleanModelField("collectGiftBox", "领取礼盒", false).also { collectGiftBox = it })

        modelFields.addField(BooleanModelField("medicalHealth", "健康医疗任务 | 开关", false).also { medicalHealth = it })
        modelFields.addField(
            SelectModelField(
                "medicalHealthOption", "健康医疗 | 选项", LinkedHashSet<String?>(), listHealthcareOptions(),
                "医疗健康需要先完成一次医疗打卡"
            ).also { medicalHealthOption = it })

        modelFields.addField(BooleanModelField("forestMarket", "森林集市", false).also { forestMarket = it })
        modelFields.addField(BooleanModelField("ecoLife", "绿色行动 | 开关", false).also { ecoLife = it })
        modelFields.addField(StringModelField("ecoLifeTime", "绿色行动 | 默认8点后执行", "0800").also { ecoLifeTime = it })
        modelFields.addField(BooleanModelField("ecoLifeOpen", "绿色任务 |  自动开通", false).also { ecoLifeOpen = it })
        modelFields.addField(
            SelectModelField(
                "ecoLifeOption", "绿色行动 | 选项", LinkedHashSet<String?>(), listEcoLifeOptions(), "光盘行动需要先完成一次光盘打卡"
            ).also { ecoLifeOption = it })

        modelFields.addField(StringModelField("queryInterval", "查询间隔(毫秒或毫秒范围)", "500-1500").also { queryInterval = it })
        modelFields.addField(StringModelField("collectInterval", "收取间隔(毫秒或毫秒范围)", "500-1500").also { collectInterval = it })
        modelFields.addField(StringModelField("doubleCollectInterval", "双击间隔(毫秒或毫秒范围)", "50-150").also { doubleCollectInterval = it })
        modelFields.addField(BooleanModelField("balanceNetworkDelay", "平衡网络延迟", true).also { balanceNetworkDelay = it })
        modelFields.addField(IntegerModelField("advanceTime", "提前时间(毫秒)", 0, Int.MIN_VALUE, 500).also { advanceTime = it })
        modelFields.addField(IntegerModelField("tryCount", "尝试收取(次数)", 3, 0, 5).also { tryCount = it })
        modelFields.addField(IntegerModelField("retryInterval", "重试间隔(毫秒)", 1200, 0, 10000).also { retryInterval = it })
        modelFields.addField(IntegerModelField("cycleinterval", "循环间隔(毫秒)", 1500, 0, 10000).also { cycleinterval = it })
        //modelFields.addField(BooleanModelField("showBagList", "显示背包内容", true).also { showBagList = it })
        return modelFields
    }

    override fun check(): Boolean {
        if (!super.check()) return false
        val currentTime = System.currentTimeMillis()
        // 1️⃣ 异常等待状态
        val runtimeInfo = RuntimeInfo.getInstance()
        val forestPauseTime = runtimeInfo.getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime)
        if (forestPauseTime > currentTime) {
            StructuredLog.record(
                getName() + "任务-异常等待中，暂不执行检测！恢复时间：" +
                    TimeUtil.getCommonDate(forestPauseTime),
            )
            return false
        }
        if (forestPauseTime > 0L) {
            // The deadline is persisted across Alipay process restarts. Clear it
            // once observed as expired so stale state cannot mask later runs.
            runtimeInfo.put(RuntimeInfo.RuntimeInfoKey.ForestPauseTime, 0L)
            StructuredLog.record(getName() + "任务-异常等待解除，恢复执行")
        }
        // -----------------------------
        // 3️⃣ 只收能量时间段判断
        // -----------------------------
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val isEnergyTime = TaskCommon.IS_ENERGY_TIME || hour == 7 && minute < 30
        if (isEnergyTime) {
            // 关键改动：将循环放入协程，避免阻塞TaskRunner；delay 可响应会话取消
            GlobalThreadPools.execute(block = { startEnergyCollectionLoop() })
            return false // 只收能量期间不执行正常任务，check()立刻返回
        }
        return true
    }

    /**
     * 只收能量时间的循环任务（协程版本）
     */
    private suspend fun startEnergyCollectionLoop() {
        if (!isEnergyLoopRunning.compareAndSet(false, true)) {
            StructuredLog.record(TAG, "只收能量循环任务已在运行中，跳过重复启动。")
            return
        }
        try {
            val energyTimeStr = BaseModel.energyTime.value.toString()
            StructuredLog.record(TAG, "森林🌿 只收能量模式【$energyTimeStr】")
            try {
                while (true) {
                    // 每次循环更新状态
                    TaskCommon.update()
                    // 如果不在能量时间段，退出循环
                    val now = Calendar.getInstance()
                    val hour = now.get(Calendar.HOUR_OF_DAY)
                    val minute = now.get(Calendar.MINUTE)
                    if (!(TaskCommon.IS_ENERGY_TIME || hour == 7 && minute < 30)) {
                        StructuredLog.record(TAG, "当前不在只收能量时间段，退出循环")
                        break
                    }
                    // 收取自己能量（协程中执行）
                    val selfHomeObj = querySelfHome()
                    if (selfHomeObj != null) {
                        collectEnergy(UserMap.currentUid, selfHomeObj, "self")
                        StructuredLog.forest("森林🌿 收取自己的能量完成")
                    } else {
                        StructuredLog.error(TAG, "❌ 获取自己主页信息失败，跳过收取自己的能量")
                    }
                    // 只收能量时间段，启用循环查找能量功能
                    try {
                        quickcollectEnergyByTakeLook() // 查找能量（协程）
                    } catch (e: CancellationException) {
                        StructuredLog.record(TAG, "查找能量被取消，退出循环")
                        break
                    }
                    // 循环间隔（suspend delay 可响应协程取消）
                    val sleepMillis = cycleinterval!!.value.toLong()
                    StructuredLog.record(TAG, "森林🌿 只收能量轮询：${TimeFormatter.formatDuration(sleepMillis)} 后执行下一轮")
                    delay(sleepMillis)
                }
            } catch (e: CancellationException) {
                StructuredLog.record(TAG, "只收能量循环被取消")
            }
        } finally {
            isEnergyLoopRunning.set(false)
        }
    }

    /**
     * 创建区间限制对象
     *
     * @param intervalStr 区间字符串，如 "1000-2000"
     * @param defaultMin 默认最小值
     * @param defaultMax 默认最大值
     * @param description 描述，用于日志
     * @return 区间限制对象
     */
    private fun createSafeIntervalLimit(
        intervalStr: String?,
        defaultMin: Int,
        defaultMax: Int,
        description: String?
    ): FixedOrRangeIntervalLimit {
        // 记录原始输入值
        StructuredLog.record(TAG, description + "原始设置值: [" + intervalStr + "]")

        // 使用自定义区间限制类，处理所有边界情况
        val limit = FixedOrRangeIntervalLimit(intervalStr, defaultMin, defaultMax)
        StructuredLog.record(TAG, description + "成功创建区间限制")
        return limit
    }

    override fun boot(classLoader: ClassLoader?) {
        super.boot(classLoader)


        // 安全创建各种区间限制
        val queryIntervalLimit = createSafeIntervalLimit(
            queryInterval!!.value, 10, 10000, "查询间隔"
        )

        // 添加RPC间隔限制
        addIntervalLimit("alipay.antforest.forest.h5.queryHomePage", queryIntervalLimit)
        addIntervalLimit("alipay.antforest.forest.h5.queryFriendHomePage", queryIntervalLimit)
        addIntervalLimit("alipay.antmember.forest.h5.collectEnergy", 300)
        addIntervalLimit("alipay.antmember.forest.h5.queryEnergyRanking", 300)
        addIntervalLimit("alipay.antforest.forest.h5.fillUserRobFlag", 500)

        // 设置其他参数
        tryCountInt = tryCount!!.value
        retryIntervalInt = retryInterval!!.value
        advanceTime!!.value


        jsonCollectMap = dontCollectList!!.value

        // 创建收取间隔实体
        collectIntervalEntity = createSafeIntervalLimit(
            collectInterval!!.value, 50, 10000, "收取间隔"
        )

        // 创建双击收取间隔实体
        doubleCollectIntervalEntity = createSafeIntervalLimit(
            doubleCollectInterval!!.value, 10, 5000, "双击间隔"
        )
        delayTimeMath.clear()


        ForestApiVersion.initialize()

        // 设置蹲点管理器的回调
        EnergyWaitingManager.setEnergyCollectCallback(this)
    }

    override suspend fun runSuspend() {
        val runStartTime = System.currentTimeMillis()
        try {
            // 每次运行时检查并更新计数器
            checkAndUpdateCounters()
            // 正常流程会自动处理所有收取任务，无需特殊处理
            errorWait = false
            // 计数器和时间记录
            monday = true
            val tc = TimeCounter(TAG)
            if (showBagList.value) showBag()

            taskCount.set(0)
            selfId = UserMap.currentUid

            // 抽抽乐包含独立的活动窗口和抽奖余额，不依赖后续收能量流程。
            // 2026-08-09 日志显示原位置在长流程末尾，森林任务达到10分钟后被取消，
            // 导致任务已完成但抽奖阶段始终未执行，因此前置到主流程开始处。
            if (forestChouChouLe!!.value) {
                ForestChouChouLe().chouChouLe()
                tc.countDebug("抽抽乐")
            }

            // -------------------------------
            // 自己使用道具
            // -------------------------------
            // 先查询主页，更新道具状态（双击卡、保护罩等的剩余时间）
            updateSelfHomePage()
            tc.countDebug("查询道具状态")

            usePropBeforeCollectEnergy(selfId)
            tc.countDebug("使用自己道具卡")

            // -------------------------------
            // 收好友能量
            // -------------------------------
            // 先尝试使用找能量功能快速定位有能量的好友（协程）
            StructuredLog.record(TAG, "森林🌿 执行找能量功能（协程）")
            collectEnergyByTakeLook()
            tc.countDebug("找能量收取（协程）")

            // -------------------------------
            // 收PK好友能量
            // -------------------------------
            StructuredLog.record(TAG, "森林🌿 异步执行PK好友能量收取")
            collectPKEnergyCoroutine()  // 好友道具在 collectFriendEnergy 内会自动处理
            tc.countDebug("收PK好友能量（同步）")

            // -------------------------------
            // 收自己能量
            // -------------------------------
            val selfHomeObj = run {
                val obj = querySelfHome()
                tc.countDebug("获取自己主页对象信息")
                if (obj != null) {

                    collectEnergy(UserMap.currentUid, obj, "self")
                    StructuredLog.forest("森林🌿 收取自己的能量完成")
                    tc.countDebug("收取自己的能量")
                } else {
                    StructuredLog.error(TAG, "❌ 【正常流程】获取自己主页信息失败，跳过能量收取")
                    tc.countDebug("跳过自己的能量收取（主页获取失败）")
                }
                obj
            }

            // 然后执行传统的好友排行榜收取（协程）
            StructuredLog.record(TAG, "森林🌿 执行好友能量收取（协程）")
            collectFriendEnergyCoroutine() // 内部会自动调用 usePropBeforeCollectEnergy(userId, false)
            collectProtectCandidatesCoroutine() // 遍历排行榜，按能量高低复活好友能量球
            tc.countDebug("收取好友能量（同步）")

            // -------------------------------
            // 后续任务流程
            // -------------------------------
            if (selfHomeObj != null) {
                // 检查并处理打地鼠（每天一次）
                checkAndHandleWhackMole()
                tc.countDebug("拼手速")

                val processObj = if (isTeam(selfHomeObj)) {
                    selfHomeObj.optJSONObject("teamHomeResult")
                        ?.optJSONObject("mainMember")
                } else {
                    selfHomeObj
                }

                if (collectWateringBubble!!.value) {
                    forestHomeRewardService.collectWateringBubbles(processObj)
                    tc.countDebug("收取浇水金球")
                }
                if (collectProp!!.value) {
                    forestHomeRewardService.collectGivenProps(processObj)
                    tc.countDebug("收取道具")
                }
                if (userPatrol!!.value) {
                    forestPatrolService.run()
                    tc.countDebug("动物巡护任务")
                }

                canConsumeAnimalProp = forestSelfPropRewardService.collectAnimalEnergy(selfHomeObj)
                tc.countDebug("收取动物派遣能量")

                forestSelfPropRewardService.collectBombEnergy(selfHomeObj)
                tc.countDebug("收取炸弹卡能量")

                if (canConsumeAnimalProp && consumeAnimalProp!!.value) {
                    forestAnimalService.dispatchPartner()
                    tc.countDebug("森林巡护")
                } else {
                    StructuredLog.record("已经有动物伙伴在巡护森林~")
                }

                if (combineAnimalPiece!!.value) {
                    forestAnimalService.combinePieces()
                    tc.countDebug("合成动物碎片")
                }

                if (receiveForestTaskAward!!.value) {
                    forestTaskService.run()
                    tc.countDebug("森林任务")
                }
                if (ecoLife!!.value) {
                    // 检查是否到达执行时间
                    if (TaskTimeChecker.isTimeReached(ecoLifeTime?.value, "0800")) {
                        EcoLife.ecoLife()
                        tc.countDebug("绿色行动")
                    } else {
                        StructuredLog.record(TAG, "绿色行动未到执行时间，跳过")
                    }
                }

                waterFriends()
                tc.countDebug("给好友浇水")

                if (giveProp.value) {
                    giveProp()
                    tc.countDebug("赠送道具")
                }

                if (vitalityExchange!!.value) {
                    handleVitalityExchange()
                    tc.countDebug("活力值兑换")
                }

                if (energyRain!!.value) {
                    // 检查是否到达执行时间
                    if (TaskTimeChecker.isTimeReached(energyRainTime?.value, "0810")) {
                        if (energyRainChance!!.value) {
                            useEnergyRainChanceCard()
                            tc.countDebug("使用能量雨卡")
                        }
                        EnergyRainCoroutine.execEnergyRainCompat()
                        tc.countDebug("能量雨")
                    } else {
                        StructuredLog.record(TAG, "能量雨未到执行时间，跳过")
                    }
                }

                if (forestMarket!!.value) {
                    GreenLife.ForestMarket("GREEN_LIFE")
                    //  GreenLife.ForestMarket("ANTFOREST")  二级条目暂时关闭
                    tc.countDebug("森林集市")
                }

                if (medicalHealth!!.value) {
                    if (medicalHealthOption!!.value.contains("FEEDS")) {
                        Healthcare.queryForestEnergy("FEEDS")
                        tc.countDebug("绿色医疗")
                    }
                    if (medicalHealthOption!!.value.contains("BILL")) {
                        Healthcare.queryForestEnergy("BILL")
                        tc.countDebug("电子小票")
                    }
                }


                doforestgame()


                tc.stop()
            }
        } catch (e: CancellationException) {
            // 协程被取消是正常行为，不记录错误日志
            StructuredLog.record(TAG, "蚂蚁森林任务协程被取消")
            throw e // 重新抛出，让协程系统处理
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "执行蚂蚁森林任务时发生错误: ", t)
        } finally {
            // 计算总耗时
            val totalTime = System.currentTimeMillis() - runStartTime
            val timeInSeconds = totalTime / 1000

            // 优化：不再等待蹲点任务完成，让主任务立即结束
            // 蹲点任务会在后台独立协程中继续运行，不影响其他模块
            val waitingTaskCount = EnergyWaitingManager.getWaitingTaskCount()

            StructuredLog.forest("森林🌿 耗时: ${TimeFormatter.formatDuration(totalTime)} | 收${totalCollected.get()}g 帮${TOTAL_HELP_COLLECTED}g 浇${TOTAL_WATERED}g")
            if (waitingTaskCount > 0) {
                StructuredLog.forest("森林🌿 后台蹲点任务: $waitingTaskCount 个")
            }

            forestUserNameService.clear()
            processedUsersCache.clear()
            // 清空本轮的空森林缓存，以便下一轮（如下次"执行间隔"到达）重新检查所有好友
            emptyForestCache.clear()
            // 清空跳过用户缓存，下一轮重新检测保护罩状态
            skipUsersCache.clear()
            // 清空好友主页缓存
            val strTotalCollected =
                "本次总 收:" + totalCollected.get() + "g 帮:" + TOTAL_HELP_COLLECTED + "g 浇:" + TOTAL_WATERED + "g"
            updateLastExecText(strTotalCollected)
        }
    }

    /**
     * 每日重置
     */
    // 上次检查的日期（用于判断是否跨天）
    private var lastCheckDate: String? = null

    private fun checkAndUpdateCounters() {
        val today = TimeUtil.getDateStr() // 获取当前日期，如 "2025-10-07"
        // 只在日期变化时重置计数器（跨天）
        if (lastCheckDate != today) {
            resetTaskCounters()
            lastCheckDate = today
            StructuredLog.record(TAG, "检测到新的一天[$today]，重置计数器")
        }
    }

    // 重置任务计数器（你需要根据具体任务的计数器来调整）
    private fun resetTaskCounters() {
        taskCount.set(0) // 重置任务计数
        // 每日重置时清空频率限制记录，让所有好友都有新的机会
        ForestUtil.clearAllFrequencyLimits()
        StructuredLog.record(TAG, "任务计数器已重置")
    }

    /**
     * 给好友浇水
     */
    private fun waterFriends() {
        try {
            val friendMap = waterFriendList!!.value
            val notify = notifyFriend!!.value // 获取通知开关状态

            for (friendEntry in friendMap.entries) {
                val uid = friendEntry.key ?: continue
                if (selfId == uid) {
                    continue
                }
                var waterCount = friendEntry.value
                if (waterCount == null || waterCount <= 0) {
                    continue
                }
                waterCount = min(waterCount, 3)

                if (Status.canWaterFriendToday(uid, waterCount)) {
                    try {
                        val response = ForestCoreRpc.queryFriendHomePage(uid, null)
                        val jo = parseJsonObject(response)
                        if (ResChecker.checkRes(TAG, jo)) {
                            val bizNo = jo.getString("bizNo")

                            // ✓ 关键改动：传入通知开关
                            val waterCountKVNode = forestWaterService.water(
                                uid, bizNo, waterCount, waterFriendCount!!.value, notify
                            )

                            val actualWaterCount: Int = waterCountKVNode.key!!
                            if (actualWaterCount > 0) {
                                Status.waterFriendToday(uid, actualWaterCount)
                            }
                            if (java.lang.Boolean.FALSE == waterCountKVNode.value) {
                                break
                            }
                        } else {
                            StructuredLog.error(TAG, "森林🌿 ❌ 好友浇水失败")
                        }
                    } catch (e: RuntimeException) {
                        StructuredLog.record(TAG, "waterFriends JSON解析错误: " + e.message)
                    } catch (t: Throwable) {
                        StructuredLog.printStackTrace(TAG, t)
                    }
                }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "未知错误: " + e.message, e)
        }
    }

    private fun handleVitalityExchange() {
        try {
//            JsonObject bag = getBag();

            Vitality.initVitality("SC_ASSETS")
            val exchangeList = vitalityExchangeList!!.value
            //            Map<String, Integer> maxLimitList = vitalityExchangeMaxList.value;
            for (entry in exchangeList.entries) {
                val skuId = entry.key ?: continue
                val count = entry.value
                if (count == null || count <= 0) {
                    StructuredLog.record(TAG, "无效的count值: skuId=$skuId, count=$count")
                    continue
                }
                // 处理活力值兑换
                while (Status.canVitalityExchangeToday(skuId, count)) {
                    if (!Vitality.handleVitalityExchange(skuId)) {
                        StructuredLog.record(TAG, "活力值兑换失败: " + getNameById(skuId))
                        break
                    }
                    GlobalThreadPools.sleepCompat(1000L)
                }
            }
        } catch (t: Throwable) {
            handleException("handleVitalityExchange", t)
        }
    }

    /**
     * 获取自己主页对象信息
     *
     * @return 用户的主页信息，如果发生错误则返回null。
     */
    private fun querySelfHome(): JsonObject? = forestHomeQueryService.querySelf()

    /**
     * 更新好友主页信息
     *
     * @param userId 好友ID
     * @return 更新后的好友主页信息，如果发生错误则返回null。
     */
    private fun queryFriendHome(userId: String?, fromAct: String?): JsonObject? =
        forestHomeQueryService.queryFriend(userId, fromAct)

    /**
     * 检查并处理6秒拼手速逻辑（每天主动执行一次）
     */
    private fun checkAndHandleWhackMole() {
        try {
            // 获取当前选择的索引 (0, 1, 或 2)
            val modeIndex = whackMoleMode?.value ?: 0

            // 如果索引为 0 (关闭)，直接返回
            if (modeIndex == 0) return

            // 检查执行时间
            val targetTime = whackMoleTime?.value ?: "0820"
            if (TaskTimeChecker.isTimeReached(targetTime, "0820")) {

                val whackMoleFlag = "forest::whackMole::executed"
                if (Status.hasFlagToday(whackMoleFlag)) return

                // 根据索引匹配模式
                when (modeIndex) {
                    1 -> { // 兼容模式
                        StructuredLog.record(TAG, "🎮 触发拼手速任务: 兼容模式")
                        WhackMole.setTotalGames(1)
                        WhackMole.setMoleCount(whackMoleMoleCount?.value ?: 15)
                        WhackMole.start(WhackMole.Mode.COMPATIBLE)
                    }

                    2 -> { // 激进模式
                        StructuredLog.record(TAG, "🎮 触发拼手速任务: 激进模式")
                        val configGames = whackMoleGames?.value ?: 5
                        WhackMole.setTotalGames(configGames)
                        WhackMole.start(WhackMole.Mode.AGGRESSIVE)
                    }
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    /**
     * 收取用户的蚂蚁森林能量。
     *
     * @param userId      用户ID
     * @param userHomeObj 用户主页的JSON对象，包含用户的蚂蚁森林信息
     * @return 更新后的用户主页JSON对象，如果发生异常返回null
     */
    private fun collectEnergy(
        userId: String?,
        userHomeObj: JsonObject?,
        fromTag: String?
    ): JsonObject? {
        try {
            if (userHomeObj == null) {
                return null
            }
            // 1. 检查接口返回是否成功
            if (!ResChecker.checkRes(TAG + "载入用户主页失败:", userHomeObj)) {
                StructuredLog.error(TAG, "森林🌿 ❌ 主页载入失败")
                return userHomeObj
            }
            val serverTime = userHomeObj.optLong("now", System.currentTimeMillis())
            val isSelf = userId == UserMap.currentUid

            // 2. 自己的能量不受缓存限制，好友的能量检查缓存避免重复处理
            if (!isSelf && !userId.isNullOrEmpty() && processedUsersCache.contains(userId)) {
                return userHomeObj
            }

            // 标记用户为已处理（无论是否成功收取能量）
            if (!isSelf && !userId.isNullOrEmpty()) {
                processedUsersCache.add(userId)
            }
            val userName = getAndCacheUserName(userId, userHomeObj, fromTag)

            // 3. 判断是否允许收取能量 (开关关闭 或 在黑名单中)
            if (!collectEnergy!!.value || jsonCollectMap.contains(userId)) {
                StructuredLog.record(TAG, "[$userName] 不允许收取能量，跳过")
                return userHomeObj
            }

            // 4. 获取所有可收集的能量球 (extractBubbleInfo 内部已包含"收自己阈值"的逻辑)
            val availableBubbles: MutableList<Long> = ArrayList()
            extractBubbleInfo(userHomeObj, serverTime, availableBubbles, userId)

            if (availableBubbles.isEmpty()) {
                if (fromTag == "takeLook") {
                    StructuredLog.record(TAG, "找能量候选[${userName}]主页无当前可收取能量球")
                }
                // 记录空森林的时间戳，避免本轮重复检查
                if (!userId.isNullOrEmpty()) {
                    emptyForestCache[userId] = System.currentTimeMillis()
                }
                return userHomeObj
            }

            if (fromTag == "takeLook") {
                StructuredLog.record(TAG, "找能量候选[${userName}]主页发现${availableBubbles.size}个可收取能量球")
            }

            // 5. 检查是否有能量罩或炸弹卡保护
            var hasProtection = false
            if (!isSelf) {
                // 检查保护罩
                if (hasShield(userHomeObj, serverTime)) {
                    hasProtection = true
                    StructuredLog.record(TAG, "[$userName]被能量罩❤️保护着哟，跳过收取")
                }

                // 🆕【核心修改】检查炸弹卡 及 阈值判断逻辑
                if (!hasProtection && hasBombCard(userHomeObj, serverTime)) {
                    var bypassBomb = false
                    val bombLimit = collectBombEnergyLimit?.value ?: 0

                    // 如果设定了阈值(>0)，检查是否有大额能量球值得冒险
                    if (bombLimit > 0) {
                        val bubbles = userHomeObj.optJSONArray("bubbles")
                        if (bubbles != null) {
                            for (i in 0 until bubbles.length()) {
                                val bubble = bubbles.getJSONObject(i)
                                // 获取能量值 (fullEnergy通常是当前可收取的能量)
                                val energy = bubble.optInt("fullEnergy", 0)
                                if (energy >= bombLimit) {
                                    bypassBomb = true
                                    StructuredLog.record(TAG, "[$userName] 发现大能量球($energy g) >= 炸弹阈值($bombLimit g)，无视炸弹卡强行收取！💥")
                                    break
                                }
                            }
                        }
                    }

                    if (!bypassBomb) {
                        hasProtection = true
                        StructuredLog.record(TAG, "[$userName]开着炸弹卡💣，跳过收取")
                    }
                }
            }

            // 6. 只有没有保护(或无视保护)时才收集当前可用能量
            if (!hasProtection) {
                collectVivaEnergy(userId, userHomeObj, availableBubbles, fromTag)
                if (fromTag == "takeLook") {
                    StructuredLog.record(TAG, "找能量候选[${userName}]已提交${availableBubbles.size}个能量球收取请求")
                }
            }

            return userHomeObj
        } catch (e: RuntimeException) {
            StructuredLog.printStackTrace(TAG, "collectUserEnergy JSON解析错误", e)
            return null
        } catch (e: NullPointerException) {
            StructuredLog.printStackTrace(TAG, "collectUserEnergy 空指针异常", e)
            return null
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "collectUserEnergy 出现异常", t)
            return null
        }
    }

    /**
     * {{ 新增辅助方法：统一判断是否满足收自己能量的阈值条件 }}
     * @param bubbleCount 能量球数值
     * @param canBeRobbedAgain 是否可被再次偷取（保底状态为false）
     */
    private fun shouldCollectSelfBubble(bubbleCount: Int, canBeRobbedAgain: Boolean): Boolean {
        val type = collectSelfEnergyType?.value ?: CollectSelfType.ALL
        val threshold = collectSelfEnergyThreshold?.value ?: 0

        return when (type) {
            CollectSelfType.OVER_THRESHOLD -> {
                // 模式：大于阈值才收
                // 逻辑：只有当 [小于阈值] 且 [还能被偷] 时才跳过 (不收)
                // 如果已经到底了(!canBeRobbedAgain)，即使小于阈值也应该收回来，防止浪费
                if (bubbleCount < threshold && canBeRobbedAgain) {
                    false
                } else {
                    // 满足阈值 OR 触发保底收取 (能量很少了，朋友偷不走，必须自己收，不然就浪费了)
                    if (bubbleCount < threshold && !canBeRobbedAgain) {
                        StructuredLog.record(TAG, "触发保底收取：能量[$bubbleCount g] < 阈值[$threshold g]，但已无法被偷，强制收取")
                    }
                    true
                }
            }
            CollectSelfType.BELOW_THRESHOLD -> {
                // 模式：小于阈值才收
                bubbleCount < threshold
            }
            // CollectSelfType.ALL -> 默认 true
            else -> true
        }
    }

    /** Extracts current and waiting bubbles through the dedicated service. */
    private fun extractBubbleInfo(
        userHomeObj: JsonObject,
        serverTime: Long,
        availableBubbles: MutableList<Long>,
        userId: String?
    ) {
        forestBubbleService.extract(userHomeObj, serverTime, availableBubbles, userId)
    }

    /**
     * 批量或逐一收取能量
     *
     * @param userId      用户ID
     * @param userHomeObj 用户主页的JSON对象
     * @param bubbleIds   能量球ID列表
     * @param fromTag     收取来源标识
     */
    @Throws(RuntimeException::class)
    /**
     * 收取活力能量
     * @param userId 用户ID
     * @param userHomeObj 用户主页对象
     * @param bubbleIds 能量球ID列表
     * @param fromTag 来源标识
     * @param skipPropCheck 是否跳过道具检查（用于蹲点收取快速通道）
     */
    private fun collectVivaEnergy(
        userId: String?,
        userHomeObj: JsonObject?,
        bubbleIds: MutableList<Long>,
        fromTag: String?,
        skipPropCheck: Boolean = false
    ) {
        forestEnergyDispatchService.collect(
            userId,
            userHomeObj,
            bubbleIds,
            fromTag,
            skipPropCheck,
        )
    }

    /**
     * 函数式接口，用于提供RPC调用
     */
    private fun interface RpcSupplier<T> {
        @Throws(Exception::class)
        fun get(): T?
    }

    /**
     * 函数式接口，用于对JSON对象进行断言
     */
    private fun interface JsonPredicate<T> {
        @Throws(Exception::class)
        fun test(t: T?): Boolean
    }

    /**
     * 协程版本的排行榜收取方法
     */

    private suspend fun collectRankingsCoroutine(
        rankingName: String?,
        rpcCall: RpcSupplier<String?>,
        jsonArrayKey: String,
        flag: String,
        preCondition: JsonPredicate<JsonObject?>?
    ) = forestRankingCollectionService.run(
        rankingName = rankingName.orEmpty(),
        query = { rpcCall.get() },
        arrayKey = jsonArrayKey,
        flag = flag,
        preCondition = preCondition?.let { predicate -> { json -> predicate.test(json) } },
    )

    /**
     * 协程版本：收取PK好友能量
     */
    private suspend fun collectPKEnergyCoroutine() {
        collectRankingsCoroutine(
            "PK排行榜",
            { ForestCoreRpc.queryTopEnergyChallengeRanking() },
            "totalData",
            "pk",
            JsonPredicate { pkObject: JsonObject? ->
                if (pkObject!!.getString("rankMemberStatus") != "JOIN") {
                    StructuredLog.record(TAG, "未加入PK排行榜,跳过,尝试关闭")
                    pkEnergy!!.value = false
                    return@JsonPredicate false
                }
                true
            }
        )
    }

    /**
     * 使用找能量功能收取好友能量（协程版本）
     * 这是一个更高效的收取方式，可以直接找到有能量的好友
     */
    /**
     * 使用找能量功能收取好友能量（协程版本 - 修正版）
     * 逻辑：服务器自动轮询，返回空 friendId 代表无更多目标
     */
    private fun collectEnergyByTakeLook() {
        forestTakeLookService.run(fastMode = false)
    }

    /**
     * 7点-7点30分快速收取能量，跳过道具判断
     */
    private fun quickcollectEnergyByTakeLook() {
        forestTakeLookService.run(fastMode = true)
    }

    /**
     * 将用户添加到跳过列表（内存缓存）
     *
     * @param userId 用户ID
     */
    private fun addToSkipUsers(userId: String?) {
        if (!userId.isNullOrEmpty()) skipUsersCache[userId] = "baohuzhao"
    }

    /**
     * 协程版本：收取好友能量
     */
    private suspend fun collectFriendEnergyCoroutine() {
        collectRankingsCoroutine(
            "好友排行榜",
            { ForestCoreRpc.queryFriendsEnergyRanking() },
            "totalDatas",
            "普通好友",
            null
        )
    }


    /**
     * 遍历全排行榜，查询每个可复活好友的气泡能量，按气泡能量从高到低复活（最多6次/天）。
     * 注意：好友总能量高不代表可复活气泡能量高，需查询主页获取实际气泡数值。
     */
    private suspend fun collectProtectCandidatesCoroutine() {
        StructuredLog.forest("复活能量🔍 helpFriendCollectType=${helpFriendCollectType.value}, remaining=${Status.remainingProtectCount()}")
        if (helpFriendCollectType.value == HelpFriendCollectType.NONE) return
        val remaining = Status.remainingProtectCount()
        if (remaining <= 0) return

        try {
            val response = ForestCoreRpc.queryFriendsEnergyRanking().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return
            val total = response.optJSONArray("totalDatas") ?: return

            // 第一步：收集满足条件的候选好友ID
            var checkedCount = 0
            val candidateIds = buildList {
                val maxCheck = minOf(50, total.length()) // 只检查前50名好友
                for (i in 0 until maxCheck) {
                    val obj = total.optJSONObject(i) ?: continue
                    val userId = obj.optString("userId")
                    if (userId.isBlank() || userId == selfId) continue
                    checkedCount++
                    val inList = helpFriendCollectList.value.contains(userId)
                    val shouldProtect = if (helpFriendCollectType.value == HelpFriendCollectType.HELP) inList else !inList
                    if (!shouldProtect) continue
                    add(userId)
                }
            }

            StructuredLog.forest("复活能量🔍 排行榜${total.length()}人, 检查${checkedCount}人, 候选${candidateIds.size}人(将分段查询主页)")
            if (candidateIds.isEmpty()) return

            // 第二步：分段查询主页，记录所有有气泡的好友
            data class ProtectCandidate(val userId: String, val bubbleEnergy: Int, val home: kotlinx.serialization.json.JsonObject)
            val candidates = mutableListOf<ProtectCandidate>()
            val startTime = System.currentTimeMillis()
            val timeLimit = 8 * 60 * 1000L // 8分钟时间阈值
            val batchSize = 20 // 每批查询20人
            var queriedCount = 0

            for (batchStart in candidateIds.indices step batchSize) {
                // 检查时间阈值
                if (System.currentTimeMillis() - startTime > timeLimit) {
                    StructuredLog.forest("复活能量⏰ 已达时间阈值(8分钟), 停止查询, 已查询${queriedCount}人, 找到${candidates.size}个候选")
                    break
                }

                val batchEnd = minOf(batchStart + batchSize, candidateIds.size)
                val batch = candidateIds.subList(batchStart, batchEnd)

                for (userId in batch) {
                    try {
                        val home = queryFriendHome(userId, null) ?: continue
                        val bubbles = home.optJSONArray("wateringBubbles") ?: continue
                        for (j in 0 until bubbles.length()) {
                            val bubble = bubbles.optJSONObject(j) ?: continue
                            if (bubble.optString("bizType") != "fuhuo") continue
                            if (!bubble.optBoolean("canProtect")) continue
                            val energy = bubble.optInt("fullEnergy", 0)
                            candidates.add(ProtectCandidate(userId, energy, home))
                            break
                        }
                    } catch (_: Throwable) { }
                    queriedCount++
                    GlobalThreadPools.sleepCompat(300)
                }

                StructuredLog.forest("复活能量🔍 已查询${queriedCount}人, 找到${candidates.size}个可复活气泡")
            }

            // 第三步：查询完成后，按气泡能量排序，复活最高的
            if (candidates.isEmpty()) {
                StructuredLog.forest("复活能量🔍 查询完成, 无好友有可复活气泡")
                return
            }

            val sorted = candidates.sortedByDescending { it.bubbleEnergy }
            StructuredLog.forest(
                "复活能量🔍 查询完成, 共${candidates.size}个候选: ${sorted.joinToString { "${UserMap.getMaskName(it.userId)}-${it.bubbleEnergy}g" }}，剩余配额$remaining",
            )

            var protected = 0
            for (c in sorted) {
                if (protected >= remaining) break
                if (Status.remainingProtectCount() <= 0) break
                val beforeCount = Status.INSTANCE.protectBubbleCount
                try {
                    forestFriendSupportService.protectFriendEnergy(c.home)
                    if (Status.INSTANCE.protectBubbleCount > beforeCount) {
                        protected++
                        StructuredLog.forest("复活能量🚑 ${UserMap.getMaskName(c.userId)}-${c.bubbleEnergy}g")
                        GlobalThreadPools.sleepCompat(1000)
                    }
                } catch (t: Throwable) {
                    StructuredLog.printStackTrace(TAG, "复活好友[${c.userId}]异常", t)
                }
            }

            if (protected > 0) {
                StructuredLog.forest("复活能量✅ 本轮共复活${protected}个好友")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "遍历排行榜复活能量异常", t)
        }
    }
    /**
     * 统一的协程批量好友处理方法
     *
     * @param friendSource 好友数据源，可以是：
     *   - JsonArray: 直接的好友列表
     *   - MutableList<String?>: 用户ID列表，需要通过API获取
     * @param flag 标记（空字符串=普通好友，"pk"=PK好友）
     * @param sourceName 数据源名称（用于日志）
     */
    private suspend fun processFriendsEnergyCoroutine(
        friendSource: Any,
        flag: String,
        sourceName: String = "好友"
    ) = forestFriendBatchService.process(friendSource, flag, sourceName)

    /**
     * 处理单个好友的核心逻辑（无锁）
     *
     * @param obj  好友/PK好友 的JSON对象
     * @param flag 标记是普通好友还是PK好友
     */
    @Throws(Exception::class)
    private fun processEnergyInternal(obj: JsonObject, flag: String?) =
        forestFriendEnergyService.process(obj, flag)
    private fun isIsProtected(userId: String?): Boolean {
        var isProtected: Boolean
        // StructuredLog.forest("is_monday:"+_is_monday);
        if (monday) {
            isProtected = alternativeAccountList.value.contains(userId)
        } else {
            isProtected = helpFriendCollectList.value.contains(userId)
            if (helpFriendCollectType.value != HelpFriendCollectType.HELP) {
                isProtected = !isProtected
            }
        }
        return isProtected
    }

    /** lzw add end */
    /**
     * 协程版本：收取排名靠前好友能量
     */
    private fun collectEnergy(collectEnergyEntity: CollectEnergyEntity) {
        forestEnergyCollectionService.collect(collectEnergyEntity)
    }

    private fun getReturnCount(collected: Int): Int {
        var returnCount = 0
        if (returnWater33!!.value in 1..collected) {
            returnCount = 33
        } else if (returnWater18!!.value in 1..collected) {
            returnCount = 18
        } else if (returnWater10!!.value in 1..collected) {
            returnCount = 10
        }
        return returnCount
    }

    /**
     * 更新使用中的的道具剩余时间
     */
    @Throws(RuntimeException::class)
    private fun updateSelfHomePage() {
        val s = ForestCoreRpc.queryHomePage()
        GlobalThreadPools.sleepCompat(100)
        val joHomePage = parseJsonObject(s)
        updateSelfHomePage(joHomePage)
    }

    /**
     * 更新使用中的的道具剩余时间
     *
     * @param joHomePage 首页 JSON 对象
     */
    private fun updateSelfHomePage(joHomePage: JsonObject) {
        forestActivePropService.update(joHomePage)
    }

    /**
     * 兑换能量保护罩
     * 类别 spuid skuid price
     * 限时 CR20230517000497  CR20230516000370  166
     * 永久 CR20230517000497  CR20230516000371  500
     */
    private fun exchangeEnergyShield(): Boolean {
        val spuId = "CR20230517000497"
        val skuId = "CR20230516000370"
        if (!Status.canVitalityExchangeToday(skuId, 1)) {
            return false
        }
        return Vitality.VitalityExchange(spuId, skuId, "保护罩")
    }

    /**
     * 兑换隐身卡
     */
    private fun exchangeStealthCard(): Boolean {
        val skuId = "SK20230521000206"
        val spuId = "SP20230521000082"
        if (!Status.canVitalityExchangeToday(skuId, 1)) {
            return false
        }
        return Vitality.VitalityExchange(spuId, skuId, "隐身卡")
    }

    /**
     * 兑换双击卡
     * 优先兑换31天双击卡，失败后尝试限时双击卡
     */
    private fun exchangeDoubleCard(): Boolean {
        // 尝试兑换31天双击卡
        if (Vitality.handleVitalityExchange("SK20240805004754")) {
            return true
        }
        // 失败后尝试兑换限时双击卡
        return Vitality.handleVitalityExchange("CR20230516000363")
    }

    private suspend fun waitForHuaLingShiCompletion(taskType: String): Boolean {
        repeat(5) { attempt ->
            val response = try {
                parseJsonObject(ForestTaskRpc.queryList())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (response != null && HuaLingShiTask.isTaskFinished(response, taskType)) {
                return true
            }
            if (attempt < 4) delay(1000L)
        }
        return false
    }

    /**
     * 在收集能量之前使用道具。
     * 这个方法检查是否需要使用增益卡
     * 并在需要时使用相应的道具。
     *
     * @param userId 用户的ID。
     */
    /**
     * 在收集能量之前决定是否使用增益类道具卡
     * @param userId 用户ID
     * @param skipPropCheck 是否跳过道具检查（快速收取通道）
     */
    private fun usePropBeforeCollectEnergy(userId: String?, skipPropCheck: Boolean = false) {
        synchronized(doubleCardLockObj) {
            forestPropPreparationService.prepare(
                state = ForestPropUsagePlanner.State(
                    now = System.currentTimeMillis(),
                    doubleEnabled = doubleCard!!.value != ApplyPropType.CLOSE,
                    doubleEndTime = doubleEndTime,
                    robExpandEnabled = robExpandCard!!.value != ApplyPropType.CLOSE,
                    robExpandEndTime = robExpandCardEndTime,
                    stealthEnabled = stealthCard.value != ApplyPropType.CLOSE,
                    stealthEndTime = stealthEndTime,
                    shieldEnabled = shieldCard!!.value != ApplyPropType.CLOSE,
                    shieldEndTime = shieldEndTime,
                    bombEnabled = energyBombCardType!!.value != ApplyPropType.CLOSE,
                    bombEndTime = energyBombCardEndTime,
                    bubbleBoostEnabled = bubbleBoostCard!!.value != ApplyPropType.CLOSE,
                ),
                skip = skipPropCheck,
            )
        }
    }
    fun useCardBoot(targetTimeValue: MutableList<String?>, propName: String?, func: Runnable) {
        for (targetTimeStr in targetTimeValue) {
            if ("-1" == targetTimeStr) {
                return
            }
            val targetTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(targetTimeStr) ?: return
            val targetTime = targetTimeCalendar.getTimeInMillis()
            val now = System.currentTimeMillis()
            if (now > targetTime) {
                continue
            }
            val targetTaskId = "TAGET|$targetTime"
            if (!hasChildTask(targetTaskId)) {
                addChildTask(ChildModelTask(targetTaskId, "TAGET", func, targetTime))
                StructuredLog.record(
                    TAG,
                    "添加定时使用" + propName + "[" + UserMap.getCurrentMaskName() + "]在[" + TimeUtil.getCommonDate(
                        targetTime
                    ) + "]执行"
                )
            } else {
                addChildTask(ChildModelTask(targetTaskId, "TAGET", func, targetTime))
            }
        }
    }

    /**
     * 检查当前时间是否在设置的使用双击卡时间内
     *
     * @return 如果当前时间在双击卡的有效时间范围内，返回true；否则返回false。
     */
    private fun hasDoubleCardTime(): Boolean {
        val currentTimeMillis = System.currentTimeMillis()
        return TimeUtil.checkInTimeRange(currentTimeMillis, doubleCardTime!!.value)
    }

    private fun giveProp() {
        val set = whoYouWantToGiveTo.value
        if (!set.isEmpty()) {
            for (userId in set) {
                if (selfId != userId) {
                    giveProp(userId)
                    break
                }
            }
        }
    }

    /**
     * 向指定用户赠送道具。 这个方法首先查询可用的道具列表，然后选择一个道具赠送给目标用户。 如果有多个道具可用，会尝试继续赠送，直到所有道具都赠送完毕。
     *
     * @param targetUserId 目标用户的ID。
     */
    private fun giveProp(targetUserId: String?) {
        forestPropService.give(targetUserId)
    }

    /**
     * 获取背包信息
     */
    private fun queryPropList(forceRefresh: Boolean = false): JsonObject? =
        forestPropService.query(forceRefresh)

    /**
     * 查找背包道具
     *
     * @param bagObject 背包对象
     * @param propType  道具类型 LIMIT_TIME_ENERGY_SHIELD_TREE,...
     */
    private fun findPropBag(bagObject: JsonObject?, propType: String): JsonObject? =
        forestPropService.find(bagObject, propType)

    /**
     * 返回背包道具信息
     */
    private fun showBag() {
        forestPropService.show()
    }

    /**
     * 使用背包道具
     *
     * @param propJsonObj 道具对象
     * @param needRefreshHome 是否需要刷新主页（默认true。加速卡等紧接着会查询主页的场景可设为false以优化延迟）
     */
    private fun usePropBag(propJsonObj: JsonObject?, needRefreshHome: Boolean = true): Boolean =
        forestPropService.use(propJsonObj, needRefreshHome)

    /**
     * 使用双击卡道具
     * 功能：在指定时间内，使好友的一个能量球可以收取两次
     *
     * @param bagObject 背包的JSON对象
     */
    private fun useDoubleCard(bagObject: JsonObject) {
        forestDoubleCardService.use(bagObject)
    }
    private fun useStealthCard(bagObject: JsonObject?) {
        val config = PropConfig(
            "隐身卡",
            arrayOf<String>("LIMIT_TIME_STEALTH_CARD", "STEALTH_CARD"),
            null,  // 无特殊条件
            { this.exchangeStealthCard() },
            { time: Long? -> stealthEndTime = time!! + TimeFormatter.ONE_DAY_MS }
        )
        usePropTemplate(bagObject, config, stealthCardConstant.value)
    }


    /**
     * 使用保护罩道具
     * 功能：保护自己的能量不被好友偷取，防止能量被收走。
     * 优先使用即将过期的限时保护罩，避免浪费。
     * 支持来源：
     *   - 背包中已有的多种类型保护罩
     *   - 青春特权自动领取（若开启）
     *   - 活力值兑换（若开启且兑换成功）
     *
     * @param bagObject 当前背包的 JSON 对象（可能为 null）
     */
    private fun useShieldCard(bagObject: JsonObject?) {
        forestProtectionCardService.useShield(
            bag = bagObject,
            constantEnabled = shieldCardConstant?.value == true,
        )
    }
    private fun useBubbleBoostCard(bag: JsonObject? = queryPropList()) {
        forestBubbleBoostService.use(bag)
    }

    /**
     * 立即收取自己能量（专用方法）
     */
    private fun collectSelfEnergyImmediately(tag: String = "立即收取") {
        try {
            // querySelfHome 内部会处理 updateSelfHomePage 逻辑，确保道具倒计时等状态同步
            val selfHomeObj = querySelfHome()
            if (selfHomeObj != null) {
                val availableBubbles: MutableList<Long> = ArrayList()
                val serverTime = selfHomeObj.optLong("now", System.currentTimeMillis())

                // ✓ 核心逻辑点：
                // 调用 extractBubbleInfo，该方法内部调用了 shouldCollectSelfBubble(bubbleCount, canBeRobbedAgain)
                // 从而严格执行了【收自己单个能量球方式】和【阈值】的判断逻辑。
                // 只有符合条件的 bubbleId 才会加入 availableBubbles
                extractBubbleInfo(selfHomeObj, serverTime, availableBubbles, UserMap.currentUid)

                if (availableBubbles.isNotEmpty()) {
                    StructuredLog.record(TAG, "🎯 $tag：找到${availableBubbles.size}个符合阈值条件的可收能量球")
                    // 即使 batchRobEnergy 为 true，collectVivaEnergy 也是对传入的 list 进行操作
                    // 因此【一键收取】、【找能量】、【普通收取】都复用了这个逻辑，保证了统一性
                    collectVivaEnergy(UserMap.currentUid, selfHomeObj, availableBubbles, "加速卡$tag", skipPropCheck = true)
                } else {
                    StructuredLog.record(TAG, "🎯 $tag：未找到满足条件的能量球 (可能是被阈值过滤或无能量)")
                }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "collectSelfEnergyImmediately err", e)
        }
    }

    /**
     * 使用1.1倍能量卡道具
     * 功能：增加能量收取倍数，收取好友能量时获得1.1倍效果
     */
    private fun userobExpandCard(bag: JsonObject? = queryPropList()) {
        forestRobExpandCardService.use(bag)
    }

    private suspend fun useEnergyRainChanceCard() {
        forestEnergyRainChanceService.use()
    }

    /**
     * 使用炸弹卡道具
     * 功能：对有保护罩的好友使用，可以破坏其保护罩并收取能量
     * 注意：与保护罩功能冲突，通常二选一使用
     *
     * @param bagObject 背包的JSON对象
     */
    private fun useEnergyBombCard(bagObject: JsonObject?) {
        forestProtectionCardService.useBomb(bagObject)
    }
    fun doforestgame() {
        forestGameCenterService.run()
    }
    /**
     * 收取状态的枚举类型
     */
    enum class CollectStatus {
        AVAILABLE, WAITING, INSUFFICIENT, ROBBED
    }

    /**
     * 统一获取和缓存用户名的方法
     * @param userId 用户ID
     * @param userHomeObj 用户主页对象（可选）
     * @param fromTag 来源标记（可选）
     * @return 用户名
     */
    private fun getAndCacheUserName(userId: String?, userHomeObj: JsonObject?, fromTag: String?): String? {
        return forestUserNameService.get(userId, userHomeObj, fromTag)
    }

    /**
     * 统一获取用户名的简化方法（无上下文）
     */
    private fun getAndCacheUserName(userId: String?): String? {
        return getAndCacheUserName(userId, null, null)
    }

    /**
     * 通用错误处理器
     * @param operation 操作名称
     * @param throwable 异常对象
     */
    private fun handleException(operation: String?, throwable: Throwable) {
        if (throwable is RuntimeException) {
            // JSON解析错误通常是网络响应问题，只记录错误信息不打印堆栈，避免刷屏
            StructuredLog.error(TAG, operation + " JSON解析错误: " + throwable.message)
        } else {
            StructuredLog.error(TAG, operation + " 错误: " + throwable.message)
            StructuredLog.printStackTrace(TAG, throwable)
        }
    }

    /**
     * 道具使用配置类
     */
    @JvmRecord
    private data class PropConfig(
        val propName: String?, val propTypes: Array<String>?,
        val condition: Supplier<Boolean?>?,
        val exchangeFunction: Supplier<Boolean?>?,
        val endTimeUpdater: Consumer<Long?>?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PropConfig
            if (propName != other.propName) return false
            if (!propTypes.contentEquals(other.propTypes)) return false
            if (condition != other.condition) return false
            if (exchangeFunction != other.exchangeFunction) return false
            if (endTimeUpdater != other.endTimeUpdater) return false
            return true
        }

        override fun hashCode(): Int {
            var result = propName?.hashCode() ?: 0
            result = 31 * result + (propTypes?.contentHashCode() ?: 0)
            result = 31 * result + (condition?.hashCode() ?: 0)
            result = 31 * result + (exchangeFunction?.hashCode() ?: 0)
            result = 31 * result + (endTimeUpdater?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * 通用道具使用模板方法
     *
     * @param bagObject    背包对象
     * @param config       道具配置
     * @param constantMode 是否开启永动机模式
     */
    private fun usePropTemplate(bagObject: JsonObject?, config: PropConfig, constantMode: Boolean) {
        try {
            if (config.condition != null && !config.condition.get()!!) {
                StructuredLog.record(TAG, "不满足使用" + config.propName + "的条件")
                return
            }
            StructuredLog.record(TAG, "尝试使用" + config.propName + "...")
            // 按优先级查找道具
            var propObj: JsonObject? = null
            for (propType in config.propTypes!!) {
                propObj = findPropBag(bagObject, propType)
                if (propObj != null) break
            }
            // 如果背包中没有道具且开启永动机，尝试兑换
            if (propObj == null && constantMode && config.exchangeFunction != null) {
                StructuredLog.record(TAG, "背包中没有" + config.propName + "，尝试兑换...")
                if (config.exchangeFunction.get() == true) {
                    // 重新查找兑换后的道具
                    for (propType in config.propTypes) {
                        propObj = findPropBag(queryPropList(), propType)
                        if (propObj != null) break
                    }
                }
            }
            if (propObj != null) {
                // 针对限时双击卡的时间检查
                if ("双击卡" == config.propName) {
                    val propType = propObj.optString("propType")
                    if ("ENERGY_DOUBLE_CLICK" == propType && !hasDoubleCardTime()) {
                        StructuredLog.record(TAG, "跳过双击卡[$propType]，当前不在指定使用时间段内")
                        return
                    }
                }
                StructuredLog.record(TAG, "找到" + config.propName + "，准备使用: " + propObj)
                if (usePropBag(propObj)) {
                    config.endTimeUpdater?.accept(System.currentTimeMillis())
                }
            } else {
                StructuredLog.record(TAG, "背包中未找到任何可用的" + config.propName)
                updateSelfHomePage()
            }
        } catch (th: Throwable) {
            handleException("use" + config.propName, th)
        }
    }

    companion object {
        val TAG: String = AntForest::class.java.getSimpleName()

        private const val TOTAL_HELP_COLLECTED = 0
        private const val TOTAL_WATERED = 0
        private const val MAX_BATCH_SIZE = 6

        @JvmStatic
        @Synchronized
        fun canDoubleToday(): Boolean {
            val task = Model.getModel(AntForest::class.java) ?: return false
            return Status.INSTANCE.doubleTimes < (task.doubleCountLimit?.value ?: 0)
        }

        @JvmStatic
        @Synchronized
        fun doubleToday() {
            Status.INSTANCE.doubleTimes += 1
            Status.save()
        }

        private fun propEmoji(propName: String): String {
            val tag: String = if (propName.contains("保")) {
                "🛡️"
            } else if (propName.contains("双")) {
                "👥"
            } else if (propName.contains("加")) {
                "🌪"
            } else if (propName.contains("雨")) {
                "🌧️"
            } else if (propName.contains("炸")) {
                "💥"
            } else {
                "🥳"
            }
            return tag
        }
        init {
            ModelOrder.register(ModelDefinition(AntForest::class.java) { AntForest() })
        }
    }

    /**
     * 检查用户是否有保护罩或炸弹（按照原有逻辑）
     */
    private fun checkUserShieldAndBomb(userHomeObj: JsonObject, userName: String?, userId: String, serverTime: Long): Boolean {
        var hasProtection = false
        val isSelf = userId == UserMap.currentUid

        if (!isSelf) {
            val shieldEndTime = ForestUtil.getShieldEndTime(userHomeObj)
            val bombEndTime = ForestUtil.getBombCardEndTime(userHomeObj)
            maxOf(shieldEndTime, bombEndTime)

            if (shieldEndTime > serverTime) {
                hasProtection = true
                StructuredLog.record(
                    TAG,
                    "[$userName]被能量罩❤️保护着哟(还剩${TimeFormatter.formatDuration(shieldEndTime - serverTime)}),跳过收取",
                )
            }
            if (bombEndTime > serverTime) {
                hasProtection = true
                StructuredLog.record(
                    TAG,
                    "[$userName]开着炸弹卡💣(还剩${TimeFormatter.formatDuration(bombEndTime - serverTime)}),跳过收取",
                )
            }
        }

        return hasProtection
    }

    /**
     * 专门用于蹲点的能量收取方法
     */
    @SuppressLint("SimpleDateFormat")
    private fun collectEnergyForWaiting(
        userId: String,
        userHomeObj: JsonObject,
        fromTag: String?,
        userName: String?
    ): CollectResult {
        try {
            // 获取服务器时间
            val serverTime = userHomeObj.optLong("now", System.currentTimeMillis())
            // 判断是否是自己的账号
            val isSelf = userId == UserMap.currentUid

            // 先检查保护罩和炸弹（仅对好友检查）
            val shieldEndTime = ForestUtil.getShieldEndTime(userHomeObj)
            val bombEndTime = ForestUtil.getBombCardEndTime(userHomeObj)
            val hasShield = shieldEndTime > serverTime
            val hasBomb = bombEndTime > serverTime
            val hasProtection = hasShield || hasBomb

            StructuredLog.record(TAG, "蹲点收取保护检查详情：")
            StructuredLog.record(TAG, "  是否是主号: $isSelf")
            StructuredLog.record(
                TAG, "  服务器时间: ${
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(
                        Date(serverTime)
                    )
                }"
            )
            StructuredLog.record(
                TAG, "  保护罩结束时间: ${
                    if (shieldEndTime > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(
                        Date(shieldEndTime)
                    ) else "无保护罩"
                }"
            )
            StructuredLog.record(
                TAG, "  炸弹卡结束时间: ${
                    if (bombEndTime > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(
                        Date(bombEndTime)
                    ) else "无炸弹卡"
                }"
            )
            StructuredLog.record(TAG, "  是否有保护罩: $hasShield")
            StructuredLog.record(TAG, "  是否有炸弹卡: $hasBomb")
            StructuredLog.record(TAG, "  总体保护状态: $hasProtection")

            // 只对好友账号进行保护检查，主号无视保护罩
            if (!isSelf && hasProtection) {
                // 调用原有的日志输出方法
                checkUserShieldAndBomb(userHomeObj, userName, userId, serverTime)
                return CollectResult(
                    success = false,
                    userName = userName,
                    message = "有保护，已跳过",
                    hasShield = hasShield,
                    hasBomb = hasBomb
                )
            }

            // 主号的保护罩不影响收取自己的能量
            if (isSelf && hasProtection) {
                StructuredLog.record(TAG, "  ⭐ 主号有保护罩，但可以收取自己的能量")
            }

            // 先查询用户能量状态
            val queryResult = collectEnergy(userId, userHomeObj, fromTag) ?: return CollectResult(
                success = false,
                userName = userName,
                message = "无法查询用户能量信息"
            )

            // 提取可收取的能量球ID
            val availableBubbles: MutableList<Long> = ArrayList()
            val queryServerTime = queryResult.optLong("now", System.currentTimeMillis())
            extractBubbleInfo(queryResult, queryServerTime, availableBubbles, userId)

            if (availableBubbles.isEmpty()) {
                return CollectResult(
                    success = false,
                    userName = userName,
                    message = "用户无可收取的能量球"
                )
            }

            StructuredLog.record(TAG, "蹲点收取找到${availableBubbles.size}个可收取能量球: $availableBubbles")

            // 记录收取前的总能量
            val beforeTotal = totalCollected.get()

            // 🚀 启用快速收取通道：跳过道具检查，加速蹲点收取
            collectVivaEnergy(userId, queryResult, availableBubbles, fromTag, skipPropCheck = true)

            // 计算收取的能量数量
            val collectedEnergy = totalCollected.get() - beforeTotal

            return if (collectedEnergy > 0) {
                CollectResult(
                    success = true,
                    userName = userName,
                    energyCount = collectedEnergy,
                    totalCollected = totalCollected.get(),
                    message = "收取成功，共收取${availableBubbles.size}个能量球，${collectedEnergy}g能量"
                )
            } else {
                CollectResult(
                    success = false,
                    userName = userName,
                    message = "未收取到任何能量"
                )
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "collectEnergyForWaiting err", e)
            return CollectResult(
                success = false,
                userName = userName,
                message = "收取异常：${e.message}"
            )
        }
    }

    /**
     * 实现EnergyCollectCallback接口
     * 为蹲点管理器提供能量收取功能（增强版）
     */
    override fun addToTotalCollected(energyCount: Int) {
        totalCollected.addAndGet(energyCount)
    }

    override fun getWaitingCollectDelay(): Long {
        return 0L // 立即收取，无延迟
    }

    override suspend fun collectUserEnergyForWaiting(task: EnergyWaitingManager.WaitingTask): CollectResult {
        return try {
            withContext(Dispatchers.Default) {
                // 查询好友主页
                val friendHomeObj = queryFriendHome(task.userId, task.fromTag)
                if (friendHomeObj != null) {
                    // 获取真实用户名
                    val realUserName = getAndCacheUserName(task.userId, friendHomeObj, task.fromTag)
                    val isSelf = task.userId == UserMap.currentUid
                    StructuredLog.record(TAG, "蹲点收取：用户[${realUserName}] userId=${task.userId} currentUid=${UserMap.currentUid} isSelf=${isSelf}")
                    // 直接执行能量收取，让原有的collectEnergy方法处理保护罩和炸弹检查
                    val result = collectEnergyForWaiting(task.userId, friendHomeObj, task.fromTag, realUserName)
                    result.copy(userName = realUserName)
                } else {
                    CollectResult(
                        success = false,
                        userName = task.userName,
                        message = "无法获取用户主页信息"
                    )
                }
            }
        } catch (e: CancellationException) {
            // 协程取消是正常现象，不记录为错误
            StructuredLog.record(TAG, "collectUserEnergyForWaiting 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "collectUserEnergyForWaiting err", e)
            CollectResult(
                success = false,
                userName = task.userName,
                message = "异常：${e.message}"
            )
        }
    }

    /**
     * 判断是否为团队
     *
     * @param homeObj 用户主页的JSON对象
     * @return 是否为团队
     */
    private fun isTeam(homeObj: JsonObject): Boolean {
        return homeObj.optString("nextAction", "") == "Team"
    }

    /**
     * 手动触发森林打地鼠
     */
    suspend fun manualWhackMole(modeIndex: Int, games: Int) {
        try {
            val obj = querySelfHome()
            if (obj != null) {
                // 确定模式：1 为兼容，2 为激进
                val mode = if (modeIndex == 2) WhackMole.Mode.AGGRESSIVE else WhackMole.Mode.COMPATIBLE

                // 设置本次执行的总局数
                WhackMole.setTotalGames(games)
                WhackMole.setMoleCount(whackMoleMoleCount?.value ?: 15)

                StructuredLog.record(
                    TAG,
                    "🎮 手动触发拼手速任务: ${if (mode == WhackMole.Mode.AGGRESSIVE) "激进模式" else "兼容模式"}, 目标局数: $games"
                )

                // 执行游戏
                WhackMole.startSuspend(mode)
            } else {
                StructuredLog.record(TAG, "无法获取自己主页信息")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    /**
     * 手动运行能量雨逻辑
     * @param exchange 是否先尝试兑换并使用能量雨卡
     */
    suspend fun manualUseEnergyRain(exchange: Boolean) {
        try {
            StructuredLog.record(TAG, "森林🌿 手动能量雨")
            val obj = querySelfHome()
            if (obj != null) {

                if (exchange) {
                    StructuredLog.record(TAG, "尝试兑换并激活能量雨卡...")
                    useEnergyRainChanceCard()
                }

                EnergyRainCoroutine.execEnergyRainCompat()
                StructuredLog.forest("森林🌿 能量雨任务处理完毕")
            } else {
                StructuredLog.record(TAG, "无法获取自己主页信息")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "manualUseEnergyRain 异常:", t)
        }
    }
}
