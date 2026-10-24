package io.github.magisk317.sesame.hook.rpc.debug

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** RPC entry points used by the debug screen and its probes. */
object DebugRpcCall {
    private const val SOURCE = "fuqiTown"
    private const val FOREST_SOURCE = "chInfo_ch_appcenter__chsub_9patch"

    private fun request(method: String, value: JsonObject): String =
        RequestManager.requestString(method, jsonArray(value).toString())

    @JvmStatic
    fun queryBaseinfo(): String = request(
        "com.alipay.neverland.biz.rpc.queryBaseinfo",
        jsonObject("branchId" to "WUFU", "source" to SOURCE),
    )

    @JvmStatic
    fun walkGrid(): String = request(
        "com.alipay.neverland.biz.rpc.walkGrid",
        jsonObject("drilling" to false, "mapId" to "MF1", "source" to SOURCE),
    )

    @JvmStatic
    fun miniGameFinish(gameId: String, gameKey: String): String = request(
        "com.alipay.neverland.biz.rpc.miniGameFinish",
        jsonObject("gameId" to gameId, "gameKey" to gameKey, "mapId" to "MF1", "score" to 490, "source" to SOURCE),
    )

    @JvmStatic
    fun taskFinish(bizId: String): String = request(
        "com.alipay.adtask.biz.mobilegw.service.task.finish",
        jsonObject("bizId" to bizId),
    )

    @JvmStatic
    fun queryAdFinished(bizId: String, scene: String): String = request(
        "com.alipay.neverland.biz.rpc.queryAdFinished",
        jsonObject("adBizNo" to bizId, "scene" to scene, "source" to SOURCE),
    )

    @JvmStatic
    fun queryWufuTaskHall(): String = request(
        "com.alipay.neverland.biz.rpc.queryWufuTaskHall",
        jsonObject("source" to SOURCE),
    )

    @JvmStatic
    fun fuQiTaskQuery(): String = request(
        "com.alipay.wufudragonprod.biz.wufu2024.fuQiTown.fuQiTask.query",
        jsonObject(),
    )

    @JvmStatic
    fun fuQiTaskTrigger(appletId: String, stageCode: String): String = request(
        "com.alipay.wufudragonprod.biz.wufu2024.fuQiTown.fuQiTask.trigger",
        jsonObject("appletId" to appletId, "stageCode" to stageCode),
    )

    @JvmStatic
    fun queryEnvironmentCertDetailList(alias: String, pageNum: Int, targetUserID: String): String = request(
        "alipay.antforest.forest.h5.queryEnvironmentCertDetailList",
        jsonObject(
            "alias" to alias, "certId" to "", "pageNum" to pageNum,
            "shareId" to "", "source" to FOREST_SOURCE,
            "targetUserID" to targetUserID, "version" to "20230701",
        ),
    )

    @JvmStatic
    fun sendTree(certificateId: String, friendUserId: String): String = request(
        "alipay.antforest.forest.h5.sendTree",
        jsonObject(
            "blessWords" to "梭梭没有叶子，四季常青，从不掉发，祝你发量如梭。",
            "certificateId" to certificateId, "friendUserId" to friendUserId,
            "source" to FOREST_SOURCE,
        ),
    )
}
