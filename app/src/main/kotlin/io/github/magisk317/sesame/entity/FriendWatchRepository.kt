package io.github.magisk317.sesame.entity

import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** File repository for friend-watch records. */
object FriendWatchRepository {
    private const val TAG = "FriendWatchRepository"
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val serializer = MapSerializer(String.serializer(), FriendWatchRecord.serializer())

    fun load(userId: String?): LinkedHashMap<String, FriendWatchRecord> {
        if (userId.isNullOrEmpty()) return linkedMapOf()
        return runCatching {
            val file = Files.getFriendWatchFile(userId) ?: return@runCatching linkedMapOf()
            val body = Files.readFromFile(file)
            if (body.isBlank()) linkedMapOf()
            else LinkedHashMap(json.decodeFromString(serializer, body))
        }.getOrElse {
            StructuredLog.printStackTrace(TAG, "Failed to load friend-watch records", it)
            linkedMapOf()
        }
    }

    fun save(userId: String?, records: Map<String, FriendWatchRecord>): Boolean {
        if (userId.isNullOrEmpty()) return false
        return runCatching {
            val file = Files.getFriendWatchFile(userId) ?: return@runCatching false
            Files.write2File(json.encodeToString(serializer, records), file)
        }.getOrElse {
            StructuredLog.printStackTrace(TAG, "Failed to save friend-watch records", it)
            false
        }
    }
}
