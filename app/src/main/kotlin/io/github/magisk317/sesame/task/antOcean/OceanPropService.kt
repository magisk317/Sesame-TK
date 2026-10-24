package io.github.magisk317.sesame.task.antOcean

import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.getJSONObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Universal-piece exchange and application. */
object OceanPropService {
    private const val TAG = "AntOcean"

    fun repair() {
        try {
            val response = OceanPropRpc.queryExchangePropList().toJsonObject()
            if (ResChecker.checkRes("$TAG 查询海洋道具列表失败:", response)) {
                OceanCoreRpc.repairSeaArea()
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryOceanPropList err:", t)
        }
    }

    fun exchange() {
        try {
            while (true) {
                val list = OceanPropRpc.queryExchangePropList().toJsonObject()
                if (!ResChecker.checkRes("$TAG 查询海洋道具兑换列表失败:", list) || list.optInt("duplicatePieceNum") < 10) return
                val result = OceanPropRpc.exchangeProp().toJsonObject()
                if (!ResChecker.checkRes("$TAG 海洋道具兑换失败:", result)) return
                StructuredLog.forest("神奇海洋🏖️[万能拼图]制作${result.optString("exchangeNum")}张,剩余${result.optString("duplicatePieceNum")}张碎片")
                GlobalThreadPools.sleepCompat(1000)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "exchangeProp error:", t)
        }
    }

    fun useByType() {
        try {
            val response = OceanPropRpc.queryUsePropList().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询海洋道具使用类型列表失败:", response)) return
            val types = response.optJSONArray("oceanPropVOByTypeList") ?: return
            for (typeIndex in 0 until types.length()) {
                val type = types.getJSONObject(typeIndex)
                var remaining = type.optInt("holdsNum")
                var page = 0
                while (remaining > 0) {
                    val fishResponse = OceanPropRpc.queryFishList(++page).toJsonObject()
                    if (!ResChecker.checkRes("$TAG 查询海洋鱼类列表失败:", fishResponse)) break
                    val fishList = fishResponse.optJSONArray("fishVOS") ?: break
                    for (fishIndex in 0 until fishList.length()) {
                        val fish = fishList.getJSONObject(fishIndex)
                        val pieces = fish.optJSONArray("pieces") ?: continue
                        val missing = linkedSetOf<Int>()
                        for (pieceIndex in 0 until pieces.length()) {
                            val piece = pieces.getJSONObject(pieceIndex)
                            if (piece.optInt("num") == 0) {
                                missing.add(piece.optString("id").toInt())
                                remaining--
                                if (remaining <= 0) break
                            }
                        }
                        if (missing.isEmpty()) continue
                        val result = OceanPropRpc.usePropByType(fish.optInt("order"), missing)?.toJsonObject() ?: continue
                        if (ResChecker.checkRes("$TAG 使用海洋万能拼图失败:", result)) {
                            StructuredLog.forest("神奇海洋🏖️[万能拼图]使用${missing.size}张，获得[${fish.optString("name")}]剩余${remaining}张")
                            GlobalThreadPools.sleepCompat(1000)
                        }
                        if (remaining <= 0) break
                    }
                    if (!fishResponse.optBoolean("hasMore")) break
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "usePropByType error:", t)
        }
    }
}
