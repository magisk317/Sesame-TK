package io.github.magisk317.sesame.entity

import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.ReserveaMap

/** Selectable representation of one reserve area. */
class ReserveEntity(
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
        fun getList(): List<ReserveEntity> =
            IdMapManager.getInstance(ReserveaMap::class.java).map
                .filterKeys { it !in removedIds }
                .map { (id, name) -> ReserveEntity(id, name) }

        @JvmStatic
        @Synchronized
        fun remove(id: String) {
            removedIds += id
        }
    }
}
