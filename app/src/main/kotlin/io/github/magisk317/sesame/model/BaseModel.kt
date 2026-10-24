package io.github.magisk317.sesame.model

import io.github.magisk317.sesame.BuildConfig
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.ChoiceModelField
import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField
import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField.MultiplyIntegerModelField
import io.github.magisk317.sesame.model.modelFieldExt.ListModelField.ListJoinCommaToStringModelField
import io.github.magisk317.sesame.model.modelFieldExt.StringModelField
import io.github.magisk317.sesame.util.ListUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.maps.BeachMap
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

/**
 * 基础配置模块
 */
class BaseModel : Model() {
    override fun getName(): String {
        return "基础"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.BASE
    }

    override fun getIcon(): String {
        return "BaseModel.png"
    }

    override fun getEnableFieldName(): String {
        return "启用模块"
    }

    override fun getFields(): ModelFields {
        val modelFields = ModelFields()
        modelFields.addField(stayAwake) //是否保持唤醒状态
        modelFields.addField(manualTriggerAutoSchedule) //手动触发是否自动安排下次执行
        modelFields.addField(checkInterval) //执行间隔时间
        modelFields.addField(taskExecutionRounds) //轮数
        modelFields.addField(modelSleepTime) //模块休眠时间范围
        modelFields.addField(execAtTimeList) //定时执行的时间点列表
        modelFields.addField(wakenAtTimeList) //定时唤醒的时间点列表
        modelFields.addField(energyTime) //能量收集的时间范围
        modelFields.addField(timedTaskModel) //定时任务模式选择
        modelFields.addField(timeoutRestart) //超时是否重启
        modelFields.addField(waitWhenException) //异常发生时的等待时间
        modelFields.addField(errNotify) //异常通知开关
        modelFields.addField(setMaxErrorCount) //异常次数阈值
        modelFields.addField(newRpc) //是否启用新接口

        if (BuildConfig.DEBUG) {
            modelFields.addField(debugMode) //是否开启抓包调试模式
            modelFields.addField(lite2GameCapture) //独立抓包支付宝 lite2 游戏进程
            modelFields.addField(sendHookData) //启用Hook数据转发
            modelFields.addField(sendHookDataUrl) //Hook数据转发地址
        }

        modelFields.addField(batteryPerm) //是否申请目标应用的后台运行权限
        modelFields.addField(recordLog) //是否记录record日志
        modelFields.addField(runtimeLog) //是否记录runtime日志
        modelFields.addField(showToast) //是否显示气泡提示
        modelFields.addField(enableOnGoing) //是否开启状态栏禁删
        modelFields.addField(languageSimplifiedChinese) //是否只显示中文并设置时区
        modelFields.addField(toastOffsetY) //气泡提示的纵向偏移量
        modelFields.addField(toastPerfix)//气泡提示的前缀
        return modelFields
    }

    interface TimedTaskModel {
        companion object {
            const val SYSTEM: Int = 0
            const val PROGRAM: Int = 1
            val nickNames: Array<String?> = arrayOf<String?>("🤖系统计时", "📦程序计时")
        }
    }

    companion object {
        private const val TAG = "BaseModel"

        /**
         * 是否保持唤醒状态
         */
        val stayAwake: BooleanModelField = BooleanModelField("stayAwake", "保持唤醒", true)

        /**
         * //手动触发是否自动安排下次执行
         */
        val manualTriggerAutoSchedule: BooleanModelField = BooleanModelField("manualTriggerAutoSchedule", "手动触发目标应用运行", false) //一般人不开这个

        /**
         * 执行间隔时间（分钟）
         */
        val checkInterval: MultiplyIntegerModelField = MultiplyIntegerModelField("checkInterval", "执行间隔(分钟)", 50, 1, 12 * 60, 60000) //此处调整至30分钟执行一次，可能会比平常耗电一点。。

        /**
         * 任务执行轮数配置
         */
        val taskExecutionRounds: IntegerModelField = IntegerModelField("taskExecutionRounds", "任务执行轮数", 1, 1, 99) //1轮就好，没必要2轮

        /**
         * 定时执行的时间点列表
         */
        val execAtTimeList: ListJoinCommaToStringModelField = ListJoinCommaToStringModelField(
            "execAtTimeList", "定时执行(关闭:-1)", ListUtil.newArrayList<String?>(
                "0010", "0030", "0100", "0700", "0730", "1200", "1230", "1700", "1730", "2000", "2030", "2359"
            )
        )

        /**
         * 定时唤醒的时间点列表
         */
        val wakenAtTimeList: ListJoinCommaToStringModelField = ListJoinCommaToStringModelField(
            "wakenAtTimeList", "定时唤醒(关闭:-1)", ListUtil.newArrayList<String?>(
                "0010", "0030", "0100", "0650", "2350" // 添加多个0点后的时间点
            )
        )

        /**
         * 能量收集的时间范围
         */
        val energyTime: ListJoinCommaToStringModelField = ListJoinCommaToStringModelField("energyTime", "只收能量时间(范围|关闭:-1)", ListUtil.newArrayList<String?>("0700-0730"))

        /**
         * 模块休眠时间范围
         */
        val modelSleepTime: ListJoinCommaToStringModelField =
            ListJoinCommaToStringModelField("modelSleepTime", "模块休眠时间(范围|关闭:-1)", ListUtil.newArrayList<String?>("0200-0201"))

        /**
         * 定时任务模式选择
         */
        val timedTaskModel: ChoiceModelField = ChoiceModelField("timedTaskModel", "定时任务模式", TimedTaskModel.Companion.SYSTEM, TimedTaskModel.Companion.nickNames)

        /**
         * 超时是否重启
         */
        val timeoutRestart: BooleanModelField = BooleanModelField("timeoutRestart", "超时重启", true)

        /**
         * 异常发生时的等待时间（分钟）
         */
        val waitWhenException: MultiplyIntegerModelField = MultiplyIntegerModelField("waitWhenException", "异常等待时间(分钟)", 60, 0, 24 * 60, 60000)

        /**
         * 异常通知开关
         */
        val errNotify: BooleanModelField = BooleanModelField("errNotify", "开启异常通知", false)

        val setMaxErrorCount: IntegerModelField = IntegerModelField("setMaxErrorCount", "异常次数阈值", 8)

        /**
         * 是否启用新接口（最低支持版本 v10.3.96.8100）
         */
        val newRpc: BooleanModelField = BooleanModelField("newRpc", "使用新接口(最低支持v10.3.96.8100)", true)

        /**
         * 是否开启抓包调试模式
         */
        val debugMode: BooleanModelField = BooleanModelField("debugMode", "开启抓包", false)

        /**
         * 仅在支付宝独立 lite2 游戏进程中启用调试抓包，不启动完整任务逻辑。
         */
        val lite2GameCapture: BooleanModelField = BooleanModelField(
            "lite2GameCapture",
            "抓包 | com.eg.android.AlipayGphone:lite2",
            false,
        ).also {
            it.desc = "仅在 debugMode + newRpc 下生效；只安装轻量 Hook，不启动任务逻辑"
        }

        /**
         * 是否申请目标应用的后台运行权限
         */
        val batteryPerm: BooleanModelField = BooleanModelField("batteryPerm", "为目标应用申请后台运行权限", true)


        /**
         * 是否记录record日志
         */
        val recordLog: BooleanModelField = BooleanModelField("recordLog", "全部 | 记录record日志", true)

        /**
         * 是否记录runtime日志
         */
        val runtimeLog: BooleanModelField = BooleanModelField("runtimeLog", "全部 | 记录runtime日志", false)

        /**
         * 是否显示气泡提示
         */
        val showToast: BooleanModelField = BooleanModelField("showToast", "气泡提示", true)

        val toastPerfix: StringModelField = StringModelField("toastPerfix", "气泡前缀", null)

        /**
         * 气泡提示的纵向偏移量
         */
        val toastOffsetY: IntegerModelField = IntegerModelField("toastOffsetY", "气泡纵向偏移", 99)

        /**
         * 只显示中文并设置时区
         */
        val languageSimplifiedChinese: BooleanModelField = BooleanModelField("languageSimplifiedChinese", "只显示中文并设置时区", true)

        /**
         * 是否开启状态栏禁删
         */
        val enableOnGoing: BooleanModelField = BooleanModelField("enableOnGoing", "开启状态栏禁删", false)

        val sendHookData: BooleanModelField = BooleanModelField("sendHookData", "启用Hook数据转发", false)

        val sendHookDataUrl: StringModelField = StringModelField("sendHookDataUrl", "Hook数据转发地址", "http://127.0.0.1:9527/hook")

        /**
         * 清理数据，在模块销毁时调用，清空 Reserve 和 Beach 数据。
         */
        @JvmStatic
        fun destroyData() {
            try {
                StructuredLog.record(TAG, "🧹清理所有数据")
                IdMapManager.getInstance(BeachMap::class.java).clear()
                //            IdMapManager.getInstance(ReserveaMap.class).clear();
//            IdMapManager.getInstance(CooperateMap.class).clear();
//            IdMapManager.getInstance(MemberBenefitsMap.class).clear();
//            IdMapManager.getInstance(ParadiseCoinBenefitIdMap.class).clear();
//            IdMapManager.getInstance(VitalityRewardsMap.class).clear();
                //其他也可以清理清理
            } catch (e: Exception) {
                StructuredLog.printStackTrace(e)
            }
        }
        init {
            ModelOrder.register(ModelDefinition(BaseModel::class.java) { BaseModel() })
        }
    }
}
