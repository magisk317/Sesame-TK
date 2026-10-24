package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optDouble
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

internal enum class FarmNpcConfig(val animalId: String, val source: String, val nickName: String) {
    NONE("", "", "关闭"),
    ZHIMA_PIGEON("20250901105101013088000000000006", "zhimaxiaoji_lianjin", "芝麻大表鸽"),
    GOLD_CHICKEN("20250725105101013088000000000004", "licaixiaoji_2025_1", "黄金鸡"),
    FARM_CHICKEN("20250613105101013088000000000002", "feiliaoji_202507", "农场小鸡");

    companion object {
        val nickNames: Array<String> by lazy { entries.map { it.nickName }.toTypedArray() }
        fun getByIndex(index: Int): FarmNpcConfig = entries.toTypedArray().getOrElse(index) { NONE }
    }
}

/** Owns NPC chicken synchronization, switching, reward collection and NPC tasks. */
internal class FarmNpcService(
    private val refreshStatus: (String?) -> Unit,
) {
    suspend fun run(selectedIndex: Int, animals: Array<AntFarm.Animal>?, ownerFarmId: String?) {
        try {
            val target = FarmNpcConfig.getByIndex(selectedIndex)
            if (target == FarmNpcConfig.NONE) return
            var currentAnimal = animals?.firstOrNull { it.subAnimalType == "NPC" }
            var currentJson: JsonObject? = null
            val sync = FarmCoreRpc.syncAnimalStatus(ownerFarmId, "SYNC_NPC", "QUERY_FARM_INFO")
                .toJsonObject()
            if (!ResChecker.checkRes(TAG, sync)) return
            sync.optJSONObject("subFarmVO")?.optJSONArray("animals")?.let { list ->
                for (index in list.indices) {
                    val item = list.getJSONObject(index)
                    if (item.optString("subAnimalType") == "NPC") {
                        currentJson = item
                        currentAnimal = AntFarm.Animal.fromJson(item)
                        break
                    }
                }
            }
            when {
                currentAnimal == null -> {
                    StructuredLog.record(TAG, "NPC小鸡🤖[当前未雇佣，准备雇佣${target.nickName}]")
                    hire(target, ownerFarmId)
                }
                currentAnimal.animalId == target.animalId -> {
                    checkRewardAndTask(currentAnimal, currentJson, target, ownerFarmId)
                }
                else -> switch(currentAnimal, target, ownerFarmId)
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "handleNpcAnimalLogic err:", error)
        }
    }

    private suspend fun switch(animal: AntFarm.Animal, target: FarmNpcConfig, ownerFarmId: String?) {
        val name = animal.masterUserInfoVO?.get("nickName")?.jsonPrimitive?.content ?: "未知NPC"
        StructuredLog.record(TAG, "NPC小鸡🤖[检测到${name}，目标是${target.nickName}，执行切换]")
        val result = FarmPropRpc.sendBackNpcAnimal(
            animal.animalId,
            animal.currentFarmId,
            animal.masterFarmId,
        ).toJsonObject()
        if (ResChecker.checkRes(TAG, result)) {
            StructuredLog.farm("NPC小鸡🤖[已遣返${name}]")
            delay(1000)
            hire(target, ownerFarmId)
        } else {
            StructuredLog.record(TAG, "NPC小鸡🤖[遣返失败，暂停切换]")
        }
    }

    private fun hire(config: FarmNpcConfig, ownerFarmId: String?): Boolean {
        return try {
            val result = FarmPropRpc.hireNpcAnimal(config.animalId, config.source).toJsonObject()
            if (ResChecker.checkRes(TAG, result)) {
                StructuredLog.farm("NPC小鸡🤖[成功雇佣${config.nickName}]")
                refreshStatus(ownerFarmId)
                true
            } else {
                StructuredLog.record(TAG, "NPC小鸡🤖[雇佣${config.nickName}失败: ${result.optString("memo")}]")
                false
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "hireNpc err", error)
            false
        }
    }

    private suspend fun checkRewardAndTask(
        animal: AntFarm.Animal,
        animalJson: JsonObject?,
        config: FarmNpcConfig,
        ownerFarmId: String?,
    ) {
        val reward = animalJson?.optDouble("npcBizReward", 0.0) ?: 0.0
        val isFull = animalJson?.optBoolean("reachNpcBizRewardLimit", false) == true ||
            (config == FarmNpcConfig.ZHIMA_PIGEON && reward >= 88.0)
        if (isFull) {
            StructuredLog.farm("NPC小鸡🤖[${config.nickName}产出已满($reward)，领取并重雇]")
            val result = FarmPropRpc.sendBackNpcAnimal(
                animal.animalId,
                animal.currentFarmId,
                animal.masterFarmId,
            ).toJsonObject()
            if (ResChecker.checkRes(TAG, result)) {
                StructuredLog.farm("NPC小鸡🤖[奖励领取成功]")
                delay(1500)
                hire(config, ownerFarmId)
            }
        } else {
            StructuredLog.record(TAG, "NPC小鸡🤖[${config.nickName}工作中... 当前产出:$reward]")
            if (config == FarmNpcConfig.ZHIMA_PIGEON) receiveZhimaTasks()
        }
    }

    private fun receiveZhimaTasks() {
        try {
            val result = FarmPropRpc.listZhimaNpcFarmTask().toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) return
            result.optJSONArray("farmTaskList")?.let { tasks ->
                for (index in tasks.indices) {
                    val task = tasks.getJSONObject(index)
                    if (task.optString("taskStatus") != TaskStatus.FINISHED.name) continue
                    val award = FarmPropRpc.receiveZhimaNpcFarmTaskAward(task.optString("taskId"))
                        .toJsonObject()
                    if (ResChecker.checkRes(TAG, award)) {
                        StructuredLog.farm(
                            "NPC任务🤖[完成: ${task.optString("title")}, 奖励: ${task.optInt("awardCount", 0)} 芝麻粒]",
                        )
                    }
                }
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "handleZhimaPigeonTasks err", error)
        }
    }

    private companion object {
        const val TAG = "FarmNpcService"
    }
}
