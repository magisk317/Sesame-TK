package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap

/** Owns the farm sleep/wake query and action RPCs. */
internal class FarmSleepService {
    fun sleepNow() {
        try {
            var response = FarmDrawRpc.queryLoveCabin(UserMap.currentUid).toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return
            val info = response.getJSONObject("sleepNotifyInfo")
            if (!info.optBoolean("canSleep", false)) {
                StructuredLog.farm("小鸡无需睡觉🛌")
                return
            }
            val groupId = response.optString("groupId")
            response = (if (groupId.isNotEmpty()) FarmCoreRpc.sleep(groupId) else FarmCoreRpc.sleep())
                .toJsonObject()
            if (ResChecker.checkRes(TAG, response)) {
                StructuredLog.farm(if (groupId.isNotEmpty()) "家庭🏡小鸡睡觉🛌" else "小鸡睡觉🛌")
                Status.animalSleep()
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "animalSleepNow err:", error)
        }
    }

    fun wakeUpNow() {
        try {
            var response = FarmDrawRpc.queryLoveCabin(UserMap.currentUid).toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return
            val info = response.getJSONObject("sleepNotifyInfo")
            if (info.optBoolean("canSleep", true)) {
                StructuredLog.farm("小鸡无需起床 🛏")
                return
            }
            response = FarmCoreRpc.wakeUp().toJsonObject()
            if (ResChecker.checkRes(TAG, response)) StructuredLog.farm("小鸡起床 🛏")
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "animalWakeUpNow err:", error)
        }
    }

    private companion object {
        const val TAG = "FarmSleepService"
    }
}
