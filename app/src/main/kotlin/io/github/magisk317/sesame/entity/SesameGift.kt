package io.github.magisk317.sesame.entity

import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.SesameGiftMap

class SesameGift(i: String, n: String) : MapperEntity() {
    init {
        id = i; name = n
    }

    companion object {
        fun getList(): List<SesameGift> {
            return IdMapManager.getInstance(SesameGiftMap::class.java).map
                .map { (key, value) -> SesameGift(key, value) }
        }
    }
}