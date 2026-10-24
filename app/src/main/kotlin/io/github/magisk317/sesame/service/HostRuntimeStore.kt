package io.github.magisk317.sesame.service

import android.content.Context

/** Private preferences shared by the Alipay processes under the same Android UID. */
object HostRuntimeStore {
    private const val PREFS = "sesame-host-runtime"
    const val LOCATION_KEY = "cached_location"
    const val DEBUG_SERVER_TOKEN_KEY = "debug_server_token"

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = (context.applicationContext ?: context)
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun getString(key: String): String? = prefs?.getString(key, null)?.takeIf(String::isNotBlank)

    fun putString(key: String, value: String): Boolean =
        prefs?.edit()?.putString(key, value)?.commit() == true

    fun clear() {
        prefs?.edit()?.clear()?.commit()
    }

}
