package io.github.magisk317.sesame.service

import android.content.Context
import io.github.magisk317.sesame.SesameApplication

/** Stores the UI's last-known active user while the authoritative hook value lives in remote prefs. */
object ActiveUserStore {
    private const val KEY_ACTIVE_USER_ID = "ui_active_user_id"

    fun read(context: Context): String? =
        RemotePrefsStore.getActiveUserId()
            ?: context.applicationContext
                .getSharedPreferences(SesameApplication.PREFERENCES_KEY, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_USER_ID, null)
                ?.takeIf(String::isNotBlank)

    fun publish(context: Context, userId: String?) {
        context.applicationContext
            .getSharedPreferences(SesameApplication.PREFERENCES_KEY, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (userId.isNullOrBlank()) remove(KEY_ACTIVE_USER_ID) else putString(KEY_ACTIVE_USER_ID, userId)
            }
            .commit()
        RemotePrefsStore.putActiveUserId(userId)
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(SesameApplication.PREFERENCES_KEY, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACTIVE_USER_ID)
            .commit()
        RemotePrefsStore.putActiveUserId(null)
    }

}
