package io.github.magisk317.sesame.entity

import kotlinx.serialization.json.JsonObject

/** Mutable runtime state for collecting energy from one user. */
class CollectEnergyEntity @JvmOverloads constructor(
    val userId: String,
    var userHome: JsonObject? = null,
    var rpcEntity: RpcEntity? = null,
    var fromTag: String? = null,
    var skipPropCheck: Boolean = false,
) {
    var collectCount: Int = 0
        private set

    var tryCount: Int = 0
        private set

    var needDouble: Boolean = false
    var needRetry: Boolean = false

    fun addTryCount(): Int {
        tryCount += 1
        return tryCount
    }

    fun resetTryCount() {
        tryCount = 0
    }

    fun setNeedDouble() {
        collectCount += 1
        needDouble = true
    }

    fun unsetNeedDouble() {
        needDouble = false
    }

    fun setNeedRetry() {
        needRetry = true
    }

    fun unsetNeedRetry() {
        needRetry = false
    }
}
