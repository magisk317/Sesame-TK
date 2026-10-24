package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.CoroutineUtils
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject

/** Forest market actions backed by [ForestGreenLifeRpc]. */
object GreenLife {
    const val TAG = "GreenLife"

    @JvmStatic
    fun ForestMarket(sourceType: String) {
        try {
            var response = ForestGreenLifeRpc.consult(sourceType).toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) {
                StructuredLog.record(TAG, response.optJSONObject("data")?.optString("resultCode").orEmpty())
                CoroutineUtils.sleepCompat(300)
                return
            }
            val data = response.getJSONObject("data")
            if (!data.optBoolean("canSendEnergy", false)) return

            CoroutineUtils.sleepCompat(1000)
            response = ForestGreenLifeRpc.send(sourceType).toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return
            val result = response.getJSONObject("data")
            if (result.optBoolean("canSendEnergy", false)) {
                StructuredLog.forest("集市逛街🛍[获得:能量${result.getInt("receivedEnergyAmount")}g]")
            }
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "sendEnergyByAction err:")
            StructuredLog.printStackTrace(TAG, t)
        }
    }
}
