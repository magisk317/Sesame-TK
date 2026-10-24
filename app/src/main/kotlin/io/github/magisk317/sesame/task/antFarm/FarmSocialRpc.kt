package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** Friend interaction, feed sharing and chicken diary RPC boundary. */
object FarmSocialRpc {
    private const val VERSION = "1.8.2302070202.46"
    fun letsGetChickenFeedTogether() = request("com.alipay.antiep.canInvitePersonListP2P", json("needHasInviteUserByCycle" to "true", "requestType" to "RPC", "sceneCode" to "ANTFARM_P2P", "source" to "ANTFARM", "startIndex" to 0, "version" to VERSION))
    fun giftOfFeed(bizTraceId: String?, userId: String?) = request("com.alipay.antiep.inviteP2P", json("beInvitedUserId" to userId, "bizTraceId" to bizTraceId, "requestType" to "RPC", "sceneCode" to "ANTFARM_P2P", "source" to "ANTFARM", "version" to VERSION))
    fun queryChickenDiaryList() = request("com.alipay.antfarm.queryChickenDiaryList", json("requestType" to "NORMAL", "sceneCode" to "DIARY", "source" to "antfarm_icon"))
    fun queryChickenDiaryList(yearMonth: String?) = request("com.alipay.antfarm.queryChickenDiaryList", json("queryMonthStr" to yearMonth, "requestType" to "NORMAL", "sceneCode" to "DIARY", "source" to "antfarm_icon"))
    fun queryChickenDiary(queryDay: String?) = request("com.alipay.antfarm.queryChickenDiary", json("queryDayStr" to queryDay, "requestType" to "NORMAL", "sceneCode" to "DIARY", "source" to "antfarm_icon"))
    fun diaryTietie(diaryDate: String?, roleId: String?) = request("com.alipay.antfarm.diaryTietie", json("diaryDate" to diaryDate, "requestType" to "NORMAL", "roleId" to roleId, "sceneCode" to "DIARY", "source" to "antfarm_icon"))
    fun collectChickenDiary(diaryId: String?) = request("com.alipay.antfarm.collectChickenDiary", json("collectStatus" to true, "diaryId" to diaryId, "requestType" to "NORMAL", "sceneCode" to "MOOD", "source" to "H5"))
    fun visitAnimal() = request("com.alipay.antfarm.visitAnimal", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to VERSION))
    fun feedFriendAnimalVisit(friendFarmId: String?) = request("com.alipay.antfarm.feedFriendAnimal", json("friendFarmId" to friendFarmId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "visitChicken", "version" to VERSION))
    fun visitAnimalSendPrize(token: String?) = request("com.alipay.antfarm.visitAnimalSendPrize", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "token" to token, "version" to VERSION))
    private fun json(vararg values: Pair<String, Any?>): JsonObject = jsonObject(*values)
    private fun request(method: String, payload: JsonObject) = RequestManager.requestString(method, jsonArray(payload).toString())
}
