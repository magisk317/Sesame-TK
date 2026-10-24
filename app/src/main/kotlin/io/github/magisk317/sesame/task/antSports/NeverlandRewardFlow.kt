package io.github.magisk317.sesame.task.antSports

import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal data class NeverlandMapSummary(
    val mapId: String,
    val mapName: String,
    val branchId: String,
    val status: String,
    val newIsland: Boolean? = null,
)

internal data class NeverlandClaimableReward(
    val mapId: String,
    val mapName: String,
    val rewardName: String,
    val reason: String,
) {
    val notificationTitle: String get() = "健康岛奖励可兑换"

    val notificationText: String
        get() = "「$mapName」$rewardName 可兑换"

    val notifyFlag: String
        get() = "Flag_Neverland_EntityReward_${mapId}_$rewardName"
}

internal fun neverlandMapSummaries(mapList: JsonArray?): List<NeverlandMapSummary> {
    if (mapList == null || mapList.isEmpty()) return emptyList()
    return (0 until mapList.size).mapNotNull { index ->
        val map = mapList[index] as? JsonObject ?: return@mapNotNull null
        val mapId = firstNonBlank(
            map.optString("mapId"),
            map.optString("islandId"),
            map.optString("id"),
        )
        if (mapId.isBlank()) return@mapNotNull null
        NeverlandMapSummary(
            mapId = mapId,
            mapName = firstNonBlank(
                map.optString("mapName"),
                map.optString("islandName"),
                map.optString("name"),
                map.optString("title"),
                mapId,
            ),
            branchId = firstNonBlank(map.optString("branchId"), "MASTER"),
            status = map.optString("status").uppercase(),
            newIsland = when {
                map.containsKey("newIsLandFlg") -> map.optBoolean("newIsLandFlg")
                map.containsKey("newIslandFlag") -> map.optBoolean("newIslandFlag")
                else -> null
            },
        )
    }
}

internal fun neverlandHasBuildableMap(summaries: Collection<NeverlandMapSummary>): Boolean =
    summaries.any { neverlandMapIsBuildable(it.status) }

internal fun neverlandAllMapsComplete(summaries: Collection<NeverlandMapSummary>): Boolean =
    summaries.isNotEmpty() && summaries.all { neverlandMapLooksComplete(it.status) }

internal enum class NeverlandMapAvailability {
    DOING,
    AVAILABLE,
    ALL_COMPLETE,
    INVALID,
}

internal fun neverlandMapAvailability(
    summaries: Collection<NeverlandMapSummary>,
): NeverlandMapAvailability = when {
    summaries.isEmpty() -> NeverlandMapAvailability.INVALID
    summaries.any { it.status.equals("DOING", ignoreCase = true) } -> NeverlandMapAvailability.DOING
    summaries.any { neverlandMapIsBuildable(it.status) } -> NeverlandMapAvailability.AVAILABLE
    neverlandAllMapsComplete(summaries) -> NeverlandMapAvailability.ALL_COMPLETE
    else -> NeverlandMapAvailability.INVALID
}

internal fun neverlandMapIsBuildable(status: String): Boolean =
    status.uppercase() in BUILDABLE_MAP_STATUSES

internal fun neverlandMapLooksComplete(
    status: String,
    mapEnergyProcess: Int? = null,
    mapEnergyFinal: Int? = null,
): Boolean {
    val normalized = status.uppercase()
    if (normalized in COMPLETE_MAP_STATUSES) return true
    if (neverlandMapIsBuildable(normalized)) return false
    val process = mapEnergyProcess ?: return false
    val finalEnergy = mapEnergyFinal ?: return false
    return finalEnergy > 0 && process >= finalEnergy
}

internal fun neverlandCollectClaimableEntityRewards(
    map: NeverlandMapSummary,
    payloads: Iterable<JsonObject?>,
): List<NeverlandClaimableReward> {
    val rewards = linkedMapOf<String, NeverlandClaimableReward>()
    payloads.filterNotNull().forEach { payload ->
        collectClaimableEntityRewards(map, payload, rewards)
    }
    return rewards.values.toList()
}

private fun collectClaimableEntityRewards(
    map: NeverlandMapSummary,
    element: JsonElement,
    sink: MutableMap<String, NeverlandClaimableReward>,
) {
    when (element) {
        is JsonArray -> element.forEach { collectClaimableEntityRewards(map, it, sink) }
        is JsonObject -> {
            maybeRecordReward(map, element, sink)
            element.values.forEach { collectClaimableEntityRewards(map, it, sink) }
        }
        else -> Unit
    }
}

private fun maybeRecordReward(
    map: NeverlandMapSummary,
    node: JsonObject,
    sink: MutableMap<String, NeverlandClaimableReward>,
) {
    val rewardName = entityRewardName(node) ?: return
    if (!isClaimableReward(node)) return
    val key = "${map.mapId}::$rewardName"
    sink.putIfAbsent(
        key,
        NeverlandClaimableReward(
            mapId = map.mapId,
            mapName = map.mapName,
            rewardName = rewardName,
            reason = firstNonBlank(
                node.optString("status"),
                node.optString("rewardStatus"),
                node.optString("prizeStatus"),
                node.optString("exchangeStatus"),
                "CLAIMABLE",
            ),
        ),
    )
}

private fun entityRewardName(node: JsonObject): String? {
    val texts = listOf(
        node.optString("rewardName"),
        node.optString("prizeName"),
        node.optString("itemName"),
        node.optString("title"),
        node.optString("name"),
        node.optString("desc"),
        node.optString("subTitle"),
        node.optString("rewardType"),
        node.optString("prizeType"),
        node.optString("itemType"),
        node.optString("type"),
        node.optString("goodsType"),
    ).filter { it.isNotBlank() }
    if (texts.none { text -> ENTITY_REWARD_MARKERS.any { marker -> text.contains(marker, ignoreCase = true) } }) {
        return null
    }
    return firstNonBlank(
        node.optString("rewardName"),
        node.optString("prizeName"),
        node.optString("itemName"),
        node.optString("title"),
        node.optString("name"),
        "实体奖牌盲盒",
    )
}

private fun isClaimableReward(node: JsonObject): Boolean {
    if (booleanFlag(node, RECEIVED_BOOL_KEYS)) return false
    val status = firstNonBlank(
        node.optString("status"),
        node.optString("rewardStatus"),
        node.optString("prizeStatus"),
        node.optString("exchangeStatus"),
        node.optString("claimStatus"),
    ).uppercase()
    if (status in RECEIVED_STATUSES) return false
    if (status in CLAIMABLE_STATUSES) return true
    if (booleanFlag(node, CLAIMABLE_BOOL_KEYS)) return true
    if (node.optInt("leftCount", 0) > 0 || node.optInt("remainCount", 0) > 0) return true
    return false
}

private fun booleanFlag(node: JsonObject, keys: Set<String>): Boolean =
    keys.any { key -> node.containsKey(key) && node.optBoolean(key) }

private fun firstNonBlank(vararg values: String): String =
    values.firstOrNull { it.isNotBlank() }.orEmpty()

private val BUILDABLE_MAP_STATUSES = setOf(
    "DOING",
    "LOCKED",
    "UNLOCK",
    "UNLOCKED",
    "BUILDING",
    "IN_PROGRESS",
    "PROCESSING",
)

private val COMPLETE_MAP_STATUSES = setOf(
    "FINISHED",
    "FINISH",
    "FINISH_NOT_REWARD",
    "DONE",
    "COMPLETED",
    "SUCCESS",
    "BUILT",
    "CLEAR",
)

private val ENTITY_REWARD_MARKERS = listOf(
    "盲盒",
    "奖牌",
    "实体",
    "实物",
    "medal",
    "blindbox",
    "blind_box",
    "physical",
)

private val CLAIMABLE_STATUSES = setOf(
    "UNRECEIVED",
    "WAIT_RECEIVE",
    "TO_RECEIVE",
    "CAN_RECEIVE",
    "AVAILABLE",
    "WAIT_EXCHANGE",
    "TO_EXCHANGE",
    "EXCHANGEABLE",
    "CAN_EXCHANGE",
    "CAN_CHOOSE",
    "WAIT_CHOOSE",
    "TO_CHOOSE",
    "UNCLAIMED",
    "INIT",
)

private val RECEIVED_STATUSES = setOf(
    "RECEIVED",
    "RECEIVE_SUCCESS",
    "EXCHANGED",
    "CLAIMED",
    "DONE",
    "FINISHED",
    "SUCCESS",
)

private val CLAIMABLE_BOOL_KEYS = setOf(
    "canReceive",
    "canExchange",
    "canChoose",
    "canClaim",
    "canChooseReward",
    "exchangeFlag",
    "hasUnclaimedReward",
    "needReceive",
    "needExchange",
    "waitReceive",
)

private val RECEIVED_BOOL_KEYS = setOf(
    "received",
    "exchanged",
    "claimed",
    "alreadyReceive",
    "alreadyExchanged",
)
