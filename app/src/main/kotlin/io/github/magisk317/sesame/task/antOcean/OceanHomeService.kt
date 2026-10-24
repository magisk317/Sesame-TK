package io.github.magisk317.sesame.task.antOcean

import io.github.magisk317.sesame.entity.AlipayBeach
import io.github.magisk317.sesame.hook.Toast
import io.github.magisk317.sesame.task.antForest.ForestCoreRpc
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.getInt
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.BeachMap
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Home-page state, energy collection, and friend interaction for ocean. */
object OceanHomeService {
    private const val TAG = "AntOcean"

    fun isOpened(protectType: Int): Boolean {
        return try {
            val response = OceanCoreRpc.queryOceanStatus().toJsonObject()
            if (!ResChecker.checkRes(TAG, response)) return false
            if (!response.optBoolean("opened")) {
                StructuredLog.record(TAG, "请先开启神奇海洋,并完成引导教程")
                return false
            }
            initBeach(protectType)
            true
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryOceanStatus err:", t)
            false
        }
    }

    fun initBeach(protectType: Int) {
        try {
            val response = OceanCultivationRpc.queryCultivationList().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询种植列表失败:", response)) {
                IdMapManager.getInstance(BeachMap::class.java).load()
                return
            }
            val items = response.optJSONArray("cultivationItemVOList") ?: return
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                if (OceanOptions.ApplyAction.fromString(item.optString("applyAction")) != OceanOptions.ApplyAction.AVAILABLE) continue
                val subtype = item.optString("templateSubType")
                if (protectType == OceanOptions.PROTECT_ALL || (protectType == OceanOptions.PROTECT_BEACH && subtype != "BEACH")) {
                    IdMapManager.getInstance(BeachMap::class.java).add(
                        item.getString("templateCode"),
                        "${item.getString("cultivationName")}(${item.getInt("energy")}g)",
                    )
                }
            }
            StructuredLog.record(TAG, "初始化沙滩数据成功。")
            IdMapManager.getInstance(BeachMap::class.java).save()
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "初始化沙滩任务时出错", t)
            IdMapManager.getInstance(BeachMap::class.java).load()
        }
    }

    fun processHome(cleanEnabled: Boolean, cleanType: Int, selectedFriends: Set<String?>) {
        try {
            val home = OceanCoreRpc.queryHomePage().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询海洋主页失败:", home)) return
            home.optJSONArray("bubbleVOList")?.let(::collectEnergy)
            val user = home.optJSONObject("userInfoVO") ?: return
            val userId = user.getString("userId")
            cleanOcean(userId, user.optInt("rubbishNumber"))
            if (user.optJSONObject("ipVO")?.optInt("surprisePieceNum") ?: 0 > 0) openSurprise()
            OceanProtectionService.querySeaArea()
            queryMisc()
            OceanReplicaService.processHome()
            if (cleanEnabled) queryRanking(cleanType, selectedFriends)
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryHomePage err:", t)
        }
    }

    private fun collectEnergy(bubbles: JsonArray) {
        for (index in 0 until bubbles.length()) {
            try {
                val bubble = bubbles.getJSONObject(index)
                if (bubble.optString("channel") != "ocean" || bubble.optString("collectStatus") != "AVAILABLE") continue
                val userId = bubble.getString("userId")
                val result = ForestCoreRpc.collectEnergy("", userId, bubble.getLong("id")).toJsonObject()
                if (!ResChecker.checkRes("$TAG 收取海洋能量失败:", result)) continue
                val returned = result.optJSONArray("bubbles") ?: continue
                for (itemIndex in 0 until returned.length()) {
                    val item = returned.optJSONObject(itemIndex) ?: continue
                    val amount = item.optInt("collectedEnergy")
                    StructuredLog.forest("神奇海洋🌊收取[${UserMap.getMaskName(userId)}]#${amount}g")
                    Toast.show("海洋能量🌊收取[${UserMap.getMaskName(userId)}]#${amount}g")
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "collectEnergy err:", t)
            }
        }
    }

    private fun cleanOcean(userId: String, count: Int) {
        repeat(count) {
            try {
                val response = OceanCoreRpc.cleanOcean(userId).toJsonObject()
                if (ResChecker.checkRes("$TAG 清理海洋失败:", response)) {
                    OceanProtectionService.checkRewards(response.optJSONArray("cleanRewardVOS"))
                    StructuredLog.forest("神奇海洋🌊[清理:${UserMap.getMaskName(userId)}海域]")
                }
            } catch (t: Throwable) {
                StructuredLog.printStackTrace(TAG, "cleanOcean err:", t)
            }
        }
    }

    private fun openSurprise() {
        try {
            val response = OceanCoreRpc.ipOpenSurprise().toJsonObject()
            if (ResChecker.checkRes("$TAG 开启海洋惊喜失败:", response)) {
                OceanProtectionService.checkRewards(response.optJSONArray("surpriseRewardVOS"))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "ipOpenSurprise err:", t)
        }
    }

    private fun queryMisc() {
        try {
            val response = OceanCoreRpc.queryMiscInfo().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询海洋杂项信息失败:", response)) return
            val tips = response.optJSONObject("miscHandlerVOMap")?.optJSONObject("HOME_TIPS_REFRESH")
            if (tips?.optBoolean("fishCanBeCombined") == true || tips?.optBoolean("canBeRepaired") == true) {
                OceanProtectionService.querySeaArea()
            }
            OceanProtectionService.switchChapter()
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryMiscInfo err:", t)
        }
    }

    private fun queryRanking(cleanType: Int, selectedFriends: Set<String?>) {
        try {
            val response = OceanCoreRpc.queryUserRanking().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询海洋用户排行榜失败:", response)) return
            val list = response.optJSONArray("fillFlagVOList") ?: return
            for (index in 0 until list.length()) {
                val flag = list.optJSONObject(index) ?: continue
                if (flag.optBoolean("canClean")) cleanFriend(flag, cleanType, selectedFriends)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "queryUserRanking err:", t)
        }
    }

    private fun cleanFriend(flag: JsonObject, cleanType: Int, selectedFriends: Set<String?>) {
        val userId = flag.optString("userId")
        val selected = selectedFriends.contains(userId)
        if (if (cleanType == OceanOptions.DONT_CLEAN) selected else !selected) return
        try {
            val page = OceanCoreRpc.queryFriendPage(userId).toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询好友海洋页面失败:", page)) return
            val response = OceanCoreRpc.cleanFriendOcean(userId).toJsonObject()
            if (ResChecker.checkRes("$TAG 清理好友海洋失败:", response)) {
                StructuredLog.forest("神奇海洋🌊[帮助:${UserMap.getMaskName(userId)}清理海域]")
                OceanProtectionService.checkRewards(response.optJSONArray("cleanRewardVOS"))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "cleanFriendOcean err:", t)
        }
    }
}
