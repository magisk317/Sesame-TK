package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.StructuredLog.record
import kotlinx.serialization.json.JsonObject

/** Handles gold-ticket sign-in and redemption. */
class MemberGoldTicketService {
    private val TAG = "MemberGoldTicketService"
        fun run(doSignIn: Boolean, doConsume: Boolean) {
            try {

                // 1. 获取首页数据 (签到需要)
                var homeResult: JsonObject? = null
                if (doSignIn) {
                    val homeRes = MemberWelfareRpc.queryHome()
                    if (homeRes != null) {
                        val homeJson = homeRes.toJsonObject()
                        if (ResChecker.checkRes(TAG, homeJson)) {
                            homeResult = homeJson.optJSONObject("result")
                        }
                    }
                }

                // 2. 执行签到
                if (doSignIn && homeResult != null) {
                    doGoldTicketSignIn(homeResult)
                }

                // 3. 执行提取 (提取功能独立，总是需要调用 queryConsumeHome 获取最新余额)
                if (doConsume) {
                    doGoldTicketConsume()
                }
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, e)
            }
        }

        /**
         * 黄金票签到逻辑 (使用新接口 welfareCenterTrigger)
         */
        private fun doGoldTicketSignIn(homeResult: JsonObject) {
            try {
                val signObj = homeResult.optJSONObject("sign")
                if (signObj != null) {
                    val todayHasSigned = signObj.optBoolean("todayHasSigned", false)
                    if (todayHasSigned) {
                        record("黄金票🎫[今日已签到]")
                    } else {
                        record("黄金票🎫[准备签到]")
                        // 调用新接口进行签到
                        val signRes = MemberWelfareRpc.trigger("SIGN") ?: return
                        val signJson = signRes.toJsonObject()

                        if (ResChecker.checkRes(TAG, signJson)) {
                            val signResult = signJson.optJSONObject("result")
                            var amount = ""
                            if (signResult != null && signResult.has("prize")) {
                                amount = signResult.getJSONObject("prize").optString("amount")
                            }
                            StructuredLog.other("黄金票🎫[签到成功]#获得: $amount")
                        }
                    }
                }
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, e)
            }
        }

        /**
         * 黄金票提取逻辑 (使用新接口 queryConsumeHome 和 submitConsume)
         */
        private fun doGoldTicketConsume() {
            try {
                record("黄金票🎫[准备检查余额及提取]")

                // 1. 调用新接口 queryConsumeHome 获取最新的资产信息
                val queryRes = MemberWelfareRpc.queryConsume() ?: return
                val queryJson = queryRes.toJsonObject()
                if (!ResChecker.checkRes(TAG, queryJson)) return

                val result = queryJson.optJSONObject("result") ?: return

                // 2. 获取余额
                val assetInfo = result.optJSONObject("assetInfo") ?: return

                val availableAmount = assetInfo.optInt("availableAmount", 0)

                // 3. 计算提取数量 (整百提取逻辑)
                val extractAmount = (availableAmount / 100) * 100

                if (extractAmount < 100) {
                    record("黄金票🎫[余额不足] 当前: $availableAmount，最低需100")
                    return
                }

                // 4. 获取必要参数 productId 和 bonusAmount
                var productId = ""
                val product = result.optJSONObject("product")
                if (product != null) {
                    productId = product.optString("productId")
                } else if (result.has("productList") && result.optJSONArray("productList") != null && (result.optJSONArray(
                        "productList"
                    )?.length() ?: 0) > 0
                ) {
                    productId = result.optJSONArray("productList")?.optJSONObject(0)?.optString("productId") ?: ""
                }

                if (productId.isEmpty()) {
                    StructuredLog.error("黄金票🎫[提取异常] 未找到有效的基金ID")
                    return
                }

                var bonusAmount = 0
                val bonusInfo = result.optJSONObject("bonusInfo")
                if (bonusInfo != null) {
                    bonusAmount = bonusInfo.optInt("bonusAmount", 0)
                }

                // 5. 提交提取
                record("黄金票🎫[提取] 计划: $extractAmount 份 (持有: $availableAmount)")
                val submitRes = MemberWelfareRpc.submitConsume(extractAmount, productId, bonusAmount)

                if (submitRes != null) {
                    val submitJson = submitRes.toJsonObject()
                    if (ResChecker.checkRes(TAG, submitJson)) {
                        val submitResult = submitJson.optJSONObject("result")
                        val writeOffNo = if (submitResult != null) submitResult.optString("writeOffNo") else ""

                        if (!writeOffNo.isEmpty()) {
                            StructuredLog.other("黄金票🎫[提取成功]#消耗: $extractAmount 份")
                        } else {
                            StructuredLog.error("黄金票🎫[提取失败] 未返回核销码")
                        }
                    }
                }
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, e)
            }
        }
    }
