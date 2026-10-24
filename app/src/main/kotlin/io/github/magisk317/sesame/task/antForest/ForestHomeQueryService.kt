package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.Average
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.util.optLong
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

/** Owns forest home-page queries and their response validation. */
internal class ForestHomeQueryService(
    private val updateSelfHome: (JsonObject) -> Unit,
) {
    private val offsetTime = Average(5)

    fun querySelf(): JsonObject? {
        var home: JsonObject? = null
        try {
            val start = System.currentTimeMillis()
            val response = ForestCoreRpc.queryHomePage()
            if (response.trim().isEmpty()) return null
            val parsed = response.toJsonObject()
            home = parsed
            if (!ResChecker.checkRes(TAG, parsed)) {
                StructuredLog.error(TAG, "查询自己主页失败: ${parsed.optString("resultDesc", "未知错误")}")
                return null
            }
            updateSelfHome(parsed)
            recordOffset(start, parsed)
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "查询自己主页异常", error)
        }
        return home
    }

    fun queryFriend(userId: String?, fromAct: String?): JsonObject? {
        if (userId == null) return null
        var home: JsonObject? = null
        try {
            val start = System.currentTimeMillis()
            val response = ForestCoreRpc.queryFriendHomePage(userId, fromAct)
            if (response.trim().isEmpty()) return null
            val parsed = response.toJsonObject()
            home = parsed
            if (!ResChecker.checkRes(TAG, parsed)) {
                ForestUtil.checkAndRecordFrequencyError(userId, parsed)
                return null
            }
            recordOffset(start, parsed)
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(
                TAG,
                "查询好友主页异常, userId: ${UserMap.getMaskName(userId)}",
                error,
            )
        }
        return home
    }

    private fun recordOffset(start: Long, home: JsonObject) {
        val end = System.currentTimeMillis()
        val serverTime = home.optLong("now", System.currentTimeMillis())
        offsetTime.nextInteger(((start + end) / 2 - serverTime).toInt())
    }

    private companion object {
        const val TAG = "ForestHomeQueryService"
    }
}
