package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.optString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Feeds configured friends' hungry animals without owning AntFarm state. */
internal class FarmFriendFeedService(
    private val targets: () -> Map<String?, Int?>,
    private val currentUserId: () -> String?,
    private val autoFeedEnabled: () -> Boolean,
    private val canFeedToday: (String, Int) -> Boolean,
    private val enterFarm: (String) -> JsonObject,
    private val parseTarget: (JsonObject) -> FarmFriendAnimalStateService.Target?,
    private val foodStock: () -> Int,
    private val pendingAwardCount: () -> Int,
    private val receiveAwards: suspend () -> Unit,
    private val feed: (String) -> JsonObject,
    private val setFoodStock: (Int) -> Unit,
    private val markFedToday: (String) -> Unit,
    private val hasReachedLimit: () -> Boolean,
    private val markReachedLimit: () -> Unit,
    private val displayName: (String) -> String?,
    private val warnSelfConflict: (Int) -> Unit,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun run() {
        try {
            for ((userIdValue, maxCountValue) in targets()) {
                val userId = userIdValue ?: continue
                val maxCount = maxCountValue ?: continue
                if (userId == currentUserId() && autoFeedEnabled()) {
                    warnSelfConflict(maxCount)
                    continue
                }
                if (!canFeedToday(userId, maxCount)) continue

                val entered = enterFarm(userId)
                pause(ENTER_DELAY_MS)
                if (!ResChecker.checkRes(TAG, entered)) {
                    StructuredLog.error(
                        TAG,
                        "进入用户 $userId[${displayName(userId).orEmpty()}] 的庄园失败> $entered",
                    )
                    continue
                }
                val target = parseTarget(entered) ?: continue
                if (!target.isHome || !target.isHungry) continue

                val name = displayName(userId).orEmpty()
                if (foodStock() < FEED_AMOUNT && pendingAwardCount() > 0) {
                    StructuredLog.record(TAG, "✨还有待领取的饲料")
                    receiveAwards()
                }
                if (foodStock() < FEED_AMOUNT) {
                    StructuredLog.record(TAG, "😞喂鸡[$name]饲料不足")
                    continue
                }
                if (hasReachedLimit()) return

                val result = feed(target.farmId)
                if (ResChecker.checkRes(TAG, result)) {
                    setFoodStock(result.getInt("foodStock"))
                    StructuredLog.farm("帮喂好友🥣[$name]的小鸡[${FEED_AMOUNT}g]#剩余${foodStock()}g")
                    markFedToday(target.farmId)
                } else {
                    StructuredLog.error(TAG, "😞喂[$name]的鸡失败$result")
                    markReachedLimit()
                    return
                }
            }
        } catch (error: CancellationException) {
            StructuredLog.record(TAG, "feedFriend 协程被取消")
            throw error
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "feedFriendAnimal err:", error)
        }
    }

    internal companion object {
        private const val TAG = "FarmFriendFeedService"
        const val FEED_AMOUNT = 180
        const val ENTER_DELAY_MS = 3_000L
    }
}
