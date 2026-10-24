package io.github.magisk317.sesame.task.antTravel

import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

/**
 * Travel-rights center snapshot task.
 *
 * Only query RPCs have been verified on-device. Sign-in, task jump, progress
 * submission and reward collection intentionally remain outside this task until
 * their page actions have been captured and replay-verified.
 */
class TravelRights : ModelTask() {
    private lateinit var queryTravelRights: BooleanModelField
    private lateinit var doTravelTasks: BooleanModelField
    private lateinit var claimTravelRights: BooleanModelField

    override fun getName() = "出行权益"
    override fun getGroup() = ModelGroup.OTHER
    override fun getIcon() = "AntMember.png"

    override fun getFields() = ModelFields().also { fields ->
        queryTravelRights = BooleanModelField("queryTravelRights", "查询出行权益状态", false)
        fields.addField(queryTravelRights)
        doTravelTasks = BooleanModelField("doTravelTasks", "出行权益 | 做任务", false)
        fields.addField(doTravelTasks)
        claimTravelRights = BooleanModelField("claimTravelRights", "出行权益 | 抢权益", false)
        fields.addField(claimTravelRights)
    }

    override suspend fun runSuspend() {
        if (!queryTravelRights.value && !doTravelTasks.value && !claimTravelRights.value) return
        try {
            val benefits = TravelRightsStateParser.parseBenefits(TravelRightsRpc.queryBenefits().toJsonObject())
            if (benefits == null) {
                StructuredLog.record(TAG, "出行权益状态查询失败")
                return
            }
            val taskResponse = TravelRightsRpc.queryNormalTasks().toJsonObject()
            val tasks = TravelRightsStateParser.parseTasksOrNull(taskResponse)
            if (doTravelTasks.value && tasks != null) {
                runPageTasks(tasks)
            }
            if (claimTravelRights.value) {
                StructuredLog.record(TAG, "出行权益抢权益暂未执行：5连抢 RPC 尚未完成真机捕获")
            }
            val mileageExperiment =
                TravelRightsStateParser.isSuccessful(TravelRightsRpc.queryMileageExperiment().toJsonObject())
            val recommendedBenefits =
                TravelRightsStateParser.isSuccessful(TravelRightsRpc.queryRecommendedBenefits().toJsonObject())

            StructuredLog.record(
                TAG,
                "出行权益: 里程币=${benefits.currentPoint}, 总里程=${benefits.totalPoint}, " +
                    "签到=${if (benefits.signOpen && !benefits.signed) "可签到" else "已签到或未开放"}, " +
                    "优惠分类=${benefits.couponTabCount}, 优惠=${benefits.couponProductCount}, " +
                    "任务=${tasks?.size ?: 0}, 任务查询=${if (tasks != null) "成功" else "失败"}, " +
                    "里程实验=${if (mileageExperiment) "成功" else "失败"}, " +
                    "推荐权益=${if (recommendedBenefits) "成功" else "失败"}",
            )
            tasks.orEmpty().forEach { task ->
                StructuredLog.record(
                    TAG,
                    "出行任务[${task.title}] 状态=${task.status}, 进度=${task.progress}%, " +
                        "推进=${task.advanceType}, 领取=${task.claimType}",
                )
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "出行权益查询异常", t)
        }
    }

    private suspend fun runPageTasks(initialTasks: List<TravelRightsTaskState>) {
        var tasks = initialTasks
        val attempted = linkedSetOf<String>()
        repeat(MAX_PAGE_TASKS) {
            val task = TravelRightsTaskPlanner.nextPageTask(tasks, attempted) ?: return
            attempted += task.taskCode
            if (!TravelRightsPageTaskExecutor.execute(task)) {
                StructuredLog.record(TAG, "出行任务[${task.title}]页面未打开，跳过")
                return@repeat
            }

            val refreshed = TravelRightsTaskPlanner.parseTaskResponse(
                TravelRightsRpc.queryNormalTasks().toJsonObject(),
            ) ?: run {
                StructuredLog.record(TAG, "出行任务[${task.title}]状态刷新失败，停止任务循环")
                return
            }
            if (TravelRightsTaskPlanner.progressed(task, refreshed)) {
                StructuredLog.record(TAG, "出行任务[${task.title}]完成，继续下一个")
            } else {
                StructuredLog.record(TAG, "出行任务[${task.title}]无进展，跳过并继续")
            }
            tasks = refreshed
        }
    }

    private companion object {
        init {
            ModelOrder.register(ModelDefinition(TravelRights::class.java) { TravelRights() })
        }
        const val TAG = "TravelRights"
        const val MAX_PAGE_TASKS = 5
    }
}
