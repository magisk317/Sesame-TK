package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.task.antFarm.AntFarm.AnimalFeedStatus
import io.github.magisk317.sesame.task.antFarm.AntFarm.AnimalInteractStatus
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

/** Finds the friend's own animal and exposes only the state needed for feeding. */
internal class FarmFriendAnimalStateService {
    internal data class Target(
        val farmId: String,
        val isHome: Boolean,
        val isHungry: Boolean,
    )

    fun parse(response: JsonObject): Target? {
        return try {
            val subFarm = response.getJSONObject("farmVO").getJSONObject("subFarmVO")
            val farmId = subFarm.getString("farmId")
            val animals = subFarm.getJSONArray("animals")
            for (index in animals.indices) {
                val animal = animals.getJSONObject(index)
                if (animal.getString("masterFarmId") != farmId) continue
                val status = animal.optJSONObject("animalStatusVO") ?: return null
                return Target(
                    farmId = farmId,
                    isHome = status.getString("animalInteractStatus") == AnimalInteractStatus.HOME.name,
                    isHungry = status.getString("animalFeedStatus") == AnimalFeedStatus.HUNGRY.name,
                )
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    fun parse(rawResponse: String): Target? = parse(rawResponse.toJsonObject())
}
