package io.github.magisk317.sesame.util

object ListUtil {
    @JvmStatic
    fun <T> newArrayList(vararg objects: T): List<T> = objects.toList()
}
