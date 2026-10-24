package io.github.magisk317.sesame.entity

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.maps.UserMap

/** Selectable representation of one configured Alipay user. */
class AlipayUser(
    id: String,
    name: String,
) : MapperEntity() {
    init {
        this.id = id
        this.name = name
    }

    companion object {
        @JvmStatic
        fun getList(): List<AlipayUser> = getList(Filter { true })

        @JvmStatic
        fun getList(filterFunc: Filter): List<AlipayUser> = buildList {
            UserMap.getUserMap().forEach { (id, user) ->
                try {
                    if (filterFunc.apply(user)) add(AlipayUser(id, user.fullName))
                } catch (t: Throwable) {
                    StructuredLog.printStackTrace(t)
                }
            }
        }
    }

    fun interface Filter {
        fun apply(user: UserEntity): Boolean
    }
}
