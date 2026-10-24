package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.entity.MapperEntity
import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.model.SelectModelFieldFunc
import kotlin.jvm.JvmWildcard

class SelectModelField : ModelField<MutableSet<String?>>, SelectModelFieldFunc {
    private val selectListFunc: SelectListFunc?
    private val expandValue: List<MapperEntity>?

    constructor(code: String, name: String, value: Set<String?>, expandValue: List<MapperEntity>?) :
        super(code, name, value.toMutableSet()) {
        this.expandValue = expandValue
        this.selectListFunc = null
    }

    constructor(code: String, name: String, value: Set<String?>, selectListFunc: SelectListFunc) :
        super(code, name, value.toMutableSet()) {
        this.expandValue = null
        this.selectListFunc = selectListFunc
    }

    constructor(code: String, name: String, value: Set<String?>, expandValue: List<MapperEntity>?, desc: String?) :
        super(code, name, value.toMutableSet(), desc) {
        this.expandValue = expandValue
        this.selectListFunc = null
    }

    constructor(code: String, name: String, value: Set<String?>, selectListFunc: SelectListFunc, desc: String?) :
        super(code, name, value.toMutableSet(), desc) {
        this.expandValue = null
        this.selectListFunc = selectListFunc
    }

    override fun getType(): String = "SELECT"

    override fun getExpandValue(): List<MapperEntity>? = selectListFunc?.getList() ?: expandValue

    override fun clear() {
        value.clear()
    }

    override fun get(id: String?): Int = 0

    override fun add(id: String?, count: Int?) {
        value.add(id)
    }

    override fun remove(id: String?) {
        value.remove(id)
    }

    override fun contains(id: String?): Boolean = value.contains(id)

    fun interface SelectListFunc {
        fun getList(): List<@JvmWildcard MapperEntity>
    }
}
