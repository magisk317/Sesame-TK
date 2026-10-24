package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonObject

/** Parses active props from the forest home response. */
class ForestActivePropService(
    private val isTeam: (JsonObject) -> Boolean,
    private val onActiveProp: (String, Long) -> Unit,
    private val onRobExpandEnergy: (JsonObject, String) -> Unit,
) {
    fun update(home: JsonObject) {
        try {
            val props = if (isTeam(home)) {
                home.optJSONObject("teamHomeResult")
                    ?.optJSONObject("mainMember")
                    ?.optJSONArray("usingUserProps") ?: jsonArray()
            } else {
                home.optJSONArray("usingUserPropsNew") ?: jsonArray()
            }
            for (index in props.indices) {
                val prop = props.optJSONObject(index) ?: continue
                val group = prop.getString("propGroup")
                val name = prop.optString("propName")
                val endTime = prop.getLong("endTime")
                when (group) {
                    "doubleClick", "stealthCard", "shield", "energyBombCard", "robExpandCard" -> {
                        onActiveProp(group, endTime)
                        StructuredLog.record(
                            TAG,
                            "$name 剩余时间⏰：${TimeFormatter.formatTimeDifference(endTime - System.currentTimeMillis())}"
                        )
                        if (group == "robExpandCard" && prop.optString("extInfo").isNotEmpty()) {
                            onRobExpandEnergy(prop, name)
                        }
                    }
                    else -> StructuredLog.record(TAG, "跳过非目标道具:$prop")
                }
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "update active props err", error)
        }
    }

    private companion object {
        const val TAG = "ForestActivePropService"
    }
}
