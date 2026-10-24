package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

/** Extracts guest animals that can be notified from a friend's farm response. */
internal class FarmStealingAnimalStateService {
    internal data class Target(
        val animalId: String,
        val farmId: String,
        val status: JsonObject,
    )

    fun parse(response: JsonObject, ownerFarmId: String?): List<Target> {
        return try {
            val subFarm = response.getJSONObject("farmVO").getJSONObject("subFarmVO")
            val friendFarmId = subFarm.getString("farmId")
            val animals = subFarm.getJSONArray("animals")
            buildList {
                for (index in animals.indices) {
                    val animal = animals.getJSONObject(index)
                    val masterFarmId = animal.getString("masterFarmId")
                    if (masterFarmId == friendFarmId || masterFarmId == ownerFarmId) continue
                    val status = animal.optJSONObject("animalStatusVO") ?: continue
                    add(
                        Target(
                            animalId = animal.getString("animalId"),
                            farmId = friendFarmId,
                            status = status,
                        ),
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun parse(rawResponse: String, ownerFarmId: String?): List<Target> =
        parse(rawResponse.toJsonObject(), ownerFarmId)
}
