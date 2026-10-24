package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.entity.AlipayUser
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.task.antFarm.AntFarm.AnimalFeedStatus
import io.github.magisk317.sesame.task.antFarm.AntFarm.AnimalInteractStatus
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optLong
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import java.util.Calendar
import java.util.Objects
import kotlin.math.abs

data object AntFarmFamily {
    private const val TAG = "小鸡家庭"

    /**
     * 家庭ID
     */
    private var groupId: String = ""

    /**
     * 家庭名称
     */
    private var groupName: String = ""

    /**
     * 家庭成员对象
     */
    private var familyAnimals: JsonArray = JsonArray(emptyList())

    /**
     * 家庭成员列表
     */
    private var familyUserIds: MutableList<String> = mutableListOf()

    /**
     * 互动功能列表
     */
    private var familyInteractActions: JsonArray = JsonArray(emptyList())

    /**
     * 美食配置对象
     */
    private var eatTogetherConfig: JsonObject = JsonObject(emptyMap())


    fun run(familyOptions: SelectModelField, notInviteList: SelectModelField) {
        try {
            enterFamily(familyOptions, notInviteList)
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }

    /**
     * 进入家庭
     */
    fun enterFamily(familyOptions: SelectModelField, notInviteList: SelectModelField) {
        try {
            val enterRes = FarmFamilyRpc.enterFamily().toJsonObject()
            if (ResChecker.checkRes(TAG, enterRes)) {
                if (!enterRes.has("groupId")) {
                    StructuredLog.farm("请先开通小鸡家庭");
                    return;
                }
                groupId = enterRes.getString("groupId")
                groupName = enterRes.getString("groupName")
                val familyAwardNum: Int = enterRes.optInt("familyAwardNum", 0)//奖励数量
                val familySignTips: Boolean = enterRes.optBoolean("familySignTips", false)//签到
                val assignFamilyMemberInfo: JsonObject? = enterRes.optJSONObject("assignFamilyMemberInfo")//分配成员信息-顶梁柱
                familyAnimals = enterRes.getJSONArray("animals")//家庭动物列表
                familyUserIds = (0..<familyAnimals.length())
                    .map { familyAnimals.getJSONObject(it).getString("userId") }
                    .toMutableList()
                familyInteractActions = enterRes.getJSONArray("familyInteractActions")//互动功能列表
                eatTogetherConfig = enterRes.getJSONObject("eatTogetherConfig")//美食配置对象


                if (familyOptions.value.contains("familySign") && familySignTips) {
                    familySign()
                }

                if (assignFamilyMemberInfo != null
                    && familyOptions.value.contains("assignRights")
                    && assignFamilyMemberInfo.getJSONObject("assignRights").getString("status") != "USED"
                ) {
                    if (assignFamilyMemberInfo.getJSONObject("assignRights").getString("assignRightsOwner") == UserMap.currentUid) {
                        assignFamilyMember(assignFamilyMemberInfo, familyUserIds)
                    } else {
                        StructuredLog.record("家庭任务🏡 使用顶梁柱特权 不是家里的顶梁柱！")
                        familyOptions.value.remove("assignRights")
                    }
                }

                if (familyOptions.value.contains("familyClaimReward") && familyAwardNum > 0) {
                    familyClaimRewardList()
                }

                if (familyOptions.value.contains("feedFamilyAnimal")) {
                    familyFeedFriendAnimal(familyAnimals)
                }

                if (familyOptions.value.contains("eatTogetherConfig")) {
                    familyEatTogether(eatTogetherConfig, familyInteractActions, familyUserIds)
                }

                if (familyOptions.value.contains("deliverMsgSend")) {
                    deliverMsgSend(familyUserIds)
                }

                if (familyOptions.value.contains("shareToFriends")) {
                    familyShareToFriends(familyUserIds, notInviteList)
                }
                if (familyOptions.value.contains("ExchangeFamilyDecoration")) {
                    autoExchangeFamilyDecoration()
                }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG,  e)
        }
    }

    /**
     * 家庭签到
     */
    fun familySign() {
        try {
            if (Status.hasFlagToday("farmfamily::dailySign")) return
            val res = FarmFamilyRpc.familyReceiveFarmTaskAward("FAMILY_SIGN_TASK").toJsonObject()
            if (ResChecker.checkRes(TAG, res)) {
                StructuredLog.farm("家庭任务🏡每日签到")
                Status.setFlagToday("farmfamily::dailySign")
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG,  e)
        }
    }

    /**
     * 领取家庭奖励
     */
    fun familyClaimRewardList() {
        try {
            var jo = FarmFamilyRpc.familyAwardList().toJsonObject()
            if (ResChecker.checkRes(TAG, jo)) {
                val ja = jo.getJSONArray("familyAwardRecordList")
                for (i in 0..<ja.length()) {
                    jo = ja.getJSONObject(i)
                    if (jo.optBoolean("expired")
                        || jo.optBoolean("received", true)
                        || jo.has("linkUrl")
                        || (jo.has("operability") && !jo.getBoolean("operability"))
                    ) {
                        continue
                    }
                    val rightId = jo.getString("rightId")
                    val awardName = jo.getString("awardName")
                    val count = jo.optInt("count", 1)
                    val receveRes = FarmFamilyRpc.receiveFamilyAward(rightId).toJsonObject()
                    if (ResChecker.checkRes(TAG, receveRes)) {
                        StructuredLog.farm("家庭奖励🏆: $awardName x $count")
                    }
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "家庭领取奖励", t)
        }
    }

    /**
     * 顶梁柱
     */
    fun assignFamilyMember(jsonObject: JsonObject, userIds: MutableList<String>) {
        try {
            userIds.remove(UserMap.currentUid)
            //随机选一个家庭成员
            if (userIds.isEmpty()) {
                return
            }
            val beAssignUser = userIds[RandomUtil.nextInt(0, userIds.size - 1)]
            //随机获取一个任务类型
            val assignConfigList = jsonObject.getJSONArray("assignConfigList")
            val assignConfig = assignConfigList.getJSONObject(RandomUtil.nextInt(0, assignConfigList.length() - 1))
            val jo = FarmFamilyRpc.assignFamilyMember(assignConfig.getString("assignAction"), beAssignUser).toJsonObject()
            if (ResChecker.checkRes(TAG, jo)) {
                StructuredLog.farm("家庭任务🏡 使用顶梁柱特权 ${assignConfig.getString("assignDesc")}")
//                FarmFamilyRpc.sendChat(assignConfig.getString("chatCardType"), beAssignUser)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    /**
     * 帮好友喂小鸡
     * @param animals 家庭动物列表
     */
    fun familyFeedFriendAnimal(animals: JsonArray) {
        try {
            for (i in 0 until animals.length()) {
                val animal = animals.getJSONObject(i)
                val status = animal.getJSONObject("animalStatusVO")

                val interactStatus = status.getString("animalInteractStatus")
                val feedStatus = status.getString("animalFeedStatus")

                // 过滤非 HOME / HUNGRY 的
                if (interactStatus != AnimalInteractStatus.HOME.name ||
                    feedStatus != AnimalFeedStatus.HUNGRY.name) continue

                val groupId = animal.getString("groupId")
                val farmId = animal.getString("farmId")
                val userId = animal.getString("userId")

                // 非好友 → 跳过
                if (!UserMap.getUserIdSet().contains(userId)) {
                    StructuredLog.error(TAG, "$userId 不是你的好友！ 跳过家庭喂食")
                    continue
                }

                val flagKey = "farm::feedFriendLimit::$userId"

                // 如果该用户已经记录今日上限 → 跳过
                if (Status.hasFlagToday(flagKey)) {
                    StructuredLog.record("[$userId] 今日喂鸡次数已达上限（已记录）🥣，跳过")
                    continue
                }

                // 调用 RPC
                val jo = FarmFamilyRpc.feedFriendAnimal(farmId, groupId).toJsonObject()

                // 统一错误码检查
                if (!jo.optBoolean("success", false)) {
                    val code = jo.optString("resultCode")

                    if (code == "391") {
                        // 记录该用户今日不能再喂
                        Status.setFlagToday(flagKey)
                        StructuredLog.record("[$userId] 今日帮喂次数已达上限🥣，已记录为当日限制")
                    } else {
                        StructuredLog.error(TAG, "喂食失败 user=$userId code=$code msg=${jo.optString("memo")}")
                    }
                    continue
                }

                // 正常成功
                val foodStock = jo.optInt("foodStock")
                val maskName = UserMap.getMaskName(userId)
                StructuredLog.farm("家庭任务🏡帮喂好友🥣[$maskName]的小鸡180g #剩余${foodStock}g")
            }

        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "familyFeedFriendAnimal err:",t)
        }
    }

    /**
     * 请客吃美食
     * @param eatTogetherConfig 美食配置对象
     * @param familyInteractActions 互动功能列表
     * @param familyUserIds 家庭成员列表
     */
    private fun familyEatTogether(eatTogetherConfig: JsonObject, familyInteractActions: JsonArray, familyUserIds: MutableList<String>) {
        try {
            if (Status.hasFlagToday("farmfamily::eatTogether")) return
            var isEat = false
            val periodItemList = eatTogetherConfig.optJSONArray("periodItemList")
            if (periodItemList == null || periodItemList.length() == 0) {
                StructuredLog.error(TAG, "美食不足,无法请客,请检查小鸡厨房")
                return
            }
            if (familyInteractActions.length() > 0) {
                for (i in 0..<familyInteractActions.length()) {
                    val familyInteractAction = familyInteractActions.getJSONObject(i)
                    if ("EatTogether" == familyInteractAction.optString("familyInteractType")) {
                        val endTime = familyInteractAction.optLong("interactEndTime", 0)
                        val gaptime = endTime - System.currentTimeMillis()
                        StructuredLog.record("正在吃..${formatDuration(gaptime)} 吃完")
                        return
                    }
                }
            }
            var periodName = ""
            val currentTime = Calendar.getInstance()
            for (i in 0..<periodItemList.length()) {
                val periodItem = periodItemList.getJSONObject(i)
                val startHour = periodItem.optInt("startHour")
                val startMinute = periodItem.optInt("startMinute")
                val endHour = periodItem.optInt("endHour")
                val endMinute = periodItem.optInt("endMinute")
                val startTime = Calendar.getInstance()
                startTime.set(Calendar.HOUR_OF_DAY, startHour)
                startTime.set(Calendar.MINUTE, startMinute)
                val endTime = Calendar.getInstance()
                endTime.set(Calendar.HOUR_OF_DAY, endHour)
                endTime.set(Calendar.MINUTE, endMinute)
                if (currentTime.after(startTime) && currentTime.before(endTime)) {
                    periodName = periodItem.optString("periodName")
                    isEat = true
                    break
                }
            }
            if (!isEat) {
                StructuredLog.record("家庭任务🏡请客吃美食#当前时间不在美食时间段")
                return
            }
            if (Objects.isNull(familyUserIds) || familyUserIds.isEmpty()) {
                StructuredLog.record("家庭成员列表为空,无法请客")
                return
            }
            val array: JsonArray? = queryRecentFarmFood(familyUserIds.size)
            if (array == null) {
                StructuredLog.record("查询最近的几份美食为空,无法请客")
                return
            }
            val jo = FarmFamilyRpc.familyEatTogether(groupId, jsonArray(*familyUserIds.map(::JsonPrimitive).toTypedArray()), array)
            if (ResChecker.checkRes(TAG, jo)) {
                StructuredLog.farm("家庭任务🏡请客" + periodName + "#消耗美食" + familyUserIds.size + "份")
                Status.setFlagToday("farmfamily::eatTogether")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "familyEatTogether err:",t)
        }
    }

    /**
     * 查询最近的几份美食
     * @param queryNum 查询数量
     */
    fun queryRecentFarmFood(queryNum: Int): JsonArray? {
        try {
            val jo = FarmFamilyRpc.queryRecentFarmFood(queryNum).toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) {
                return null
            }
            val cuisines = jo.getJSONArray("cuisines")
            var count = 0
            for (i in 0..<cuisines.length()) {
                val cuisine = cuisines.getJSONObject(i)
                count += cuisine.optInt("count")
            }
            if (queryNum <= count) {
                return cuisines
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryRecentFarmFood err:", t)
        }
        return null
    }

    /**
     * 家庭「道早安」任务
     *
     *
     *
     * 1）先通过 familyTaskTips 判断今日是否还有「道早安」任务：
     *    - 请求方法：com.alipay.antfarm.familyTaskTips
     *    - 请求体关键字段：
     *        animals      -> 直接复用 enterFamily 返回的家庭 animals 列表
     *        taskSceneCode-> "ANTFARM_FAMILY_TASK"
     *        sceneCode    -> "ANTFARM"
     *        source       -> "H5"
     *        requestType  -> "NORMAL"
     *        timeZoneId   -> "Asia/Shanghai"
     *    - 响应 familyTaskTips 数组中存在 bizKey="GREETING" 且 taskStatus="TODO" 时，说明可以道早安
     *
     * 2）未完成早安任务时，按顺序调用以下 RPC 获取 AI 文案并发送：
     *    a. com.alipay.antfarm.deliverSubjectRecommend
     *       -> 入参：friendUserIds（家庭其他成员 userId 列表），sceneCode="ChickFamily"，source="H5"
     *       -> 取出：ariverRpcTraceId、eventId、eventName、sceneId、sceneName 等上下文
     *    b. com.alipay.antfarm.DeliverContentExpand
     *       -> 入参：上一步取到的 ariverRpcTraceId / eventId / eventName / sceneId / sceneName 等 + friendUserIds
     *       -> 返回：AI 生成的 content 以及 deliverId
     *    c. com.alipay.antfarm.QueryExpandContent
     *       -> 入参：deliverId
     *       -> 用于再次确认 content 与场景（可选安全校验）
     *    d. com.alipay.antfarm.DeliverMsgSend
     *       -> 入参：content、deliverId、friendUserIds、groupId（家庭 groupId）、sceneCode="ANTFARM"、spaceType="ChickFamily" 等
     *
     *   额外增加保护：
     *  - 仅在每天 06:00~10:00 之间执行
     *  - 每日仅发送一次（本地 Status 标记 + 远端 familyTaskTips 双重判断）
     *  - 自动从家庭成员列表中移除自己，避免接口报参数错误
     *
     * @param familyUserIds 家庭成员 userId 列表（包含自己，方法内部会移除当前账号）
     */
    fun deliverMsgSend(familyUserIds: MutableList<String>) {
        try {
            // 1. 时间窗口控制：仅允许在「早安时间段」内自动发送（06:00 ~ 10:00）
            val now = Calendar.getInstance()
            val startTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (now.before(startTime) || now.after(endTime)) {
                StructuredLog.record(TAG, "家庭任务🏡道早安#当前时间不在 06:00-10:00，跳过")
                return
            }

            // groupId 是 enterFamily 返回的家庭 ID，如果为空说明当前账号未开通家庭
            if (groupId.isEmpty()) {
                StructuredLog.record(TAG, "家庭任务🏡道早安#未检测到家庭 groupId，可能尚未加入家庭，跳过")
                return
            }

            // 本地去重：一天只发送一次，避免重复打扰
            if (Status.hasFlagToday("antFarm::deliverMsgSend")) {
                StructuredLog.record(TAG, "家庭任务🏡道早安#今日已在本地发送过，跳过")
                return
            }

            // 2. 远端任务状态校验：确认「道早安」任务是否仍为 TODO
            try {
                val taskTipsRes = FarmFamilyRpc.familyTaskTips(familyAnimals).toJsonObject()
                if (!ResChecker.checkRes(TAG, taskTipsRes)) {
                    StructuredLog.error(TAG, "家庭任务🏡道早安#familyTaskTips 调用失败，跳过")
                    return
                }

                val taskTips = taskTipsRes.optJSONArray("familyTaskTips")
                if (taskTips == null || taskTips.length() == 0) {
                    // familyTaskTips 为空：要么今天已经完成，要么当前无早安任务
                    StructuredLog.record(TAG, "家庭任务🏡道早安#远端无 GREETING 任务，可能今日已完成，跳过")
                    Status.setFlagToday("antFarm::deliverMsgSend")
                    return
                }

                var hasGreetingTodo = false
                for (i in 0 until taskTips.length()) {
                    val item = taskTips.getJSONObject(i)
                    val bizKey = item.optString("bizKey")
                    val taskStatus = item.optString("taskStatus")
                    if ("GREETING" == bizKey && "TODO" == taskStatus) {
                        hasGreetingTodo = true
                        break
                    }
                }

                if (!hasGreetingTodo) {
                    StructuredLog.record(TAG, "家庭任务🏡道早安#GREETING 任务非 TODO 状态，跳过")
                    Status.setFlagToday("antFarm::deliverMsgSend")
                    return
                }
            } catch (e: Throwable) {
                // safety：远端任务判断异常时，为了避免误刷，多数情况下选择跳过
                StructuredLog.printStackTrace(TAG, "familyTaskTips 解析失败，出于安全考虑跳过道早安：", e)
                return
            }

            // 3. 构建好友 userId 列表（去掉自己）
            // 先移除当前用户自己的 ID，否则 DeliverMsgSend 等接口会因为参数不合法而报错
            familyUserIds.remove(UserMap.currentUid)
            if (familyUserIds.isEmpty()) {
                StructuredLog.record(TAG, "家庭任务🏡道早安#家庭成员仅自己一人，跳过")
                return
            }

            val userIds = buildJsonArray {
                for (userId in familyUserIds) add(JsonPrimitive(userId))
            }

            // 4. 确认 AI 隐私协议（OpenAIPrivatePolicy 抓包见看我.txt 中 deliverChickInfoVO.privatePolicyId）
            val resp0 = FarmFamilyRpc.openAiPrivatePolicy().toJsonObject()
            if (!ResChecker.checkRes(TAG, resp0)) {
                StructuredLog.error(TAG, "家庭任务🏡道早安#OpenAIPrivatePolicy 调用失败")
                return
            }

            // 5. 请求推荐早安场景（deliverSubjectRecommend）以获取事件上下文
            val resp1 = FarmFamilyRpc.deliverSubjectRecommend(userIds).toJsonObject()
            if (!ResChecker.checkRes(TAG, resp1)) {
                StructuredLog.error(TAG, "家庭任务🏡道早安#deliverSubjectRecommend 调用失败")
                return
            }

            // 提取后续调用所需的关键字段（均为动态值，绝不可写死）
            val ariverRpcTraceId = resp1.getString("ariverRpcTraceId")
            val eventId = resp1.getString("eventId")
            val eventName = resp1.getString("eventName")
            val memo = resp1.optString("memo")
            val resultCode = resp1.optString("resultCode")
            val sceneId = resp1.getString("sceneId")
            val sceneName = resp1.getString("sceneName")
            val success = resp1.optBoolean("success", true)

            // 6. 调用 DeliverContentExpand，实际向 AI 请求生成完整早安文案
            val resp2 = FarmFamilyRpc.deliverContentExpand(
                    ariverRpcTraceId,
                    eventId,
                    eventName,
                    memo,
                    resultCode,
                    sceneId,
                    sceneName,
                    success,
                    userIds
                )
                .toJsonObject()
            if (!ResChecker.checkRes(TAG, resp2)) {
                StructuredLog.error(TAG, "家庭任务🏡道早安#DeliverContentExpand 调用失败")
                return
            }

            val deliverId = resp2.getString("deliverId")

            // 7. 使用 deliverId 再次确认扩展内容，得到最终的早安文案
            val resp3 = FarmFamilyRpc.queryExpandContent(deliverId).toJsonObject()
            if (!ResChecker.checkRes(TAG, resp3)) {
                StructuredLog.error(TAG, "家庭任务🏡道早安#QueryExpandContent 调用失败")
                return
            }

            val content = resp3.getString("content")

            // 8. 最终发送早安消息：DeliverMsgSend
            val resp4 = FarmFamilyRpc.deliverMsgSend(groupId, userIds, content, deliverId).toJsonObject()
            if (ResChecker.checkRes(TAG, resp4)) {
                StructuredLog.farm("家庭任务🏡道早安: $content 🌈")
                Status.setFlagToday("antFarm::deliverMsgSend")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "deliverMsgSend err:", t)
        }
    }

    /**
     * 好友分享家庭
     * @param familyUserIds 好友列表
     * @param notInviteList 不邀请列表
     */
    private fun familyShareToFriends(familyUserIds: MutableList<String>, notInviteList: SelectModelField) {
        try {
            if (Status.hasFlagToday("antFarm::familyShareToFriends")) {
                return
            }

            val familyValue: MutableSet<String?> = notInviteList.value
            val allUser: List<AlipayUser> = AlipayUser.getList()

            if (allUser.isEmpty()) {
                StructuredLog.error(TAG, "allUser is empty")
                return
            }

            // 打乱顺序，实现随机选取
            val shuffledUsers = allUser.shuffled()

            val inviteList = buildJsonArray {
                shuffledUsers
                    .asSequence()
                    .filter { !familyUserIds.contains(it.id) && !familyValue.contains(it.id) }
                    .take(6)
                    .forEach { add(JsonPrimitive(it.id)) }
            }

            if (inviteList.length() == 0) {
                StructuredLog.error(TAG, "没有符合分享条件的好友")
                return
            }

            StructuredLog.record(TAG, "inviteList: $inviteList")

            val jo = FarmFamilyRpc.inviteFriendVisitFamily(inviteList).toJsonObject()
            if (ResChecker.checkRes(TAG, jo)) {
                StructuredLog.farm("家庭任务🏡分享好友")
                Status.setFlagToday("antFarm::familyShareToFriends")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "familyShareToFriends err:", t)
        }
    }


    /**
     * 自动购买家具
     */
    fun autoExchangeFamilyDecoration() {
        StructuredLog.record(TAG, "家庭装扮🏠 启动分类购买任务...")
        try {
            // 获取活动 ID
            val familyRes = FarmFamilyRpc.enterFamily()
            val familyJo = familyRes.toJsonObject()
            if (!ResChecker.checkRes(TAG, familyJo)) return

            val activityId = familyJo.optString("decorationCoinActivityId", "20250808")
            StructuredLog.record(TAG, "家庭装扮🏠 当前活动 ID: $activityId")

            // 分类列表
            val labelTypes = listOf(
                "", "recentlyAdded", "sofa", "seat2", "seat4", "seat5", "seat3",
                "curtain", "table", "carpet", "mattress", "bed3", "bed4",
                "bed5", "ceiling", "windowView", "firstFloor", "firstWall", "secondFloor",
                "secondWall", "leftWallDecoration", "rightWallDecoration", "treadmill", "slide"
            )

            var currentBalance = 0

            for (label in labelTypes) {
                var startIndex = 0
                var hasMore = true
                StructuredLog.record(TAG, "家庭装扮🏠 正在检查分类: ${if (label.isEmpty()) "新品" else label}")

                while (hasMore) {
                    val itemListRes = FarmGameMallRpc.getFitmentItemList(activityId, 10, label, startIndex)
                    val itemJo = itemListRes.toJsonObject()
                    if (!ResChecker.checkRes(TAG, itemJo)) break

                    // 解析实时装修金余额
                    val accountInfo = itemJo.optJSONObject("mallAccountInfoVO")
                    currentBalance = accountInfo?.optJSONObject("holdingCount")?.optInt("cent") ?: 0

                    val items = itemJo.optJSONArray("itemInfoVOList")
                    if (items == null || items.length() == 0) break

                    for (j in 0 until items.length()) {
                        val item = items.getJSONObject(j)
                        val spuId = item.getString("spuId")
                        val spuName = item.getString("spuName")
                        val price = item.optJSONObject("minPrice")?.optInt("cent") ?: 9999999

                        val itemStatusList = item.optJSONArray("itemStatusList")
                        val canBuy = itemStatusList == null || itemStatusList.length() == 0

                        if (canBuy && currentBalance >= price) {
                            val skuList = item.optJSONArray("skuModelList")
                            if (skuList != null && skuList.length() > 0) {
                                val skuId = skuList.getJSONObject(0).getString("skuId")
                                StructuredLog.record(TAG, "家庭装扮🏠 发现未拥有家具: $spuName")

                                val exchangeRes = FarmGameMallRpc.exchangeBenefit(spuId, skuId, activityId)
                                val exchangeJo = exchangeRes.toJsonObject()

                                if (ResChecker.checkRes(TAG, exchangeJo)) {
                                    StructuredLog.farm("家庭装扮💸#成功购买[$spuName]#消耗[${price/100}装修金]")
                                    currentBalance -= price
                                }
                                GlobalThreadPools.sleepCompat(2000)
                            }
                        }
                    }

                    val nextIndex = itemJo.optInt("nextStartIndex", 0)
                    val hasMoreField = itemJo.optBoolean("hasMore", false)
                    if (hasMoreField && nextIndex > startIndex) {
                        startIndex = nextIndex
                    } else {
                        hasMore = false
                    }
                }

                // 当处理完 seat3 分类后，如果装修金 < 49，终止后续更贵的分类的遍历
                if (currentBalance < 4900 && label == "seat3") {
                    StructuredLog.record(TAG, "家庭装扮🏠 装修金不足 49 且已完成 seat3 遍历，终止任务")
                    break
                }
            }
            StructuredLog.record(TAG, "家庭装扮🏠 全量检查任务执行完毕")
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "autoExchangeFamilyDecoration 失败", t)
        }
    }


    /**
     * 通用时间差格式化（自动区分过去/未来）
     * @param diffMillis 任意时间戳（毫秒）
     * @return 易读字符串，如 "刚刚", "5分钟后", "3天前"
     */
    fun formatDuration(diffMillis: Long): String {
        val absolute = abs(diffMillis)
        if (absolute < TimeFormatter.ONE_SECOND_MS) return "刚刚"
        val suffix = if (diffMillis > 0) "后" else "前"
        return "${TimeFormatter.formatDuration(absolute)} $suffix"
    }
}
