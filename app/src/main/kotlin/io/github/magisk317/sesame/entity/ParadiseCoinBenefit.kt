package io.github.magisk317.sesame.entity

import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.ParadiseCoinBenefitIdMap

/** Selectable representation of one Paradise coin benefit. */
class ParadiseCoinBenefit(
    id: String,
    name: String,
) : MapperEntity() {
    init {
        this.id = id
        this.name = name
    }

    companion object {
        @JvmStatic
        fun getList(): List<ParadiseCoinBenefit> =
            IdMapManager.getInstance(ParadiseCoinBenefitIdMap::class.java)
                .map
                .map { (id, name) -> ParadiseCoinBenefit(id, name) }
    }
}
