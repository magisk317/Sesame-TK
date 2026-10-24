package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonObject

/** Parses the response of an energy collection RPC without owning task state. */
class ForestEnergyResultService {
    data class Result(
        val resultCode: String,
        val resultDescription: String,
        val bubbleCount: Int = 0,
        val collectedEnergy: Int = 0,
        val canCollectAgainIds: List<Long> = emptyList(),
    ) {
        val isSuccess: Boolean
            get() = resultCode.equals("SUCCESS", ignoreCase = true)
    }

    fun parse(response: JsonObject): Result {
        val resultCode = response.getString("resultCode")
        val resultDescription = response.optString("resultDesc", "")
        if (!resultCode.equals("SUCCESS", ignoreCase = true)) {
            return Result(resultCode, resultDescription)
        }

        val bubbles = response.getJSONArray("bubbles")
        var collectedEnergy = 0
        val canCollectAgainIds = ArrayList<Long>()
        for (index in 0..<bubbles.length()) {
            val bubble = bubbles.getJSONObject(index)
            collectedEnergy += bubble.getInt("collectedEnergy")
            if (bubble.getBoolean("canBeRobbedAgain")) {
                canCollectAgainIds += bubble.getLong("id")
            }
        }
        return Result(
            resultCode = resultCode,
            resultDescription = resultDescription,
            bubbleCount = bubbles.length(),
            collectedEnergy = collectedEnergy,
            canCollectAgainIds = canCollectAgainIds,
        )
    }
}
