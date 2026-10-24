package io.github.magisk317.sesame.model

import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField

/** Base model contract. Registry state and lifecycle orchestration live in ModelRegistry. */
abstract class Model {
    private val enableField = BooleanModelField("enable", getEnableFieldName(), getName() == "基础")

    open fun getEnableFieldName(): String = "开启${getName()}"

    fun getEnableField(): BooleanModelField = enableField

    fun isEnable(): Boolean = enableField.value

    open fun getType(): ModelType = ModelType.NORMAL

    abstract fun getName(): String?
    abstract fun getGroup(): ModelGroup
    abstract fun getIcon(): String
    abstract fun getFields(): ModelFields?

    open fun prepare() = Unit
    open fun boot(classLoader: ClassLoader?) = Unit
    open fun destroy() = Unit

    companion object {
        @JvmStatic
        fun getModelConfigMap(): kotlin.collections.Map<String, ModelConfig> = ModelRegistry.getModelConfigMap()

        @JvmStatic
        fun getGroupModelConfig(modelGroup: ModelGroup?): kotlin.collections.Map<String, ModelConfig> =
            ModelRegistry.getGroupModelConfig(modelGroup)

        @JvmStatic
        fun <T : Model> getModel(modelClass: Class<T>): T? = ModelRegistry.getModel(modelClass)

        @JvmStatic
        @Synchronized
        fun initAllModel() {
            ModelRegistry.initAllModel()
        }

        @JvmStatic
        @Synchronized
        fun bootAllModel(classLoader: ClassLoader?) {
            ModelRegistry.bootAllModel(classLoader)
        }

        @JvmStatic
        @Synchronized
        fun destroyAllModel() {
            ModelRegistry.destroyAllModel()
        }

        @JvmStatic
        fun getModelArray(): Array<Model> = ModelRegistry.getModelArray()
    }
}
