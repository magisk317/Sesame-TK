package io.github.magisk317.sesame.util

import java.util.concurrent.ThreadLocalRandom

object RandomUtil {
    @JvmStatic
    fun delay(): Int = nextInt(100, 300)

    @JvmStatic
    fun nextInt(min: Int, max: Int): Int =
        if (min >= max) min else ThreadLocalRandom.current().nextInt(min, max)

    @JvmStatic
    fun nextLong(): Long = ThreadLocalRandom.current().nextLong()

    @JvmStatic
    fun nextLong(min: Long, max: Long): Long =
        if (min >= max) min else ThreadLocalRandom.current().nextLong(min, max)

    @JvmStatic
    fun nextDouble(): Double = ThreadLocalRandom.current().nextDouble()

    @JvmStatic
    fun getRandomInt(len: Int): String = buildString(len) {
        repeat(len) { append(ThreadLocalRandom.current().nextInt(10)) }
    }

    @JvmStatic
    fun getRandomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(length) {
            repeat(length) { append(chars[ThreadLocalRandom.current().nextInt(chars.length)]) }
        }
    }

    @JvmStatic
    fun getRandomTag(): String = "_${System.currentTimeMillis()}_${getRandomString(8)}"
}
