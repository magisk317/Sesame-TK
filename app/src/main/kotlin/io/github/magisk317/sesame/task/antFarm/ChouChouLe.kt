package io.github.magisk317.sesame.task.antFarm


import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.coroutines.CancellationException
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optLong
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.model.ModelRegistry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs
import kotlin.math.max

/**
 * 小鸡抽抽乐功能类
 */
class ChouChouLe(
    private val allowDonationTasks: () -> Boolean = { false },
    private val allowAutoExchange: () -> Boolean = { false },
) {

    companion object {
        private val TAG = ChouChouLe::class.java.simpleName
    }

    /**
     * 任务状态枚举
     */
    @Suppress("unused")
    enum class TaskStatus {
        TODO, FINISHED, RECEIVED, DONATION
    }

    /**
     * 任务信息结构体
     */
    private data class TaskInfo(
        var taskStatus: String = "",
        var title: String = "",
        var taskId: String = "",
        var innerAction: String = "",
        var rightsTimes: Int = 0,
        var rightsTimesLimit: Int = 0,
        var awardType: String = "",
        var awardCount: Int = 0,
        var targetUrl: String = ""
    ) {
        /**
         * 获取剩余次数
         */
        fun getRemainingTimes(): Int {
            return max(0, rightsTimesLimit - rightsTimes)
        }
    }

    /**
     * 抽抽乐主入口
     * 返回值判断是否真的完成任务，是否全部执行完毕且无剩余（任务已做、奖励已领、抽奖已完）
     */
    fun chouchoule(): Boolean {
        var allFinished = true
        try {
            val response = FarmDrawRpc.queryLoveCabin(UserMap.currentUid)
            val jo = response.toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) {
                return false
            }

            val drawMachineInfo = jo.optJSONObject("drawMachineInfo")
            if (drawMachineInfo == null) {
                StructuredLog.error(TAG, "抽抽乐🎁[获取抽抽乐活动信息失败]")
                return false
            }

            // 执行IP抽抽乐
            if (drawMachineInfo.has("ipDrawMachineActivityId")) {
                // Keep the result when both the IP and daily campaigns exist.
                // The previous assignment could overwrite an IP failure with a
                // later successful daily campaign.
                allFinished = allFinished and doChouchoule("ipDraw")
            }

            // 执行普通抽抽乐
            if (drawMachineInfo.has("dailyDrawMachineActivityId")) {
                allFinished = allFinished and doChouchoule("dailyDraw")
            }

            return allFinished
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("chouchoule err:", t)
            return false
        }
    }

    /**
     * 执行抽抽乐
     *
     * @param drawType "dailyDraw" 或 "ipDraw"
     * 返回是否该类型已全部完成
     */
    private fun doChouchoule(drawType: String): Boolean {
        var doubleCheck: Boolean
        try {
            do {
                doubleCheck = false
                val jo = FarmDrawRpc.chouchouleListFarmTask(drawType).toJsonObject()
                if (!ResChecker.checkRes(TAG, jo)) {
                    StructuredLog.error(TAG, if (drawType == "ipDraw") "IP抽抽乐任务列表获取失败" else "抽抽乐任务列表获取失败")
                    return false
                }

                val farmTaskList = jo.getJSONArray("farmTaskList")
                val tasks = parseTasks(farmTaskList)

                for (task in tasks) {
                    if (TaskStatus.FINISHED.name == task.taskStatus) {
                        if (receiveTaskAward(drawType, task.taskId)) {
                            GlobalThreadPools.sleepCompat(300L)
                            doubleCheck = true
                        }
                    } else if (TaskStatus.TODO.name == task.taskStatus) {
                        // 只要有剩余次数，且（不是捐赠任务 OR 开启了捐赠任务开关），就执行
                        if (task.getRemainingTimes() > 0 &&
                            (task.innerAction != "DONATION" || allowDonationTasks())) {
                            if (doChouTask(drawType, task)) {
                                doubleCheck = true
                            }
                        }
                    }
                }
            } while (doubleCheck)
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("doChouchoule err:", t)
            return false
        }

        // 执行抽奖
        val drawSuccess = if ("ipDraw" == drawType) {
            handleIpDraw()
        } else {
            handleDailyDraw()
        }

        if (!drawSuccess) return false

        // 最后校验是否真的全部完成
        return verifyFinished(drawType)
    }

    /*
     校验是否还有未完成的任务或抽奖
     */
    private fun verifyFinished(drawType: String): Boolean {
        return try {
            // 校验任务
            val jo = FarmDrawRpc.chouchouleListFarmTask(drawType).toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) return false

            val farmTaskList = jo.getJSONArray("farmTaskList")
            val tasks = parseTasks(farmTaskList)
            for (task in tasks) {
                if (TaskStatus.FINISHED.name == task.taskStatus) {
                    return false
                } else if (TaskStatus.TODO.name == task.taskStatus) {
                    // 还有剩余次数且满足执行条件
                    if (task.getRemainingTimes() > 0 &&
                        (task.innerAction != "DONATION" || allowDonationTasks())) {
                        return false
                    }
                }
            }

            // 校验抽奖次数
            val drawJo = if ("ipDraw" == drawType) {
                FarmDrawRpc.queryDrawMachineActivity("ipDrawMachine", "dailyDrawMachine").toJsonObject()
            } else {
                FarmDrawRpc.queryDrawMachineActivity("dailyDrawMachine", "ipDrawMachine").toJsonObject()
            }
            if (!ResChecker.checkRes(TAG, drawJo)) return false
            val drawTimes = drawJo.optInt("drawTimes", 0)
            if (drawTimes > 0) return false

            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 解析任务列表
     */
    @Throws(Exception::class)
    private fun parseTasks(array: JsonArray): List<TaskInfo> {
        val list = ArrayList<TaskInfo>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val info = TaskInfo(
                taskStatus = item.getString("taskStatus"),
                title = item.getString("title"),
                taskId = item.getString("bizKey"),
                innerAction = item.optString("innerAction"),
                rightsTimes = item.optInt("rightsTimes", 0),
                rightsTimesLimit = item.optInt("rightsTimesLimit", 0),
                awardType = item.optString("awardType"),
                awardCount = item.optInt("awardCount", 0),
                targetUrl = item.optString("targetUrl", "")
            )
            list.add(info)
        }
        return list
    }

    /**
     * 执行任务
     */
    private fun doChouTask(drawType: String, task: TaskInfo): Boolean {
        try {
            val antFarm = ModelRegistry.getModel(AntFarm::class.java) ?: return false
            val taskName = if (drawType == "ipDraw") "IP抽抽乐" else "抽抽乐"

            // 特殊任务：浏览广告
            if (task.taskId == "SHANGYEHUA_DAILY_DRAW_TIMES" || task.taskId == "IP_SHANGYEHUA_TASK") {
                return handleAdTask(drawType, task)
            }

            // 普通任务
            if (task.title == "消耗饲料换机会") {
                if (antFarm.foodStock < 90) {
                    StructuredLog.record(TAG, "饲料余量(${antFarm.foodStock}g)少于90g，跳过任务: ${task.title}")
                    return false // 返回 false 避免 doubleCheck，且不执行后续 RPC
                }
            }
            val s = FarmDrawRpc.chouchouleDoFarmTask(drawType, task.taskId)
            val jo = s.toJsonObject()
            if (ResChecker.checkRes(TAG, jo)) {
                StructuredLog.farm("$taskName🧾️[任务: ${task.title}]")
                if (task.title == "消耗饲料换机会") {
                    GlobalThreadPools.sleepCompat(300L)
                } else {
                    GlobalThreadPools.sleepCompat(1000L)
                }
                return true
            } else {
                val resultCode = jo.optString("resultCode")
                if ("DRAW_MACHINE07" == resultCode) {
                    StructuredLog.record(TAG, "${taskName}任务[${task.title}]失败: 饲料不足，停止后续尝试")
                    return false
                }
            }
            return false
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("执行抽抽乐任务 err:", t)
            return false
        }
    }

    /**
     * 处理广告任务
     */
    private fun handleAdTask(drawType: String, task: TaskInfo): Boolean {
        try {
            val antFarm = ModelRegistry.getModel(AntFarm::class.java) ?: return false
            val referToken = antFarm.loadAntFarmReferToken()
            val taskSceneCode = if (drawType == "ipDraw") "ANTFARM_IP_DRAW_TASK" else "ANTFARM_DAILY_DRAW_TASK"

            // 如果有referToken，尝试执行广告任务
            if (!referToken.isNullOrEmpty()) {
                val response = FarmDrawRpc.xlightPlugin(referToken, "HDWFCJGXNZW_CUSTOM_20250826173111")
                val jo = response.toJsonObject()

                if (jo.optString("retCode") == "0") {
                    val resData = jo.getJSONObject("resData")
                    val adList = resData.optJSONArray("adList")

                    if (adList != null && adList.length() > 0) {
                        // 检查是否有猜一猜任务
                        val playingResult = resData.optJSONObject("playingResult")
                        if (playingResult != null &&
                            "XLIGHT_GUESS_PRICE_FEEDS" == playingResult.optString("playingStyleType")
                        ) {
                            return handleGuessTask(drawType, task, adList, playingResult)
                        }
                    }
                }
                StructuredLog.record(TAG, "抽抽乐🎁 浏览广告任务[没有可用广告或不支持，使用普通完成方式]")
            } else {
                StructuredLog.record(TAG, "抽抽乐🎁 浏览广告任务[没有可用Token，请手动看一起广告]")
            }

            // 没有token或广告任务失败，使用普通完成方式
            val outBizNo = task.taskId + "_" + System.currentTimeMillis() + "_" +
                    Integer.toHexString((Math.random() * 0xFFFFFF).toInt())
            val response = FarmDrawRpc.finishTask(task.taskId, taskSceneCode, outBizNo)
            val jo = response.toJsonObject()

            if (jo.optBoolean("success", false)) {
                StructuredLog.farm((if (drawType == "ipDraw") "IP抽抽乐" else "抽抽乐") + "🧾️[任务: ${task.title}]")
                GlobalThreadPools.sleepCompat(3000L)
                return true
            }
            return false
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("处理广告任务 err:", t)
            return false
        }
    }

    /**
     * 处理猜一猜任务
     */
    private fun handleGuessTask(
        drawType: String, task: TaskInfo,
        adList: JsonArray, playingResult: JsonObject
    ): Boolean {
        try {
            // 找到正确价格
            var correctPrice = -1
            var targetAdId = ""

            for (i in 0 until adList.length()) {
                val ad = adList.getJSONObject(i)
                val schemaJson = ad.optString("schemaJson", "")
                if (schemaJson.isNotEmpty()) {
                    val schema = schemaJson.toJsonObject()
                    val price = schema.optInt("price", -1)
                    if (price > 0) {
                        if (correctPrice == -1 || abs(price - 11888) < abs(correctPrice - 11888)) {
                            correctPrice = price
                            targetAdId = ad.optString("adId", "")
                        }
                    }
                }
            }

            if (correctPrice > 0 && targetAdId.isNotEmpty()) {
                // 提交猜价格结果
                val playBizId = playingResult.optString("playingBizId", "")
                val eventRewardDetail = playingResult.optJSONObject("eventRewardDetail")
                if (eventRewardDetail != null) {
                    val eventRewardInfoList = eventRewardDetail.optJSONArray("eventRewardInfoList")
                    if (eventRewardInfoList != null && eventRewardInfoList.length() > 0) {
                        val playEventInfo = eventRewardInfoList.getJSONObject(0)

                        val taskSceneCode =
                            if (drawType == "ipDraw") "ANTFARM_IP_DRAW_TASK" else "ANTFARM_DAILY_DRAW_TASK"

                        val response = FarmDrawRpc.finishAdTask(
                            playBizId, playEventInfo, task.taskId, taskSceneCode
                        )
                        val jo = response.toJsonObject()

                        if (jo.optJSONObject("resData") != null &&
                            jo.getJSONObject("resData").optBoolean("success", false)
                        ) {
                            StructuredLog.farm(
                                (if (drawType == "ipDraw") "IP抽抽乐" else "抽抽乐") +
                                        "🧾️[猜价格任务完成: ${task.title}, 猜中价格: $correctPrice]"
                            )
                            GlobalThreadPools.sleepCompat(300L)
                            return true
                        }
                    }
                }
            }

            StructuredLog.record(TAG, "抽抽乐🎁 猜价格任务[未找到合适价格，使用普通完成方式]")
            return false
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("处理猜价格任务 err:", t)
            return false
        }
    }

    /**
     * 领取任务奖励
     */
    private fun receiveTaskAward(drawType: String, taskId: String): Boolean {
        try {
            val s = FarmDrawRpc.chouchouleReceiveFarmTaskAward(drawType, taskId)
            val jo = s.toJsonObject()
            if (ResChecker.checkRes(TAG, jo)) {
                return true
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("receiveFarmTaskAward err:", t)
        }
        return false
    }

    /** Claim-only scan used by the session-level award poller. */
    suspend fun claimPendingAwards() {
        try {
            listOf("dailyDraw", "ipDraw").forEach { drawType ->
                val response = FarmDrawRpc.chouchouleListFarmTask(drawType).toJsonObject()
                val tasks = parseTasks(response.optJSONArray("farmTaskList") ?: return@forEach)
                tasks.asSequence()
                    .filter { it.taskStatus == TaskStatus.FINISHED.name }
                    .take(10)
                    .forEach { task ->
                        if (receiveTaskAward(drawType, task.taskId)) {
                            StructuredLog.farm("${if (drawType == "ipDraw") "IP抽抽乐" else "抽抽乐"}奖励[${task.title}]")
                        }
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "庄园抽抽乐领奖轮询异常", t)
        }
    }

    /**
     * 执行IP抽抽乐抽奖
     */
    private fun handleIpDraw(): Boolean {
        try {
            val jo = FarmDrawRpc.queryDrawMachineActivity(
                "ipDrawMachine", "dailyDrawMachine"
            ).toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) {
                return false
            }

            val activity = jo.optJSONObject("drawMachineActivity") ?: return true
            val activityId = activity.optString("activityId")
            val endTime = activity.optLong("endTime", 0)
            if (endTime > 0 && System.currentTimeMillis() > endTime) {
                StructuredLog.record(TAG, "庄园抽抽乐🎁 活动[${activity.optString("activityId")}]已关闭")
                return true
            }

            var remainingTimes = jo.optInt("drawTimes", 0)
            var allSuccess = true
            StructuredLog.record(TAG, "IP抽抽乐剩余次数: $remainingTimes")

            while (remainingTimes > 0) {
                val batchCount = remainingTimes.coerceAtMost(10)
                StructuredLog.record(TAG, "执行 IP 抽抽乐 $batchCount 连抽...")

                val response = FarmDrawRpc.drawMachineIP(batchCount)
                allSuccess = allSuccess and drawPrize("IP抽抽乐", response)

                remainingTimes -= batchCount
                if (remainingTimes > 0) {
                    GlobalThreadPools.sleepCompat(1500L)
                }
            }
            if (activityId.isNotEmpty() && allowAutoExchange()) {
                batchExchangeRewards(activityId)
            }
            return allSuccess
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("handleIpDraw err:", t)
            return false
        }
    }

    /**
     * 执行普通抽抽乐抽奖
     */
    private fun handleDailyDraw(): Boolean {
        try {
            val jo = FarmDrawRpc.queryDrawMachineActivity(
                "dailyDrawMachine", "ipDrawMachine"
            ).toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) {
                return false
            }

            val activity = jo.optJSONObject("drawMachineActivity") ?: return true
            val endTime = activity.optLong("endTime", 0)
            if (endTime > 0 && System.currentTimeMillis() > endTime) {
                StructuredLog.record(TAG, "庄园抽抽乐🎁 活动[${activity.optString("activityId")}]已关闭")
                return true
            }

            var remainingTimes = jo.optInt("drawTimes", 0)
            var allSuccess = true

            StructuredLog.record(TAG, "日常抽抽乐剩余次数: $remainingTimes")

            while (remainingTimes > 0) {
                val batchCount = remainingTimes.coerceAtMost(10)
                StructuredLog.record(TAG, "执行日常抽抽乐 $batchCount 连抽...")

                val response = FarmDrawRpc.drawMachineDaily(batchCount)
                allSuccess = allSuccess and drawPrize("日常抽抽乐", response)

                remainingTimes -= batchCount
                if (remainingTimes > 0) {
                    GlobalThreadPools.sleepCompat(1500L)
                }
            }
            return allSuccess
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("handleDailyDraw err:", t)
            return false
        }
    }

    /**
     * 领取抽抽乐奖品
     *
     * @param prefix   抽奖类型前缀
     * @param response 服务器返回的结果
     * 返回是否领取成功
     */
    private fun drawPrize(prefix: String, response: String): Boolean {
        try {
            val jo = response.toJsonObject()
            if (ResChecker.checkRes(TAG, jo)) {
                val prizeList = jo.optJSONArray("drawMachinePrizeList")
                if (prizeList != null && prizeList.length() > 0) {
                    for (i in 0 until prizeList.length()) {
                        val prize = prizeList.getJSONObject(i)
                        val title = prize.optString("title", prize.optString("prizeName", "未知奖品"))
                        StructuredLog.farm("$prefix🎁[领取: $title]")
                    }
                } else {
                    val prize = jo.optJSONObject("drawMachinePrize")
                    if (prize != null) {
                        val title = prize.optString("title", prize.optString("prizeName", "未知奖品"))
                        StructuredLog.farm("$prefix🎁[领取: $title]")
                    } else {
                        StructuredLog.farm("$prefix🎁[抽奖成功，但未解析到具体奖品名称]")
                    }
                }
                return true
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace("drawPrize err:", t)
        }
        return false
    }

    /**
     * 批量兑换奖励（严格优先级策略：攒钱买最好的）
     */
    fun batchExchangeRewards(activityId: String) {
        try {
            val response = FarmGameMallRpc.getItemList(activityId, 10, 0)
            val respJson = response.toJsonObject()

            if (respJson.optBoolean("success", false) || respJson.optString("code") == "100000000") {
                var totalCent = 0
                val mallAccount = respJson.optJSONObject("mallAccountInfoVO")
                if (mallAccount != null) {
                    val holdingCount = mallAccount.optJSONObject("holdingCount")
                    if (holdingCount != null) {
                        totalCent = holdingCount.optInt("cent", 0)
                    }
                }
                StructuredLog.record("自动兑换", "当前持有总碎片: " + (totalCent / 100))
                val itemVOList = respJson.optJSONArray("itemInfoVOList") ?: return

                val allSkus = ArrayList<JsonObject>()
                for (i in 0 until itemVOList.length()) {
                    val item = itemVOList.optJSONObject(i) ?: continue
                    val itemReachedLimit = isReachedLimit(item)
                    val minPriceObj = item.optJSONObject("minPrice")
                    val cent = minPriceObj?.optInt("cent", 0) ?: 0

                    val skuList = item.optJSONArray("skuModelList") ?: continue
                    for (j in 0 until skuList.length()) {
                        val sku = skuList.optJSONObject(j) ?: continue
                        allSkus.add(JsonObject(sku + mapOf(
                            "_spuId" to kotlinx.serialization.json.JsonPrimitive(item.optString("spuId")),
                            "_spuName" to kotlinx.serialization.json.JsonPrimitive(item.optString("spuName")),
                            "_isReachLimit" to kotlinx.serialization.json.JsonPrimitive(itemReachedLimit || isReachedLimit(sku)),
                            "_cent" to kotlinx.serialization.json.JsonPrimitive(cent),
                        )))
                    }
                }
                allSkus.sortWith { a, b -> b.optInt("_cent", 0).compareTo(a.optInt("_cent", 0)) }

                for (sku in allSkus) {
                    if (sku.optBoolean("_isReachLimit")) continue
                    val cent = sku.optInt("_cent", 0)
                    val skuName = sku.optString("skuName")

                    if (isNoEnoughPoint(sku) || (cent > 0 && totalCent < cent)) {
                        StructuredLog.record("自动兑换", "最高价值项 [$skuName] 碎片不足(持有 ${totalCent/100}, 需 ${cent/100})，等攒够再换，终止本次兑换")
                        return
                    }
                    break
                }

                // 执行顺序兑换，按价格从高到低
                for (sku in allSkus) {
                    if (sku.optBoolean("_isReachLimit")) continue

                    val skuName = sku.optString("skuName")
                    val cent = sku.optInt("_cent", 0)
                    val extendInfo = sku.optString("skuExtendInfo")
                    val limitCount = if (extendInfo.contains("20次")) 20 else if (extendInfo.contains("5次")) 5 else 1

                    // 【核心逻辑】：如果当前项买不起，直接 return 停止，不再尝试后续更便宜的项目
                    if (isNoEnoughPoint(sku) || (cent > 0 && totalCent < cent)) {
                        StructuredLog.record("自动兑换", "剩余碎片不足以兑换优先级项 [$skuName] (需 ${cent/100})，停止后续兑换任务")
                        return
                    }

                    var sessionExchangedCount = 0
                    while (sessionExchangedCount < limitCount) {
                        // 预检查当前余额
                        if (cent > 0 && totalCent < cent) break

                        val result = FarmGameMallRpc.exchangeBenefit(
                            sku.optString("_spuId"), sku.optString("skuId"),
                            activityId, "ANTFARM_IP_DRAW_MALL", "antfarm_villa"
                        )

                        val resObj = result.toJsonObject()
                        val resultCode = resObj.optString("resultCode")

                        if ("SUCCESS" == resultCode) {
                            sessionExchangedCount++
                            totalCent -= cent // 减去花费
                            StructuredLog.record(
                                "自动兑换",
                                "成功兑换: $skuName (本次第 $sessionExchangedCount 次，剩余碎片: ${totalCent/100})"
                            )
                            GlobalThreadPools.sleepCompat(800L)
                        } else if ("NO_ENOUGH_POINT" == resultCode) {
                            StructuredLog.record("自动兑换", "兑换过程中积分不足，停止后续所有任务")
                            return
                        } else if (resultCode.contains("LIMIT") || resultCode.contains("MAX")) {
                            StructuredLog.record("自动兑换", "[$skuName] 已达兑换上限: " + resObj.optString("resultDesc"))
                            break
                        } else {
                            StructuredLog.record("自动兑换", "跳过 [$skuName]: " + resObj.optString("resultDesc"))
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG,"自动兑换异常", e)
        }
    }

    private fun isReachedLimit(jo: JsonObject?): Boolean {
        if (jo == null) return false
        if ("REACH_LIMIT" == jo.optString("itemStatus")) return true
        val list = jo.optJSONArray("itemStatusList")
        if (list != null) {
            for (i in 0 until list.length()) {
                val status = list.optString(i)
                if ("REACH_LIMIT" == status || status.contains("LIMIT")) return true
            }
        }
        return false
    }

    private fun isNoEnoughPoint(jo: JsonObject?): Boolean {
        if (jo == null) return false
        if ("NO_ENOUGH_POINT" == jo.optString("itemStatus")) return true
        val list = jo.optJSONArray("itemStatusList")
        if (list != null) {
            for (i in 0 until list.length()) {
                if ("NO_ENOUGH_POINT" == list.optString(i)) return true
            }
        }
        return false
    }
}
