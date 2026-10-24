package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.model.ModelField

class ChoiceModelField : ModelField<Int> {
    private var choiceArray: Array<String?>? = null

    constructor(code: String, name: String, value: Int) : super(code, name, value)

    constructor(code: String, name: String, value: Int, choiceArray: Array<out String?>?) :
        super(code, name, value) {
        this.choiceArray = choiceArray?.map { it }?.toTypedArray()
    }

    constructor(code: String, name: String, value: Int, desc: String?) :
        super(code, name, value, desc)

    constructor(code: String, name: String, value: Int, choiceArray: Array<out String?>?, desc: String?) :
        super(code, name, value, desc) {
        this.choiceArray = choiceArray?.map { it }?.toTypedArray()
    }

    override fun getType(): String = "CHOICE"

    override fun getExpandKey(): Array<String?>? = choiceArray
}
