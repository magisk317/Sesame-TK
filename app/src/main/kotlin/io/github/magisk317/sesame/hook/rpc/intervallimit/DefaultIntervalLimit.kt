package io.github.magisk317.sesame.hook.rpc.intervallimit

class DefaultIntervalLimit(override val interval: Int?) : IntervalLimit {
    override var time: Long = 0
}