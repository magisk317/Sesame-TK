package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.*
import kotlinx.serialization.json.JsonObject

/** Executes the forest patrol workflow without requiring a foreground page. */
class ForestPatrolService {
    private val tag = "ForestPatrolService"
    private fun parse(raw: String): JsonObject = raw.toJsonObject()

    fun run() {
        val waitTime = 300L
        try {
            do {
                var response = parse(ForestPatrolRpc.queryUserPatrol())
                if (!ResChecker.checkRes(tag + "查询巡护任务失败:", response)) {
                    StructuredLog.record(tag, response.getString("resultDesc"))
                    break
                }
                val records = parse(ForestPatrolRpc.queryMyPatrolRecord())
                if (records.optBoolean("canSwitch")) {
                    val patrolRecords = records.getJSONArray("records")
                    for (index in 0..<patrolRecords.length()) {
                        val record = patrolRecords.getJSONObject(index)
                        val userPatrol = record.getJSONObject("userPatrol")
                        if (userPatrol.getInt("unreachedNodeCount") <= 0) continue
                        if (userPatrol.getString("mode") != "silent") break
                        val patrolId = record.getJSONObject("patrolConfig").getString("patrolId")
                        val switched = parse(ForestPatrolRpc.switchUserPatrol(patrolId))
                        GlobalThreadPools.sleepCompat(waitTime)
                        if (ResChecker.checkRes(tag + "切换巡护地图失败:", switched)) {
                            StructuredLog.forest("巡护⚖️-切换地图至$patrolId")
                        }
                    }
                }

                val userPatrol = response.getJSONObject("userPatrol")
                val currentNode = userPatrol.getInt("currentNode")
                val patrolId = userPatrol.getInt("patrolId")
                val chance = userPatrol.getJSONObject("chance")
                val leftChance = chance.getInt("leftChance")
                val leftStep = chance.getInt("leftStep")
                val usedStep = chance.getInt("usedStep")
                when (userPatrol.getString("currentStatus")) {
                    "STANDING" -> when {
                        leftChance > 0 -> {
                            response = parse(ForestPatrolRpc.patrolGo(currentNode, patrolId))
                            GlobalThreadPools.sleepCompat(waitTime)
                            keepGoing(response.toString(), currentNode, patrolId)
                            continue
                        }
                        leftStep >= 2000 && usedStep < 10000 -> {
                            response = parse(ForestPatrolRpc.exchangePatrolChance(leftStep))
                            if (ResChecker.checkRes(tag + "兑换巡护次数失败:", response)) {
                                StructuredLog.forest("步数兑换⚖️[巡护次数*${response.optInt("addedChance", 0)}]")
                                continue
                            }
                            StructuredLog.record(tag, response.getString("resultDesc"))
                        }
                    }
                    "GOING" -> keepGoing(null, currentNode, patrolId)
                }
                break
            } while (true)
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "queryUserPatrol err", t)
        }
    }

    private fun keepGoing(initial: String?, nodeIndex: Int, patrolId: Int) {
        var raw = initial
        try {
            while (true) {
                val response = parse(raw ?: ForestPatrolRpc.patrolKeepGoing(nodeIndex, patrolId, "image"))
                if (!ResChecker.checkRes(tag, response)) {
                    StructuredLog.record(tag, response.getString("resultDesc"))
                    return
                }
                val events = response.optJSONArray("events")
                if (events == null || events.length() == 0) return
                val event = events.getJSONObject(0)
                event.optJSONObject("rewardInfo")?.optJSONObject("animalProp")?.optJSONObject("animal")?.let {
                    StructuredLog.forest("巡护森林🏇🏻[${it.getString("name")}碎片]")
                }
                if (response.getString("currentStatus") != "GOING") return
                val userPatrol = response.getJSONObject("userPatrol")
                val currentNode = userPatrol.getInt("currentNode")
                val materialType = event.getJSONObject("materialInfo").optString("materialType", "image")
                raw = ForestPatrolRpc.patrolKeepGoing(currentNode, patrolId, materialType)
                GlobalThreadPools.sleepCompat(100)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "patrolKeepGoing err", t)
        }
    }
}
