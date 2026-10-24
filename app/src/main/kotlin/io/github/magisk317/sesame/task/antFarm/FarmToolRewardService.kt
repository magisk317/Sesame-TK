package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

/** Owns parsing and claiming rewards from completed farm-tool tasks. */
internal class FarmToolRewardService(
    private val tools: () -> Array<AntFarm.FarmTool>,
) {
    internal data class Reward(
        val awardType: String,
        val awardCount: Int,
        val taskType: String,
        val taskTitle: String,
        val toolType: AntFarm.ToolType,
    )

    internal fun parse(response: JsonObject): List<Reward> {
        if (!ResChecker.checkRes(TAG, response)) return emptyList()
        val tasks = response.getJSONArray("list")
        return buildList {
            for (index in tasks.indices) {
                val task = tasks.getJSONObject(index)
                if (task.optString("taskStatus") != TaskStatus.FINISHED.name) continue
                val info = runCatching { task.getString("bizInfo").toJsonObject() }.getOrNull()
                    ?: continue
                val awardType = info.optString("awardType")
                val toolType = runCatching { AntFarm.ToolType.valueOf(awardType) }.getOrNull()
                    ?: continue
                if (tools().any { it.toolType == toolType && it.toolCount >= it.toolHoldLimit }) {
                    StructuredLog.record(TAG, "领取道具[${toolType.nickName()}]#已满，暂不领取")
                    continue
                }
                val taskType = task.optString("taskType")
                if (awardType.isEmpty() || taskType.isEmpty()) continue
                add(
                    Reward(
                        awardType = awardType,
                        awardCount = info.optInt("awardCount", 0),
                        taskType = taskType,
                        taskTitle = info.optString("taskTitle", "未知任务"),
                        toolType = toolType,
                    ),
                )
            }
        }
    }

    internal fun receive() {
        try {
            val response = FarmPropRpc.listToolTaskDetails().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) {
                StructuredLog.record(TAG, response.optString("memo"))
                StructuredLog.record(response.toString())
                return
            }
            parse(response).forEach { reward ->
                val result = FarmPropRpc.receiveToolTaskReward(
                    reward.awardType,
                    reward.awardCount,
                    reward.taskType,
                ).toJsonObject()
                if (ResChecker.checkRes(TAG, result)) {
                    StructuredLog.farm(
                        "领取道具🎖️[${reward.taskTitle}-${reward.toolType.nickName()}]#${reward.awardCount}张",
                    )
                } else {
                    val memo = result.optString("memo").replace("道具", reward.toolType.nickName().toString())
                    StructuredLog.record(memo)
                    StructuredLog.record(result.toString())
                }
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "receiveToolTaskReward err:", error)
        }
    }

    private companion object {
        const val TAG = "FarmToolRewardService"
    }
}
