package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.util.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Owns partner dispatch and animal-piece combination workflows. */
class ForestAnimalService {
    private val tag = "ForestAnimalService"
    private fun parse(raw: String): JsonObject = raw.toJsonObject()

    fun dispatchPartner() {
        try {
            val response = parse(ForestPropRpc.animalList())
            if (!ResChecker.checkRes(tag, response)) {
                StructuredLog.record(tag, response.getString("resultDesc"))
                return
            }
            val animals = response.getJSONArray("animalProps")
            var selected: JsonObject? = null
            for (index in 0..<animals.length()) {
                val candidate = animals.getJSONObject(index)
                val current = selected
                if (current == null || candidate.getJSONObject("main").getInt("holdsNum") >
                    current.getJSONObject("main").getInt("holdsNum")) {
                    selected = candidate
                }
            }
            selected?.let(::consumePartner)
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "queryAnimalPropList err", t)
        }
    }

    private fun consumePartner(animalProp: JsonObject) {
        try {
            val main = animalProp.getJSONObject("main")
            val name = animalProp.getJSONObject("partner").getString("name")
            val response = parse(ForestPropRpc.consume(main.getString("propGroup"), "", main.getString("propType"), false))
            if (ResChecker.checkRes(tag + "巡护派遣失败:", response)) {
                StructuredLog.forest("巡护派遣🐆[$name]")
            } else {
                StructuredLog.record(tag, response.getString("resultDesc"))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "consumeAnimalProp err", t)
        }
    }

    fun combinePieces() {
        try {
            val response = parse(ForestAnimalRpc.queryAnimalAndPiece(0))
            if (response.optString("resultCode") != "SUCCESS") {
                StructuredLog.record(tag, "查询失败: ${response.optString("resultDesc")}")
                return
            }
            val animals = response.optJSONArray("animalProps")
            if (animals == null || animals.length() == 0) {
                StructuredLog.record(tag, "动物属性列表为空")
                return
            }
            for (index in 0..<animals.length()) {
                val animal = animals.optJSONObject(index) ?: continue
                val pieces = animal.optJSONArray("pieces") ?: continue
                val animalId = animal.optJSONObject("animal")?.optInt("id", -1) ?: -1
                if (animalId >= 0 && canCombine(pieces)) combine(animalId)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(tag, "queryAnimalAndPiece err", t)
        }
    }

    private fun canCombine(pieces: JsonArray): Boolean = (0..<pieces.length()).all { index ->
        pieces.optJSONObject(index)?.optInt("holdsNum", 0)?.let { it > 0 } == true
    }

    private fun combine(initialAnimalId: Int) {
        var animalId = initialAnimalId
        while (true) {
            try {
                val response = parse(ForestAnimalRpc.queryAnimalAndPiece(animalId))
                if (response.optString("resultCode") != "SUCCESS") {
                    StructuredLog.record(tag, "查询失败: ${response.optString("resultDesc")}")
                    return
                }
                val animalProp = response.optJSONArray("animalProps")?.getJSONObject(0) ?: return
                val animal = animalProp.optJSONObject("animal") ?: return
                val pieces = animalProp.optJSONArray("pieces") ?: return
                val ids = mutableListOf<String>()
                for (index in 0..<pieces.length()) {
                    val piece = pieces.optJSONObject(index)
                    if (piece == null || piece.optInt("holdsNum", 0) <= 0) {
                        StructuredLog.record(tag, "碎片不足，无法合成动物")
                        return
                    }
                    piece.optJSONArray("propIdList")?.optString(0, "")?.takeIf { it.isNotEmpty() }?.let(ids::add)
                }
                val result = parse(ForestAnimalRpc.combineAnimalPiece(animal.optInt("id", -1), JsonArray(ids.map(::JsonPrimitive)).toString()))
                if (result.optString("resultCode") != "SUCCESS") {
                    StructuredLog.record(tag, "合成失败: ${result.optString("resultDesc")}")
                    return
                }
                StructuredLog.forest("成功合成动物💡[${animal.optString("name", "未知动物")}]")
                animalId = animal.optInt("id", animalId)
                GlobalThreadPools.sleepCompat(100)
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(tag, "combineAnimalPiece err", t)
                return
            }
        }
    }
}
