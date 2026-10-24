package io.github.magisk317.sesame.model

import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.util.StructuredLog
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/** Owns model instances, configuration indexes, and their lifecycle. */
object ModelRegistry {
    private const val TAG = "ModelRegistry"
    private val modelDefinitions: List<ModelDefinition> by lazy {
        ModelOrder.ensureRegistered()
        ModelOrder.all
    }
    private val modelsByClass = ConcurrentHashMap<Class<out Model>, Model>()
    private val bootedModelClasses = mutableSetOf<Class<out Model>>()
    private val configsByCode = LinkedHashMap<String, ModelConfig>()
    private val configsByGroup = LinkedHashMap<ModelGroup, LinkedHashMap<String, ModelConfig>>()
    private var orderedModels = arrayOfNulls<Model>(modelDefinitions.size)

    @JvmStatic
    @Synchronized
    fun getModelArray(): Array<Model> = orderedModels.filterNotNull().toTypedArray()

    @JvmStatic
    @Synchronized
    fun getModelConfigMap(): Map<String, ModelConfig> =
        Collections.unmodifiableMap(LinkedHashMap(configsByCode))

    @JvmStatic
    @Synchronized
    fun getGroupModelConfig(group: ModelGroup?): Map<String, ModelConfig> =
        group?.let { configsByGroup[it]?.let(::LinkedHashMap) }
            ?.let(Collections::unmodifiableMap)
            ?: emptyMap()

    @JvmStatic
    @Synchronized
    fun <T : Model> getModel(modelClass: Class<T>): T? =
        modelClass.cast(modelsByClass[modelClass])
            .also { if (it == null) StructuredLog.error(TAG, "Model ${modelClass.simpleName} not found") }

    @JvmStatic
    @Synchronized
    fun initAllModel() {
        destroyAllModel()
        val newOrdered = arrayOfNulls<Model>(modelDefinitions.size)
        modelDefinitions.forEachIndexed { index, definition ->
            runCatching {
                val model = definition.factory()
                val config = ModelConfig(model)
                newOrdered[index] = model
                modelsByClass[definition.type] = model
                configsByCode[config.code.orEmpty()] = config
                configsByGroup.getOrPut(config.group ?: ModelGroup.OTHER) {
                    LinkedHashMap()
                }[config.code.orEmpty()] = config
            }.onFailure { StructuredLog.printStackTrace(TAG, "Failed to initialize ${definition.type.name}", it) }
        }
        orderedModels = newOrdered
    }

    @JvmStatic
    @Synchronized
    fun bootAllModel(classLoader: ClassLoader?) {
        orderedModels.filterNotNull().forEach { model ->
            runCatching { model.prepare() }
                .onFailure { StructuredLog.printStackTrace(TAG, "Failed to prepare ${model.javaClass.name}", it) }
            if (model.isEnable() && model.javaClass !in bootedModelClasses) {
                runCatching {
                    model.boot(classLoader)
                    bootedModelClasses += model.javaClass
                }
                    .onFailure { StructuredLog.printStackTrace(TAG, "Failed to boot ${model.javaClass.name}", it) }
            }
        }
    }

    /** Prepare and boot a model on demand without exposing its legacy static instance. */
    @JvmStatic
    @Synchronized
    fun <T : Model> prepareAndBoot(modelClass: Class<T>, classLoader: ClassLoader?): T? {
        val model = modelClass.cast(modelsByClass[modelClass]) ?: return null
        if (bootedModelClasses.add(modelClass)) {
            try {
                model.prepare()
                model.boot(classLoader)
            } catch (error: Throwable) {
                bootedModelClasses.remove(modelClass)
                throw error
            }
        }
        return model
    }

    @JvmStatic
    @Synchronized
    fun destroyAllModel() {
        orderedModels.filterNotNull().forEach { model ->
            runCatching {
                if (model is ModelTask) model.stopTask()
                model.destroy()
            }.onFailure { StructuredLog.printStackTrace(TAG, "Failed to destroy ${model.javaClass.name}", it) }
        }
        orderedModels = arrayOfNulls(modelDefinitions.size)
        modelsByClass.clear()
        bootedModelClasses.clear()
        configsByCode.clear()
        configsByGroup.clear()
    }
}
