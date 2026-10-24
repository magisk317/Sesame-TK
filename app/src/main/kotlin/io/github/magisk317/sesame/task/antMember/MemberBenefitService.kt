package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.data.Status.Companion.setFlagToday
import io.github.magisk317.sesame.data.Status.Companion.memberPointExchangeBenefitToday
import io.github.magisk317.sesame.data.Status.Companion.canMemberPointExchangeBenefitToday
import io.github.magisk317.sesame.data.Status.Companion.hasFlagToday
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.StructuredLog.record
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.MemberBenefitsMap
import io.github.magisk317.sesame.util.maps.SesameGiftMap
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.delay

/** Owns member point and sesame-grain benefit exchanges. */
class MemberBenefitService {
    private val TAG = "MemberBenefitService"

    /**
     * 会员积分0元兑，权益道具兑换
     */
    fun exchangePoints(targetIds: Set<String?>) {
        if (hasFlagToday("memberBenefit::refresh")) {
            return
        }
        try {
            val userId = UserMap.currentUid
            record(TAG, "会员积分商品加载..")
            // 1. 分类配置直接放在函数内部
            val categoryMap = mapOf(
                "公益道具" to listOf("94000SR2025022012011004"),
                "出行旅游" to listOf("94000SR2025010611441006", "94000SR2025010611458001"),
                "餐饮" to listOf("94000SR2025110315351006"),
                "皮肤藏品" to listOf("94000SR2025110315357001", "94000SR2025111015444005"),
                "理财还款" to listOf("94000SR2025011411575008", "94000SR2025091814834002"),
                "红包神券" to listOf("94000SR2025092414916001"),
                "充值缴费" to listOf("94000SR2025011611640002", "94000SR2025091814821018")
            )
            // 3. 遍历分类
            categoryMap.forEach { (catName, ids) ->
                var currentPage = 1
                var hasNextPage = true
                while (hasNextPage) {//此处请求过载，容易风控，循环频繁请求会炸
                    GlobalThreadPools.sleepCompat(1000L)
                    val responseStr = MemberBenefitsRpc.queryDeliveryZoneDetail(ids, currentPage, 48)
                    if (responseStr.isNullOrEmpty()) {
                        StructuredLog.error(TAG, "分类[$catName] 接口返回空字符串")
                        break
                    }
                    val jo = responseStr.toJsonObject()
                    if (!ResChecker.checkRes(TAG, jo)) {
                        StructuredLog.error(TAG, "分类[$catName] 校验失败: $responseStr")
                        break
                    }
                    val benefits = jo.optJSONArray("briefConfigInfos")
                    if (benefits == null || benefits.length() == 0) {
                        StructuredLog.error(TAG, "分类[$catName] 第 $currentPage 页没有权益数据")
                        break
                    }
                    for (i in 0 until benefits.length()) {
                        val rawItem = benefits.getJSONObject(i)
                        // 兼容 benefitInfo 嵌套结构
                        val benefit = if (rawItem.has("benefitInfo")) rawItem.getJSONObject("benefitInfo") else rawItem
                        val name = benefit.optString("name", "未知")
                        val benefitId = benefit.optString("benefitId")
                        val itemId = benefit.optString("itemId")
                        val pointNeeded = benefit.optJSONObject("pricePresentation")?.optString("point") ?: "0"
                        if (benefitId.isEmpty()) {
                            record(TAG, "商品[$name] 没有 benefitId，跳过")
                            continue
                        }
                        // 记录 benefitId 映射关系
                        IdMapManager.getInstance(MemberBenefitsMap::class.java).add(benefitId, name)
                        // 校验是否在白名单
                        val inWhiteList = targetIds.contains(benefitId)
                        if (!inWhiteList) {
                            // 如果不在白名单，保持安静，不刷 record 日志，或者你可以按需开启
                            continue
                        }
                        // 校验频率限制
                        if (!canMemberPointExchangeBenefitToday(benefitId)) {
                            record(TAG, "跳过[$name]: 今日已兑换过")
                            continue
                        }
                        // 5. 执行兑换
                        record(TAG, "准备兑换[$name], ID: $benefitId, 需积分: $pointNeeded")
                        if (exchangeBenefit(benefitId, itemId, userId)) {
                            StructuredLog.other("会员积分🎐兑换[$name]#花费[$pointNeeded 积分]")
                        } else {
                            record(TAG, "兑换失败: $name (ItemId: $itemId)")
                        }
                    }
                    val nextPageNum = jo.optInt("nextPageNum", 0)
                    if (nextPageNum > 0 && nextPageNum > currentPage) {
                        currentPage = nextPageNum
                    } else {
                        hasNextPage = false
                    }
                }
                IdMapManager.getInstance(MemberBenefitsMap::class.java).save(userId)
                record(TAG, "分类[$catName]处理完毕，已执行中间保存")
            }
            // 7. 保存映射表
            IdMapManager.getInstance(MemberBenefitsMap::class.java).save(userId)
            record(TAG, "会员积分🎐全部分类任务处理完毕")
            setFlagToday("memberBenefit::refresh")

        } catch (t: Throwable) {
            record(TAG, "memberPointExchangeBenefit 运行异常: ${t.message}")
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    private fun exchangeBenefit(benefitId: String, itemid: String, userid: String?): Boolean {
        try {
            val resString = MemberBenefitsRpc.exchangeBenefit(benefitId, itemid, userid ?: return false)
            val jo = resString.toJsonObject()
            val resultCode = jo.optString("resultCode")

            if (resultCode == "BEYOND_BUYING_TIMES") {
                record(TAG, "会员权益兑换已达上限，标记任务今日完成")
                memberPointExchangeBenefitToday(benefitId)
                return true
            }

            if (ResChecker.checkRes(TAG + "会员权益兑换失败:", jo)) {
                memberPointExchangeBenefitToday(benefitId)
                return true
            }

        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "exchangeBenefit 错误:", t)
        }
        return false
    }

    /**
     * 【新增】芝麻粒兑换道具
     * 仿照会员积分兑换逻辑：遍历列表更新Map，同时匹配用户设置进行兑换
     */
    suspend fun exchangeSesameGrains(targetIds: Set<String?>): Unit = CoroutineUtils.run {
        // 每日只运行一次，避免重复请求
        if (hasFlagToday("sesameGrainExchange::done")) {
            return@run
        }

        try {
            val userId = UserMap.currentUid
            // 获取用户在配置中选中的商品ID列表（白名单）
            
            var currentPage = 1
            // 限制最大页数，防止无限循环（抓包看大概也就3-5页）
            val maxPage = 10
            val pageSize = 99 //适当调整pageSize 减少请求
            var hasNextPage = true

            while (hasNextPage && currentPage <= maxPage) {
                // 稍微延时，避免请求过快被风控
                GlobalThreadPools.sleepCompat(1500L)
                // 调用 RPC 获取列表
                val jo = MemberBenefitsRpc.queryExchangeList(currentPage, pageSize).toJsonObject()
//                所有的请求使用这个类方法检查过滤就行了
                if (!ResChecker.checkRes(TAG, jo)) {//一次失败直接return不要break
                    StructuredLog.error(TAG, "芝麻粒商品列表校验失败: $jo")
                    return@run
                }

                val data = jo.optJSONObject("data") ?: return@run //没数据也return
                val list = data.optJSONArray("awardTemplateList") ?: return@run

                // 遍历当前页的商品
                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    val name = item.optString("awardName", "未知商品")
                    val id = item.optString("awardTemplateId")
                    val pointNeeded = item.optString("point", "0")
                    val remainingBudget = item.optInt("remainingBudget", 0) // 库存
                    if (id.isEmpty()) continue
                    // 1. 核心步骤：记录 ID 和 名称 的映射关系
                    // 这样下次进入设置界面，就能看到中文名称了
                    IdMapManager.getInstance(SesameGiftMap::class.java).add(id, name)
                    // 2. 检查是否在用户的待兑换列表里（白名单）
                    val inWhiteList = targetIds.contains(id)
                    if (!inWhiteList) {
                        // 如果没勾选，就跳过，不做处理
                        continue
                    }
                    // 3. 检查库存
                    if (remainingBudget <= 0) {
                        record(TAG, "跳过[$name]: 库存不足")
                        continue
                    }
                    // 4. 执行兑换 (这里不加每日限制判断了，只要有库存且勾选了就尝试兑换)
                    record(TAG, "准备兑换[$name], ID: $id, 需芝麻粒: $pointNeeded")
                    if (exchangeSesameGift(id, name, pointNeeded)) {
                        // 兑换成功后，稍微等待一下
                        delay(2000)
                    }
                }
                // 判断是否有下一页
                hasNextPage = data.optBoolean("hasNext", false)
                currentPage++
            }

            // 保存映射关系到本地文件 sesame_gift.json
            IdMapManager.getInstance(SesameGiftMap::class.java).save(userId)
            record(TAG, "芝麻粒兑换任务处理完毕，商品列表已更新")
            // 标记今日已完成
            setFlagToday("sesameGrainExchange::done")

        } catch (t: Throwable) {//这里
            StructuredLog.printStackTrace(TAG, "doSesameGrainExchange 运行异常:", t)
        }
    }

    /**
     * 执行具体的芝麻粒兑换请求
     */
    private fun exchangeSesameGift(templateId: String, name: String, point: String): Boolean {
        try {
            // 调用兑换接口
            val resString = MemberBenefitsRpc.obtainAward(templateId)
            val jo = resString.toJsonObject()

            // 检查结果
            if (ResChecker.checkRes(TAG, jo)) {
                val recordId = jo.optJSONObject("data")?.optString("awardRecordId", "")
                StructuredLog.other("芝麻粒兑换🛒[成功] $name #消耗${point}粒")
                return true
            } else {
                val errorMsg = jo.optString("resultView", resString)
                // 如果是“积分不足”等错误，也会在这里打印
                StructuredLog.error(TAG, "兑换失败[$name]: $errorMsg")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "exchangeSesameGift 错误:", t)
        }
        return false
    }
}
