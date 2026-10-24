package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** Payload builders for the forest prop and vitality item protocols. */
object ForestPropRpcPayloads {
    fun queryPropList(onlyGive: Boolean, version: String): String = jsonArray(
        jsonObject("onlyGive" to if (onlyGive) "Y" else "", "source" to SOURCE, "version" to version),
    ).toString()

    fun queryAnimalPropList(): String = jsonArray(jsonObject("source" to SOURCE)).toString()

    fun consumeProp(propGroup: String?, propId: String, propType: String, secondConfirm: Boolean?, version: String): String {
        val values = buildList {
            if (!propGroup.isNullOrEmpty()) add("propGroup" to propGroup)
            add("propId" to propId)
            add("propType" to propType)
            add("sToken" to "${System.currentTimeMillis()}_${RandomUtil.getRandomString(8)}")
            if (secondConfirm != null) add("secondConfirm" to secondConfirm)
            add("source" to SOURCE)
            add("timezoneId" to "Asia/Shanghai")
            add("version" to version)
        }
        return jsonArray(jsonObject(*values.toTypedArray())).toString()
    }

    fun giveProp(giveConfigId: String, propId: String, targetUserId: String): String = jsonArray(
        jsonObject("giveConfigId" to giveConfigId, "propId" to propId, "source" to "self_corner", "targetUserId" to targetUserId),
    ).toString()

    fun collectProp(giveConfigId: String, giveId: String): String = jsonArray(
        jsonObject("giveConfigId" to giveConfigId, "giveId" to giveId, "source" to SOURCE),
    ).toString()

    fun collectBombCardEnergy(propId: String): String = jsonArray(
        jsonObject("propId" to propId, "source" to SOURCE),
    ).toString()

    private const val SOURCE = "chInfo_ch_appcenter__chsub_9patch"

}
