package io.github.magisk317.sesame.task.antDodo

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

object AntDodoRpcCall {
    private const val VERSION = "20241203"
    private const val SOURCE = "chInfo_ch_appcenter__chsub_9patch"

    @JvmStatic
    fun queryAnimalStatus(): String = request("alipay.antdodo.rpc.h5.queryAnimalStatus", jsonObject("source" to SOURCE))

    @JvmStatic
    fun homePage(): String = request("alipay.antdodo.rpc.h5.homePage", jsonObject())

    @JvmStatic
    fun collect(): String = request("alipay.antdodo.rpc.h5.collect", jsonObject())

    @JvmStatic
    fun taskList(): String = request("alipay.antdodo.rpc.h5.taskList", jsonObject("version" to VERSION))

    @JvmStatic
    fun finishTask(sceneCode: String, taskType: String): String {
        val id = uniqueId()
        return request(
            "com.alipay.antiep.finishTask",
            jsonObject("outBizNo" to id, "requestType" to "rpc", "sceneCode" to sceneCode,
                "source" to "af-biodiversity", "taskType" to taskType, "uniqueId" to id),
        )
    }

    @JvmStatic
    fun receiveTaskAward(sceneCode: String, taskType: String): String = request(
        "com.alipay.antiep.receiveTaskAward",
        jsonObject("ignoreLimit" to 0, "requestType" to "rpc", "sceneCode" to sceneCode,
            "source" to "af-biodiversity", "taskType" to taskType),
    )

    @JvmStatic
    fun propList(): String = request("alipay.antdodo.rpc.h5.propList", jsonObject())

    @JvmStatic
    fun consumeProp(propId: String, propType: String, animalId: String?): String = request(
        "alipay.antdodo.rpc.h5.consumeProp",
        jsonObject("propId" to propId, "propType" to propType,
            "extendInfo" to animalId?.takeIf { it.isNotEmpty() }?.let { jsonObject("animalId" to it) }),
    )

    @JvmStatic
    fun consumePropForFriend(propId: String, propType: String): String =
        request("alipay.antdodo.rpc.h5.consumeProp", jsonObject("propId" to propId, "propType" to propType))

    @JvmStatic
    fun queryBookInfo(bookId: String): String = request("alipay.antdodo.rpc.h5.queryBookInfo", jsonObject("bookId" to bookId))

    @JvmStatic
    fun social(targetAnimalId: String, targetUserId: String): String = request(
        "alipay.antdodo.rpc.h5.social",
        jsonObject("actionCode" to "GIFT_TO_FRIEND", "source" to "GIFT_TO_FRIEND_FROM_CC",
            "targetAnimalId" to targetAnimalId, "targetUserId" to targetUserId,
            "triggerTime" to System.currentTimeMillis().toString()),
    )

    @JvmStatic
    fun queryFriend(): String = request("alipay.antdodo.rpc.h5.queryFriend", jsonObject("sceneCode" to "EXCHANGE"))

    @JvmStatic
    fun collecttarget(targetUserId: String): String = request(
        "alipay.antdodo.rpc.h5.collect",
        jsonObject("targetUserId" to (targetUserId.toLongOrNull() ?: targetUserId)),
    )

    @JvmStatic
    fun queryBookList(pageSize: Int, pageStart: String?): String = request(
        "alipay.antdodo.rpc.h5.queryBookList",
        jsonObject("pageSize" to pageSize, "v2" to "true", "pageStart" to pageStart),
    )

    @JvmStatic
    fun generateBookMedal(bookId: String): String =
        request("alipay.antdodo.rpc.h5.generateBookMedal", jsonObject("bookId" to bookId))

    private fun uniqueId(): String = System.currentTimeMillis().toString() + RandomUtil.nextLong()

    private fun request(method: String, value: JsonObject): String =
        RequestManager.requestString(method, jsonArray(value).toString())
}
