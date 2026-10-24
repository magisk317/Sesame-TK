package io.github.magisk317.sesame.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

@Serializable
data class PhotoCacheEntry(
    val before: String = "",
    val after: String = "",
)

/** Dedicated shared business cache for EcoLife photo references. */
object PhotoCache {
    private const val LOCK_NAME = "plate.json.lock"
    private val serializer = ListSerializer(PhotoCacheEntry.serializer())
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val localLock = Any()

    fun read(): List<PhotoCacheEntry> = withFileLock(emptyList()) {
        val file = Files.getPhotoCacheFile()
        if (!file.isFile || file.length() == 0L) return@withFileLock emptyList()
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    fun write(entries: List<PhotoCacheEntry>): Boolean = withFileLock(false) {
        val target = Files.getPhotoCacheFile()
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) return@withFileLock false
        val temp = File(parent, "${target.name}.tmp")
        runCatching {
            temp.writeText(json.encodeToString(serializer, entries))
            var renamed = temp.renameTo(target)
            if (!renamed) {
                if (target.exists()) target.delete()
                renamed = temp.renameTo(target)
            }
            if (!renamed) temp.copyTo(target, overwrite = true)
            true
        }.getOrElse {
            StructuredLog.e("PhotoCache", "写入光盘照片缓存失败: ${it.message}", it)
            false
        }.also {
            if (temp.exists()) temp.delete()
        }
    }

    fun clear(): Boolean {
        val file = Files.getPhotoCacheFile()
        return if (!file.exists()) true else withFileLock(false) { file.delete() }
    }

    private fun <T> withFileLock(default: T, block: () -> T): T {
        val target = Files.getPhotoCacheFile()
        val parent = target.parentFile ?: return block()
        if (!parent.exists() && !parent.mkdirs()) return default
        val lockFile = File(parent, LOCK_NAME)
        return try {
            FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use { block() }
            }
        } catch (_: OverlappingFileLockException) {
            synchronized(localLock) { block() }
        } catch (t: Throwable) {
            StructuredLog.e("PhotoCache", "光盘照片缓存锁不可用: ${t.message}", t)
            synchronized(localLock) { block() }
        }
    }
}
