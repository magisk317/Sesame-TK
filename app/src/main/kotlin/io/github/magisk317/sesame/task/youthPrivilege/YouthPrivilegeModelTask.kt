package io.github.magisk317.sesame.task.youthPrivilege

import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

/**
 * 青春豆任务模型
 *
 * 青春特权升级后的 H5 页面任务，提供签到、兑换、任务查询功能。
 */
class YouthPrivilegeModelTask : ModelTask() {
    companion object {
        init {
            ModelOrder.register(ModelDefinition(YouthPrivilegeModelTask::class.java) { YouthPrivilegeModelTask() })
        }
    }

    private val service = YouthPrivilegeService()

    override fun getName(): String = "青春豆"

    override fun getGroup(): ModelGroup = ModelGroup.OTHER

    override fun getIcon(): String = "YouthPrivilege.png"

    override fun getFields(): ModelFields = ModelFields()

    override fun runJava() {
        // TODO: 实现青春豆自动签到/兑换逻辑
    }
}
