package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.optString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Replenishes and consumes the configured forest energy-rain chance cards. */
internal class ForestEnergyRainChanceService(
    private val hasRunToday: () -> Boolean,
    private val findProp: (JsonObject?, String) -> JsonObject?,
    private val queryBag: (Boolean) -> JsonObject?,
    private val useProp: (JsonObject) -> Boolean,
    private val exchangeChance: () -> Boolean,
    private val markRunToday: () -> Unit,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun use() {
        if (hasRunToday()) return

        try {
            for (propType in PROP_TYPES) {
                consumeCards(propType)
                if (propType == LIMITED_PROP_TYPE) exchangeLimitedCard()
            }
            markRunToday()
            StructuredLog.record(TAG, "所有能量雨卡已处理完毕")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "useEnergyRainChanceCard err", error)
        }
    }

    private suspend fun consumeCards(propType: String) {
        while (true) {
            val prop = findProp(queryBag(true), propType) ?: return
            if (!useProp(prop)) return
            StructuredLog.record(TAG, "成功使用一个能量雨道具: $propType")
            pause(CARD_USE_DELAY_MS)
        }
    }

    private suspend fun exchangeLimitedCard() {
        if (!exchangeChance()) return
        pause(EXCHANGE_DELAY_MS)
        val exchangedCard = findProp(queryBag(true), LIMITED_PROP_TYPE) ?: return
        if (useProp(exchangedCard)) pause(EXCHANGE_USE_DELAY_MS)
    }

    private companion object {
        const val TAG = "ForestEnergyRainChanceService"
        const val LIMITED_PROP_TYPE = "LIMIT_TIME_ENERGY_RAIN_CHANCE"
        const val CARD_USE_DELAY_MS = 2_000L
        const val EXCHANGE_DELAY_MS = 1_000L
        const val EXCHANGE_USE_DELAY_MS = 1_000L
        val PROP_TYPES = arrayOf(LIMITED_PROP_TYPE, "ENERGY_RAIN_CHANCE")
    }
}
