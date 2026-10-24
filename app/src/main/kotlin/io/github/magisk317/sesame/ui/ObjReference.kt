package io.github.magisk317.sesame.ui

/** Non-thread-safe nullable reference wrapper used by the legacy UI flow. */
class ObjReference<T>(
    @JvmField var obj: T? = null,
) {
    fun has(): Boolean = obj != null

    fun get(): T? = obj

    fun set(value: T?): Boolean {
        if (obj === value) return true
        if (obj != null) return false
        obj = value
        return true
    }

    fun setForce(value: T?) {
        obj = value
    }

    fun del() {
        obj = null
    }

    fun delIfEquals(value: T?) {
        if (obj === value) obj = null
    }
}
