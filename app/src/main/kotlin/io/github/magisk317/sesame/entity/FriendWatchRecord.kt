package io.github.magisk317.sesame.entity

import kotlinx.serialization.Serializable

/** Persisted statistics for one watched friend. */
@Serializable
data class FriendWatchRecord(
    val name: String = "",
    val allGet: Int = 0,
    val weekGet: Int = 0,
    val startTime: String = "",
)
