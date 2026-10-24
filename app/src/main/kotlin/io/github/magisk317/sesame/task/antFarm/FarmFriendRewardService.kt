package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getDouble
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import java.util.Locale

/** Claims benevolence rewards for friends shown on the farm home response. */
internal class FarmFriendRewardService(
    private val currentScore: () -> Double,
    private val updateScore: (Double) -> Unit,
    private val displayName: (String?) -> String?,
) {
    internal fun reward(
        friends: Array<AntFarm.RewardFriend>?,
        productNumber: String?,
    ) {
        if (friends == null) return
        try {
            for (friend in friends) {
                val response = FarmCoreRpc.rewardFriend(
                    friend.consistencyKey,
                    friend.friendId,
                    productNumber,
                    friend.time,
                ).toJsonObject()
                if (ResChecker.checkRes(TAG, response)) {
                    val rewardCount = currentScore() - response.getDouble("farmProduct")
                    updateScore(currentScore() - rewardCount)
                    StructuredLog.farm(
                        String.format(
                            Locale.CHINA,
                            "打赏好友💰[%s]# 得%.2f颗爱心鸡蛋",
                            displayName(friend.friendId),
                            rewardCount,
                        ),
                    )
                } else {
                    StructuredLog.record(TAG, response.optString("memo"))
                    StructuredLog.record(response.toString())
                }
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "rewardFriend err:", error)
        }
    }

    private companion object {
        const val TAG = "FarmFriendRewardService"
    }
}
