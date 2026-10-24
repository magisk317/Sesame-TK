package io.github.magisk317.sesame.task.antOcean

import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StringUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.length
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray

/** Fish combination, chapter progression, and ecological protection. */
object OceanProtectionService {
    private const val TAG = "AntOcean"

    fun querySeaArea() {
        try {
            val response = OceanCoreRpc.querySeaAreaDetailList().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询海洋区域详情失败:", response)) return
            if (response.optBoolean("awardSeaAreaCanCreateExtraCollect")) {
                val created = OceanCoreRpc.createSeaAreaExtraCollect().toJsonObject()
                if (ResChecker.checkRes("$TAG 接取限时挑战:", created)) {
                    StructuredLog.forest("限时挑战🌊接取成功")
                    querySeaArea()
                    return
                }
            }
            val areas = response.optJSONArray("seaAreaVOs") ?: return
            for (index in 0 until areas.length()) {
                val area = areas.getJSONObject(index)
                combineCompleted(area.optJSONArray("fishVO"), "")
                val extra = area.optJSONObject("seaAreaExtraCollectVO")?.optJSONArray("fishVO")
                combineCompleted(extra, "EXTRA_COLLECT")
            }
            if (response.optInt("currentSeaAreaIndex") < response.optInt("fixSeaAreaNum") &&
                response.optInt("seaAreaNum") > response.optInt("fixSeaAreaNum")) {
                OceanPropService.repair()
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "querySeaAreaDetailList err:", t)
        }
    }

    private fun combineCompleted(fishList: JsonArray?, logType: String) {
        if (fishList == null) return
        for (index in 0 until fishList.length()) {
            val fish = fishList.optJSONObject(index) ?: continue
            if (fish.optBoolean("unlock") || fish.optString("status") != "COMPLETED") continue
            combineFish(fish.optString("id"), logType, fish.optString("name", "未知鱼类"))
        }
    }

    private fun combineFish(fishId: String, logType: String, fallbackName: String) {
        try {
            val response = OceanCultivationRpc.combineFish(fishId).toJsonObject()
            if (!ResChecker.checkRes("$TAG 合成海洋鱼类失败:", response)) return
            val name = response.optJSONObject("fishDetailVO")?.optString("name", fallbackName) ?: fallbackName
            StructuredLog.forest(if (logType == "EXTRA_COLLECT") "限时挑战🌊[$name]合成成功" else "神奇海洋🌊[$name]合成成功")
            checkAndCreateExtraCollect()
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "combineFish err:", t)
        }
    }

    fun checkRewards(rewards: JsonArray?) {
        if (rewards == null) return
        try {
            for (index in 0 until rewards.length()) {
                val reward = rewards.getJSONObject(index)
                val attached = reward.optJSONArray("attachRewardBOList") ?: continue
                if (attached.length() == 0) continue
                StructuredLog.forest("神奇海洋🌊[获得:${reward.optString("name")}碎片]")
                val canCombine = (0 until attached.length()).all { attached.getJSONObject(it).optInt("count") != 0 }
                if (canCombine && reward.optBoolean("unlock")) {
                    combineFish(reward.optString("id"), "", reward.optString("name", "未知鱼类"))
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "checkReward err:", t)
        }
    }

    fun switchChapter() {
        try {
            val response = OceanCultivationRpc.queryOceanChapterList().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询海洋章节列表失败:", response)) return
            val current = response.optString("currentChapterCode")
            var finished = false
            var targetCode = ""
            var targetName = ""
            val chapters = response.optJSONArray("userChapterDetailVOList") ?: return
            for (index in 0 until chapters.length()) {
                val chapter = chapters.getJSONObject(index)
                val complete = chapter.optInt("repairedSeaAreaNum") >= chapter.optInt("seaAreaNum")
                if (chapter.optString("chapterCode") == current) {
                    finished = complete
                } else if (!complete && chapter.optBoolean("chapterOpen")) {
                    targetCode = chapter.optString("chapterCode")
                    targetName = chapter.optString("chapterName")
                }
            }
            if (finished && !StringUtil.isEmpty(targetCode)) {
                StructuredLog.record(TAG, "当前海域已完成，等待切换...")
                GlobalThreadPools.sleepCompat(5000)
                val result = OceanCultivationRpc.switchOceanChapter(targetCode).toJsonObject()
                if (ResChecker.checkRes("$TAG 切换海洋章节失败:", result)) StructuredLog.forest("神奇海洋🌊切换到[$targetName]系列")
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "switchOceanChapter err:", t)
        }
    }

    fun protect(selected: Map<String?, Int?>) {
        try {
            val response = OceanCultivationRpc.queryCultivationList().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询海洋培育列表失败:", response)) return
            val list = response.optJSONArray("cultivationItemVOList") ?: return
            for (index in 0 until list.length()) {
                val item = list.getJSONObject(index)
                val count = selected[item.optString("templateCode")] ?: continue
                if (count <= 0) continue
                val project = item.optJSONObject("projectConfigVO")?.optString("code") ?: continue
                exchangeTree(item.optString("templateCode"), project, item.optString("cultivationName"), count)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "protectOcean err:", t)
        }
    }

    private fun exchangeTree(code: String, project: String, name: String, count: Int) {
        var applied = queryDetail(code, project, count)
        if (applied < 0) return
        for (index in 1..count) {
            try {
                val response = OceanCultivationRpc.oceanExchangeTree(code, project).toJsonObject()
                if (!ResChecker.checkRes("$TAG 海洋兑换树木失败:", response)) break
                val awards = response.optJSONArray("rewardItemVOs") ?: buildJsonArray { }
                val award = buildString {
                    for (awardIndex in 0 until awards.length()) {
                        val item = awards.getJSONObject(awardIndex)
                        append(item.optString("name")).append('*').append(item.optInt("num"))
                    }
                }
                StructuredLog.forest("保护海洋生态🏖️[$name]#第${applied}次-获得奖励$award")
                GlobalThreadPools.sleepCompat(300)
                applied = queryDetail(code, project, count)
                if (applied < 0) break
                GlobalThreadPools.sleepCompat(300)
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "海洋保护错误:", t)
                break
            }
        }
    }

    private fun queryDetail(code: String, project: String, count: Int): Int {
        return try {
            val response = OceanCultivationRpc.queryCultivationDetail(code, project).toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询海洋培育详情失败:", response)) return -1
            val user = response.optJSONObject("userInfoVO") ?: return -1
            val detail = response.optJSONObject("cultivationDetailVO") ?: return -1
            if (detail.optString("applyAction") != "AVAILABLE") {
                StructuredLog.forest("保护海洋🏖️[${detail.optString("cultivationName")}]#似乎没有了")
                return -1
            }
            if (user.optInt("currentEnergy") < detail.optInt("energy")) {
                StructuredLog.forest("保护海洋🏖️[${detail.optString("cultivationName")}]#能量不足停止申请")
                return -1
            }
            val cert = detail.optInt("certNum")
            if (cert < count) cert + 1 else -1
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryCultivationDetail err:", t)
            -1
        }
    }

    private fun checkAndCreateExtraCollect() {
        try {
            val response = OceanCoreRpc.querySeaAreaDetailList().toJsonObject()
            if (!ResChecker.checkRes("$TAG 复查海洋区域详情:", response)) return
            if (!response.optBoolean("awardSeaAreaCanCreateExtraCollect")) return
            val result = OceanCoreRpc.createSeaAreaExtraCollect().toJsonObject()
            if (ResChecker.checkRes("$TAG 接取限时挑战:", result)) StructuredLog.forest("限时挑战🌊接取成功")
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "checkAndCreateExtraCollect err:", t)
        }
    }
}
