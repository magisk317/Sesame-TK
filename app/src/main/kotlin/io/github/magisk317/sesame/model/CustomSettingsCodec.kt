package io.github.magisk317.sesame.model

import kotlinx.serialization.json.Json

/** Serialization boundary for stable custom settings data. */
object CustomSettingsCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(settings: CustomSettingsDto): String = json.encodeToString(settings)

    fun decode(raw: String): CustomSettingsDto = json.decodeFromString(raw)
}
