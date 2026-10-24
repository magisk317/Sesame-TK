package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.entity.MapperEntity
import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.model.SelectModelFieldFunc
import kotlin.jvm.JvmWildcard

class SelectOneModelField : ModelField<String?>, SelectModelFieldFunc {
    private val selectListFunc: SelectListFunc?
    private val expandValue: List<MapperEntity>?

    constructor(code: String, name: String, value: String?, expandValue: List<MapperEntity>?) :
        super(code, name, value) {
        this.expandValue = expandValue
        this.selectListFunc = null
    }

    constructor(code: String, name: String, value: String?, selectListFunc: SelectListFunc) :
        super(code, name, value) {
        this.expandValue = null
        this.selectListFunc = selectListFunc
    }

    override fun getType(): String = "SELECT_ONE"

    override fun getExpandValue(): List<MapperEntity>? = selectListFunc?.getList() ?: expandValue

    override fun clear() {
        value = defaultValue
    }

    override fun get(id: String?): Int = 0

    override fun add(id: String?, count: Int?) {
        value = id
    }

    override fun remove(id: String?) {
        if (value == id) value = defaultValue
    }

    override fun contains(id: String?): Boolean = value == id

    fun interface SelectListFunc {
        fun getList(): List<@JvmWildcard MapperEntity>
    }
}
