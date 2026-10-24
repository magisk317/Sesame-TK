package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.entity.CollectEnergyEntity
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonObject

/** Builds single or batched energy collection requests. */
class ForestEnergyDispatchService(
    private val batchEnabled: () -> Boolean,
    private val dispatch: (CollectEnergyEntity) -> Unit,
    private val batchSize: Int = 6,
) {
    fun collect(
        userId: String?,
        home: JsonObject?,
        bubbleIds: List<Long>,
        fromTag: String?,
        skipPropCheck: Boolean,
    ) {
        if (userId == null || bubbleIds.isEmpty()) return
        if (batchEnabled()) {
            bubbleIds.chunked(batchSize).forEach { ids ->
                dispatch(
                    CollectEnergyEntity(
                        userId,
                        home,
                        ForestCoreRpc.batchEnergyEntity("GREEN", userId, ids),
                        fromTag,
                        skipPropCheck,
                    )
                )
            }
        } else {
            bubbleIds.forEach { id ->
                dispatch(
                    CollectEnergyEntity(
                        userId,
                        home,
                        ForestCoreRpc.energyEntity("GREEN", userId, id),
                        fromTag,
                        skipPropCheck,
                    )
                )
            }
        }
    }
}
