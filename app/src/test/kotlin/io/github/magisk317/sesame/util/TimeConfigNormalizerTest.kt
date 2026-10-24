package io.github.magisk317.sesame.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TimeConfigNormalizerTest {
    @Test
    fun normalizesTimePointsAndRangesWithoutChangingSentinels() {
        assertEquals("0800", TimeConfigNormalizer.normalize("800"))
        assertEquals("0700-0800", TimeConfigNormalizer.normalize("700-800"))
        assertEquals("0030,0630", TimeConfigNormalizer.normalize("30,630"))
        assertEquals("-1", TimeConfigNormalizer.normalize("-1"))
    }
}
