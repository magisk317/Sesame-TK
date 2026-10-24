package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.hook.Toast
import io.github.magisk317.sesame.util.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Consumes animal-dispatch and bomb-card rewards exposed by the forest home. */
class ForestSelfPropRewardService(
    private val isTeam: (JsonObject) -> Boolean,
    private val onCollected: (Int) -> Unit,
    private val refreshHome: () -> Unit,
) {
    private val tag = "ForestSelfPropRewardService"
    private fun parse(raw: String): JsonObject = raw.toJsonObject()

    /** Returns true when a new animal partner may still be dispatched. */
    fun collectAnimalEnergy(home: JsonObject): Boolean {
        var canDispatch = true
        try {
            val props = usingProps(home)
            for (index in 0..<props.length()) {
                val prop = props.getJSONObject(index)
                if (prop.getString("propGroup") != "animal") continue
                canDispatch = false
                val extInfo = parse(prop.getString("extInfo"))
                if (extInfo.optBoolean("isCollected")) {
                    StructuredLog.record(tag, "动物派遣能量已被收取")
                    continue
                }
                val response = parse(
                    ForestAnimalRpc.collectAnimalRobEnergy(
                        prop.getString("propId"),
                        prop.getString("propType"),
                        extInfo.getString("shortDay"),
                    ),
                )
                if (ResChecker.checkRes(tag + "收取动物派遣能量失败:", response)) {
                    val energy = extInfo.optInt("energy", 0)
                    onCollected(energy)
                    val message = "收取[${extInfo.getJSONObject("animal").getString("name")}]派遣能量🦩[${energy}g]"
                    Toast.show(message)
                    StructuredLog.forest(message)
                } else {
                    StructuredLog.record(tag, "收取动物能量失败: ${response.getString("resultDesc")}")
                    StructuredLog.record(response.toString())
                }
                GlobalThreadPools.sleepCompat(300L)
                break
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "handleUserProps err", t)
        }
        return canDispatch
    }

    fun collectBombEnergy(home: JsonObject) {
        try {
            val props = usingProps(home)
            for (index in 0..<props.length()) {
                val prop = props.getJSONObject(index)
                if (prop.getString("propGroup") != "energyBombCard") continue
                val extInfoText = prop.optString("extInfo")
                if (extInfoText.isEmpty()) continue
                val extInfo = parse(extInfoText)
                val remainEnergy = extInfo.optInt("remainEnergy", 0)
                if (remainEnergy <= 0) continue
                val propName = prop.getString("propName")
                StructuredLog.record(tag, "发现[$propName]有 $remainEnergy g能量待收取，尝试收取...")
                val response = parse(ForestPropRpc.collectBombEnergy(prop.getString("propId")))
                if (ResChecker.checkRes(tag + "收取炸弹卡能量失败:", response)) {
                    val collected = response.optInt("collectEnergy", 0)
                    onCollected(collected)
                    val message = "收取炸弹卡能量💥[${collected} g]"
                    Toast.show(message)
                    StructuredLog.forest(message)
                    refreshHome()
                } else {
                    StructuredLog.record(tag, "收取炸弹卡失败: ${response.getString("resultDesc")}")
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "collectEnergyBomb err", t)
        }
    }

    private fun usingProps(home: JsonObject): JsonArray = if (isTeam(home)) {
        home.optJSONObject("teamHomeResult")
            ?.optJSONObject("mainMember")
            ?.optJSONArray("usingUserProps")
            ?: jsonArray()
    } else {
        home.optJSONArray("usingUserPropsNew") ?: jsonArray()
    }
}
