package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray

/** Generates and collects the forest healthcare energy bubbles. */
object Healthcare {
    const val TAG = "Healthcare"

    @JvmStatic
    fun queryForestEnergy(scene: String) {
        try {
            var response = ForestHealthcareRpc.query(scene).toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return
            val result = response.getJSONObject("data").getJSONObject("response")
            var bubbles = result.getJSONArray("energyGeneratedList")
            if (bubbles.length() > 0) harvestForestEnergy(scene, bubbles)

            repeat(result.optInt("remainBubble")) {
                bubbles = produceForestEnergy(scene)
                if (bubbles.length() == 0 || !harvestForestEnergy(scene, bubbles)) return
                TimeUtil.sleepCompat(1000)
            }
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "queryForestEnergy err:")
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    private fun produceForestEnergy(scene: String): JsonArray {
        return try {
            var response = ForestHealthcareRpc.produce(scene).toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return buildJsonArray { }
            response = response.getJSONObject("data").getJSONObject("response")
            val bubbles = response.getJSONArray("energyGeneratedList")
            if (bubbles.length() > 0) {
                val title = if (scene == "FEEDS") "绿色医疗" else "电子小票"
                StructuredLog.forest("医疗健康🚑完成[$title]#产生[${response.getInt("cumulativeEnergy")}g能量]")
            }
            bubbles
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "produceForestEnergy err:")
            StructuredLog.printStackTrace(TAG, t)
            buildJsonArray { }
        }
    }

    private fun harvestForestEnergy(scene: String, bubbles: JsonArray): Boolean {
        return try {
            var response = ForestHealthcareRpc.harvest(scene, bubbles).toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return false
            response = response.getJSONObject("data").getJSONObject("response")
            val collected = response.getInt("collectedEnergy")
            if (collected > 0) {
                val title = if (scene == "FEEDS") "绿色医疗" else "电子小票"
                StructuredLog.forest("医疗健康🚑收取[$title]#获得[${collected}g能量]")
                true
            } else false
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "harvestForestEnergy err:")
            StructuredLog.printStackTrace(TAG, t)
            false
        }
    }
}
