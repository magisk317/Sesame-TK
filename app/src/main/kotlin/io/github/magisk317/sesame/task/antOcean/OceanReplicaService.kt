package io.github.magisk317.sesame.task.antOcean

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject

/** Pandora replica asset collection and award claiming. */
object OceanReplicaService {
    private const val TAG = "AntOcean"

    fun processHome() {
        try {
            val response = OceanCoreRpc.queryReplicaHome().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询海洋副本主页失败:", response)) return
            val assets = response.optJSONObject("userReplicaAssetVO")?.optInt("canCollectAssetNum") ?: 0
            repeat(assets) {
                val result = OceanCoreRpc.collectReplicaAsset().toJsonObject()
                if (ResChecker.checkRes("$TAG 收集海洋科普知识失败:", result)) {
                    StructuredLog.forest("神奇海洋🌊[学习海洋科普知识]#潘多拉能量+1")
                }
            }
            val phase = response.optJSONObject("userCurrentPhaseVO") ?: return
            if (phase.optString("phaseStatus") != "COMPLETED") return
            val replicaCode = response.optJSONObject("userReplicaInfoVO")?.optString("code") ?: return
            val result = OceanTaskRpc.unlockReplicaPhase(replicaCode, phase.optString("phaseCode")).toJsonObject()
            if (ResChecker.checkRes("$TAG 解锁海洋副本阶段失败:", result)) {
                val name = result.optJSONObject("currentPhaseInfo")?.optJSONObject("extInfo")?.optString("name")
                StructuredLog.forest("神奇海洋🌊迎回[$name]")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryReplicaHome err:", t)
        }
    }

    fun receiveAwards() {
        try {
            val home = OceanTaskRpc.queryReplicaHome().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询潘多拉海洋副本主页失败:", home)) return
            val response = OceanTaskRpc.queryReplicaTaskList().toJsonObject()
            val tasks = response.optJSONArray("antOceanTaskVOList") ?: return
            for (index in 0 until tasks.length()) {
                val task = tasks.optJSONObject(index) ?: continue
                if (task.optString("taskStatus") != "FINISHED") continue
                val info = task.optString("bizInfo").toJsonObject()
                val result = OceanTaskRpc.receiveReplicaTaskAward(task.optString("taskType")).toJsonObject()
                if (result.optInt("code") == 100000000) {
                    StructuredLog.forest("海洋奖励🌊[领取:${info.optString("taskTitle")}]获得潘多拉能量x${info.optInt("awardCount")}")
                } else {
                    StructuredLog.record(TAG, "领取任务奖励失败: ${result.optString("message", "未返回错误信息")}")
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "doOceanPDLTask err:", t)
        }
    }
}
