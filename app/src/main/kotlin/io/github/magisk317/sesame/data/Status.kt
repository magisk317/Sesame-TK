package io.github.magisk317.sesame.data

import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.StringUtil
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.maps.UserMap
import java.util.Calendar
import java.util.Date
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
class Status {

    // =========================== forest
    var waterFriendLogList: MutableMap<String, Int> = HashMap()
    var cooperateWaterList: MutableSet<String> = HashSet() // 合作浇水
    var reserveLogList: MutableMap<String, Int> = HashMap()
    var ancientTreeCityCodeList: MutableSet<String> = HashSet() // 古树
    var protectBubbleCount: Int = 0
    var doubleTimes: Int = 0
    var exchangeEnergyShield: Boolean = false // 活力值兑换能量保护罩
    var exchangeCollectHistoryAnimal7Days: Boolean = false
    var exchangeCollectToFriendTimes7Days: Boolean = false
    var studentTask: Boolean = true
    var vitalityStoreList: MutableMap<String, Int> = HashMap() // 注意命名规范首字母小写

    // =========================== farm
    var answerQuestion: Boolean = false
    var feedFriendLogList: MutableMap<String, Int> = HashMap()
    var visitFriendLogList: MutableMap<String, Int> = HashMap()

    // 可以存各种今日计数（步数、次数等）
    // 2025/12/4 GSMT 用来存储int类型数据，无需再重复定义
    var intFlagMap: MutableMap<String, Int> = HashMap()

    var dailyAnswerList: MutableSet<String> = HashSet()
    var donationEggList: MutableSet<String> = HashSet()
    var useAccelerateToolCount: Int = 0

    /** 小鸡换装 */
    var canOrnament: Boolean = true
    var animalSleep: Boolean = false

    // ============================= stall
    var stallHelpedCountLogList: MutableMap<String, Int> = HashMap()
    var spreadManureList: MutableSet<String> = HashSet()
    var stallP2PHelpedList: MutableSet<String> = HashSet()
    var canStallDonate: Boolean = true

    // ========================== sport
    var syncStepList: MutableSet<String> = HashSet()
    var exchangeList: MutableSet<String> = HashSet()

    /** 捐运动币 */
    var donateCharityCoin: Boolean = false

    // ======================= other
    var memberSignInList: MutableSet<String> = HashSet()
    val flagList: MutableSet<String> = HashSet()

    /** 口碑签到 */
    var kbSignIn: Long = 0

    /** 保存时间 */
    var saveTime: Long = 0L

    /** 新村助力好友，已上限的用户 */
    var antStallAssistFriend: MutableSet<String> = HashSet()

    /** 新村-罚单已贴完的用户 */
    var canPasteTicketTime: MutableSet<String> = HashSet()

    /** 绿色经营，收取好友金币已完成用户 */
    var greenFinancePointFriend: MutableSet<String> = HashSet()

    /** 绿色经营，评级领奖已完成用户 */
    var greenFinancePrizesMap: MutableMap<String, Int> = HashMap()

    /** 农场助力 */
    var antOrchardAssistFriend: MutableSet<String> = HashSet()

    /** 会员权益 */
    var memberPointExchangeBenefitLogList: MutableSet<String> = HashSet()

    companion object {
        private val TAG = Status::class.java.simpleName
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        @JvmStatic
        val INSTANCE: Status = Status()

        @JvmStatic
        fun encode(instance: Status): String = json.encodeToString(serializer(), instance)

        @JvmStatic
        fun decode(value: String): Status = json.decodeFromString(serializer(), value)

        private fun copyInto(target: Status, source: Status) {
            target.waterFriendLogList = source.waterFriendLogList
            target.cooperateWaterList = source.cooperateWaterList
            target.reserveLogList = source.reserveLogList
            target.ancientTreeCityCodeList = source.ancientTreeCityCodeList
            target.protectBubbleCount = source.protectBubbleCount
            target.doubleTimes = source.doubleTimes
            target.exchangeEnergyShield = source.exchangeEnergyShield
            target.exchangeCollectHistoryAnimal7Days = source.exchangeCollectHistoryAnimal7Days
            target.exchangeCollectToFriendTimes7Days = source.exchangeCollectToFriendTimes7Days
            target.studentTask = source.studentTask
            target.vitalityStoreList = source.vitalityStoreList
            target.answerQuestion = source.answerQuestion
            target.feedFriendLogList = source.feedFriendLogList
            target.visitFriendLogList = source.visitFriendLogList
            target.intFlagMap = source.intFlagMap
            target.dailyAnswerList = source.dailyAnswerList
            target.donationEggList = source.donationEggList
            target.useAccelerateToolCount = source.useAccelerateToolCount
            target.canOrnament = source.canOrnament
            target.animalSleep = source.animalSleep
            target.stallHelpedCountLogList = source.stallHelpedCountLogList
            target.spreadManureList = source.spreadManureList
            target.stallP2PHelpedList = source.stallP2PHelpedList
            target.canStallDonate = source.canStallDonate
            target.syncStepList = source.syncStepList
            target.exchangeList = source.exchangeList
            target.donateCharityCoin = source.donateCharityCoin
            target.memberSignInList = source.memberSignInList
            target.flagList.clear()
            target.flagList.addAll(source.flagList)
            target.kbSignIn = source.kbSignIn
            target.saveTime = source.saveTime
            target.antStallAssistFriend = source.antStallAssistFriend
            target.canPasteTicketTime = source.canPasteTicketTime
            target.greenFinancePointFriend = source.greenFinancePointFriend
            target.greenFinancePrizesMap = source.greenFinancePrizesMap
            target.antOrchardAssistFriend = source.antOrchardAssistFriend
            target.memberPointExchangeBenefitLogList = source.memberPointExchangeBenefitLogList
        }

        @JvmStatic
        fun replace(instance: Status, source: Status) = copyInto(instance, source)

        @JvmStatic
        val currentDayTimestamp: Long
            get() {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                return calendar.timeInMillis
            }

        @Synchronized
        @JvmStatic
        fun getVitalityCount(skuId: String): Int {
            return INSTANCE.vitalityStoreList[skuId] ?: 0
        }

        @Synchronized
        @JvmStatic
        fun canVitalityExchangeToday(skuId: String, count: Int): Boolean {
            return !hasFlagToday("forest::VitalityExchangeLimit::$skuId") && getVitalityCount(skuId) < count
        }

        @Synchronized
        @JvmStatic
        fun vitalityExchangeToday(skuId: String) {
            val count = getVitalityCount(skuId) + 1
            INSTANCE.vitalityStoreList[skuId] = count
            save()
        }

        @Synchronized
        @JvmStatic
        fun canAnimalSleep(): Boolean {
            return !INSTANCE.animalSleep
        }

        @Synchronized
        @JvmStatic
        fun animalSleep() {
            if (!INSTANCE.animalSleep) {
                INSTANCE.animalSleep = true
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun canWaterFriendToday(id: String, newCount: Int): Boolean {
            val uid = UserMap.currentUid ?: return false
            val key = "$uid-$id"
            val count = INSTANCE.waterFriendLogList[key] ?: return true
            return count < newCount
        }

        @Synchronized
        @JvmStatic
        fun waterFriendToday(id: String, count: Int) {
            val uid = UserMap.currentUid ?: return
            val key = "$uid-$id"
            INSTANCE.waterFriendLogList[key] = count
            save()
        }

        @Synchronized
        @JvmStatic
        fun getReserveTimes(id: String): Int {
            return INSTANCE.reserveLogList[id] ?: 0
        }

        @Synchronized
        @JvmStatic
        fun canReserveToday(id: String, count: Int): Boolean {
            return getReserveTimes(id) < count
        }

        @Synchronized
        @JvmStatic
        fun reserveToday(id: String, newCount: Int) {
            val count = INSTANCE.reserveLogList[id] ?: 0
            INSTANCE.reserveLogList[id] = count + newCount
            save()
        }

        @Synchronized
        @JvmStatic
        fun canCooperateWaterToday(uid: String?, coopId: String): Boolean {
            return !INSTANCE.cooperateWaterList.contains("${uid}_$coopId")
        }

        @Synchronized
        @JvmStatic
        fun cooperateWaterToday(uid: String?, coopId: String?) {
            val v = "${uid}_$coopId"
            if (INSTANCE.cooperateWaterList.add(v)) {
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun canAncientTreeToday(cityCode: String): Boolean {
            return !INSTANCE.ancientTreeCityCodeList.contains(cityCode)
        }

        @Synchronized
        @JvmStatic
        fun ancientTreeToday(cityCode: String) {
            if (INSTANCE.ancientTreeCityCodeList.add(cityCode)) {
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun canAnswerQuestionToday(): Boolean {
            return !INSTANCE.answerQuestion
        }

        @Synchronized
        @JvmStatic
        fun answerQuestionToday() {
            if (!INSTANCE.answerQuestion) {
                INSTANCE.answerQuestion = true
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun canFeedFriendToday(id: String, newCount: Int): Boolean {
            val count = INSTANCE.feedFriendLogList[id] ?: return true
            return count < newCount
        }

        @Synchronized
        @JvmStatic
        fun feedFriendToday(id: String) {
            val count = INSTANCE.feedFriendLogList[id] ?: 0
            INSTANCE.feedFriendLogList[id] = count + 1
            save()
        }

        @Synchronized
        @JvmStatic
        fun canVisitFriendToday(id: String, newCount: Int): Boolean {
            val uid = UserMap.currentUid ?: return false
            val key = "$uid-$id"
            val count = INSTANCE.visitFriendLogList[key] ?: return true
            return count < newCount
        }

        @Synchronized
        @JvmStatic
        fun visitFriendToday(id: String, newCount: Int) {
            val uid = UserMap.currentUid ?: return
            val key = "$uid-$id"
            INSTANCE.visitFriendLogList[key] = newCount
            save()
        }

        @Synchronized
        @JvmStatic
        fun canMemberSignInToday(uid: String?): Boolean {
            return !INSTANCE.memberSignInList.contains(uid)
        }

        @Synchronized
        @JvmStatic
        fun memberSignInToday(uid: String?) {
            if (uid != null) {
                if (INSTANCE.memberSignInList.add(uid)) {
                    save()
                }
            }
        }

        @Synchronized
        @JvmStatic
        fun canUseAccelerateTool(): Boolean {
            return INSTANCE.useAccelerateToolCount < 8
        }

        @Synchronized
        @JvmStatic
        fun useAccelerateTool() {
            INSTANCE.useAccelerateToolCount += 1
            save()
        }

        @Synchronized
        @JvmStatic
        fun canDonationEgg(uid: String?): Boolean {
            return !INSTANCE.donationEggList.contains(uid)
        }

        @Synchronized
        @JvmStatic
        fun donationEgg(uid: String?) {
            if (!uid.isNullOrEmpty() && INSTANCE.donationEggList.add(uid)) {
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun canSpreadManureToday(uid: String): Boolean {
            return !INSTANCE.spreadManureList.contains(uid)
        }

        @Synchronized
        @JvmStatic
        fun spreadManureToday(uid: String) {
            if (INSTANCE.spreadManureList.add(uid)) {
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun canAntStallAssistFriendToday(): Boolean {
            return !INSTANCE.antStallAssistFriend.contains(UserMap.currentUid)
        }

        @Synchronized
        @JvmStatic
        fun antStallAssistFriendToday() {
            val uid = UserMap.currentUid ?: return
            if (INSTANCE.antStallAssistFriend.add(uid)) {
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun canAntOrchardAssistFriendToday(): Boolean {
            return !INSTANCE.antOrchardAssistFriend.contains(UserMap.currentUid)
        }

        @Synchronized
        @JvmStatic
        fun antOrchardAssistFriendToday() {
            val uid = UserMap.currentUid ?: return
            if (INSTANCE.antOrchardAssistFriend.add(uid)) {
                save()
            }
        }

        private const val MAX_PROTECT_PER_DAY = 6

        @Synchronized
        @JvmStatic
        fun canProtectBubbleToday(uid: String?): Boolean {
            return INSTANCE.protectBubbleCount < MAX_PROTECT_PER_DAY
        }

        @Synchronized
        @JvmStatic
        fun remainingProtectCount(): Int {
            return (MAX_PROTECT_PER_DAY - INSTANCE.protectBubbleCount).coerceAtLeast(0)
        }

        @Synchronized
        @JvmStatic
        fun protectBubbleToday(uid: String?) {
            if (uid != null) {
                INSTANCE.protectBubbleCount++
                save()
            } else {
                StructuredLog.error("protectBubbleToday uid is null")
            }
        }

        @Synchronized
        @JvmStatic
        fun canPasteTicketTime(): Boolean {
            return !INSTANCE.canPasteTicketTime.contains(UserMap.currentUid)
        }

        @Synchronized
        @JvmStatic
        fun pasteTicketTime() {
            val uid = UserMap.currentUid ?: return
            if (INSTANCE.canPasteTicketTime.add(uid)) {
                save()
            }
        }


        @Synchronized
        @JvmStatic
        fun canKbSignInToday(): Boolean {
            return INSTANCE.kbSignIn < currentDayTimestamp
        }

        @Synchronized
        @JvmStatic
        fun KbSignInToday() {
            val todayZero = currentDayTimestamp
            if (INSTANCE.kbSignIn != todayZero) {
                INSTANCE.kbSignIn = todayZero
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun setDadaDailySet(dailyAnswerList: MutableSet<String>) {
            INSTANCE.dailyAnswerList = dailyAnswerList
            save()
        }

        @Synchronized
        @JvmStatic
        fun canDonateCharityCoin(): Boolean {
            return !INSTANCE.donateCharityCoin
        }

        @Synchronized
        @JvmStatic
        fun donateCharityCoin() {
            if (!INSTANCE.donateCharityCoin) {
                INSTANCE.donateCharityCoin = true
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun canExchangeToday(uid: String): Boolean {
            return !INSTANCE.exchangeList.contains(uid)
        }

        @Synchronized
        @JvmStatic
        fun exchangeToday(uid: String) {
            if (INSTANCE.exchangeList.add(uid)) {
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun canGreenFinancePointFriend(): Boolean {
            return !INSTANCE.greenFinancePointFriend.contains(UserMap.currentUid)
        }

        @Synchronized
        @JvmStatic
        fun greenFinancePointFriend() {
            if (canGreenFinancePointFriend()) return
            val uid = UserMap.currentUid ?: return
            INSTANCE.greenFinancePointFriend.add(uid)
            save()
        }

        @Synchronized
        @JvmStatic
        fun canGreenFinancePrizesMap(): Boolean {
            val week = TimeUtil.getWeekNumber(Date())
            val currentUid = UserMap.currentUid
            if (INSTANCE.greenFinancePrizesMap.containsKey(currentUid)) {
                val storedWeek = INSTANCE.greenFinancePrizesMap[currentUid]
                return storedWeek == null || storedWeek != week
            }
            return true
        }

        @Synchronized
        @JvmStatic
        fun greenFinancePrizesMap() {
            if (!canGreenFinancePrizesMap()) return
            val uid = UserMap.currentUid ?: return
            INSTANCE.greenFinancePrizesMap[uid] = TimeUtil.getWeekNumber(Date())
            save()
        }

        @Synchronized
        @JvmStatic
        fun load(currentUid: String?): Status {
            if (StringUtil.isEmpty(currentUid)) {
                StructuredLog.record(TAG, "用户为空，状态加载失败")
                throw RuntimeException("用户为空，状态加载失败")
            }
            val statusFile = Files.getStatusFile(currentUid)!!
            try {
                if (statusFile.exists()) {
                    StructuredLog.record(TAG, "加载 status.json")
                    val json = Files.readFromFile(statusFile)
                    if (!json.trim().isEmpty()) {
                        // 使用 kotlinx.serialization 更新现有对象
                        replace(INSTANCE, decode(json))
                        // 格式化检查
                        val formatted = encode(INSTANCE)
                        if (formatted != json) {
                            StructuredLog.record(TAG, "重新格式化 status.json")
                            Files.write2File(formatted, statusFile)
                        }
                    } else {
                        StructuredLog.record(TAG, "配置文件为空，初始化默认配置")
                        initializeDefaultConfig(statusFile)
                    }
                } else {
                    StructuredLog.record(TAG, "配置文件不存在，初始化默认配置")
                    initializeDefaultConfig(statusFile)
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, t)
                StructuredLog.record(TAG, "状态文件格式有误，已重置")
                resetAndSaveConfig(statusFile)
            }

            // 这里逻辑有点奇怪，如果 saveTime 是 0，则设为当前时间。
            // 原始 Java 代码中 Long 默认为 null，但这里属性初始化为 0L。
            if (INSTANCE.saveTime == 0L) {
                INSTANCE.saveTime = System.currentTimeMillis()
            }
            return INSTANCE
        }

        private fun initializeDefaultConfig(statusFile: java.io.File) {
            try {
                replace(INSTANCE, Status())
                StructuredLog.record(TAG, "初始化 status.json")
                Files.write2File(encode(INSTANCE), statusFile)
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, e)
                throw RuntimeException("初始化配置失败", e)
            }
        }

        private fun resetAndSaveConfig(statusFile: java.io.File) {
            try {
                replace(INSTANCE, Status())
                Files.write2File(encode(INSTANCE), statusFile)
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, e)
                throw RuntimeException("重置配置失败", e)
            }
        }

        @Synchronized
        @JvmStatic
        fun unload() {
            try {
                // 创建新状态实例并确保清空所有每日标记
                val newStatus = Status()
                // 确保清空flagList
                INSTANCE.flagList.clear()
                replace(INSTANCE, newStatus)
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, e)
            }
        }

        @Synchronized
        @JvmStatic
        fun save(nowCalendar: Calendar = Calendar.getInstance()) {
            val currentUid = UserMap.currentUid
            if (StringUtil.isEmpty(currentUid)) {
                StructuredLog.record(TAG, "用户为空，状态保存失败")
                throw RuntimeException("用户为空，状态保存失败")
            }
            if (updateDay(nowCalendar)) {
                StructuredLog.record(TAG, "重置 statistics.json")
            } else {
                StructuredLog.record(TAG, "保存 status.json")
            }
            val lastSaveTime = INSTANCE.saveTime
            try {
                INSTANCE.saveTime = System.currentTimeMillis()
                Files.write2File(encode(INSTANCE), Files.getStatusFile(currentUid)!!)
            } catch (e: Exception) {
                INSTANCE.saveTime = lastSaveTime
                throw e
            }
        }

        @Synchronized
        @JvmStatic
        fun updateDay(nowCalendar: Calendar): Boolean {
            if (TimeUtil.isLessThanSecondOfDays(INSTANCE.saveTime, nowCalendar.timeInMillis)) {
                unload()
                return true
            }
            return false
        }

        @Synchronized
        @JvmStatic
        fun canOrnamentToday(): Boolean {
            return INSTANCE.canOrnament
        }

        @Synchronized
        @JvmStatic
        fun setOrnamentToday() {
            if (INSTANCE.canOrnament) {
                INSTANCE.canOrnament = false
                save()
            }
        }

        @Synchronized
        @JvmStatic
        fun canStallDonateToday(): Boolean {
            return INSTANCE.canStallDonate
        }

        @Synchronized
        @JvmStatic
        fun setStallDonateToday() {
            if (INSTANCE.canStallDonate) {
                INSTANCE.canStallDonate = false
                save()
            }
        }

        /**
         * ## 设置今日已运行状态
         * @param flag tagName::done
         */
        @Synchronized
        @JvmStatic
        fun hasFlagToday(flag: String): Boolean =
            FlagStore.hasFlagToday(INSTANCE.flagList, flag)

        @Synchronized
        @JvmStatic
        fun setFlagToday(flag: String) {
            if (FlagStore.setFlagToday(INSTANCE.flagList, flag)) {
                save()
            }
        }

        // 2025/12/4 用来获取 自定义flag的int
        @Synchronized
        @JvmStatic
        fun getIntFlagToday(key: String): Int? =
            FlagStore.getIntFlag(INSTANCE.intFlagMap, key)

        @Synchronized
        @JvmStatic
        fun setIntFlagToday(key: String, value: Int) {
            FlagStore.setIntFlag(INSTANCE.intFlagMap, key, value)
            save()
        }

        @Synchronized
        @JvmStatic
        fun canMemberPointExchangeBenefitToday(benefitId: String): Boolean =
            FlagStore.canExchangeBenefitToday(INSTANCE.memberPointExchangeBenefitLogList, benefitId)

        @Synchronized
        @JvmStatic
        fun memberPointExchangeBenefitToday(benefitId: String) {
            if (FlagStore.markBenefitExchanged(INSTANCE.memberPointExchangeBenefitLogList, benefitId)) {
                save()
            }
        }

        /**
         * 乐园商城-是否可以兑换该商品
         *
         * @param spuId 商品spuId
         * @return true 可以兑换 false 兑换达到上限
         */
        @Synchronized
        @JvmStatic
        fun canParadiseCoinExchangeBenefitToday(spuId: String): Boolean {
            return !hasFlagToday("farm::paradiseCoinExchangeLimit::$spuId")
        }
    }
}
