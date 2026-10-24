package io.github.magisk317.sesame.task.reserve

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.entity.ReserveEntity
import io.github.magisk317.sesame.model.ModelFields
import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountModelField
import io.github.magisk317.sesame.task.ModelTask
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.ReserveaMap
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.serialization.json.JsonObject
import io.github.magisk317.sesame.model.ModelDefinition
import io.github.magisk317.sesame.model.ModelOrder

private fun parseReserveJson(raw: String): JsonObject = raw.toJsonObject()

/** Reserve area exchange task. Selection is owned by the model field; RPC is owned by [ReserveRpcCall]. */
class Reserve : ModelTask() {
    private lateinit var reserveList: SelectAndCountModelField

    override fun getName() = "保护地"
    override fun getGroup() = ModelGroup.FOREST
    override fun getIcon() = "Reserve.png"

    override fun getFields(): ModelFields = ModelFields().also {
        reserveList = SelectAndCountModelField("reserveList", "保护地列表", emptyMap(), ReserveEntity::getList, "顾名思义")
        it.addField(reserveList)
    }

    override fun runJava() {
        try {
            initReserve()
            animalReserve()
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "保护地🏕️ 执行异常:", t)
        }
    }

    private fun animalReserve() {
        try {
            val response = ReserveRpcCall.queryTreeItemsForExchange()
            val root = parseReserveJson(response)
            if (!ResChecker.checkRes(TAG, root)) {
                StructuredLog.record(TAG, root.optString("resultDesc"))
                return
            }
            val selected = reserveList.value
            val items = root.getJSONArray("treeItems")
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                if (item.optString("projectType") != "RESERVE" || item.optString("applyAction") != "AVAILABLE") continue
                val projectId = item.getString("itemId")
                val count = selected[projectId] ?: continue
                if (count > 0 && Status.canReserveToday(projectId, count)) exchangeTree(projectId, item.getString("itemName"), count)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "animalReserve err:", t)
        }
    }

    private fun canExchange(projectId: String): Boolean = try {
        val root = parseReserveJson(ReserveRpcCall.queryTreeForExchange(projectId))
        if (!ResChecker.checkRes(TAG, root)) {
            StructuredLog.record(TAG, root.optString("resultDesc"))
            return false
        }
        val action = root.getString("applyAction")
        val energy = root.getInt("currentEnergy")
        val tree = root.getJSONObject("exchangeableTree")
        when {
            action != "AVAILABLE" -> {
                StructuredLog.forest("领保护地🏕️[${tree.getString("projectName")}]#似乎没有了")
                false
            }
            energy < tree.getInt("energy") -> {
                StructuredLog.forest("领保护地🏕️[${tree.getString("projectName")}]#能量不足停止申请")
                false
            }
            else -> true
        }
    } catch (t: Throwable) {
        StructuredLog.printStackTrace(TAG, "queryTreeForExchange err:", t)
        false
    }

    private fun exchangeTree(projectId: String, itemName: String, count: Int) {
        try {
            if (!canExchange(projectId)) return
            for (applyCount in 1..count) {
                val root = parseReserveJson(ReserveRpcCall.exchangeTree(projectId))
                if (!ResChecker.checkRes(TAG, root)) {
                    StructuredLog.error("领保护地🏕️[$itemName]#发生未知错误，停止申请")
                    break
                }
                val applied = Status.getReserveTimes(projectId) + 1
                val vitality = root.optInt("vitalityAmount", 0)
                StructuredLog.forest("领保护地🏕️[$itemName]#第${applied}次" + if (vitality > 0) "-活力值+$vitality" else "")
                Status.reserveToday(projectId, 1)
                GlobalThreadPools.sleepCompat(300)
                if (!canExchange(projectId)) break
                GlobalThreadPools.sleepCompat(300)
                if (!Status.canReserveToday(projectId, count)) break
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "exchangeTree err:", t)
        }
    }

    companion object {
        private const val TAG = "Reserve"

        @JvmStatic
        fun initReserve() {
            try {
                val root = parseReserveJson(ReserveRpcCall.queryTreeItemsForExchange())
                if (!ResChecker.checkRes(TAG, root)) {
                    StructuredLog.error(root.optString("resultDesc", "未知错误"))
                    return
                }
                root.optJSONArray("treeItems")?.let { items ->
                    for (index in 0 until items.length()) {
                        val item = items.getJSONObject(index)
                        if (item.optString("projectType") == "RESERVE" && item.optString("applyAction") == "AVAILABLE") {
                            IdMapManager.getInstance(ReserveaMap::class.java).add(
                                item.getString("itemId"), "${item.getString("itemName")}(${item.getInt("energy")}g)",
                            )
                        }
                    }
                }
                IdMapManager.getInstance(ReserveaMap::class.java).save()
                StructuredLog.record(TAG, "初始化保护地任务成功。")
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "初始化保护地任务时出错：", t)
                IdMapManager.getInstance(ReserveaMap::class.java).load()
            }
        }
        init {
            ModelOrder.register(ModelDefinition(Reserve::class.java) { Reserve() })
        }
    }
}
