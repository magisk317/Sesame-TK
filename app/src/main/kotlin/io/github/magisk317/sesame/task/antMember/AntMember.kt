package io.github.magisk317.sesame.task.antMember

import android.annotation.SuppressLint
import io.github.magisk317.sesame.data.Status.Companion.canMemberPointExchangeBenefitToday
import io.github.magisk317.sesame.data.Status.Companion.canMemberSignInToday
import io.github.magisk317.sesame.data.Status.Companion.hasFlagToday
import io.github.magisk317.sesame.data.Status.Companion.memberPointExchangeBenefitToday
import io.github.magisk317.sesame.data.Status.Companion.memberSignInToday
import io.github.magisk317.sesame.data.Status.Companion.setFlagToday
import io.github.magisk317.sesame.data.StatusFlags
import io.github.magisk317.sesame.entity.MemberBenefit
import io.github.magisk317.sesame.entity.SesameGift
import io.github.magisk317.sesame.hook.internal.LocationHelper.requestLocationSuspend
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.StructuredLog.record
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.toJsonArray
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

class AntMember : ModelTask() {
    private val memberTreeService = MemberTreeService()
    private val memberAlchemyService = MemberAlchemyService()
    private val memberSesameService = MemberSesameService()
    private val memberBenefitService = MemberBenefitService()
    private val memberAnnualReviewService = MemberAnnualReviewService()
    private val memberGoldTicketService = MemberGoldTicketService()
    private val memberStickerService = MemberStickerService()
    private val memberPointCertificateService = MemberPointCertificateService()
    private val memberTaskService = MemberTaskService { memberPointCertificateService.collect(1, 8) }
    private val memberInsuredGoldService = MemberInsuredGoldService()
    private val memberBeanService = MemberBeanService()
    private val memberGameCenterService = MemberGameCenterService()
    private val merchantTaskService = MerchantTaskService()
    private val luckyCardTaskService = LuckyCardTaskService

    override fun getName(): String {
        return "会员"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.MEMBER
    }

    override fun getIcon(): String {
        return "AntMember.png"
    }

    private var memberSign: BooleanModelField? = null
    private var memberTask: BooleanModelField? = null
    private var memberPointExchangeBenefit: BooleanModelField? = null
    private var memberPointExchangeBenefitList: SelectModelField? = null
    private var collectSesame: BooleanModelField? = null
    private var collectSesameWithOneClick: BooleanModelField? = null
    private var sesameTask: BooleanModelField? = null
    private var collectInsuredGold: BooleanModelField? = null
    private var enableGameCenter: BooleanModelField? = null
    private var merchantSign: BooleanModelField? = null
    private var merchantKmdk: BooleanModelField? = null
    private var merchantMoreTask: BooleanModelField? = null
    private var beanSignIn: BooleanModelField? = null
    private var beanExchangeBubbleBoost: BooleanModelField? = null
    private var luckyCard: BooleanModelField? = null

    // 芝麻炼金
    private var sesameAlchemy: BooleanModelField? = null

    // 芝麻树
    private var enableZhimaTree: BooleanModelField? = null

    /*//年度回顾
    private var annualReview: BooleanModelField? = null*/

    // 黄金票配置 - 签到
    private var enableGoldTicket: BooleanModelField? = null

    // 黄金票配置 - 提取/兑换
    private var enableGoldTicketConsume: BooleanModelField? = null

    /** 账单 贴纸 功能开关 */
    private var collectStickers: BooleanModelField? = null

    // 【新增】芝麻粒兑换
    private var sesameGrainExchange: BooleanModelField? = null
    private var sesameGrainExchangeList: SelectModelField? = null

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(BooleanModelField("memberSign", "会员签到", false).also {
            memberSign = it
        })

        /*
        modelFields.addField(BooleanModelField("memberTask", "会员任务", false).also {
            memberTask = it
        })
        */

        modelFields.addField(
            BooleanModelField(
                "memberPointExchangeBenefit", "会员积分 | 兑换权益", false
            ).also { memberPointExchangeBenefit = it })
        modelFields.addField(
            SelectModelField(
                "memberPointExchangeBenefitList",
                "会员积分 | 兑换列表",
                LinkedHashSet<String?>()
            ) {
                MemberBenefit.getList()
            }.also { memberPointExchangeBenefitList = it })


        modelFields.addField(
            BooleanModelField(
                "sesameGrainExchange", "芝麻信用 | 芝麻粒兑换道具", false
            ).also { sesameGrainExchange = it })

        modelFields.addField(
            BooleanModelField(
                "luckyCard", "信用卡 | 好运卡领取", false
            ).also { luckyCard = it })

        // 使用 SesameGiftMap 来存储和回显商品名称
        modelFields.addField(
            SelectModelField(
                "sesameGrainExchangeList",
                "芝麻信用 | 兑换列表",
                LinkedHashSet<String?>()
            ) {
                SesameGift.getList()
            }.also { sesameGrainExchangeList = it })

        modelFields.addField(
            BooleanModelField(
                "sesameTask", "芝麻信用|芝麻粒信用任务", false
            ).also { sesameTask = it })

        modelFields.addField(BooleanModelField("collectSesame", "芝麻信用|芝麻粒领取", false).also {
            collectSesame = it
        })
        modelFields.addField(
            BooleanModelField(
                "collectSesameWithOneClick", "芝麻信用|芝麻粒领取使用一键收取", false
            ).also { collectSesameWithOneClick = it })

        // 芝麻炼金
        modelFields.addField(
            BooleanModelField(
                "sesameAlchemy", "芝麻炼金", false
            ).also { sesameAlchemy = it })
        // 芝麻树
        modelFields.addField(BooleanModelField("enableZhimaTree", "芝麻信用|芝麻树", false).also {
            enableZhimaTree = it
        })

        /*
        modelFields.addField(
            BooleanModelField(
                "collectInsuredGold", "蚂蚁保|保障金领取", false
            ).also { collectInsuredGold = it })
         */

        // 黄金票配置
        modelFields.addField(
            BooleanModelField(
                "enableGoldTicket", "黄金票签到", false
            ).also { enableGoldTicket = it })
        modelFields.addField(
            BooleanModelField(
                "enableGoldTicketConsume", "黄金票提取(兑换黄金)", false
            ).also { enableGoldTicketConsume = it })
        modelFields.addField(BooleanModelField("enableGameCenter", "游戏中心签到", false).also {
            enableGameCenter = it
        })
        /*
        modelFields.addField(
            BooleanModelField(
                "merchantSign", "商家服务|签到", false
            ).also { merchantSign = it })
        modelFields.addField(
            BooleanModelField(
                "merchantKmdk", "商家服务|开门打卡", false
            ).also { merchantKmdk = it })
        modelFields.addField(
            BooleanModelField(
                "merchantMoreTask", "商家服务|积分任务", false
            ).also {
                merchantMoreTask = it
            })
        modelFields.addField(
            BooleanModelField(
                "beanSignIn", "安心豆签到", false
            ).also { beanSignIn = it })
        modelFields.addField(
            BooleanModelField(
                "beanExchangeBubbleBoost", "安心豆兑换时光加速器", false
            ).also { beanExchangeBubbleBoost = it })
       /* modelFields.addField(
            BooleanModelField(
                "annualReview", "年度回顾", false
            ).also { annualReview = it })*/
         */

        collectStickers = BooleanModelField("CollectStickers", "领取贴纸", false)
        modelFields.addField(requireNotNull(collectStickers))
        return modelFields
    }

    override fun runJava() {
        // 使用协程上下文运行
        runBlocking {
            try {
                // 异步获取位置信息-for 2101
                requestLocationSuspend()
                // 芝麻信用相关检测
                val isSesameOpened: Boolean = checkSesameCanRun()

                // 并行执行独立任务
                val deferredTasks = mutableListOf<Deferred<Unit>>()

                if (memberSign?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { doMemberSign() })
                }

                if (memberTask?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { doAllMemberAvailableTask() })
                }

                if (memberPointExchangeBenefit?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) {
                        memberBenefitService.exchangePoints(memberPointExchangeBenefitList?.value ?: emptySet())
                    })
                }
                if (luckyCard?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) {
                        luckyCardTaskService.claimPendingCards()
                    })
                }
                if (isSesameOpened) {

                    // 芝麻粒兑换入口
                    if (sesameGrainExchange?.value == true) {
                        deferredTasks.add(async(Dispatchers.IO) {
                            memberBenefitService.exchangeSesameGrains(sesameGrainExchangeList?.value ?: emptySet())
                        })
                    }
                    if (sesameTask?.value == true || collectSesame?.value == true) {
                        // 芝麻粒福利签到
                        memberAlchemyService.checkInZml()
                        if (hasFlagToday(StatusFlags.FLAG_ANTMEMBER_DO_ALL_SESAME_TASK)) {
                            record(TAG, "⏭️ 今天已完成过芝麻信用任务，跳过执行")
                        } else {
                            // 芝麻信用任务（今日首次）
                            record(TAG, "芝麻信用💳 今日首次任务")
                            memberSesameService.runTasks()
                            memberSesameService.runGrowthGuideTasks()
                            queryAndCollect() //做完任务领取球
                            record(TAG, "✓ 芝麻信用任务已完成，今天不再执行")
                        }
                        if (collectSesame?.value == true) {
                            deferredTasks.add(async(Dispatchers.IO) {
                                memberSesameService.collect(
                                    collectSesameWithOneClick?.value == true
                                )
                            })
                        }
                    }

                    // 芝麻炼金
                    if (sesameAlchemy?.value == true) {
                        deferredTasks.add(async(Dispatchers.IO) {
                            memberAlchemyService.run()
                            // ===== 次日奖励：只有今天还没领过才执行 =====
                            if (!hasFlagToday(StatusFlags.FLAG_ZMXY_ALCHEMY_NEXT_DAY_AWARD)) {
                                memberAlchemyService.claimNextDayAward()
                            } else record(TAG, "✓ 芝麻粒次日奖励已领取，今天不再执行")
                        })
                    }

                    // 芝麻树
                    if (enableZhimaTree?.value == true) {
                        deferredTasks.add(async(Dispatchers.IO) { memberTreeService.run() })
                    }
                }


                // 保障金功能已下线；未初始化时按关闭处理。
                if (collectInsuredGold?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { collectInsuredGold() })
                }

                // 【更新】执行黄金票任务，替换旧的 goldTicket()
                val goldTicketEnabled = enableGoldTicket?.value == true
                val goldTicketConsumeEnabled = enableGoldTicketConsume?.value == true
                if (goldTicketEnabled || goldTicketConsumeEnabled) {
                    deferredTasks.add(async(Dispatchers.IO) {
                        memberGoldTicketService.run(goldTicketEnabled, goldTicketConsumeEnabled)
                    })
                }

                if (enableGameCenter?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { enableGameCenter() })
                }

                if (beanSignIn?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { beanSignIn() })
                }

               /* if (annualReview!!.value) {   //年度回顾已下线
                    deferredTasks.add(async(Dispatchers.IO) { memberAnnualReviewService.run() })
                */

                if (beanExchangeBubbleBoost?.value == true) {
                    deferredTasks.add(async(Dispatchers.IO) { beanExchangeBubbleBoost() })
                }

                val merchantSignEnabled = merchantSign?.value == true
                val merchantKmdkEnabled = merchantKmdk?.value == true
                val merchantMoreTaskEnabled = merchantMoreTask?.value == true
                if (merchantSignEnabled || merchantKmdkEnabled || merchantMoreTaskEnabled) {
                    deferredTasks.add(async(Dispatchers.IO) {
                        merchantTaskService.run(
                            openDoor = merchantKmdkEnabled,
                            sign = merchantSignEnabled,
                            moreTask = merchantMoreTaskEnabled,
                        )
                    })
                }

                if (collectStickers?.value == true) {
                    queryAndCollectStickers()
                }


                // 等待所有异步任务完成
                deferredTasks.awaitAll()

            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, t)
            }
        }
    }

    /**
     * 会员签到
     */
    /**
     * 会员签到
     */
    private suspend fun doMemberSign() = memberTaskService.sign()

    /**
     * 会员任务-逛一逛
     * 单次执行 1
     */
    private suspend fun doAllMemberAvailableTask() = memberTaskService.runAvailable()

    /**
     * 保障金领取
     */
    private suspend fun collectInsuredGold() = memberInsuredGoldService.collect()

    /**
     * 执行会员任务 类型1
     * @param task 单个任务对象
     */
    /**
     * 查询指定会员任务是否完成
     * @param taskId 任务id
     */

    private suspend fun enableGameCenter() = memberGameCenterService.run()
    private fun beanSignIn() = memberBeanService.signIn()

    private fun beanExchangeBubbleBoost() = memberBeanService.exchangeBubbleBoost()

    /**
     * 查询 + 自动领取贴纸
     */
    @SuppressLint("DefaultLocale")
    fun queryAndCollectStickers() {
        memberStickerService.queryAndCollect()
    }

    companion object {
        private val TAG: String = AntMember::class.java.getSimpleName()

        /**
         * 查询 + 自动领取可领取球（精简一行输出领取信息）
         */
        @SuppressLint("DefaultLocale")
        fun queryAndCollect() {
            try {
                // 1. 查询进度球状态
                val queryResp = ZmxyRpc.queryScoreProgress()
                if (queryResp == null || queryResp.isEmpty()) return

                val json = queryResp.toJsonObject()

                // 检查 success
                if (!ResChecker.checkRes(TAG, json)) return

                val totalWait = json.optJSONObject("totalWaitProcessVO") ?: return

                val idList = totalWait.optJSONArray("totalProgressIdList")
                if (idList == null || idList.length() == 0) return

                // 直接传 JsonArray
                val collectResp = ZmxyRpc.collectProgressBall(idList.toString().toJsonArray()) ?: return

                val collectJson = collectResp.toJsonObject()

                StructuredLog.other(
                    TAG, String.format(
                        "领取完成 → 本次加速进度: %d, 当前加速倍率: %.2f",
                        collectJson.optInt("collectedAccelerateProgress", -1),
                        collectJson.optDouble("currentAccelerateValue", -1.0)
                    )
                )
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG + "queryAndCollect err", e)
            }
        }

        /**
         * 会员积分收取
         * @param page 第几页
         * @param pageSize 每页数据条数
         */
        /**
         * 检查是否满足运行芝麻信用任务的条件
         * @return bool
         */
        private fun checkSesameCanRun(): Boolean {
            try {
                val s = ZmxyLegacyRpc.queryHome()
                val jo = s.toJsonObject()
                if (!ResChecker.checkRes(TAG, jo)) {
                    StructuredLog.error("$TAG.checkSesameCanRun.queryHome", "芝麻信用💳[首页响应失败]#$s")
                    return false
                }
                val entrance = jo.getJSONObject("entrance")
                if (!entrance.optBoolean("openApp")) {
                    StructuredLog.other("芝麻信用💳[未开通芝麻信用]")
                    return false
                }
                return true
            } catch (t: Throwable) {
                StructuredLog.printStackTrace("$TAG.checkSesameCanRun", t)
                return false
            }
        }

        init {
            ModelOrder.register(ModelDefinition(AntMember::class.java) { AntMember() })
        }
    }
}
