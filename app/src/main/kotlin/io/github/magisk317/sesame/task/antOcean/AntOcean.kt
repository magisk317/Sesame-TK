package io.github.magisk317.sesame.task.antOcean

import io.github.magisk317.sesame.entity.AlipayBeach
import io.github.magisk317.sesame.entity.AlipayUser
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.ChoiceModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.util.StructuredLog
import java.util.LinkedHashMap
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

/** Configuration and orchestration entry point for the ocean domain. */
class AntOcean : ModelTask() {
    private lateinit var dailyOceanTask: BooleanModelField
    private lateinit var cleanOcean: BooleanModelField
    private lateinit var cleanOceanType: ChoiceModelField
    private lateinit var cleanOceanList: SelectModelField
    private lateinit var exchangeProp: BooleanModelField
    private lateinit var usePropByType: BooleanModelField
    private lateinit var protectOceanList: SelectAndCountModelField
    private lateinit var protectType: ChoiceModelField
    private lateinit var pdlTask: BooleanModelField

    override fun getName() = "神奇海洋"
    override fun getGroup() = ModelGroup.FOREST
    override fun getIcon() = "AntOcean.png"

    override fun getFields() = ModelFields().apply {
        addField(BooleanModelField("dailyOceanTask", "海洋任务", false).also { dailyOceanTask = it })
        addField(BooleanModelField("cleanOcean", "清理 | 开启", false).also { cleanOcean = it })
        addField(ChoiceModelField("cleanOceanType", "清理 | 动作", OceanOptions.DONT_CLEAN, OceanOptions.cleanNames).also { cleanOceanType = it })
        addField(SelectModelField("cleanOceanList", "清理 | 好友列表", emptySet(), AlipayUser::getList).also { cleanOceanList = it })
        addField(BooleanModelField("exchangeProp", "神奇海洋 | 制作万能拼图", false).also { exchangeProp = it })
        addField(BooleanModelField("usePropByType", "神奇海洋 | 使用万能拼图", false).also { usePropByType = it })
        addField(ChoiceModelField("userprotectType", "保护 | 类型", OceanOptions.DONT_PROTECT, OceanOptions.protectNames).also { protectType = it })
        addField(SelectAndCountModelField("protectOceanList", "保护 | 海洋列表", LinkedHashMap(), AlipayBeach::getList).also { protectOceanList = it })
        addField(BooleanModelField("PDL_task", "潘多拉任务", false).also { pdlTask = it })
    }

    override fun runJava() {
        try {
            if (!OceanHomeService.isOpened(protectType.value)) return
            OceanHomeService.processHome(cleanOcean.value, cleanOceanType.value, cleanOceanList.value)
            if (dailyOceanTask.value) OceanTaskRunner.run()
            if (protectType.value != OceanOptions.DONT_PROTECT) OceanProtectionService.protect(protectOceanList.value)
            if (exchangeProp.value) OceanPropService.exchange()
            if (usePropByType.value) OceanPropService.useByType()
            if (pdlTask.value) OceanReplicaService.receiveAwards()
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "start.run err:", t)
        }
    }

    companion object {
        private const val TAG = "AntOcean"

        init {
            ModelOrder.register(ModelDefinition(AntOcean::class.java) { AntOcean() })
        }
    }
}
