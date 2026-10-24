package io.github.magisk317.sesame.hook.rpc.debug

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.task.reserve.ReserveRpcCall
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getBoolean
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject

/** Debug RPC probes. Work is session-scoped and never starts an unmanaged thread. */
class DebugRpc {
    private fun parseJsonObject(raw: String): JsonObject = raw.toJsonObject()

    fun getName(): String = TAG

    fun start(broadcastFun: String?, broadcastData: String?, testType: String?) {
        GlobalThreadPools.execute {
            when (testType) {
                "Rpc" -> StructuredLog.debug(
                    "收到测试消息:\n方法:$broadcastFun\n数据:$broadcastData\n结果:${RequestManager.requestString(broadcastFun.orEmpty(), broadcastData.orEmpty())}",
                )
                "getNewTreeItems" -> getNewTreeItems()
                "getTreeItems" -> getTreeItems()
                "queryAreaTrees" -> queryAreaTrees()
                "getUnlockTreeItems" -> getUnlockTreeItems()
                "walkGrid" -> walkGrid()
                else -> StructuredLog.debug("未知的测试类型: $testType")
            }
        }
    }

    fun queryEnvironmentCertDetailList(alias: String, pageNum: Int, targetUserID: String): String =
        DebugRpcCall.queryEnvironmentCertDetailList(alias, pageNum, targetUserID)

    private fun getNewTreeItems() = runProbe("getTreeItems") {
        val root = parseJsonObject(ReserveRpcCall.queryTreeItemsForExchange())
        if (!ResChecker.checkRes(TAG, root)) {
            StructuredLog.record(TAG, root.optString("resultDesc"))
            return@runProbe
        }
        val items = root.optJSONArray("treeItems") ?: return@runProbe
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            if (item.optString("projectType") == "TREE" && item.optString("applyAction") == "COMING") {
                queryTreeForExchange(item.getString("itemId"))
            }
        }
    }

    private fun queryTreeForExchange(projectId: String) = runProbe("queryTreeForExchange") {
        val root = parseJsonObject(ReserveRpcCall.queryTreeForExchange(projectId))
        if (!ResChecker.checkRes(TAG, root)) {
            StructuredLog.record("${root.optString("resultDesc")} projectId: $projectId")
            return@runProbe
        }
        val tree = root.getJSONObject("exchangeableTree")
        val tips = if (tree.optBoolean("canCoexchange")) {
            "可以合种-合种类型：${tree.optJSONObject("extendInfo")?.optString("cooperate_template_id_list")}"
        } else "不可合种"
        StructuredLog.debug(TAG, "新树上苗🌱[${tree.optString("region")}-${tree.optString("treeName")}]#${tree.optInt("currentBudget")}株-$tips")
    }

    private fun getTreeItems() = runProbe("getTreeItems") {
        val root = parseJsonObject(ReserveRpcCall.queryTreeItemsForExchange())
        if (!ResChecker.checkRes(TAG, root)) {
            StructuredLog.record(TAG, root.optString("resultDesc"))
            return@runProbe
        }
        val items = root.optJSONArray("treeItems") ?: return@runProbe
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            if (item.optString("applyAction") != "AVAILABLE") continue
            getTreeCurrentBudget(item.getString("itemId"), item.getString("itemName"))
            GlobalThreadPools.sleepCompat(100)
        }
    }

    private fun getTreeCurrentBudget(projectId: String, treeName: String) = runProbe("getTreeCurrentBudget") {
        val root = parseJsonObject(ReserveRpcCall.queryTreeForExchange(projectId))
        if (!ResChecker.checkRes(TAG, root)) {
            StructuredLog.record("${root.optString("resultDesc")} projectId: $projectId")
            return@runProbe
        }
        val tree = root.getJSONObject("exchangeableTree")
        StructuredLog.debug(TAG, "树苗查询🌱[${tree.optString("region")}-${treeName}]#剩余:${tree.optInt("currentBudget")}")
    }

    private fun walkGrid() = runProbe("walkGrid") {
        while (true) {
            val root = parseJsonObject(DebugRpcCall.walkGrid())
            if (!root.optBoolean("success")) {
                StructuredLog.record("${root.optString("errorMsg")}$root")
                return@runProbe
            }
            val data = root.optJSONObject("data") ?: return@runProbe
            val award = data.optJSONArray("mapAwards")?.optJSONObject(0)
            val game = award?.optJSONObject("miniGameInfo")
            if (game != null) finishMiniGame(game)
            if (data.optInt("leftCount") <= 0) return@runProbe
            GlobalThreadPools.sleepCompat(3000)
        }
    }

    private fun finishMiniGame(game: JsonObject) {
        GlobalThreadPools.sleepCompat(4000)
        val finish = parseJsonObject(DebugRpcCall.miniGameFinish(game.getString("gameId"), game.getString("key")))
        val adBizNo = finish.optJSONObject("data")?.optJSONObject("adVO")?.optString("adBizNo")
        if (!finish.optBoolean("success") || adBizNo.isNullOrEmpty()) return
        val task = parseJsonObject(DebugRpcCall.taskFinish(adBizNo))
        if (task.optBoolean("success") && parseJsonObject(DebugRpcCall.queryAdFinished(adBizNo, "NEVERLAND_DOUBLE_AWARD_AD")).optBoolean("success")) {
            StructuredLog.farm("完成双倍奖励🎁")
        }
    }

    private fun queryAreaTrees() = runProbe("queryAreaTrees") {
        val root = parseJsonObject(ReserveRpcCall.queryAreaTrees())
        if (!ResChecker.checkRes(TAG, root)) return@runProbe
        val areaTrees = root.getJSONObject("areaTrees")
        val regions = root.getJSONObject("regionConfig")
        for (key in regions.keys) {
            if (!areaTrees.has(key)) StructuredLog.debug(TAG, "未解锁地区🗺️[${regions.getJSONObject(key).optString("regionName")}]")
        }
    }

    private fun getUnlockTreeItems() = runProbe("getUnlockTreeItems") {
        val root = parseJsonObject(ReserveRpcCall.queryTreeItemsForExchange("", "project"))
        if (!ResChecker.checkRes(TAG, root)) return@runProbe
        val items = root.optJSONArray("treeItems") ?: return@runProbe
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            if (item.optInt("certCountForAlias", -1) == 0) {
                StructuredLog.debug(TAG, "未解锁项目🐘[${item.optString("region")}-${item.optString("itemName")}]#${item.optString("organization")}")
            }
        }
    }

    private inline fun runProbe(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "$name err:")
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    private companion object { const val TAG = "Rpc测试" }
}
