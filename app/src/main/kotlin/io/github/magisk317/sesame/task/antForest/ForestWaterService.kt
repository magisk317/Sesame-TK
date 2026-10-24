package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.entity.KVMap
import io.github.magisk317.sesame.util.*
import io.github.magisk317.sesame.util.maps.UserMap

/** Executes the shared friend-watering RPC protocol and retry policy. */
class ForestWaterService {
    private val tag = "ForestWaterService"
    private fun parse(raw: String) = raw.toJsonObject()

    fun water(
        userId: String?,
        bizNo: String?,
        count: Int,
        waterEnergy: Int,
        notifyFriend: Boolean,
    ): KVMap<Int?, Boolean?> {
        if (bizNo.isNullOrEmpty() || userId == null) return KVMap(0, true)
        var wateredTimes = 0
        var shouldContinue = true
        try {
            val energyId = energyId(waterEnergy)
            var attempt = 1
            while (attempt <= count) {
                val raw = ForestCoreRpc.transferEnergy(userId, bizNo, energyId, notifyFriend)
                if (raw.isEmpty()) {
                    StructuredLog.record(tag, "好友浇水返回空: ${UserMap.getMaskName(userId)}")
                    shouldContinue = false
                    break
                }
                val response = parse(raw)
                when (response.optString("error")) {
                    "1009" -> {
                        StructuredLog.record(tag, "好友浇水🚿访问被拒绝: ${UserMap.getMaskName(userId)}")
                        shouldContinue = false
                        break
                    }
                    "3000" -> {
                        StructuredLog.record(tag, "好友浇水🚿系统错误，稍后重试: ${UserMap.getMaskName(userId)}")
                        GlobalThreadPools.sleepCompat(500)
                        continue
                    }
                }
                when (response.optString("resultCode")) {
                    "SUCCESS" -> {
                        val userInfo = response.optJSONObject("userBaseInfo")
                        val current = userInfo?.optInt("currentEnergy", 0) ?: "未知"
                        val total = userInfo?.optInt("totalEnergy", 0) ?: "未知"
                        StructuredLog.forest("好友浇水🚿[${UserMap.getMaskName(userId)}]#$waterEnergy g，当前能量状态 [$current/$total g]")
                        wateredTimes++
                        GlobalThreadPools.sleepCompat(1200L)
                    }
                    "WATERING_TIMES_LIMIT" -> {
                        StructuredLog.record(tag, "好友浇水🚿今日已达上限: ${UserMap.getMaskName(userId)}")
                        wateredTimes = 3
                        break
                    }
                    "ENERGY_INSUFFICIENT" -> {
                        StructuredLog.record(tag, "好友浇水🚿${response.optString("resultDesc")}")
                        shouldContinue = false
                        break
                    }
                    else -> StructuredLog.record(tag, "好友浇水🚿${response.optString("resultDesc")}")
                }
                attempt++
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "returnFriendWater err", t)
        }
        return KVMap(wateredTimes, shouldContinue)
    }

    private fun energyId(waterEnergy: Int): Int = when {
        waterEnergy <= 0 -> 0
        waterEnergy >= 66 -> 42
        waterEnergy >= 33 -> 41
        waterEnergy >= 18 -> 40
        else -> 39
    }
}
