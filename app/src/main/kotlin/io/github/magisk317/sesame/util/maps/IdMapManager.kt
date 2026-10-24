package io.github.magisk317.sesame.util.maps

import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/** Thread-safe persistent string-to-string map used by selectable task data. */
abstract class IdMapManager {
    private val idMap = ConcurrentHashMap<String, String>()
    private val readOnlyIdMap: Map<String, String> = Collections.unmodifiableMap(idMap)

    protected abstract fun thisFileName(): String

    val map: Map<String, String>
        get() = readOnlyIdMap

    fun get(key: String): String? = idMap[key]

    @Synchronized
    fun add(key: String, value: String) {
        idMap[key] = value
    }

    @Synchronized
    fun remove(key: String) {
        idMap.remove(key)
    }

    /** Loads the map scoped to a user. An empty user never falls back to global data. */
    @Synchronized
    fun load(userId: String?) {
        if (userId.isNullOrEmpty()) {
            idMap.clear()
            StructuredLog.record(TAG, "Skip loading map for empty userId")
            return
        }
        loadFrom(Files.getTargetFileofUser(userId, thisFileName()))
    }

    /** Loads the shared map and migrates the old location when necessary. */
    @Synchronized
    fun load() {
        idMap.clear()
        val newFile = File(Files.CONFIG_DIR, thisFileName())
        val oldFile = File(Files.MAIN_DIR, thisFileName())

        try {
            when {
                newFile.exists() -> loadFrom(newFile)
                oldFile.exists() -> {
                    StructuredLog.record(TAG, "Old configuration file found, migrating to new path")
                    val body = Files.readFromFile(oldFile)
                    if (body.isNotEmpty() && Files.write2File(body, newFile)) {
                        oldFile.delete()
                        decodeInto(body)
                    }
                }
                else -> Files.write2File(encode(), newFile)
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "ID map initialization failed", e)
        }
    }

    @Synchronized
    fun save(userId: String?): Boolean {
        if (userId.isNullOrEmpty()) return false
        return saveTo(Files.getTargetFileofUser(userId, thisFileName()))
    }

    @Synchronized
    fun save(): Boolean = saveTo(Files.getTargetFileofDir(Files.CONFIG_DIR, thisFileName()))

    @Synchronized
    fun clear() {
        idMap.clear()
    }

    private fun loadFrom(file: File?) {
        if (file == null) return
        val body = Files.readFromFile(file)
        if (body.isNotEmpty()) decodeInto(body)
    }

    private fun decodeInto(body: String) {
        idMap.putAll(json.decodeFromString(mapSerializer, body))
    }

    private fun encode(): String = json.encodeToString(mapSerializer, idMap.toMap())

    private fun saveTo(file: File?): Boolean = runCatching {
        file != null && Files.write2File(encode(), file)
    }.getOrElse {
        StructuredLog.printStackTrace(it)
        false
    }

    companion object {
        private const val TAG = "IdMapManager"
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
            prettyPrint = true
        }
        private val mapSerializer = MapSerializer(String.serializer(), String.serializer())
        private val instances = ConcurrentHashMap<Class<out IdMapManager>, IdMapManager>()

        @JvmStatic
        @Synchronized
        fun <T : IdMapManager> getInstance(clazz: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return instances[clazz] as? T ?: run {
                val created = create(clazz)
                instances[clazz] = created
                created as T
            }
        }

        private fun create(clazz: Class<out IdMapManager>): IdMapManager = when (clazz) {
            CooperateMap::class.java -> CooperateMap()
            BeachMap::class.java -> BeachMap()
            MemberBenefitsMap::class.java -> MemberBenefitsMap()
            VitalityRewardsMap::class.java -> VitalityRewardsMap()
            SesameGiftMap::class.java -> SesameGiftMap()
            ParadiseCoinBenefitIdMap::class.java -> ParadiseCoinBenefitIdMap()
            ReserveaMap::class.java -> ReserveaMap()
            VipDataIdMap::class.java -> VipDataIdMap()
            else -> error("No IdMapManager factory registered for ${clazz.name}")
        }
    }
}
