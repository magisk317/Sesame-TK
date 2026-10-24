package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

/** Owns the farm animal visit, feed, and prize-claim workflow. */
internal class FarmAnimalVisitService {
    internal data class Plan(
        val farmId: String,
        val prizeKeys: List<String>,
    )

    fun plan(response: JsonObject): Plan? {
        return try {
            val configs = response.getJSONArray("talkConfigs")
            val nodes = response.getJSONArray("talkNodes")
            val farmId = configs.getJSONObject(0).getString("farmId")
            Plan(
                farmId = farmId,
                prizeKeys = buildList {
                    for (index in nodes.indices) {
                        val node = nodes.getJSONObject(index)
                        if (node.optString("type") == "FEED") {
                            add(node.getString("consistencyKey"))
                        }
                    }
                },
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun run() {
        try {
            val response = FarmSocialRpc.visitAnimal()
            if (response.isNullOrEmpty()) {
                StructuredLog.record(TAG, "visitAnimal: 收到空响应")
                return
            }
            val home = response.toJsonObject()
            if (!ResChecker.checkRes(TAG, home)) {
                StructuredLog.record(home.getString("resultDesc"), home.toString())
                return
            }
            val visitPlan = plan(home) ?: return
            val feedResponse = FarmSocialRpc.feedFriendAnimalVisit(visitPlan.farmId)
            if (feedResponse.isNullOrEmpty()) {
                StructuredLog.record(TAG, "feedFriendAnimalVisit: 收到空响应")
                return
            }
            val feed = feedResponse.toJsonObject()
            if (!ResChecker.checkRes(TAG, feed)) {
                StructuredLog.record(feed.getString("memo"), feed.toString())
                return
            }
            for (key in visitPlan.prizeKeys) {
                val prizeResponse = FarmSocialRpc.visitAnimalSendPrize(key)
                if (prizeResponse.isNullOrEmpty()) continue
                val prize = prizeResponse.toJsonObject()
                if (ResChecker.checkRes(TAG, prize)) {
                    StructuredLog.farm("小鸡到访💞[${prize.getString("prizeName")}]")
                } else {
                    StructuredLog.record(prize.getString("memo"), prize.toString())
                }
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "visitAnimal err:", error)
        }
    }

    private companion object {
        const val TAG = "FarmAnimalVisitService"
    }
}
