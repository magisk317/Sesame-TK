package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject

/** RPC gateway for forest inventory and prop operations. */
object ForestPropRpc {
    fun list(onlyGive: Boolean): String = request(
        "alipay.antforest.forest.h5.queryPropList",
        ForestPropRpcPayloads.queryPropList(onlyGive, ForestApiVersion.value),
    )
    fun animalList(): String = request(
        "alipay.antforest.forest.h5.queryAnimalPropList", ForestPropRpcPayloads.queryAnimalPropList(),
    )
    fun consume(propGroup: String?, propId: String, propType: String, secondConfirm: Boolean?): String = request(
        "alipay.antforest.forest.h5.consumeProp",
        ForestPropRpcPayloads.consumeProp(propGroup, propId, propType, secondConfirm, ForestApiVersion.value),
    )
    fun give(giveConfigId: String, propId: String, targetUserId: String): String = request(
        "alipay.antforest.forest.h5.giveProp", ForestPropRpcPayloads.giveProp(giveConfigId, propId, targetUserId),
    )
    fun collect(giveConfigId: String, giveId: String): String = request(
        "alipay.antforest.forest.h5.collectProp", ForestPropRpcPayloads.collectProp(giveConfigId, giveId),
    )
    fun collectBombEnergy(propId: String): String = request(
        "alipay.antforest.forest.h5.collectBombCardEnergy", ForestPropRpcPayloads.collectBombCardEnergy(propId),
    )
    fun collectRobExpandEnergy(propId: String, propType: String): String = request(
        "alipay.antforest.forest.h5.collectRobExpandEnergy",
        jsonArray(jsonObject("propId" to propId, "propType" to propType,
            "source" to "chInfo_ch_appcenter__chsub_9patch")).toString(),
    )
    private fun request(method: String, payload: String): String = RequestManager.requestString(method, payload)
}

object ForestPropGroup {
    fun forType(propType: String): String = when {
        propType.contains("SHIELD") -> "shield"
        propType.contains("DOUBLE_CLICK") -> "doubleClick"
        propType.contains("STEALTH") -> "stealthCard"
        propType.contains("BOMB_CARD") || propType.contains("NO_EXPIRE") -> "energyBombCard"
        propType.contains("ROB_EXPAND") -> "robExpandCard"
        propType.contains("BUBBLE_BOOST") -> "boost"
        else -> ""
    }
}
