package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.entity.MapperEntity
import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.model.SelectModelFieldFunc
import kotlin.jvm.JvmWildcard

class SelectAndCountModelField : ModelField<MutableMap<String?, Int?>>, SelectModelFieldFunc {
    private val selectListFunc: SelectListFunc?
    private val expandValue: List<MapperEntity>?

    constructor(code: String, name: String, value: Map<String?, Int?>, expandValue: List<MapperEntity>?) :
        super(code, name, value.toMutableMap()) {
        this.expandValue = expandValue
        this.selectListFunc = null
    }

    constructor(code: String, name: String, value: Map<String?, Int?>, selectListFunc: SelectListFunc) :
        super(code, name, value.toMutableMap()) {
        this.expandValue = null
        this.selectListFunc = selectListFunc
    }

    constructor(code: String, name: String, value: Map<String?, Int?>, expandValue: List<MapperEntity>?, desc: String?) :
        super(code, name, value.toMutableMap(), desc) {
        this.expandValue = expandValue
        this.selectListFunc = null
    }

    constructor(code: String, name: String, value: Map<String?, Int?>, selectListFunc: SelectListFunc, desc: String?) :
        super(code, name, value.toMutableMap(), desc) {
        this.expandValue = null
        this.selectListFunc = selectListFunc
    }

    override fun getType(): String = "SELECT_AND_COUNT"

    override fun getExpandValue(): List<MapperEntity>? = selectListFunc?.getList() ?: expandValue

    override fun clear() {
        value.clear()
    }

    override fun get(id: String?): Int? = value[id]

    override fun add(id: String?, count: Int?) {
        value[id] = count
    }

    override fun remove(id: String?) {
        value.remove(id)
    }

    override fun contains(id: String?): Boolean = value.containsKey(id)

    fun interface SelectListFunc {
        fun getList(): List<@JvmWildcard MapperEntity>
    }
}
