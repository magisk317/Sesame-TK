package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** RPC gateway for the forest healthcare energy activity. */
object ForestHealthcareRpc {
    fun query(scene: String): String = request(
        jsonObject("activityCode" to "query_forest_energy", "activityId" to "2024052300762675", "version" to "2.0", "body" to jsonObject("scene" to scene)),
    )

    fun produce(scene: String): String = request(
        jsonObject("activityCode" to "produce_forest_energy", "activityId" to "2024052300762674", "version" to "2.0", "body" to jsonObject("scene" to scene, "uniqueId" to System.currentTimeMillis().toString())),
    )

    fun harvest(scene: String, bubbles: JsonArray): String = request(
        jsonObject("activityCode" to "harvest_forest_energy", "activityId" to "2024052300762676", "version" to "2.0", "body" to jsonObject("bubbles" to bubbles, "scene" to scene)),
    )

    private fun request(payload: JsonObject): String = RequestManager.requestString(
        "alipay.iblib.channel.data", jsonArray(payload).toString(),
    )

}
