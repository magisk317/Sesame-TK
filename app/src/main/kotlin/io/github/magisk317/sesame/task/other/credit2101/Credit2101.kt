package io.github.magisk317.sesame.task.other.credit2101


import android.annotation.SuppressLint
import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.data.StatusFlags
import io.github.magisk317.sesame.hook.internal.LocationHelper
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountModelField
import io.github.magisk317.sesame.util.TaskRuntimeStore
import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.coroutines.delay
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.optDouble
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.TaskBlacklist.autoAddToBlacklist
import io.github.magisk317.sesame.util.TaskBlacklist.isTaskInBlacklist
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Random
import kotlin.math.cos

/**
 * 信用2101
 *  查询账户详情
 * 有宝箱开宝箱
 * 检查签到
 * 检查每日任务
 * 检查天赋点
 *
 * ======前置完成======
 *
 * 获取定位
 * 循环探测
 * 检查图鉴合成
 *
 * @author Darkness
 */
// ================= 信誉2101=================
object Credit2101 {
    //GenShin Master

    //沈万三的故事   1001034
    //李时珍           6001026

    //creditProfile                 信用印记 排名用的
    //creditSpValue                 印记碎片 购买道具用的
    //staminaAvailable              注能值   进行任务需要的，可以天赋升级


    //道具类型和ID
    //  SP_PRIZE      印记碎片
    //  CARD_PRIZE    藏品卡片  benefitId：   100021 路引文书(蓝)      100043 驷马难追(紫)       100050 草鞋(蓝)        100051 春秋(蓝)        100061 尾生抱柱(蓝)        100065 铜币(蓝)      100072机械钟(蓝)        100074电子钟(紫)    100075智能手表(金)    100080 破镜重圆(蓝)    100081 凤求凰(蓝)       100082 乞巧针(蓝)          100083 化蝶(紫)         100084 比翼鸟(紫)     100085 白头吟(金)   100070 [日晷]蓝
    //
    //  YJ_PRIZE      信用印记

    //==================== 下面是任务事件 ====================
    //GOLD_MARK 金色印记，每次消耗5注能值

    private const val TAG = "2101"//Credit

    /**
     * 信用2101 专用任务/游戏类型定义
     */
    object EventType {
        /** 消除小游戏 🎮 */
        const val MINI_GAME_ELIMINATE = "MINI_GAME_ELIMINATE"
        /** 收集小游戏 🏺 */
        const val MINI_GAME_COLLECTYJ = "MINI_GAME_COLLECTYJ"
        /** 击杀小游戏 🧩 */
        const val MINI_GAME_MATCH3 = "MINI_GAME_MATCH3"
        /** 金色印记 🟡 */
        const val GOLD_MARK = "GOLD_MARK"
        /** 黑色印记 ⚫ */
        const val BLACK_MARK = "BLACK_MARK"
        /** 时空之门 🌀 */
        const val SPACE_TIME_GATE = "SPACE_TIME_GATE"
    }
    // 私有变量：用于存放整个选项控件
    private var mCreditOptions: SelectAndCountModelField? = null



    /** 故事ID数组 */
    private val STORY_IDS = listOf(
        1001011, 1001022, 1001034, 1001043, 2001011, 2001019, 2001026, 2001035,
        3001011, 3001020, 3001027, 3001036, 4001010, 4001018, 4001027, 4001035,
        5001009, 5001016, 5001025, 5001034, 6001010, 6001019, 6001026, 6001033,
        7001010, 7001015, 7001026, 7001033
    )

    /** 账户信息缓存，用于事件处理和能量判断（解析逻辑已抽入 CreditAccountService） */
    private typealias AccountInfo = CreditAccountService.AccountInfo

    private val accountService = CreditAccountService(
        tag = { TAG },
        queryRaw = { Credit2101RpcCall.queryAccountAsset() },
    )

    /** 经纬度 + 城市编码 */
    private data class LocationInfo(
        val cityCode: String,
        val latitude: Double,
        val longitude: Double
    )

    /**
     * 奖励类型 → 展示名称
     */
    private fun getItemName(type: String): String {
        return when (type) {
            "SP_PRIZE" -> "印记碎片"
            "CARD_PRIZE" -> "藏品卡片"
            "YJ_PRIZE" -> "信用印记"
            "BX_PRIZE" -> "印记宝箱"
            "EXPLORE_COUNT_PRIZE" -> "探测次数"
            else -> type
        }
    }

    /**
     * benefitId → 藏品卡片，展示名称
     */
    private fun getBenefitName(benefitId: String): String {
        val id = benefitId.toIntOrNull() ?: return "无法转换类型x未知奖励($benefitId)"
        return when (id) {
            100021 -> "路引文书(蓝)"
            100043 -> "驷马难追(紫)"
            100050 -> "草鞋(蓝)"
            100051 -> "春秋(蓝)"
            100061 -> "尾生抱柱(蓝)"
            100065 -> "铜币(蓝)"
            100070 -> "日晷(蓝)"
            100072 -> "机械钟(蓝)"
            100074 -> "电子钟(紫)"
            100075 -> "智能手表(金)"
            100080 -> "破镜重圆(蓝)"
            100081 -> "凤求凰(蓝)"
            100082 -> "乞巧针(蓝)"
            100083 -> "化蝶(紫)"
            100084 -> "比翼鸟(紫)"
            100085 -> "白头吟(金)"

            // 信义 / 典故
            100060 -> "鸡黍之约(蓝)"
            100062 -> "季札挂剑(紫)"

            // 时间器物
            100071 -> "沙漏(蓝)"
            100073 -> "怀表(紫)"

            // 身份 / 文书
            100020 -> "青铜铭牌(蓝)"
            100022 -> "身份证件(紫)"

            // 合同 / 订单
            100010 -> "古代契约书(蓝)"
            100011 -> "现代合同(蓝)"
            100012 -> "虚拟订单(紫)"

            // 信用典故
            100040 -> "一言九鼎(蓝)"
            100041 -> "徙木立信(蓝)"
            100042 -> "桃园结义(蓝)"
            100044 -> "崔妪还珠(紫)"
            100045 -> "续约之“鸽”(金)"

            // 货币
            100030 -> "贝壳(蓝)"
            100031 -> "谷物(蓝)"
            100033 -> "银两(紫)"
            100034 -> "黄金(紫)"
            100035 -> "交子(金)"

            // 日用品
            100052 -> "酒坛(紫)"

            else -> "未知奖励($benefitId)"
        }
    }

    /**
     * 获取章节图鉴名称
     */
    private fun getChapterName(chapterId: String): String {
        return when (chapterId) {
            "10001" -> "交易之凭"
            "10002" -> "身份之证"
            "10003" -> "货币历史"
            "10004" -> "社交之诺"
            "10005" -> "桃园三英"
            "10006" -> "约定之信"
            "10007" -> "时光刻度"
            "10008" -> "爱情之约"
            else -> "未知章节($chapterId)"
        }
    }

    /**
     * attributeType → 天赋，展示名称
     */
    private fun getTalentName(attributeType: String): String {
        return when (attributeType) {
            "EXPLORE_RADIUS" -> "探索范围"
            "EXPLORE_COUNT" -> "探索次数"
            "EXPLORE_RECOVER" -> "探索恢复"
            "ENERGY_COUNT" -> "注能上限"
            "ENERGY_RECOVER" -> "注能恢复"
            else -> attributeType
        }
    }

    @SuppressLint("DefaultLocale")
    @JvmStatic
    suspend fun doCredit2101(autoOpenChest: Boolean ,creditoptions: SelectAndCountModelField) {
        try {
            this.mCreditOptions = creditoptions
            var account = queryAccountAsset() ?: run {
                StructuredLog.record(TAG, "信用2101 账户查询失败，服务端不可用，跳过本轮")
                Status.setFlagToday(StatusFlags.FLAG_CREDIT2101_UNAVAILABLE)
                return
            }
            if (Status.hasFlagToday(StatusFlags.FLAG_CREDIT2101_UNAVAILABLE)) {
                StructuredLog.record(TAG, "信用2101 今日已标记不可用，跳过")
                return
            }
            // 1. 开宝箱（如有）
            if (account.lotteryNo > 0 && autoOpenChest) {
                openChest(account.lotteryNo)
                account = queryAccountAsset() ?: account
            }
            // 2. 签到
            handleSignIn()
            // 3. 每日任务
            handleUserTasks()
            // 4. 天赋检查
            handleAutoUpgradeTalent()
            // 5. 获取经纬度 + cityCode
            val location = resolveLocation(account.cityCode)
            var currentLat: Double
            var currentLng: Double
            val cityCode: String
            if (location == null) {
                StructuredLog.record(TAG, "信用2101📍[定位失败] 使用北京默认值")
                cityCode = "110000"
                currentLat = 39.44 + Math.random() * (41.05 - 39.44)
                currentLng = 115.42 + Math.random() * (117.50 - 115.42)
                currentLat = String.format("%.6f", currentLat).toDouble()
                currentLng = String.format("%.6f", currentLng).toDouble()
            } else {
                currentLat = location.latitude
                currentLng = location.longitude
                cityCode = location.cityCode
            }
            StructuredLog.record(
                TAG,
                "信用2101📍[定位信息] 城市编码=$cityCode，纬度=$currentLat，经度=$currentLng"
            )

            // ================== 探测控制参数 ==================
            val maxLoopCount = 10
            var currentLoopCount = 0

            val maxShiftCount = 10
            var shiftCount = 0

            var failExploreCount = 0

            handleVisitRecover()        //时段恢复

            handleGuardMarkAward()        //检查是否有可领取的印记

            StructuredLog.record(TAG, "信用2101🔍[探测循环]")

            // ================== 主循环 ==================
            while (true) {
                //GlobalThreadPools.sleepCompat(2000)

                // 防死循环保护
                currentLoopCount++
                if (currentLoopCount > maxLoopCount) {
                    StructuredLog.record(TAG, "信用2101🔍[探测停止] 达到最大循环次数($maxLoopCount)")
                    break
                }

                // 刷新账户状态
                account = queryAccountAsset() ?: run {
                    StructuredLog.error(TAG, "信用2101🛡️[账户刷新失败] 本轮停止")
                    return
                }

                if (account.exploreStamina <= 0) {
                    StructuredLog.record(TAG, "信用2101🔍[探测停止] 探索次数已用完")
                    break
                }

                if (account.energyStamina < 5) {
                    StructuredLog.record(
                        TAG,
                        "信用2101🔍[探测停止] 能量不足(${account.energyStamina})"
                    )
                    break
                }

                // 1️⃣ 优先处理已有事件
                val hadDoable = queryAndHandleEvents(
                    cityCode,
                    currentLat,
                    currentLng,
                    account
                )

                if (hadDoable) {
                    // 找到并处理了事件，重置探测/位移计数
                    failExploreCount = 0
                    shiftCount = 0
                    // StructuredLog.record("找到并处理了事件")
                    continue
                }

                // 2️⃣ 探测新事件
                val found = exploreOnce(cityCode, currentLat, currentLng)

                if (found) {
                    // 探测到事件，下轮重新查询处理
                    failExploreCount = 0
                    shiftCount = 0
                    //StructuredLog.record("探测到事件")
                    continue
                }

                // 3️⃣ 探测失败 → 移动位置
                failExploreCount++

                val (nLat, nLng) = shiftLocation(currentLat, currentLng)
                currentLat = nLat
                currentLng = nLng
                shiftCount++

                StructuredLog.record(
                    TAG,
                    "信用2101📍[移动位置] 第$shiftCount 次 lat=$currentLat lng=$currentLng (≈±500m)"
                )

                // 位移次数耗尽才真正退出
                if (shiftCount >= maxShiftCount) {
                    StructuredLog.record(
                        TAG,
                        "信用2101🔍[探测停止] 已移动 $shiftCount 次仍未发现事件"
                    )
                    break
                }
            }

            // ================== 所有任务结束后检查是否合成 ==================
            if (!isTaskInBlacklist(StatusFlags.FLAG_CREDIT2101_CHAPTER_TASK_DONE)) {
                handleChapterTasks()
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    /** 查询账户详情并解析为 AccountInfo（委托 CreditAccountService） */
    private fun queryAccountAsset(): AccountInfo? = accountService.query()

    /** 开宝箱并解析奖励（按数量多次尝试，遇到失败则停止） */
    private suspend fun openChest(lotteryNo: Int) {
        var successCount = 0
        try {
            for (i in 1..lotteryNo) {
                delay(5000)
                val resp = Credit2101RpcCall.triggerBenefit()
                if (!ResChecker.checkRes(TAG, resp)) {
                    StructuredLog.record(TAG, "信用2101🎁[开宝箱失败] 第 $i 个返回为空，停止后续宝箱")
                    break
                }

                val jo = resp.toJsonObject()
                if (!ResChecker.checkRes(TAG, jo)) {
                    StructuredLog.record(TAG, "信用2101🎁[开宝箱失败] resp=$resp，停止后续宝箱")//第 $i 个
                    break
                }

                successCount++

                val benefitArr = jo.optJSONArray("benefitTriggerVOS")
                if (benefitArr == null || benefitArr.length() == 0) {
                    StructuredLog.other("信用2101🎁[开宝箱] 无详细奖励信息")//第$i 个
                    continue
                }

                val descList = mutableListOf<String>()
                for (j in 0 until benefitArr.length()) {
                    val item = benefitArr.optJSONObject(j) ?: continue

                    val type = item.optString("benefitType", "")
                    val count = item.optInt("count", 0)
                    if (count <= 0) continue

                    val desc = when (type) {
                        // 卡片 / 藏品类 → 用 benefitId 映射名称
                        "CARD_PRIZE" -> {
                            val benefitId = item.optString("benefitId", "")
                            val name = getBenefitName(benefitId)
                            "$name x$count"
                        }

                        // 其他资源类 → 用 awardType 映射
                        else -> {
                            val typeName = getItemName(type)
                            "$typeName x$count"
                        }
                    }

                    descList.add(desc)
                }

                if (descList.isEmpty()) {
                    StructuredLog.other("信用2101🎁[开宝箱]第 $i 个")
                } else {
                    StructuredLog.other(
                        TAG,
                        "信用2101🎁[开宝箱]#$i[${descList.joinToString("，")}]"
                    )
                }
            }

            StructuredLog.record(TAG, "信用2101🎁[宝箱统计] 共$lotteryNo 个，成功打开$successCount 个")
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }

    /**
     * 查询签到数据并按 totalLoginDays 判断是否需要签到
     */
    private fun handleSignIn() {
        try {
            val jo = Credit2101RpcCall.querySignInData().toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) {
                return
            }
            val totalLoginDays = jo.optInt("totalLoginDays", 0)
            if (totalLoginDays <= 0) return
            val signInDays = jo.optJSONArray("signInDays")
            var alreadySigned = false
            if (signInDays != null) {
                for (i in 0 until signInDays.length()) {
                    if (signInDays.optString(i, "-1").toIntOrNull() == totalLoginDays) {
                        alreadySigned = true
                        break
                    }
                }
            }
            if (alreadySigned) {
                return
            }

            val signResp = Credit2101RpcCall.userSignIn(totalLoginDays)
            if (!ResChecker.checkRes(TAG, signResp)) {
                StructuredLog.error(TAG, "信用2101🗓[签到失败] 返回为空")
                return
            }

            val sJo = signResp.toJsonObject()
            val success = ResChecker.checkRes(TAG, sJo)
            val resultCode = sJo.optString("resultCode", "")

            if (!success) {
                if (resultCode == "SIGN_DAYS_NOT_ENOUGH") {
                    StructuredLog.record(TAG, "信用2101🗓[签到] 已领取签到奖励")
                } else {
                    StructuredLog.record(TAG, "信用2101🗓[签到失败] resp=$signResp")
                }
                return
            }

            val awardArr = sJo.optJSONArray("awardVOList")
            val desc = if (awardArr != null && awardArr.length() > 0) {
                val item = awardArr.optJSONObject(0)
                val amount = item?.optString("amount", "") ?: ""
                val awardType = item?.optString("awardType", "") ?: ""
                if (amount.isNotEmpty()) "$awardType $amount" else awardType
            } else null

            if (!desc.isNullOrEmpty()) {
                StructuredLog.other("信用2101🗓[签到成功] 获得$desc")
            } else {
                StructuredLog.other("信用2101🗓[签到成功]")
            }

        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }

    /**
     * 每日任务：领取任务 + 领取任务奖励
     */
    private suspend fun handleUserTasks() {
        try {
            val resp = Credit2101RpcCall.queryUserTask()
            if (resp.isEmpty()) {
                StructuredLog.error(TAG, "查询任务为空(可能黑号)")
                return
            }

            val jo = resp.toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) {
                StructuredLog.record(
                    TAG, "信用2101📋[查询任务失败] resp=$resp"
                )
                return
            }

            val taskList = jo.optJSONArray("taskList") ?: return
            if (taskList.length() == 0) {
                StructuredLog.record(TAG, "信用2101📋[任务] 当前无任务")
                return
            }

            var claimCount = 0
            var awardCount = 0

            for (i in 0 until taskList.length()) {
                val task = taskList.optJSONObject(i) ?: continue
                val taskConfigId = task.optString("taskConfigId", "")
                if (taskConfigId.isEmpty()) continue

                val taskName = task.optString("taskName", taskConfigId)
                val taskStatus = task.optString("taskStatus", "")
                val awardStatus = task.optString("awardStatus", "")

                // 1) INIT → 未领取任务，先领取（TASK_CLAIM）
                if (taskStatus == "INIT") {
                    val claimResp = Credit2101RpcCall.operateTask("TASK_CLAIM", taskConfigId)
                    if (claimResp.isEmpty()) {
                        StructuredLog.record(TAG, "信用2101📋[任务领取失败] $taskName 返回为空")
                    } else {
                        val cJo = claimResp.toJsonObject()
                        val ok = ResChecker.checkRes(TAG, cJo) &&
                                cJo.optBoolean("operateSuccess", true)
                        if (ok) {
                            claimCount++
                            StructuredLog.other("信用2101📋[任务领取成功] $taskName ($taskConfigId)")
                        } else {
                            StructuredLog.record(TAG, "信用2101📋[任务领取失败] $taskName resp=$claimResp")
                        }
                    }
                    continue
                }

                // 2) FINISH 且奖励未标记已领取 → 领取奖励  awardStatus-> UNLOCKED也就是解锁了但是没领取  && awardStatus != "CLAIMED"
                if (taskStatus == "FINISH" && awardStatus == "UNLOCKED") {
                    val awardResp = Credit2101RpcCall.awardTask(taskConfigId)
                    if (awardResp.isEmpty()) {
                        StructuredLog.error(TAG, "信用2101📋[任务奖励领取失败] $taskName 返回为空")
                        continue
                    }

                    val aJo = awardResp.toJsonObject()
                    val success = ResChecker.checkRes(TAG, aJo)
                    val resultCode = aJo.optString("resultCode", "")
                    val awardSuccess = aJo.optBoolean("awardSuccess", false)

                    if (!success || !awardSuccess) {
                        if (resultCode == "TASK_HAS_NO_AWARD") {
                            StructuredLog.record(TAG, "信用2101📋[任务奖励] $taskName 当前无奖励可领")
                        } else {
                            StructuredLog.record(TAG, "信用2101📋[任务奖励领取失败] $taskName resp=$awardResp")
                        }
                        continue
                    }

                    val awardArr = aJo.optJSONArray("awardDetailVOList")
                    val desc = if (awardArr != null && awardArr.length() > 0) {
                        val list = mutableListOf<String>()
                        for (j in 0 until awardArr.length()) {
                            val item = awardArr.optJSONObject(j) ?: continue
                            val name = item.optString("awardName", "")
                            val amount = item.optString("awardAmount", "")
                            if (name.isNotEmpty() && amount.isNotEmpty()) {
                                list.add("$name $amount")
                            }
                        }
                        list.joinToString("，").ifEmpty { null }
                    } else null

                    awardCount++
                    if (!desc.isNullOrEmpty()) {
                        StructuredLog.other("信用2101📋[任务] $taskName -> $desc")
                    } else {
                        StructuredLog.other("信用2101📋[任务奖励领取成功] $taskName")
                    }
                }

                // 3) RUNNING → 且 "taskConfigId": "GAME_SHARE", 说明是分享任务，第二次执行到这里就能自动领取了
                if (taskStatus == "RUNNING" && taskConfigId == "GAME_SHARE") {
                    val pUSHResp = Credit2101RpcCall.operateTask("TASK_PUSH", taskConfigId) //注意这里是Push
                    if (pUSHResp.isEmpty()) {
                        StructuredLog.error(TAG, "信用2101📋[分享任务失败] $taskName 返回为空")
                    } else {
                        val cJo = pUSHResp.toJsonObject()
                        val ok = ResChecker.checkRes(TAG, cJo) &&
                                cJo.optBoolean("operateSuccess", true)
                        if (ok) {
                            claimCount++
                            StructuredLog.other("信用2101📋[分享任务完成] $taskName ($taskConfigId)")
                        } else {
                            StructuredLog.error(TAG, "信用2101📋[分享任务失败] $taskName resp=$pUSHResp")
                        }
                    }
                    continue

                }

                delay(5000)
            }

            if (claimCount > 0 || awardCount > 0) {
                StructuredLog.record(TAG, "信用2101📋[任务统计] 领取任务:$claimCount 领取奖励:$awardCount")
            }
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }

    /**
     * 定时回访弹窗+ 领取任务奖励
     */

    /**
     * 处理修复列表奖励领取 (黑色印记)
     */
    private fun handleGuardMarkAward() {
        try {
            // 1. 查询列表状态
            val resp = Credit2101RpcCall.queryGuardMarkList()
            if (!ResChecker.checkRes(TAG, resp)) {
                StructuredLog.error(TAG, "信用2101🛡️[查询修复列表失败] resp=$resp")
                return
            }

            val jo = resp.toJsonObject()


            // 2. 判断是否可以领取 (hasClaimGuardMark 为 true 表示有奖可领)
            val hasClaim = jo.optBoolean("hasClaimGuardMark", false)
            // val count = jo.optInt("guardMarkCount", 0)

            if (!hasClaim) {
                // StructuredLog.record(TAG, "信用2101🛡️[修复奖励] 暂无奖励可领取 (已修复数: $count)")
                return
            }

            // 3. 执行领取动作
            StructuredLog.record(TAG, "信用2101🛡️[修复奖励] 检测到可领取奖励，正在领取...")
            val claimResp = Credit2101RpcCall.claimGuardMarkAward()



            if (!ResChecker.checkRes(TAG, claimResp)) {
                StructuredLog.error(TAG, "信用2101🛡️[领取修复奖励失败] resp=$claimResp")
                return
            }
            val cJo = claimResp.toJsonObject()
            val cnt = cJo.optInt("cnt", 0)

            if (cnt > 0) {
                // 保持你统一的奖励展示风格
                StructuredLog.other("信用2101🛡️[修复奖励]获得 信用印记 x$cnt")
            } else {
                StructuredLog.other("信用2101🛡️[修复奖励]")
            }

        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "handleGuardMarkAward 异常", e)
        }
    }

    private fun handleVisitRecover() {
        try {
            val resp = Credit2101RpcCall.queryPopupView("1")

            val jo = resp.toJsonObject()
            if (!ResChecker.checkRes(TAG, jo)) {
                StructuredLog.error(TAG, "回访🗓[查询失败] resp=$resp")
                return
            }

            val popupViewVO = jo.optJSONObject("popupViewVO") ?: return
            if (!popupViewVO.optBoolean("showResult", false)) return

            val resultMap = popupViewVO.optJSONObject("resultMap") ?: return

            // 不可领取：返回的是下一次恢复时间
            if (resultMap.has("nextEnergyRecoverMinutes")
                || resultMap.has("nextExploreRecoverMinutes")
            ) {
                return
            }

            val energyRecover = resultMap.optInt("energyRecover", 0)
            val exploreRecover = resultMap.optInt("exploreRecover", 0)

            if (energyRecover <= 0 && exploreRecover <= 0) return

            val descList = mutableListOf<String>()
            if (energyRecover > 0) {
                descList.add("注能值+$energyRecover")
            }
            if (exploreRecover > 0) {
                descList.add("探索值+$exploreRecover")
            }

            if (descList.isNotEmpty()) {
                StructuredLog.other("回访🗓[可领取] ${descList.joinToString("，")}")
            }

        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }

    private fun resolveLocation(accountCityCode: String?): LocationInfo? {
        return runCatching {
            var cityCode = accountCityCode
            var lat: Double
            var lng: Double

            try {
                val locationJson = LocationHelper.getLocation()
                if (locationJson != null) {
                    val status = locationJson.optString("status", "")
                    if (status.isNotEmpty()) {
                        StructuredLog.error(TAG, "信用2101📍[LocationHelper] $status，尝试使用 API 备用$locationJson")
                        throw Exception("LocationHelper 定位失败: $status")
                    }

                    lat = locationJson.optDouble("latitude", Double.NaN)
                    lng = locationJson.optDouble("longitude", Double.NaN)

                    if (lat.isNaN() || lng.isNaN()) {
                        StructuredLog.error(TAG, "信用2101📍[LocationHelper失败] lat/lng 缺失 lat=$lat lng=$lng，尝试使用 API 备用")
                        throw Exception("LocationHelper 定位数据不完整")
                    }

                    StructuredLog.record(TAG, "信用2101📍[LocationHelper] 使用目标应用定位成功")
                } else {
                    StructuredLog.error(TAG, "信用2101📍[LocationHelper] 返回为空，尝试使用 API 备用")
                    throw Exception("LocationHelper 返回为空")
                }
            } catch (e: Exception) {
                StructuredLog.error(TAG, "信用2101📍[LocationHelper异常] ${e.message}，尝试使用 API 备用")
                val ipJson = httpGetJson("https://api.ip.sb/geoip/") ?: run {
                    StructuredLog.error(TAG, "信用2101📍[API定位失败] ip.sb 查询失败")
                    return@runCatching null
                }

                val ip = ipJson.optString("ip", "")
                lat = ipJson.optDouble("latitude", Double.NaN)
                lng = ipJson.optDouble("longitude", Double.NaN)

                if (ip.isNotEmpty()) {
                    val qqJson = httpGetJson("https://api.qqsuu.cn/api/dm-ipquery?ip=$ip")
                    if (qqJson != null && qqJson.optInt("code", -1) == 200) {
                        val data = qqJson.optJSONObject("data")
                        cityCode = data?.optString("areacode", accountCityCode.toString())

                        val latStr = data?.optString("latitude", "")
                        val lngStr = data?.optString("longitude", "")
                        val lat2 = latStr?.toDoubleOrNull()
                        val lng2 = lngStr?.toDoubleOrNull()

                        if (lat2 != null && lng2 != null) {
                            lat = lat2
                            lng = lng2
                        }
                    }
                }

                if (cityCode.isNullOrEmpty()) {
                    cityCode = accountCityCode
                }

                if (cityCode.isNullOrEmpty() || lat.isNaN() || lng.isNaN()) {
                    StructuredLog.error(TAG, "信用2101📍[API定位失败] cityCode/lat/lng 缺失 cityCode=$cityCode lat=$lat lng=$lng")
                    return@runCatching null
                }

                StructuredLog.record(TAG, "信用2101📍[API定位] 使用 API 定位成功")
            }

            LocationInfo(cityCode.toString(), lat, lng)
        }.getOrElse {
            StructuredLog.printStackTrace(TAG, it)
            null
        }
    }

    /**
     * 简单 HTTP GET 并解析为结构化 JSON 对象
     */
    private fun httpGetJson(urlStr: String): JsonObject? {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
            }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                StructuredLog.error(TAG, "信用2101📍[HTTP失败] $urlStr code=$code")
                return null
            }

            val sb = StringBuilder()
            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { br ->
                var line: String?
                while (true) {
                    line = br.readLine() ?: break
                    sb.append(line)
                }
            }

            sb.toString().toJsonObject()
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, e)
            null
        }
    }

    /**
     * 小范围随机偏移经纬度，约 500 米内
     */
    private fun shiftLocation(latitude: Double, longitude: Double): Pair<Double, Double> {
        val meters = 500.0
        val random = Random()

        // 1 经纬度 ≈ 111km，这里近似即可
        val dLat = (random.nextDouble() * 2 - 1) * meters / 111000.0
        val cosLat = cos(Math.toRadians(latitude.coerceIn(-89.9, 89.9)))
        val dLng = (random.nextDouble() * 2 - 1) * meters / (111000.0 * cosLat.coerceAtLeast(0.01))

        return Pair(latitude + dLat, longitude + dLng)
    }

    /**
     * 查询并按类型处理当前坐标附近的事件
     * @return true 表示本轮存在可完成事件（并已尝试处理），false 表示没有可完成事件
     */
    // 定义返回状态
    private sealed class EventResult {
        data class Success(val energyUsed: Int) : EventResult()  // 成功完成
        object Skipped : EventResult()  // 跳过(条件不满足但正常)
        object Failed : EventResult()   // 失败(错误)
    }

    private suspend fun queryAndHandleEvents(
        cityCode: String,
        latitude: Double,
        longitude: Double,
        account: AccountInfo
    ): Boolean {
        val resp = Credit2101RpcCall.queryGridEvent(cityCode, latitude, longitude)
        if (!ResChecker.checkRes(TAG, resp)) {
            StructuredLog.record(TAG, "信用2101📋[事件] 查询失败 / 返回为空")
            return false
        }

        val root = runCatching { resp.toJsonObject() }.getOrElse {
            StructuredLog.printStackTrace(TAG, it)
            return false
        }

        if (!ResChecker.checkRes(TAG, root)) {
            StructuredLog.record(TAG, "信用2101📋[事件] success=false")
            return false
        }

        val eventList = root.optJSONArray("gridEventVOList")
        if (eventList == null || eventList.length() == 0) {
            StructuredLog.record(TAG, "信用2101📋[事件] 当前无事件")
            return false
        }

        var handledCount = 0
        var remainEnergy = account.energyStamina
        val configMap = mCreditOptions?.value ?: emptyMap()

        for (i in 0 until eventList.length()) {
            delay(1000L)

            val ev = eventList.optJSONObject(i) ?: continue
            if (ev.optString("eventStatus") == "FINISHED") continue

            val eventType = ev.optString("eventType")
            val eventId = ev.optString("eventId")
            val batchNo = ev.optString("batchNo")
            if (eventId.isEmpty() || batchNo.isEmpty()) continue

            val maxCount = configMap[eventType] ?: 0
            if (maxCount == 0) continue

            val flagKey = "${TAG}_Event_${eventType}_COUNT_TODAY"
            val doneCount = Status.getIntFlagToday(flagKey) ?: 0

            if (maxCount != -1 && doneCount >= maxCount) {
                continue
            }

            val result: EventResult = when (eventType) {
                EventType.MINI_GAME_ELIMINATE -> {
                    if (runCatching { handleMiniGameEliminate(ev, batchNo, eventId) }.isSuccess)
                        EventResult.Success(0)
                    else EventResult.Failed
                }

                EventType.MINI_GAME_COLLECTYJ -> {
                    if (runCatching { handleMiniGameCollectYj(ev, batchNo, eventId) }.isSuccess)
                        EventResult.Success(0)
                    else EventResult.Failed
                }

                EventType.MINI_GAME_MATCH3 -> {
                    if (runCatching { handleMiniGameMatch(ev, batchNo, eventId) }.isSuccess)
                        EventResult.Success(0)
                    else EventResult.Failed
                }

                EventType.GOLD_MARK -> {
                    if (runCatching { handleGoldMark(batchNo, eventId, cityCode, latitude, longitude) }.isSuccess)
                        EventResult.Success(0)
                    else EventResult.Failed
                }

                EventType.BLACK_MARK -> handleBlackMark(ev, remainEnergy)

                EventType.SPACE_TIME_GATE -> {
                    if (runCatching { handleSpaceTimeGate(batchNo, eventId, cityCode, latitude, longitude) }.isSuccess)
                        EventResult.Success(0)
                    else EventResult.Failed
                }

                else -> EventResult.Failed
            }

            when (result) {
                is EventResult.Success -> {
                    handledCount++
                    if (result.energyUsed > 0) {
                        remainEnergy -= result.energyUsed
                    }
                    val newCount = doneCount + 1
                    Status.setIntFlagToday(flagKey, newCount)
                    val countDesc = if (maxCount == -1) "∞" else maxCount.toString()
                    StructuredLog.record(TAG, "信用2101🛡️[事件完成] type=$eventType (进度: $newCount/$countDesc)")
                }
                is EventResult.Skipped -> {
                    // 跳过不计入失败也不计入成功,静默处理
                    StructuredLog.record(TAG, "信用2101⏭️[事件跳过] type=$eventType (条件不满足)")
                }
                is EventResult.Failed -> {
                    StructuredLog.record(TAG, "信用2101🛡️[事件失败] type=$eventType")
                }
            }
        }

        StructuredLog.record(TAG, "信用2101📋[事件处理结果] 完成=$handledCount / 总数=${eventList.length()}")
        return handledCount > 0
    }

    private fun handleMiniGameEliminate(ev: JsonObject, batchNo: String, eventId: String) {
        try {

            val cfg = ev.optJSONObject("eventConfig")
            val stageId = cfg?.optString("id", "") ?: ""
            if (stageId.isEmpty()) {
                StructuredLog.record(TAG, "信用2101🎮[小游戏关卡ID为空] resp=$ev")
                return
            }

            val startResp = Credit2101RpcCall.eventGameStart(batchNo, eventId, stageId)
            val sJo = startResp.toJsonObject()
            if (sJo.optString("resultCode") == "ENERGY_STAMINA_IS_ZERO") {
                StructuredLog.record(TAG, "信用2101💤 能量已耗尽，停止小游戏M")
                return
            }
            if (!ResChecker.checkRes(TAG, sJo)) {
                StructuredLog.record(
                    TAG,
                    "信用2101🎮[小游戏启动接口失败] batchNo=$batchNo eventId=$eventId stageId=$stageId"
                )
                return
            }

            val success = ResChecker.checkRes(TAG, sJo)
            if (!success) {
                val resultCode = sJo.optString("resultCode", "UNKNOWN")
                val resultMsg = sJo.optString("resultMsg", "未知错误")
                StructuredLog.record(TAG, "信用2101🎮[小游戏启动失败] 原因: $resultMsg (code=$resultCode) resp=$startResp")
                return
            }

            // 直接标记通关
            val completeResp = Credit2101RpcCall.eventGameCompleteSimple(batchNo, eventId, stageId)
            val cJo = completeResp.toJsonObject()
            if (cJo.optString("resultCode") == "ENERGY_STAMINA_IS_ZERO") {
                StructuredLog.record(TAG, "信用2101💤 能量已耗尽，停止小游戏")
                return
            }
            if (!ResChecker.checkRes(TAG, completeResp)) {
                StructuredLog.error(TAG, "信用2101🎮[小游戏完成失败] batchNo=$batchNo eventId=$eventId stageId=$stageId  resp=$completeResp")

                return
            }

            // 解析奖励
            val awardArr = cJo.optJSONArray("awardVOList")
            val awardDesc = if (awardArr != null && awardArr.length() > 0) {
                val item = awardArr.optJSONObject(0)
                val amount = item?.optString("awardAmount", "") ?: ""
                val type = item?.optString("awardType", "") ?: ""
                val name = getItemName(type)
                if (amount.isNotEmpty()) "$name $amount" else name
            } else null

            if (!awardDesc.isNullOrEmpty()) {
                StructuredLog.other("信用2101🎮[小游戏E完成] 奖励: $awardDesc") // MINI_GAME_ELIMINATE
            } else {
                StructuredLog.other("信用2101🎮[小游戏完成] (未获得奖励)")
            }

            // StructuredLog.other( "信用2101🎮[小游戏完成]")//MINI_GAME_ELIMINATE
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }

    /** 处理小游戏：收集印记 YJ 类型 */
    private suspend fun handleMiniGameCollectYj(ev: JsonObject, batchNo: String, eventId: String) {
        try {
            val cfg = ev.optJSONObject("eventConfig")
            if (cfg == null) {
                StructuredLog.record(TAG, "信用2101🎮[小游戏配置缺失] resp=$ev")
                return
            }

            val stageId = cfg.optString("id", "")
            if (stageId.isEmpty()) {
                StructuredLog.record(TAG, "信用2101🎮[小游戏关卡ID为空] resp=$ev")
                return
            }

            val awardArray = cfg.optJSONArray("award")
            if (awardArray == null || awardArray.length() == 0) {
                StructuredLog.record(TAG, "信用2101🎮[小游戏奖励信息获取失败] resp=$ev")
                return
            }

            val award = awardArray.optJSONObject(0)
            if (award == null) {
                StructuredLog.record(TAG, "信用2101🎮[小游戏奖励信息第一个元素为空] resp=$ev")
                return
            }

            // 开始小游戏
            val startResp = Credit2101RpcCall.eventGameStart(batchNo, eventId, stageId)
            val sJo = startResp.toJsonObject()
            if (sJo.optString("resultCode") == "ENERGY_STAMINA_IS_ZERO") {
                StructuredLog.record(TAG, "信用2101💤 能量已耗尽，无法完成游戏YJ")
                return
            }

            if (!ResChecker.checkRes(TAG, sJo)) {
                StructuredLog.record(TAG, "信用2101🎮[小游戏启动失败] type=COLLECTYJ resp=$startResp")
                return
            }

            // 获取奖励数量，用于 extParams
            val collectedYJ = award.optString("awardAmount", "0")
            val collectedYJInt = try {
                collectedYJ.toInt()
            } catch (_: Exception) {
                0
            }

            // 构造 extParams
            val extParams = jsonObject("collectedYJ" to collectedYJ)
            delay(3 * 1000L)
            // 完成小游戏
            val completeResp = Credit2101RpcCall.eventGameCompleteCollectYj(
                batchNo,
                eventId,
                stageId,
                collectedYJInt

            )
            val cJo = completeResp.toJsonObject()
            if (cJo.optString("resultCode") == "ENERGY_STAMINA_IS_ZERO") {
                StructuredLog.record(TAG, "信用2101💤 能量已耗尽，无法完成游戏YJ")
                return
            }
            if (!ResChecker.checkRes(TAG, cJo)) {
                StructuredLog.record(TAG, "信用2101🎮[小游戏完成接口返回为空] batchNo=$batchNo eventId=$eventId stageId=$stageId extParams=$extParams")
                return
            }

            if (!ResChecker.checkRes(TAG, cJo)) {
                StructuredLog.error(TAG, "信用2101🎮[小游戏完成失败] type=COLLECTYJ resp=$completeResp")
                return
            }

            // 解析奖励
            val awardArr = cJo.optJSONArray("awardVOList")
            val awardDesc = if (awardArr != null && awardArr.length() > 0) {
                val item = awardArr.optJSONObject(0)
                val amount = item?.optString("awardAmount", "") ?: ""
                val type = item?.optString("awardType", "") ?: ""
                val name = getItemName(type)
                if (amount.isNotEmpty()) "$name $amount" else name
            } else null

            if (!awardDesc.isNullOrEmpty()) {
                StructuredLog.other("信用2101🎮[小游戏完成Y] 奖励: $awardDesc") // MINI_GAME_ELIMINATE
            } else {
                StructuredLog.other("信用2101🎮[小游戏完成] (未获得奖励)")
            }

        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }

    /** 处理小游戏：击杀类 */
    /**
     * 处理小游戏：击杀类（MINI_GAME_MATCH3）
     */
    private fun handleMiniGameMatch(ev: JsonObject, batchNo: String, eventId: String) {
        try {
            // 1️⃣ eventConfig
            val cfg = ev.optJSONObject("eventConfig")
            if (cfg == null) {
                StructuredLog.error(TAG, "信用2101🎮[小游戏配置缺失] ev=$ev")
                return
            }

            val stageId = cfg.optString("id", "")
            if (stageId.isEmpty()) {
                StructuredLog.error(TAG, "信用2101🎮[stageId 为空] ev=$ev")
                return
            }

            // 2️⃣ 主奖励 award
            val awardArray = cfg.optJSONArray("award")
            if (awardArray == null || awardArray.length() == 0) {
                StructuredLog.error(TAG, "信用2101🎮[award 为空] ev=$ev")
                return
            }

            val mainAward = awardArray.optJSONObject(0)
            if (mainAward == null) {
                StructuredLog.error(TAG, "信用2101🎮[award[0] 为空] ev=$ev")
                return
            }

            // 3️⃣ start
            val startResp = Credit2101RpcCall.eventGameStart(batchNo, eventId, stageId)
            val sJo = startResp.toJsonObject()

            if (sJo.optString("resultCode") == "ENERGY_STAMINA_IS_ZERO") {
                StructuredLog.record(TAG, "信用2101💤 能量已耗尽，无法完成游戏M3")
                return
            }

            if (!ResChecker.checkRes(TAG, sJo)) {
                StructuredLog.error(TAG, "信用2101🎮[小游戏 start 失败] resp=$startResp")
                return
            }

            // =================================================
            // 4️⃣ 构造 extParams（修正重复声明与字段错误）
            // =================================================
            var extParams = jsonObject()

            // 4.1 提取主奖励 (从 award 数组获取)
            // 注意：这里不再加 val，或者确保前面没有声明过同名变量
            val mainAwardObj = awardArray.optJSONObject(0)
            if (mainAwardObj != null) {
                val mType = mainAwardObj.optString("awardType", "")
                val mAmount = mainAwardObj.optString("awardAmount", "").toIntOrNull() ?: 0
                if (mType.isNotEmpty()) {
                    extParams = JsonObject(extParams + (mType to JsonPrimitive(mAmount)))
                }
            }

            // 4.2 计算击杀数 (固定 Key 为 killCount)
            val monsterStr = cfg.optString("monster", "")
            val killCount = if (monsterStr.isNotEmpty()) {
                monsterStr.split("&").size
            } else {
                0
            }
            extParams = JsonObject(extParams + ("killCount" to JsonPrimitive(killCount)))

            // =================================================
            // 5️⃣ complete
            // =================================================
            val completeResp = Credit2101RpcCall.eventGameComplete(
                batchNo,
                eventId,
                stageId,
                if (extParams.isNotEmpty()) extParams else null
            )
            val cJo = completeResp.toJsonObject()
            if (cJo.optString("resultCode") == "ENERGY_STAMINA_IS_ZERO") {
                StructuredLog.record(TAG, "信用2101💤 能量已耗尽，无法完成游戏M3")
                return
            }

            if (!ResChecker.checkRes(TAG, cJo)) {
                StructuredLog.error(TAG, "信用2101🎮[小游戏 complete 失败] task=${ev}   + resp=$completeResp" + "Ext:" + extParams)
                return
            }

            // =================================================
            // 6️⃣ 奖励日志（展示层）
            // =================================================
            val awardArr = cJo.optJSONArray("awardVOList")
            if (awardArr != null && awardArr.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until awardArr.length()) {
                    val item = awardArr.optJSONObject(i) ?: continue
                    val type = item.optString("awardType", "")
                    val amount = item.optString("awardAmount", "")
                    if (type.isNotEmpty()) {
                        if (sb.isNotEmpty()) sb.append(" + ")
                        sb.append(getItemName(type))
                        if (amount.isNotEmpty()) sb.append(" ").append(amount)
                    }
                }
                StructuredLog.other("信用2101🎮[小游戏完成] $sb")
            } else {
                StructuredLog.other("信用2101🎮[小游戏完成] stage=$stageId")
            }

        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "信用2101🎮[handleMiniGameMatch 异常]", e)
        }
    }

    /** 处理黄金印记事件 GOLD_MARK */
    private fun handleGoldMark(
        batchNo: String,
        eventId: String,
        cityCode: String,
        originLat: Double,
        originLng: Double
    ) {
        try {
            val resp = Credit2101RpcCall.collectCredit(batchNo, eventId, cityCode, originLat, originLng)


            val jo = resp.toJsonObject()

            // ① 能量不足
            if (jo.optString("resultCode") == "ENERGY_STAMINA_IS_ZERO") {
                StructuredLog.record(TAG, "信用2101💤 能量已耗尽，停止领取")
                return
            }
            if (!ResChecker.checkRes(TAG, jo)) {
                StructuredLog.error(TAG, "信用2101💰[信用印记 领取失败] resp=$resp")
                return
            }

            val obtained = jo.optInt("obtainedCSBalance", 0)
            // 是否获得宝箱
            val gainBox = jo.optJSONObject("gainBoxVO")
                ?.optBoolean("gain", false) == true
            // 成功日志细分
            when {
                obtained > 0 && gainBox -> {
                    StructuredLog.other(
                        TAG,
                        "信用2101💰[信用印记] 获得 $obtained 颗信用值 + 🎁 印记宝箱"
                    )
                }

                obtained > 0 -> {
                    StructuredLog.other(
                        TAG,
                        "信用2101💰[信用印记] 获得 $obtained 颗信用值"
                    )
                }

                gainBox -> {
                    StructuredLog.other(
                        TAG,
                        "信用2101💰[信用印记] 🎁 获得印记宝箱"
                    )
                }

                else -> {
                    StructuredLog.other(
                        TAG,
                        "信用2101💰[信用印记领取成功]"
                    )
                }
            }
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }

    /**
     * 处理故事事件（SPACE_TIME_GATE）- 批量提交版本
     */
    private suspend fun handleSpaceTimeGate(
        batchNo: String,
        eventId: String,
        cityCode: String,
        latitude: Double,
        longitude: Double
    ) {
        try {
            val queryResp = Credit2101RpcCall.queryEventGate(batchNo, eventId, cityCode, latitude, longitude)

            if (!ResChecker.checkRes(TAG, queryResp)) {
                StructuredLog.record(TAG, "信用2101📖[故事事件查询失败] resp=$queryResp")
                return
            }
            queryResp.toJsonObject()

            //5001009  5001025(携妻归汉)   是张骞
            //1001043 是沈万三
            //4001018 是郑和

            StructuredLog.record(TAG, "信用2101📖[故事事件] 批量提交")

            // 批量完成故事事件
            val results = mutableListOf<String>()
            for (storyId in STORY_IDS) {
                val storyIdStr = storyId.toString()
                val dataKey = "credit2101_story_${storyId}"

                // 检查是否已经处理过这个storyId
                val isProcessed = TaskRuntimeStore.getBoolean(dataKey) ?: false
                if (isProcessed) {
                    StructuredLog.record(TAG, "信用2101📖[故事事件${storyId}] 已处理过，跳过")
                    continue
                }
                try {
                    val result = Credit2101RpcCall.completeEventGate(batchNo, eventId, cityCode, latitude, longitude, storyIdStr)
                    results.add(result)

                    // 检查处理结果
                    try {
                        val resultJson = result.toJsonObject()
                        val resultCode = resultJson.optString("resultCode", "")
                        if (resultJson.optString("resultMsg", "").contains("资产流水重复处理")) {
                            // 标记为已处理（遇到重复错误说明已经处理过了）
                            TaskRuntimeStore.put(dataKey, true)
                            StructuredLog.record(TAG, "信用2101📖[故事事件${storyId}] 检测到重复处理，标记为已处理")
                            break
                        } else if (ResChecker.checkRes(TAG, result)) {
                            // 处理成功，标记为已处理
                            TaskRuntimeStore.put(dataKey, true)
                            StructuredLog.record(TAG, "信用2101📖[故事事件${storyId}] 处理成功，标记为已处理")
                        } else {
                            StructuredLog.record(TAG, "信用2101📖[故事事件${storyId}] 处理失败: $resultCode")
                        }
                    } catch (_: Exception) {
                        // JSON解析失败，但也要标记避免重复尝试
                        TaskRuntimeStore.put(dataKey, true)
                        StructuredLog.record(TAG, "信用2101📖[故事事件${storyId}] JSON解析失败，标记为已处理避免重试")
                    }

                } catch (e: Exception) {
                    // 单个storyId处理失败，也要标记避免重复尝试
                    TaskRuntimeStore.put(dataKey, true)
                    results.add("""{"success":false,"resultMsg":"处理异常: ${e.message}"}""")
                    StructuredLog.record(TAG, "信用2101📖[故事事件${storyId}] 处理异常，标记为已处理: ${e.message}")
                }
                // 添加适当延迟避免请求过于频繁（suspend delay 可响应协程取消）
                delay(800)
            }
            var successCount = 0
            var totalGainAmount = 0
            val gainBuffs = mutableListOf<String>()
            // 处理所有结果
            val repeatErrorCount = 0
            var otherErrorCount = 0
            for ((index, completeResp) in results.withIndex()) {
                if (index >= STORY_IDS.size) break // 安全检查
                val currentStoryId = STORY_IDS[index]
                try {
                    // 检查是否为重复处理错误
                        val respJson = completeResp.toJsonObject()
                    if (!ResChecker.checkRes(TAG, completeResp)) {
                        otherErrorCount++
                        StructuredLog.record(TAG, "信用2101📖[故事事件${index + 1}完成失败] storyId=$currentStoryId resp=$completeResp")
                        continue
                    }
                    val gainBuff = respJson.optJSONObject("gainBuffVO")
                    if (gainBuff != null) {
                        val buffId = gainBuff.optString("buffConfigId", "")
                        val detail = gainBuff.optJSONObject("buffDetail")
                        val actionDesc = detail?.optString("buffActionDesc", "") ?: ""
                        val amount = detail?.optInt("amount", 0) ?: 0

                        if (amount > 0 && actionDesc.isNotEmpty()) {
                            successCount++
                            totalGainAmount += amount
                            gainBuffs.add("$actionDesc+$amount($buffId)")
                            StructuredLog.other("信用2101📖[故事事件${index + 1}完成] storyId=$currentStoryId 获得增益 $actionDesc +$amount ($buffId)")
                        } else {
                            StructuredLog.other("信用2101📖[故事事件${index + 1}完成] storyId=$currentStoryId buff=$buffId")
                        }
                    } else {
                        StructuredLog.other("信用2101📖[故事事件${index + 1}完成] storyId=$currentStoryId")
                    }

                } catch (e: Exception) {
                    otherErrorCount++
                    StructuredLog.record(TAG, "信用2101📖[故事事件${index + 1}处理异常] storyId=$currentStoryId error=${e.message}")
                }
            }
            // 汇总统计
            val processedCount = successCount + repeatErrorCount + otherErrorCount
            StructuredLog.record(TAG, "信用2101📖[故事事件批量完成统计] 成功:$successCount 重复错误:$repeatErrorCount 其他错误:$otherErrorCount 已处理:$processedCount/${STORY_IDS.size}")

            if (successCount > 0) {
                StructuredLog.other("信用2101📖[故事事件总增益:+$totalGainAmount")
                if (gainBuffs.isNotEmpty()) {
                    StructuredLog.other("信用2101📖[故事事件增益详情] ${gainBuffs.joinToString(" | ")}")
                }
            }

        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }

    /**
     * 处理黑色印记事件
     * @return 实际消耗的能量值
     */
    private fun handleBlackMark(ev: JsonObject, availableEnergy: Int): EventResult {
        val eventId = ev.optString("eventId")
        if (eventId.isEmpty()) {
            StructuredLog.error(TAG, "信用2101⚫[黑色印记] 事件ID为空")
            return EventResult.Failed
        }

        // 定义常量，方便维护
        val joinCost = 10
        val maxUsers = 4

        try {
            // --- 阶段 1: 初始查询与状态解析 ---
            var resp = Credit2101RpcCall.queryBlackMarkEvent(eventId)
            if (!ResChecker.checkRes(TAG, resp)) return EventResult.Failed

            var jo = resp.toJsonObject()
            var assistantVO = jo.optJSONObject("assistantVO") ?: return EventResult.Failed
            val userList = assistantVO.optJSONArray("assistantUserInfoList")

            // 检查自己是否已在列表中
            var hasSelf = false
            val userCount = userList?.length() ?: 0
            if (userList != null) {
                for (i in 0 until userCount) {
                    if (userList.optJSONObject(i)?.optBoolean("self") == true) {
                        hasSelf = true
                        break
                    }
                }
            }

            var currentEnergy = availableEnergy
            var usedEnergy = 0

            // --- 阶段 2: 尝试加入 ---
            if (!hasSelf) {
                // 2.1 检查前置条件
                if (userCount >= maxUsers) {
                    StructuredLog.record(TAG, "信用2101⚫[黑色印记] 占位已满($userCount/$maxUsers)")
                    return EventResult.Skipped
                }
                if (currentEnergy < joinCost) {
                    StructuredLog.record(TAG, "信用2101⚫[黑色印记] 加入需要能量$joinCost, 当前不足")
                    return EventResult.Skipped
                }

                // 2.2 执行加入
                val joinResp = Credit2101RpcCall.joinBlackMarkEvent(joinCost, eventId)
                if (!ResChecker.checkRes(TAG, joinResp)) {
                    StructuredLog.record(TAG, "信用2101⚫[加入失败] 可能已被填满或过期")
                    return EventResult.Skipped
                }

                // 2.3 更新状态
                usedEnergy += joinCost
                currentEnergy -= joinCost
                StructuredLog.other("信用2101⚫[黑色印记] 成功加入, 注入 $joinCost")

                // 2.4 重新查询最新状态（关键：加入后进度可能变了，或者已经完成了）
                resp = Credit2101RpcCall.queryBlackMarkEvent(eventId)
                if (!ResChecker.checkRes(TAG, resp)) {
                    // 虽然重查失败，但加入已经成功扣了能量，算成功
                    return EventResult.Success(usedEnergy)
                }

                jo = resp.toJsonObject()
                assistantVO = jo.optJSONObject("assistantVO") ?: return EventResult.Success(usedEnergy)
            }

            // --- 阶段 3: 尝试充能 (通用逻辑) ---
            // 此时无论是刚加入的，还是原本就在里面的，流程都汇聚到这里
            val curr = assistantVO.optInt("currAssistantCount", 0)
            val total = assistantVO.optInt("totalAssistantCount", 0)

            if (total == 0) {
                // 防御性编程，避免除零或逻辑错误，如果已加入则返回Success
                return if (usedEnergy > 0) EventResult.Success(usedEnergy) else EventResult.Failed
            }

            val remainNeed = total - curr

            if (remainNeed <= 0) {
                StructuredLog.record(TAG, "信用2101⚫[黑色印记] 能量已满 $curr/$total")
                return if (usedEnergy > 0) EventResult.Success(usedEnergy) else EventResult.Skipped
            }

            if (currentEnergy >= remainNeed) {
                val chargeResp = Credit2101RpcCall.chargeBlackMarkEvent(remainNeed, eventId)
                if (ResChecker.checkRes(TAG, chargeResp)) {
                    usedEnergy += remainNeed
                    StructuredLog.other("信用2101⚫[黑色印记] 完成修复, 注入 $remainNeed")
                    return EventResult.Success(usedEnergy)
                } else {
                    StructuredLog.error(TAG, "信用2101⚫[充能失败]")
                    // 如果前面加入了，这里充能失败也算部分成功
                    return if (usedEnergy > 0) EventResult.Success(usedEnergy) else EventResult.Failed
                }
            } else {
                StructuredLog.record(TAG, "信用2101⚫[黑色印记] 剩余需求 $remainNeed, 能量不足")
                // 同上，如果只加入了但不够充能，也算成功消耗了能量
                return if (usedEnergy > 0) EventResult.Success(usedEnergy) else EventResult.Skipped
            }

        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "处理黑色印记异常", e)
            return EventResult.Failed
        }
    }
    /** 探测一次事件 */
    private fun exploreOnce(cityCode: String, latitude: Double, longitude: Double): Boolean {
        val resp = Credit2101RpcCall.exploreGridEvent(cityCode, latitude, longitude)
        if (!ResChecker.checkRes(TAG, resp)) {
            StructuredLog.error(TAG, "信用2101🔍[探测失败][城市代码:$cityCode| $latitude/ $longitude] $resp")
            return false
        }

        val root = runCatching { resp.toJsonObject() }.getOrElse {
            StructuredLog.printStackTrace(TAG, it)
            return false
        }


        val list = root.optJSONArray("eventExploreVOList")
        val count = list?.length() ?: 0

        if (count <= 0) {
            StructuredLog.record(TAG, "信用2101🔍[探测] 本次未发现新事件")
            return false
        }

        val types = mutableSetOf<String>()
        for (i in 0 until count) {
            val ev = list!!.optJSONObject(i) ?: continue
            val type = ev.optString("eventType", "")
            if (type.isNotEmpty()) types.add(type)
        }

        StructuredLog.other("信用2101🔍[探测成功] 新事件$count 个，类型=${types.joinToString(",")}")
        return true
    }

    /** 自动合成领取图鉴 */
    private fun handleChapterTasks() {
        try {
            // 1. 查询图鉴进度
            val resp = Credit2101RpcCall.queryChapterProgress()


            // 校验业务逻辑结果 (resultCode 是否为 SUCCESS)
            if (!ResChecker.checkRes(TAG, resp)) {
                StructuredLog.error(TAG, "信用2101🎨[图鉴] 查询失败: 业务返回错误信息, resp=$resp")
                return
            }

            val jo = resp.toJsonObject()
            val chapterArray = jo.optJSONArray("charterProgress")

            // 如果数组不存在，记录错误原因
            if (chapterArray == null || chapterArray.length() == 0) {
                StructuredLog.error(TAG, "信用2101🎨[图鉴] 解析失败: 当前图鉴列表为空 或 未在响应中找到 charterProgress 数组")
                return
            }

            var allFinished = true

            for (i in 0 until chapterArray.length()) {
                val item = chapterArray.optJSONObject(i)
                if (item == null) {
                    StructuredLog.error(TAG, "信用2101🎨[图鉴] 数据异常: 第 ${i + 1} 条图鉴对象为 null")
                    continue
                }

                val chapterId = item.optString("chapter")
                val name = getChapterName(chapterId)
                val cardCount = item.optInt("cardCount")
                val obtainedCount = item.optInt("obtainedCardCount")
                val awardStatus = item.optString("awardStatus")

                // 情况 A：数量凑齐了 (LOCKED -> 尝试合成)
                if (awardStatus == "LOCKED" && obtainedCount >= cardCount && cardCount > 0) {
                    allFinished = false
                    StructuredLog.other("信用2101🎨[图鉴] [$name] 已集齐($obtainedCount/$cardCount)，正在合成...")

                    val res = Credit2101RpcCall.completeChapterAction("CHAPTER_COMPLETE", chapterId)
                    if (ResChecker.checkRes(TAG, res)) {
                        StructuredLog.other("信用2101🎨[图鉴] [$name] 合成完成")
                    } else {
                        StructuredLog.error(TAG, "信用2101🎨[图鉴] [$name] 合成请求失败, resp=$res")
                    }
                }
                // 情况 B：已合成未领奖 (UNLOCKED -> 尝试领奖)
                else if (awardStatus == "UNLOCKED") {
                    allFinished = false
                    StructuredLog.other("信用2101🎨[图鉴] [$name] 检测到待领取奖励...")

                    val res = Credit2101RpcCall.completeChapterAction("CHAPTER_AWARD", chapterId)
                    val resJo = res.toJsonObject()
                    if (ResChecker.checkRes(TAG, resJo)) {
                        val gain = resJo.optJSONObject("gainByCollectedAll")
                        if (gain != null) {
                            val type = gain.optString("awardType")
                            val amount = gain.optString("awardAmount")
                            val typeName = getItemName(type)
                            StructuredLog.other("信用2101🎨[图鉴] [$name] 奖励领取成功: $typeName x$amount")
                        } else {
                            StructuredLog.other("信用2101🎨[图鉴] [$name] 奖励领取成功(未解析到具体奖励)")
                        }
                    } else {
                        StructuredLog.error(TAG, "信用2101🎨[图鉴] [$name] 领奖请求失败, resp=$res")
                    }
                }
                // 情况 C：还没集齐
                else if (awardStatus == "LOCKED" && obtainedCount < cardCount) {
                    allFinished = false
                    // 此处可根据需要决定是否打印“未集齐”的日志
                }
                // 情况 D：已领取 (CLAIMED) -> 无需处理
            }

            // 最终检查：只有所有章节都处于 CLAIMED 状态
            if (allFinished) {
                StructuredLog.record(TAG, "信用2101🎨[图鉴] 检查完毕：所有图鉴奖励均已领取完毕")
                autoAddToBlacklist(StatusFlags.FLAG_CREDIT2101_CHAPTER_TASK_DONE, "信用2101🎨[图鉴]合成完毕", "1337")
            }

        } catch (e: Throwable) {
            // 捕获所有代码逻辑异常，防止模块崩溃
            StructuredLog.printStackTrace(TAG, "信用2101🎨[图鉴] 逻辑处理异常", e)
        }
    }

    /** 自动检查天赋并合成 **/
    /** 自动检查并升级天赋 **/
    private suspend fun handleAutoUpgradeTalent() {
        try {
            // 1. 获取当前状态
            val queryResp = Credit2101RpcCall.queryRelationTalent()
            val jo = queryResp.toJsonObject()
            // 使用你的 ResChecker 校验基础响应
            if (!ResChecker.checkRes(TAG, jo)) {
                StructuredLog.error(TAG, "信用2101🎮[天赋] 查询失败: ${jo.optString("resultMsg")}")
                return
            }

            var availablePoint = jo.optInt("availablePoint", 0)
            if (availablePoint <= 0) {
                StructuredLog.record(TAG, "信用2101🎮[天赋] 检查完毕：无可用天赋点")
                return
            }

            val talentArray = jo.optJSONArray("talentAttributeVOList") ?: return

            // 2. 筛选未满级的天赋 (Level < 5)
            val upgradeableList = mutableListOf<JsonObject>()
            for (i in 0 until talentArray.length()) {
                val talent = talentArray.optJSONObject(i)
                if (talent != null && talent.optInt("attributeLevel") < 5) {
                    upgradeableList.add(talent)
                }
            }

            if (upgradeableList.isEmpty()) {
                StructuredLog.record(TAG, "信用2101🎮[天赋] 所有天赋已满级")
                return
            }

            // 3. 开始升级流程
            StructuredLog.other("信用2101🎮[天赋] 发现 $availablePoint 点可用，执行升级")

            while (availablePoint > 0 && upgradeableList.isNotEmpty()) {
                // 随机选择一个未满级的天赋
                val index = (0 until upgradeableList.size).random()
                val target = upgradeableList[index]

                val attrType = target.optString("attributeType")
                val currentLevel = target.optInt("attributeLevel")
                val nextLevel = currentLevel + 1
                val talentName = getTalentName(attrType)

                // 逻辑处理：EXPLORE_COUNT -> EXPLORE
                val treeType = if (attrType.contains("_")) attrType.substringBefore("_") else attrType

                StructuredLog.other("信用2101🎮[天赋] 尝试升级 $talentName ($attrType) 至 $nextLevel 级")

                val upgradeResp = Credit2101RpcCall.upgradeTalentAttribute(attrType, treeType, nextLevel)

                // 校验升级结果：判断最外层 success 和 talentUpgradeVO 内的 success
            val upgradeJo = upgradeResp.toJsonObject()
                val upgradeVo = upgradeJo.optJSONObject("talentUpgradeVO")
                val isSuccess = upgradeJo.optBoolean("success") && (upgradeVo?.optBoolean("success") ?: false)

                if (isSuccess) {
                    availablePoint--
                    StructuredLog.other("信用2101🎮[天赋] $talentName 升级成功！剩余点数: $availablePoint")

                    // 更新本地列表状态
                    if (nextLevel >= 5) {
                        upgradeableList.removeAt(index)
                    } else {
                        upgradeableList[index] = JsonObject(target + ("attributeLevel" to JsonPrimitive(nextLevel)))
                    }

                    // 升级间隔，防止并发过快（suspend delay 可响应协程取消）
                    delay(1500)
                } else {
                    val errorMsg = upgradeJo.optString("resultMsg", "未知错误")
                    StructuredLog.error(TAG, "信用2101🎮[天赋] $talentName 升级终止: $errorMsg")
                    break
                }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "天赋升级逻辑异常", e)
        }
    }
}
