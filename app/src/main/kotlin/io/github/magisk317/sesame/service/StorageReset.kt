package io.github.magisk317.sesame.service

import android.content.Context
import io.github.magisk317.sesame.data.Config
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.PhotoCache
import io.github.magisk317.sesame.util.TaskRuntimeStore
import io.github.magisk317.sesame.util.maps.UserMap

/** Clears module configuration while keeping the separately managed entitlement lease. */
object StorageReset {
    private const val PENDING_RESET_PREFS = "sesame-storage-reset"
    private const val PENDING_RESET_GENERATION = "generation"

    data class Result(
        val localCleared: Boolean,
        val remote: RemotePrefsStore.ConfigurationClearResult,
    ) {
        val completed: Boolean
            get() = localCleared && remote.state == RemotePrefsStore.ConfigurationClearState.CLEARED
    }

    fun clearAll(context: Context): Result {
        val filesCleared = !Files.CONFIG_DIR.exists() || Files.delFile(Files.CONFIG_DIR)
        val remoteCleared = RemotePrefsStore.clearConfigurations()

        val pendingPrefs = context.applicationContext
            .getSharedPreferences(PENDING_RESET_PREFS, Context.MODE_PRIVATE)
        if (remoteCleared.state == RemotePrefsStore.ConfigurationClearState.QUEUED) {
            pendingPrefs.edit().putLong(PENDING_RESET_GENERATION, remoteCleared.generation).commit()
        } else {
            pendingPrefs.edit().remove(PENDING_RESET_GENERATION).commit()
        }

        TaskRuntimeStore.applyRemoteResetBarrier(remoteCleared.generation)
        TaskRuntimeStore.clear()
        HostRuntimeStore.clear()
        ActiveUserStore.clear(context)
        UserMap.setCurrentUserId(null)
        UserMap.unload()
        Config.unload()
        val photosCleared = PhotoCache.clear()

        return Result(
            localCleared = filesCleared && photosCleared,
            remote = remoteCleared,
        )
    }

    /** Retries a reset that was requested while the Xposed service was unavailable. */
    fun flushPendingRemoteReset(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PENDING_RESET_PREFS, Context.MODE_PRIVATE)
        val pendingGeneration = prefs.getLong(PENDING_RESET_GENERATION, 0L)
        if (pendingGeneration <= 0L || !RemotePrefsStore.available()) return pendingGeneration <= 0L

        if (RemotePrefsStore.getConfigurationResetGeneration() < pendingGeneration) {
            val result = RemotePrefsStore.clearConfigurations()
            if (result.state == RemotePrefsStore.ConfigurationClearState.QUEUED) return false
        }
        return if (RemotePrefsStore.getConfigurationResetGeneration() >= pendingGeneration) {
            prefs.edit().remove(PENDING_RESET_GENERATION).commit()
        } else {
            false
        }
    }
}
