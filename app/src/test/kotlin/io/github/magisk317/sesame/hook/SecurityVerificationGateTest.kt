package io.github.magisk317.sesame.hook

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SecurityVerificationGateTest {
    @BeforeEach
    fun resetGate() {
        SecurityVerificationGate.reset()
    }

    @Test
    fun `mark blocks repeated requests until the recheck window expires`() {
        val now = 1_000L

        assertTrue(SecurityVerificationGate.mark(now))
        assertFalse(SecurityVerificationGate.mark(now + 1L))
        assertTrue(SecurityVerificationGate.isBlocked(now + SecurityVerificationGate.RECHECK_DELAY_MS - 1L))
        assertFalse(SecurityVerificationGate.isBlocked(now + SecurityVerificationGate.RECHECK_DELAY_MS))
    }

    @Test
    fun `reset clears a pending security verification`() {
        assertTrue(SecurityVerificationGate.mark(1_000L))

        SecurityVerificationGate.reset()

        assertFalse(SecurityVerificationGate.isBlocked(1_001L))
    }
}
