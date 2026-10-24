package io.github.magisk317.sesame.entity

import io.github.magisk317.sesame.util.maps.BeachMap
import io.github.magisk317.sesame.util.maps.IdMapManager

/** Selectable representation of one ocean beach. */
class AlipayBeach(
    id: String,
    name: String,
) : MapperEntity() {
    init {
        this.id = id
        this.name = name
    }

    companion object {
        private val removedIds = linkedSetOf<String>()

        @JvmStatic
        @Synchronized
        fun getList(): List<AlipayBeach> =
            IdMapManager.getInstance(BeachMap::class.java).map
                .filterKeys { it !in removedIds }
                .map { (id, name) -> AlipayBeach(id, name) }

        @JvmStatic
        @Synchronized
        fun remove(id: String) {
            removedIds += id
        }
    }
}
