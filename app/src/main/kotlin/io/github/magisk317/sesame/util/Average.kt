package io.github.magisk317.sesame.util

/** Calculates a moving average over a fixed-size window. */
class Average(size: Int) {
    private val windowSize = size.also {
        require(it > 0) { "The size must be greater than 0" }
    }
    private val values = ArrayDeque<Int>(windowSize)
    private var sum = 0.0
    private var average = 0.0

    @Synchronized
    fun nextDouble(value: Int): Double {
        if (values.size == windowSize) {
            sum -= values.removeFirst()
        }
        values.addLast(value)
        sum += value
        average = sum / values.size
        return average
    }

    @Synchronized
    fun nextInteger(value: Int): Int = nextDouble(value).toInt()

    @Synchronized
    fun averageDouble(): Double = average

    @Synchronized
    fun getAverageInteger(): Int = average.toInt()

    @Synchronized
    fun clear() {
        values.clear()
        sum = 0.0
        average = 0.0
    }
}
