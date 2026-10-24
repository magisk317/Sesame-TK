package io.github.magisk317.sesame.model

import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import java.io.Serializable

/** Runtime metadata and fields belonging to one task model. */
class ModelConfig : Serializable {
    var code: String? = null
    var name: String? = null
    var group: ModelGroup? = null
    var icon: String? = null
    val fields: ModelFields = ModelFields()

    constructor()

    constructor(model: Model) {
        code = model.javaClass.simpleName
        name = model.getName()
        group = model.getGroup()
        icon = model.getIcon()

        val enableField: BooleanModelField = model.getEnableField()
        fields[enableField.code] = enableField
        model.getFields()?.forEach { (_, modelField) ->
            fields[modelField.code] = modelField
        }
    }

    fun hasModelField(fieldCode: String?): Boolean = fields.containsKey(fieldCode)

    fun getModelField(fieldCode: String?): ModelField<*>? = fields[fieldCode]

    @Suppress("UNCHECKED_CAST")
    fun <T : ModelField<*>> getModelFieldExt(fieldCode: String?): T? = fields[fieldCode] as? T
}
