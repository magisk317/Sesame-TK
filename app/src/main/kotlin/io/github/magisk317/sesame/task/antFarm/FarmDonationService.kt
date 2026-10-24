package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getDouble
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap

/** Handles the farm donation activity and its daily state. */
class FarmDonationService(
    private val onHarvestScore: (Double) -> Unit,
) {
    fun donate(stopAfterOne: Boolean) {
        try {
            val response = FarmCoreRpc.listActivityInfo()
            var result = response.toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) {
                StructuredLog.record(result.getString("memo"))
                StructuredLog.record(response)
                return
            }
            val activities = result.getJSONArray("activityInfos")
            var donated = false
            var lastActivityId: String? = null
            for (index in activities.indices) {
                result = activities.getJSONObject(index)
                if (result.get("donationTotal") == result.get("donationLimit")) continue
                lastActivityId = result.getString("activityId")
                val name = result.optString("projectName", lastActivityId)
                if (performDonation(lastActivityId, name)) {
                    donated = true
                    if (stopAfterOne) break
                }
            }
            if (donated) Status.donationEgg(UserMap.currentUid)
            if (lastActivityId == null) StructuredLog.record(TAG, "今日已无可捐赠的活动")
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "donation err", error)
        }
    }

    private fun performDonation(activityId: String, activityName: String): Boolean {
        return try {
            val response = FarmCoreRpc.donation(activityId, 1)
            val result = response.toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) {
                StructuredLog.record(result.getString("memo"))
                StructuredLog.record(response)
                false
            } else {
                val donation = result.getJSONObject("donation")
                onHarvestScore(donation.getDouble("harvestBenevolenceScore"))
                StructuredLog.farm("捐赠活动❤️[$activityName]#累计捐赠${donation.getInt("donationTimesStat")}次")
                true
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "performDonation err", error)
            false
        }
    }

    private companion object {
        const val TAG = "FarmDonationService"
    }
}
