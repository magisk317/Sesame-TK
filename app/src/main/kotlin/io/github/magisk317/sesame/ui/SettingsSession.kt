package io.github.magisk317.sesame.ui

import io.github.magisk317.sesame.data.Config
import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.util.maps.BeachMap
import io.github.magisk317.sesame.util.maps.CooperateMap
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.MemberBenefitsMap
import io.github.magisk317.sesame.util.maps.ParadiseCoinBenefitIdMap
import io.github.magisk317.sesame.util.maps.ReserveaMap
import io.github.magisk317.sesame.util.maps.SesameGiftMap
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.util.maps.VitalityRewardsMap

/** Shared, UI-independent initialization for all settings surfaces. */
object SettingsSession {
    fun load(userId: String?) {
        Model.initAllModel()
        UserMap.setCurrentUserId(userId)
        UserMap.load(userId)
        IdMapManager.getInstance(CooperateMap::class.java).load(userId)
        IdMapManager.getInstance(VitalityRewardsMap::class.java).load(userId)
        IdMapManager.getInstance(MemberBenefitsMap::class.java).load(userId)
        IdMapManager.getInstance(SesameGiftMap::class.java).load(userId)
        IdMapManager.getInstance(ParadiseCoinBenefitIdMap::class.java).load(userId)
        IdMapManager.getInstance(ReserveaMap::class.java).load()
        IdMapManager.getInstance(BeachMap::class.java).load()
        Config.load(userId)
    }

    fun saveMaps(userId: String?) {
        if (userId.isNullOrEmpty()) return
        UserMap.save(userId)
        IdMapManager.getInstance(CooperateMap::class.java).save(userId)
    }
}
