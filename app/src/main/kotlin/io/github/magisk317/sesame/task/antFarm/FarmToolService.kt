package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject

/** Owns farm-tool inventory reads and tool-consumption RPCs. */
internal class FarmToolService(
    private val onToolsLoaded: (List<AntFarm.FarmTool>) -> Unit,
    private val isFenceActive: () -> Boolean,
    private val markFenceActive: () -> Unit,
    private val markAccelerateLimit: () -> Unit,
) {
    internal fun list(): List<AntFarm.FarmTool>? {
        return try {
            val result = FarmCoreRpc.listFarmTool().toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) return null

            val tools = mutableListOf<AntFarm.FarmTool>()
            val toolList = result.getJSONArray("toolList")
            for (index in toolList.indices) {
                val item = toolList.getJSONObject(index)
                val type = runCatching { AntFarm.ToolType.valueOf(item.getString("toolType")) }
                    .getOrNull()
                if (type == null) {
                    StructuredLog.record(TAG, "跳过未知庄园道具类型: ${item.optString("toolType")}")
                    continue
                }
                tools += AntFarm.FarmTool(
                    toolType = type,
                    toolId = item.optString("toolId", ""),
                    toolCount = item.getInt("toolCount"),
                    toolHoldLimit = item.optInt("toolHoldLimit", 20),
                )
            }
            onToolsLoaded(tools)
            tools
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "listFarmTool err:", error)
            null
        }
    }

    internal fun use(targetFarmId: String?, toolType: AntFarm.ToolType): Boolean {
        return try {
            val inventory = FarmCoreRpc.listFarmTool().toJsonObject()
            val memo = inventory.optString("memo")
            if (!ResChecker.checkRes(TAG, inventory)) {
                StructuredLog.record(TAG, memo)
                return false
            }

            val toolList = inventory.getJSONArray("toolList")
            val item = toolList.indices
                .asSequence()
                .map { toolList.getJSONObject(it) }
                .firstOrNull { it.getString("toolType") == toolType.name }
                ?: return false
            val count = item.getInt("toolCount")
            if (count <= 0) return false
            if (toolType == AntFarm.ToolType.FENCETOOL && isFenceActive()) {
                StructuredLog.record(TAG, "🛡️ 篱笆效果尚在，跳过重复使用")
                return false
            }

            val result = FarmCoreRpc.useFarmTool(
                targetFarmId,
                item.optString("toolId", ""),
                toolType.name,
            ).toJsonObject()
            if (ResChecker.checkRes(TAG, result)) {
                StructuredLog.farm("使用了道具🎭[${toolType.nickName()}]#剩余${count - 1}张")
                if (toolType == AntFarm.ToolType.FENCETOOL) markFenceActive()
                list()
                true
            } else {
                if (toolType == AntFarm.ToolType.ACCELERATETOOL &&
                    result.optString("resultCode") == "3D16"
                ) {
                    markAccelerateLimit()
                }
                StructuredLog.record(TAG, result.optString("memo", "未知错误"))
                StructuredLog.record(result.toString())
                false
            }
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "useFarmTool err:", error)
            false
        }
    }

    private companion object {
        const val TAG = "FarmToolService"
    }
}
