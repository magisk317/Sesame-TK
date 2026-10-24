package io.github.magisk317.sesame.model

/**
 * 任务模型注册中心
 *
 * 采用自注册模式：每个 ModelTask 在 companion init 中调用 [register] 注册自身。
 * [ensureRegistered] 通过引用所有 task 类触发类加载，完成自动注册。
 *
 * 新增 task 时只需：
 * 1. 在 task 类中添加 `companion object { init { ModelOrder.register(...) } }`
 * 2. 将类引用添加到 [ensureRegistered] 的触发列表中
 */
data class ModelDefinition(
    val type: Class<out Model>,
    val factory: () -> Model,
)

object ModelOrder {

    private val _definitions = mutableListOf<ModelDefinition>()

    /** 只读视图，供 ModelRegistry 使用 */
    val all: List<ModelDefinition>
        get() = _definitions.toList()

    /**
     * 注册一个模型定义。每个 task 的 companion init 调用此方法。
     * @throws IllegalArgumentException 如果重复注册同一类型
     */
    @JvmStatic
    fun register(definition: ModelDefinition) {
        require(_definitions.none { it.type == definition.type }) {
            "Duplicate registration: ${definition.type.name}"
        }
        _definitions.add(definition)
    }

    /**
     * 触发所有 task 类的加载以完成自注册。
     * 此列表是所有 task 类的引用 —— 添加新 task 时需在此处追加。
     */
    @JvmStatic
    fun ensureRegistered() {
        // 引用所有含 companion init 注册的 task 类，触发类加载
        // 顺序即 UI 显示顺序
        triggerLoad(
            "io.github.magisk317.sesame.model.BaseModel",
            "io.github.magisk317.sesame.task.antForest.AntForest",
            "io.github.magisk317.sesame.task.antFarm.AntFarm",
            "io.github.magisk317.sesame.task.antOcean.AntOcean",
            "io.github.magisk317.sesame.task.antFish.AntAiFish",
            "io.github.magisk317.sesame.task.antFish.Fishpond",
            "io.github.magisk317.sesame.task.antOrchard.AntOrchard",
            "io.github.magisk317.sesame.task.antOrchard.AntGoldenBean",
            "io.github.magisk317.sesame.task.antStall.AntStall",
            "io.github.magisk317.sesame.task.antDodo.AntDodo",
            "io.github.magisk317.sesame.task.antCooperate.AntCooperate",
            "io.github.magisk317.sesame.task.antSports.AntSports",
            "io.github.magisk317.sesame.task.antMember.AntMember",
            "io.github.magisk317.sesame.task.antTravel.TravelRights",
            "io.github.magisk317.sesame.task.EcoProtection.EcoProtection",
            "io.github.magisk317.sesame.task.greenFinance.GreenFinance",
            "io.github.magisk317.sesame.task.reserve.Reserve",
            "io.github.magisk317.sesame.task.other.OtherTask",
            "io.github.magisk317.sesame.task.customTasks.ManualTaskModel",
            "io.github.magisk317.sesame.task.AnswerAI.AnswerAI",
            "io.github.magisk317.sesame.task.youthPrivilege.YouthPrivilegeModelTask",
        )
    }

    /** 通过类名触发类加载，companion init 自动注册 */
    private fun triggerLoad(vararg classNames: String) {
        classNames.forEach { name ->
            runCatching { Class.forName(name) }
                .onFailure { it.printStackTrace() }
        }
    }
}
