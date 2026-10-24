package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Coordinates farm hiring pagination and delegates a single hire action. */
internal class FarmHireService(
    private val enterOwnFarm: () -> JsonObject?,
    private val foodStock: () -> Int,
    private val selectedUsers: () -> Set<String?>,
    private val excludeSelectedUsers: () -> Boolean,
    private val currentUserId: () -> String?,
    private val rankingPage: (Int) -> JsonObject,
    private val hireAction: (String) -> Boolean,
    private val isFarmFull: () -> Boolean,
    private val resetFarmFull: () -> Unit,
    private val scheduleExpiry: (String, Long) -> Unit,
    private val showConfigurationWarning: () -> Unit,
) {
    fun run() {
        markFarmFullReset()
        val animals = try {
            val home = enterOwnFarm() ?: return
            if (home.optString("memo") != "SUCCESS") {
                StructuredLog.record(home.optString("memo"))
                return
            }
            home.getJSONObject("farmVO").getJSONObject("subFarmVO").getJSONArray("animals")
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "getAnimalCount err:", error)
            return
        }

        try {
            scheduleWorkingAnimals(animals)
            var animalCount = animals.length()
            if (animalCount >= MAX_ANIMALS) return
            StructuredLog.farm("雇佣小鸡👷[当前可雇佣小鸡数量:${MAX_ANIMALS - animalCount}只]")
            if (foodStock() < MIN_FOOD) {
                StructuredLog.record(TAG, "庄园🐔 雇佣失败：饲料不足（当前${foodStock()}g，至少需要${MIN_FOOD}g）")
                return
            }
            val users = selectedUsers()
            if (users.isEmpty()) {
                StructuredLog.record(TAG, "庄园🐔 雇佣失败：未配置雇佣好友列表")
                showConfigurationWarning()
                return
            }

            val initialCount = animalCount
            var pageStart = 0
            var hasNext: Boolean
            var checked = 0
            var available = 0
            do {
                val page = rankingPage(pageStart)
                if (!ResChecker.checkRes(TAG, page)) {
                    StructuredLog.record(page.optString("memo"))
                    StructuredLog.record(page.toString())
                    break
                }
                hasNext = page.getBoolean("hasNext")
                val ranking = page.getJSONArray("rankingList")
                pageStart += ranking.length()
                for (index in ranking.indices) {
                    val entry = ranking.getJSONObject(index)
                    val userId = entry.getString("userId")
                    if (!isEligible(userId, entry.getJSONArray("actionTypeList"), users, excludeSelectedUsers(), currentUserId())) continue
                    checked++
                    available++
                    if (hireAction(userId)) {
                        animalCount++
                        break
                    }
                    if (isFarmFull()) {
                        animalCount = MAX_ANIMALS
                        break
                    }
                }
            } while (hasNext && animalCount < MAX_ANIMALS)

            reportResult(initialCount, animalCount, checked, available)
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "hireAnimal err:", error)
        }
    }

    private fun markFarmFullReset() {
        resetFarmFull()
    }

    private fun scheduleWorkingAnimals(animals: JsonArray) {
        for (index in animals.indices) {
            val animal = animals.getJSONObject(index)
            if (animal.optString("subAnimalType") == "WORK") {
                scheduleExpiry(animal.getString("animalId"), animal.getLong("beHiredEndTime"))
            }
        }
    }

    private fun reportResult(initial: Int, final: Int, checked: Int, available: Int) {
        val hired = final - initial
        if (final < MAX_ANIMALS) {
            StructuredLog.record(TAG, "雇佣小鸡结果统计：")
            StructuredLog.record(TAG, "  • 成功雇佣：${hired}只")
            StructuredLog.record(TAG, "  • 还需雇佣：${MAX_ANIMALS - final}只")
            StructuredLog.record(TAG, "  • 已检查好友：${checked}人")
            StructuredLog.record(TAG, "  • 可雇佣状态：${available}人")
            when {
                available == 0 -> {
                    StructuredLog.record(TAG, "庄园🐔 失败原因：好友列表中没有可雇佣的小鸡")
                    StructuredLog.record(TAG, "   建议：等待好友的小鸡回家或添加更多好友")
                }
                hired < available -> StructuredLog.record(TAG, "⚠️ 部分雇佣失败：好友的小鸡可能不在家")
                else -> StructuredLog.record(TAG, "庄园🐔 失败原因：可雇佣的小鸡数量不足")
            }
        } else if (hired > 0) {
            StructuredLog.record(TAG, "庄园🐔 雇佣成功：共雇佣${hired}只小鸡")
        }
    }

    internal companion object {
        private const val TAG = "FarmHireService"
        const val MAX_ANIMALS = 3
        const val MIN_FOOD = 50

        internal fun isEligible(
            userId: String,
            actionTypes: JsonArray,
            selected: Set<String?>,
            excludeSelected: Boolean,
            currentUserId: String?,
        ): Boolean {
            if (userId == currentUserId || !actionTypes.toString().contains("can_hire_action")) return false
            val selectedUser = selected.contains(userId)
            return if (excludeSelected) !selectedUser else selectedUser
        }
    }
}
