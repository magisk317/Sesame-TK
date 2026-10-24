package io.github.magisk317.sesame.hook

import java.util.concurrent.atomic.AtomicLong

/**
 * Prevents an RPC burst after Alipay asks for an interactive security check.
 * The gate expires so a later scheduler run can probe again after verification.
 */
internal object SecurityVerificationGate {
    const val RECHECK_DELAY_MS = 60_000L

    private val blockedUntil = AtomicLong(0L)

    fun mark(now: Long = System.currentTimeMillis()): Boolean {
        val nextBlockedUntil = now + RECHECK_DELAY_MS
        while (true) {
            val current = blockedUntil.get()
            if (current > now) return false
            if (blockedUntil.compareAndSet(current, nextBlockedUntil)) return true
        }
    }

    fun isBlocked(now: Long = System.currentTimeMillis()): Boolean =
        blockedUntil.get() > now

    fun reset() {
        blockedUntil.set(0L)
    }
}
