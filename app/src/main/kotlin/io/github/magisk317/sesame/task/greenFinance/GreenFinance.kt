package io.github.magisk317.sesame.task.greenFinance

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.JsonUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeMap
import kotlin.math.ceil
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

/** Green finance task orchestration. Protocol construction stays in [GreenFinanceRpcCall]. */
class GreenFinance : ModelTask() {
    private lateinit var lsxd: BooleanModelField
    private lateinit var lsbg: BooleanModelField
    private lateinit var lscg: BooleanModelField
    private lateinit var lswl: BooleanModelField
    private lateinit var wdxd: BooleanModelField
    private lateinit var donationEnabled: BooleanModelField
    private lateinit var pointFriend: BooleanModelField

    override fun getName(): String = "绿色经营"
    override fun getGroup(): ModelGroup = ModelGroup.OTHER
    override fun getIcon(): String = "GreenFinance.png"

    override fun getFields(): ModelFields = ModelFields().apply {
        lsxd = BooleanModelField("greenFinanceLsxd", "打卡 | 绿色行动", false).also(::addField)
        lscg = BooleanModelField("greenFinanceLscg", "打卡 | 绿色采购", false).also(::addField)
        lsbg = BooleanModelField("greenFinanceLsbg", "打卡 | 绿色办公", false).also(::addField)
        wdxd = BooleanModelField("greenFinanceWdxd", "打卡 | 绿色销售", false).also(::addField)
        lswl = BooleanModelField("greenFinanceLswl", "打卡 | 绿色物流", false).also(::addField)
        pointFriend = BooleanModelField("greenFinancePointFriend", "收取 | 好友金币", false).also(::addField)
        donationEnabled = BooleanModelField("greenFinanceDonation", "捐助 | 快过期金币", false).also(::addField)
    }

    override fun runJava() {
        try {
            val index = GreenFinanceRpcCall.greenFinanceIndex().toJsonObject()
            if (!index.optBoolean("success")) return logFailure("index", index)
            val result = index.optJSONObject("result") ?: return
            if (!result.optBoolean("greenFinanceSigned")) {
                StructuredLog.other("绿色经营📊未开通")
                return
            }
            collectOwnLeaves(result)
            signIn("PLAY102632271")
            signIn("PLAY102232206")
            tickEnabledBehaviors()
            donateExpiringPoints()
            collectFriends()
            claimRatingPrize()
            doTask("AP13159535", TAG, "绿色经营📊")
            GlobalThreadPools.sleepCompat(500)
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "index err:", t)
        }
    }

    private fun collectOwnLeaves(result: JsonObject) {
        val leaves = result.optJSONObject("mcaGreenLeafResult")?.optJSONArray("greenLeafList") ?: return
        var code: String? = null
        var ids = mutableListOf<String>()
        for (index in 0 until leaves.length()) {
            val leaf = leaves.optJSONObject(index) ?: continue
            val nextCode = leaf.optString("code")
            if (code != null && code != nextCode && ids.isNotEmpty()) {
                batchSelfCollect(jsonArray(*ids.map(::JsonPrimitive).toTypedArray()))
                ids = mutableListOf()
            }
            code = nextCode
            leaf.optString("bsnId").takeIf(String::isNotEmpty)?.let(ids::add)
        }
        if (ids.isNotEmpty()) batchSelfCollect(jsonArray(*ids.map(::JsonPrimitive).toTypedArray()))
    }

    private fun batchSelfCollect(ids: JsonArray) = runCatching {
        val response = GreenFinanceRpcCall.batchSelfCollect(ids).toJsonObject()
        if (response.optBoolean("success")) {
            StructuredLog.other("绿色经营📊收集获得${response.optJSONObject("result")?.optInt("totalCollectPoint")}金币")
        } else logFailure("batchSelfCollect", response)
    }.onFailure { StructuredLog.printStackTrace(TAG, "batchSelfCollect err:", it) }

    private fun signIn(sceneId: String) = runCatching {
        val query = GreenFinanceRpcCall.signInQuery(sceneId).toJsonObject()
        if (!query.optBoolean("success") || query.optJSONObject("result")?.optBoolean("isTodaySignin") == true) return@runCatching
        val response = GreenFinanceRpcCall.signInTrigger(sceneId).toJsonObject()
        GlobalThreadPools.sleepCompat(300)
        if (response.optBoolean("success")) StructuredLog.other("绿色经营📊签到成功") else logFailure("signIn", response)
    }.onFailure { StructuredLog.printStackTrace(TAG, "signIn err:", it) }

    private fun tickEnabledBehaviors() {
        if (lsxd.value) tick("lsxd")
        if (lscg.value) tick("lscg")
        if (lswl.value) tick("lswl")
        if (lsbg.value) tick("lsbg")
        if (wdxd.value) tick("wdxd")
    }

    private fun tick(type: String) = runCatching {
        val response = GreenFinanceRpcCall.queryUserTickItem(type).toJsonObject()
        if (!response.optBoolean("success")) return@runCatching logFailure("tick.query", response)
        val items = response.optJSONArray("result") ?: return@runCatching
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (item.optString("status") == "Y") continue
            val result = GreenFinanceRpcCall.submitTick(type, item.optString("behaviorCode")).toJsonObject()
            GlobalThreadPools.sleepCompat(1500)
            if (!result.optBoolean("success") || result.optJSONObject("result")?.optString("result") != "true") {
                StructuredLog.error("绿色经营📊[${item.optString("title")}]打卡失败")
                break
            }
            StructuredLog.other("绿色经营📊[${item.optString("title")}]打卡成功")
        }
    }.onFailure { StructuredLog.printStackTrace(TAG, "tick err:", it) }

    private fun donateExpiringPoints() {
        if (!donationEnabled.value) return
        runCatching {
            val expire = GreenFinanceRpcCall.queryExpireMcaPoint(1).toJsonObject()
            GlobalThreadPools.sleepCompat(300)
            if (!expire.optBoolean("success")) return@runCatching logFailure("donation.expire", expire)
            val amount = expire.optJSONObject("result")?.optJSONObject("expirePoint")
                ?.optString("amount")?.toDoubleOrNull() ?: return@runCatching
            if (amount <= 0) return@runCatching
            val projects = GreenFinanceRpcCall.queryAllDonationProjectNew().toJsonObject()
            GlobalThreadPools.sleepCompat(300)
            if (!projects.optBoolean("success")) return@runCatching logFailure("donation.projects", projects)
            val ids = TreeMap<String, String>()
            projects.optJSONArray("result")?.let { list ->
                for (index in 0 until list.length()) {
                    val project = list.optJSONObject(index)?.optJSONArray("mcaDonationProjectResult")
                        ?.optJSONObject(0) ?: continue
                    project.optString("projectId").takeIf(String::isNotEmpty)?.let { ids[it] = project.optString("projectName") }
                }
            }
            val deductions = calculateDeductions(amount.toInt(), ids.size)
            ids.keys.take(deductions.first).forEachIndexed { index, id ->
                val donation = if (index == deductions.first - 1) deductions.second.toString() else "200"
                val response = GreenFinanceRpcCall.donation(id, donation).toJsonObject()
                GlobalThreadPools.sleepCompat(1000)
                if (!response.optBoolean("success")) return@runCatching logFailure("donation.$id", response)
                StructuredLog.other("绿色经营📊成功捐助[${ids[id]}]${donation}金币")
            }
        }.onFailure { StructuredLog.printStackTrace(TAG, "donation err:", it) }
    }

    private fun claimRatingPrize() = runCatching {
        if (!Status.canGreenFinancePrizesMap()) return@runCatching
        val campId = "CP14664674"
        val query = GreenFinanceRpcCall.queryPrizes(campId).toJsonObject()
        if (!query.optBoolean("success")) return@runCatching logFailure("prizes.query", query)
        val prizes = query.optJSONObject("result")?.optJSONArray("prizes")
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        for (index in 0 until (prizes?.length() ?: 0)) {
            val time = prizes?.optJSONObject(index)?.optString("bizTime") ?: continue
            if (TimeUtil.getWeekNumber(formatter.parse(time) ?: continue) == TimeUtil.getWeekNumber(Date())) {
                Status.greenFinancePrizesMap()
                return@runCatching
            }
        }
        val response = GreenFinanceRpcCall.campTrigger(campId).toJsonObject()
        if (response.optBoolean("success")) {
            val prize = response.optJSONObject("result")?.optJSONArray("prizes")?.optJSONObject(0)
            if (prize != null) StructuredLog.other("绿色经营🍬评级奖品[${prize.optString("prizeName")}]${prize.optString("price")}")
        } else logFailure("prizes.trigger", response)
    }.onFailure { StructuredLog.printStackTrace(TAG, "prizes err:", it) }

    private fun collectFriends() {
        if (!Status.canGreenFinancePointFriend() || !pointFriend.value) return
        runCatching {
            var start = 0
            while (true) {
                val ranking = GreenFinanceRpcCall.queryRankingList(start).toJsonObject()
                GlobalThreadPools.sleepCompat(1500)
                if (!ranking.optBoolean("success")) return@runCatching
                val result = ranking.optJSONObject("result") ?: return@runCatching
                result.optJSONArray("rankingList")?.let { list ->
                    for (index in 0 until list.length()) {
                        val friend = list.optJSONObject(index) ?: continue
                        if (!friend.optBoolean("collectFlag")) continue
                        collectFriend(friend)
                    }
                }
                if (result.optBoolean("lastPage")) {
                    Status.greenFinancePointFriend()
                    return@runCatching
                }
                start = result.optInt("nextStartIndex", start + 10)
            }
        }.onFailure { StructuredLog.printStackTrace(TAG, "batchStealFriend err:", it) }
    }

    private fun collectFriend(friend: JsonObject) {
        val friendId = friend.optString("uid").takeIf(String::isNotEmpty) ?: return
        val points = GreenFinanceRpcCall.queryGuestIndexPoints(friendId).toJsonObject()
        GlobalThreadPools.sleepCompat(1000)
        if (!points.optBoolean("success")) return
        val ids = mutableListOf<String>()
        points.optJSONObject("result")?.optJSONArray("pointDetailList")?.let { list ->
            for (index in 0 until list.length()) {
                val point = list.optJSONObject(index) ?: continue
                if (!point.optBoolean("collectFlag")) ids.add(point.optString("bsnId"))
            }
        }
        if (ids.isEmpty()) return
        val response = GreenFinanceRpcCall.batchSteal(jsonArray(*ids.map(::JsonPrimitive).toTypedArray()), friendId).toJsonObject()
        GlobalThreadPools.sleepCompat(1000)
        if (response.optBoolean("success")) StructuredLog.other("绿色经营🤩收[${friend.optString("nickName")}]${response.optJSONObject("result")?.optString("totalCollectPoint")}金币")
    }

    private fun logFailure(operation: String, response: JsonObject) {
        StructuredLog.other("$TAG.$operation", response.optString("resultDesc"))
    }

    private fun calculateDeductions(amount: Int, max: Int): Pair<Int, Int> {
        if (amount < 200) return 1 to 200
        val count = minOf(max, ceil(amount / 200.0).toInt())
        var remainder = amount - count * 200
        if (remainder % 100 != 0) remainder = ((remainder + 99) / 100) * 100
        if (remainder < 200) remainder = 200
        return count to remainder
    }

    companion object {
        private const val TAG = "GreenFinance"

        @JvmStatic
        fun doTask(appletId: String, tag: String, name: String) {
            runCatching {
                val query = GreenFinanceRpcCall.taskQuery(appletId).toJsonObject()
                if (!query.optBoolean("success")) return@runCatching
                query.optJSONObject("result")?.optJSONArray("taskDetailList")?.let { tasks ->
                    for (index in 0 until tasks.length()) {
                        val task = tasks.optJSONObject(index) ?: continue
                        if (task.optString("sendCampTriggerType") !in setOf("USER_TRIGGER", "EVENT_TRIGGER")) continue
                        val status = task.optString("taskProcessStatus")
                        val taskId = task.optString("taskId")
                        if (status == "TO_RECEIVE") {
                            GreenFinanceRpcCall.taskTrigger(appletId, "receive", taskId)
                        } else if (status == "NONE_SIGNUP") {
                            GreenFinanceRpcCall.taskTrigger(appletId, "signup", taskId)
                        }
                        if (status == "SIGNUP_COMPLETE" || status == "NONE_SIGNUP") {
                            GreenFinanceRpcCall.taskTrigger(appletId, "send", taskId)
                        }
                        val title = task.optJSONObject("taskExtProps")
                            ?.optJSONObject("TASK_MORPHO_DETAIL")?.optString("title")
                        StructuredLog.other("$name[$title]任务完成")
                    }
                }
            }.onFailure { StructuredLog.printStackTrace(tag, "doTask err:", it) }
        }
        init {
            ModelOrder.register(ModelDefinition(GreenFinance::class.java) { GreenFinance() })
        }
    }
}
