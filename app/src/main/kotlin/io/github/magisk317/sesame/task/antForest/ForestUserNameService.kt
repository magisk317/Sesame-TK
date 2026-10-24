package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/** Resolves and caches display names used by forest collection logs. */
class ForestUserNameService {
    private val cache = ConcurrentHashMap<String, String>()

    fun clear() = cache.clear()

    fun get(userId: String?, home: JsonObject? = null, fromTag: String? = null): String? {
        if (userId.isNullOrEmpty()) return null
        cache[userId]?.takeIf { it != userId }?.let { return it }
        val name = resolve(userId, home, fromTag).orEmpty().ifEmpty { userId }
        cache[userId] = name
        return name
    }

    private fun resolve(userId: String, home: JsonObject?, fromTag: String?): String? {
        if (fromTag == "pk" && home != null) {
            return home.optJSONObject("userEnergy")?.optString("displayName")?.let { "PK榜好友|$it" }
        }
        var name = UserMap.getMaskName(userId)
        if ((name == null || name == userId) && home != null) {
            name = home.optJSONObject("userEnergy")?.optString("displayName")?.ifEmpty { name }
        }
        return name
    }
}
