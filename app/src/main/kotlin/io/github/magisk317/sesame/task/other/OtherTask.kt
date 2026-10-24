package io.github.magisk317.sesame.task.other

import io.github.magisk317.sesame.entity.OtherEntityProvider.listCreditOptions
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.task.other.credit2101.Credit2101
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

class OtherTask : ModelTask() {
    override fun getName(): String {
        return "其他任务"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.OTHER
    }

    override fun getIcon(): String {
        return ""
    }

    /** @brief 信用2101 游戏开关 */
    private var credit2101: BooleanModelField? = null

    /** @brief 信用2101 事件列表 */
    private var creditOptions: SelectAndCountModelField? = null


    /** @brief 信用2101 自动开宝箱 */
    private var autoOpenChest: BooleanModelField? = null

    /** @brief 信用2101 仅完成1次的事件列表 */
    private var creditOnceOptions: SelectModelField? = null


    override fun getFields(): ModelFields {
        val fields = ModelFields()
        fields.addField(
            BooleanModelField(
                "credit2101", "信用2101", false
            ).apply { credit2101 = this })

        fields.addField(
            BooleanModelField(
                "AutoOpenChest", "信用2101 | 自动开宝箱", false
            ).apply { autoOpenChest = this })


        fields.addField(
            SelectAndCountModelField(
                "CreditOptions",
                "信用2101 | 事件类型",
                LinkedHashMap<String?, Int?>(),
                listCreditOptions(),
                "设置运行次数(-1为不限制)"
            ).also {
                creditOptions = it
            })









        return fields
    }

    override suspend fun runSuspend() {
        try {
            if (credit2101!!.value) {
                Credit2101.doCredit2101(autoOpenChest!!.value==true,creditOptions!!)
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }


    companion object {
        const val TAG = "OtherTask"
        fun run() {
            // TODO: 添加其他任务
        }
        init {
            ModelOrder.register(ModelDefinition(OtherTask::class.java) { OtherTask() })
        }
    }
}