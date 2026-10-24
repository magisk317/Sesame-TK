package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.serialization.json.JsonObject

/** Consumes the configured friend-energy multiplier cards. */
internal class ForestRobExpandCardService(
    private val findProp: (JsonObject?, String) -> JsonObject?,
    private val useProp: (JsonObject) -> Boolean,
    private val onActivated: () -> Unit,
) {
    fun use(initialBag: JsonObject?) {
        try {
            for (propType in PROP_TYPES) {
                val prop = findProp(initialBag, propType) ?: continue
                if (useProp(prop)) {
                    onActivated()
                }
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "useRobExpandCard err", error)
        }
    }

    private companion object {
        const val TAG = "ForestRobExpandCardService"
        val PROP_TYPES = arrayOf(
            "VITALITY_ROB_EXPAND_CARD_1.1_3DAYS",
            "SHAMO_ROB_EXPAND_CARD_1.5_1DAYS",
        )
    }
}
