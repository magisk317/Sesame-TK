package io.github.magisk317.sesame.entity

import io.github.magisk317.sesame.util.HanziToPinyin

abstract class MapperEntity : Comparable<MapperEntity> {
    @JvmField
    var name: String = ""
    @JvmField
    var id: String = ""

    private var pinyinCache: List<String>? = null

    fun getPinyin(): List<String> = pinyinCache ?: run {
        HanziToPinyin.getInstance()
            .get(name)
            .map { it.target }
            .also { pinyinCache = it }
    }

    override fun compareTo(other: MapperEntity): Int {
        val list1 = getPinyin()
        val list2 = other.getPinyin()

        return list1.zip(list2) { a, b -> a.compareTo(b) }
            .firstOrNull { it != 0 }
            ?: (list1.size - list2.size)
    }
}
