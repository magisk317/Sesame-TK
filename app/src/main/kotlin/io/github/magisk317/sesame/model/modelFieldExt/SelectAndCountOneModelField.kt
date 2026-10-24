package io.github.magisk317.sesame.model.modelFieldExt

import io.github.magisk317.sesame.entity.KVMap
import io.github.magisk317.sesame.entity.MapperEntity
import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.model.SelectModelFieldFunc
import kotlin.jvm.JvmWildcard

class SelectAndCountOneModelField : ModelField<KVMap<String?, Int?>>, SelectModelFieldFunc {
    private val selectListFunc: SelectListFunc?
    private val expandValue: List<MapperEntity>?

    constructor(code: String, name: String, value: KVMap<String?, Int?>, expandValue: List<MapperEntity>?) :
        super(code, name, value) {
        this.expandValue = expandValue
        this.selectListFunc = null
    }

    constructor(code: String, name: String, value: KVMap<String?, Int?>, selectListFunc: SelectListFunc) :
        super(code, name, value) {
        this.expandValue = null
        this.selectListFunc = selectListFunc
    }

    override fun getType(): String = "SELECT_AND_COUNT_ONE"

    override fun getExpandValue(): List<MapperEntity>? = selectListFunc?.getList() ?: expandValue

    override fun clear() {
        value = defaultValue
    }

    override fun get(id: String?): Int? = value.takeIf { it.key == id }?.value ?: 0

    override fun add(id: String?, count: Int?) {
        value = KVMap(id, count)
    }

    override fun remove(id: String?) {
        if (value.key == id) value = defaultValue
    }

    override fun contains(id: String?): Boolean = value.key == id

    fun interface SelectListFunc {
        fun getList(): List<@JvmWildcard MapperEntity>
    }
}
