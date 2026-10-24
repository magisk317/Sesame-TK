package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Runs the server-side "find energy" polling flow in either normal or fast mode. */
internal class ForestTakeLookService(
    private val currentUserId: () -> String?,
    private val takeLook: (JsonObject) -> JsonObject,
    private val queryFriendHome: (String) -> JsonObject?,
    private val collectEnergy: (String, JsonObject, String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = GlobalThreadPools::sleepCompat,
) {
    private var cooldownUntil = 0L

    fun run(fastMode: Boolean) {
        val currentTime = now()
        if (currentTime < cooldownUntil) {
            StructuredLog.record(TAG, "找能量冷却中，等待 ${TimeFormatter.formatDuration(cooldownUntil - currentTime)}")
            return
        }

        var foundCount = 0
        var shouldCooldown = false
        val skippedUsers = linkedMapOf<String, String>()
        val modeName = if (fastMode) "无视黑名单与道具" else "服务器自动轮询"

        try {
            while (true) {
                val result = try {
                    takeLook(buildSkipUsers(skippedUsers))
                } catch (error: Exception) {
                    StructuredLog.printStackTrace(TAG, "找能量接口异常", error)
                    shouldCooldown = true
                    break
                }
                if (!ResChecker.checkRes("$TAG 接口业务失败:", result)) break

                if (result.optString("actionType") != FRIEND_ACTION) {
                    StructuredLog.record(TAG, "森林🌿 找能量停止：服务端未返回 FRIEND")
                    break
                }
                val friendId = result.optString("friendId")
                if (friendId.isNullOrBlank()) {
                    StructuredLog.record(TAG, "森林🌿 找能量停止：FRIEND 响应未包含 friendId")
                    break
                }
                if (friendId == currentUserId()) {
                    StructuredLog.record(TAG, "服务端返回当前用户，加入跳过列表")
                    skippedUsers[friendId] = "self"
                    continue
                }

                val home = queryFriendHome(friendId)
                if (home != null) {
                    StructuredLog.record(
                        TAG,
                        "找能量候选[$friendId]主页查询成功，交给收取流程判断可收取球和保护状态",
                    )
                    // Every FRIEND response is handed to the common collection
                    // path. Protection and bubble validation remain centralized
                    // there, while this loop only follows the server protocol.
                    collectEnergy(friendId, home, "takeLook")
                    StructuredLog.record(TAG, "找能量候选[$friendId]收取流程已处理")
                    foundCount++
                } else {
                    StructuredLog.record(TAG, "找能量候选[$friendId]好友主页获取为空，加入跳过列表")
                }
                skippedUsers[friendId] = "takeLook"
                sleep(if (fastMode) FAST_SUCCESS_DELAY else NORMAL_SUCCESS_DELAY)
            }
        } catch (error: Exception) {
            StructuredLog.printStackTrace(TAG, "找能量流程异常", error)
        } finally {
            cooldownUntil = if (shouldCooldown) now() + COOLDOWN_MS else 0L
            StructuredLog.record(
                TAG,
                "森林🌿 找能量结果，本次${if (fastMode) "尝试" else "收取"}: $foundCount 个",
            )
        }
    }

    private fun buildSkipUsers(users: Map<String, String>): JsonObject = buildJsonObject {
        users.forEach { (userId, reason) -> put(userId, JsonPrimitive(reason)) }
    }

    internal companion object {
        private const val TAG = "ForestTakeLookService"
        private const val FRIEND_ACTION = "FRIEND"
        const val COOLDOWN_MS = 15 * 60 * 1000L
        const val FAST_SUCCESS_DELAY = 500L
        const val NORMAL_SUCCESS_DELAY = 1_200L
    }
}
