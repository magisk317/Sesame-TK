package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.RandomUtil
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.security.MessageDigest
import java.util.UUID

/** RPCs that define the farm home, animal lifecycle and farm task state. */
object FarmCoreRpc {
    private const val VERSION = "1.8.2302070202.46"

    fun enterFarm(userId: String?, targetUserId: String?): String = request(
        "com.alipay.antfarm.enterFarm",
        json(
            "animalId" to "", "bizCode" to "", "gotoneScene" to "", "gotoneTemplateId" to "",
            "groupId" to "", "growthExtInfo" to "", "inviteUserId" to "", "masterFarmId" to "",
            "queryLastRecordNum" to true, "recall" to false, "requestType" to "NORMAL",
            "sceneCode" to "ANTFARM", "shareId" to "", "shareUniqueId" to "${System.currentTimeMillis()}_$targetUserId",
            "source" to "ANTFOREST", "starFarmId" to "", "subBizCode" to "", "touchRecordId" to "",
            "userId" to userId, "userToken" to "", "version" to VERSION,
        ),
    )

    fun syncAnimalStatus(farmId: String?, operTag: String?, operType: String?): String = request(
        "com.alipay.antfarm.syncAnimalStatus",
        json("farmId" to farmId, "operTag" to operTag, "operType" to operType,
            "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to VERSION),
    )

    fun sleep(): String = request("com.alipay.antfarm.sleep", json("requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "LOVECABIN", "version" to "unknown"))
    fun sleep(groupId: String?): String = request("com.alipay.antfarm.sleep", json("groupId" to groupId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "spaceType" to "ChickFamily", "version" to "unknown"))
    fun wakeUp(): String = request("com.alipay.antfarm.wakeUp", json("requestType" to "RPC", "sceneCode" to "ANTFARM", "source" to "LOVECABIN", "version" to "unknown"))

    fun feedAnimal(farmId: String?): String = request("com.alipay.antfarm.feedAnimal", json("animalType" to "CHICK", "canMock" to true, "farmId" to farmId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "chInfo_ch_appcollect__chsub_my-recentlyUsed", "version" to VERSION))
    fun feedFriendAnimal(friendFarmId: String?): String = request("com.alipay.antfarm.feedFriendAnimal", json("friendFarmId" to friendFarmId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "chInfo_ch_appcenter__chsub_9patch", "version" to VERSION))
    fun farmId2UserId(farmId: String?): String = (farmId ?: "").substring((farmId?.length ?: 0) / 2)
    fun collectManurePot(manurePotNo: String?): String = request("com.alipay.antfarm.collectManurePot", json("isSkipTempLimit" to true, "manurePotNOs" to manurePotNo, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to VERSION))
    fun sign(): String = request("com.alipay.antfarm.sign", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to VERSION))

    fun harvestProduce(farmId: String?): String = request("com.alipay.antfarm.harvestProduce", json("canMock" to true, "farmId" to farmId, "giftType" to "", "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to VERSION))
    fun listActivityInfo(): String = request("com.alipay.antfarm.listActivityInfo", base())
    fun donation(activityId: String?, donationAmount: Int): String = request("com.alipay.antfarm.donation", with(base(), "activityId" to activityId, "donationAmount" to donationAmount))
    fun listFarmTask(): String = request("com.alipay.antfarm.listFarmTask", base())
    fun receiveFarmTaskAward(taskId: String): String = request("com.alipay.antfarm.receiveFarmTaskAward", with(base(), "taskId" to taskId))
    fun doFarmTask(bizKey: String): String = request("com.alipay.antfarm.doFarmTask", with(base(), "bizKey" to bizKey))

    fun listFarmTool(): String = request("com.alipay.antfarm.listFarmTool", base())
    fun useFarmTool(targetFarmId: String?, toolId: String?, toolType: String?): String = request("com.alipay.antfarm.useFarmTool", with(base(), "targetFarmId" to targetFarmId, "toolId" to toolId, "toolType" to toolType))
    fun rankingList(pageStartSum: Int): String = request("com.alipay.antfarm.rankingList", with(base(), "pageSize" to 20, "startNum" to pageStartSum))
    fun notifyFriend(animalId: String?, notifiedFarmId: String?): String = request("com.alipay.antfarm.notifyFriend", with(base(), "animalId" to animalId, "animalType" to "CHICK", "canBeGuest" to true, "notifiedFarmId" to notifiedFarmId))
    fun visitFriend(friendFarmId: String?): String = request("com.alipay.antfarm.visitFriend", with(base(), "friendFarmId" to friendFarmId))
    fun acceptGift(): String = request("com.alipay.antfarm.acceptGift", with(base(), "ignoreLimit" to false))

    fun rewardFriend(consistencyKey: String?, friendId: String?, productNum: String?, time: String?): String = request("com.alipay.antfarm.rewardFriend", json("canMock" to true, "consistencyKey" to consistencyKey, "friendId" to friendId, "operType" to "1", "productNum" to (productNum?.toIntOrNull() ?: productNum), "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "time" to (time?.toLongOrNull() ?: time), "version" to VERSION))
    fun recallAnimal(animalId: String?, currentFarmId: String?, masterFarmId: String?): String = request("com.alipay.antfarm.recallAnimal", with(base(), "animalId" to animalId, "currentFarmId" to currentFarmId, "masterFarmId" to masterFarmId))
    fun sendBackAnimal(sendType: String?, animalId: String?, currentFarmId: String?, masterFarmId: String?): String = request("com.alipay.antfarm.sendBackAnimal", with(base(), "animalId" to animalId, "currentFarmId" to currentFarmId, "masterFarmId" to masterFarmId, "sendType" to sendType))

    fun randomScore(gameType: String): Int = when (gameType) {
        "starGame" -> RandomUtil.nextInt(300, 400)
        "jumpGame" -> RandomUtil.nextInt(250, 270) * 10
        "flyGame" -> RandomUtil.nextInt(4000, 8000)
        "hitGame" -> RandomUtil.nextInt(80, 120)
        else -> 210
    }

    fun initFarmGame(gameType: String): String = request("com.alipay.antfarm.initFarmGame", gamePayload(gameType, false))
    fun recordFarmGame(gameType: String): String {
        val uuid = UUID.randomUUID().toString().split('-').joinToString("") { it.substring(it.length / 2) }
        val score = randomScore(gameType)
        return request("com.alipay.antfarm.recordFarmGame", with(gamePayload(gameType, true), "md5" to md5(uuid), "score" to score, "uuid" to uuid, "foodCount" to (score / 50).takeIf { gameType == "flyGame" }))
    }

    private fun gamePayload(gameType: String, record: Boolean): JsonObject = json(
        "gameType" to gameType, "requestType" to if (gameType == "flyGame" || gameType == "hitGame") "RPC" else "NORMAL",
        "sceneCode" to when (gameType) { "flyGame" -> "FLAYGAME"; "hitGame" -> "HITGAME"; else -> "ANTFARM" },
        "source" to if (gameType == "flyGame" || gameType == "hitGame") "ANTFARM" else "H5",
        "toolTypes" to if (gameType == "flyGame" || gameType == "hitGame") "ACCELERATETOOL,SHARETOOL,NONE" else "STEALTOOL,ACCELERATETOOL,SHARETOOL",
    )

    private fun base() = json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to VERSION)
    private fun json(vararg values: Pair<String, Any?>): JsonObject = jsonObject(*values)
    private fun with(base: JsonObject, vararg values: Pair<String, Any?>): JsonObject = buildJsonObject {
        base.forEach { (key, value) -> put(key, value) }
        jsonObject(*values).forEach { (key, value) -> put(key, value) }
    }
    private fun request(method: String, payload: JsonObject): String = RequestManager.requestString(method, jsonArray(payload).toString())
    private fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
