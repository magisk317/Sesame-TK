package io.github.magisk317.sesame.model

import kotlinx.serialization.Serializable

/** Stable on-disk representation of the per-user custom task settings. */
@Serializable
data class CustomSettingsDto(
    val onlyOnceDaily: Boolean = false,
    val onlyOnceDailyList: Set<String> = defaultOnlyOnceDailyList(),
    val autoHandleOnceDaily: Boolean = false,
    val autoHandleOnceDailyTimes: List<String> = listOf("0600", "2000"),
) {
    companion object {
        fun defaultOnlyOnceDailyList(): Set<String> = linkedSetOf(
            "antOrchard",
            "antCooperate",
            "antSports",
            "antMember",
            "EcoProtection",
            "greenFinance",
            "reserve",
        )
    }
}
