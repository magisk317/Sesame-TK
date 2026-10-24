package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Family, shared-feed and family-draw RPC boundary. */
object FarmFamilyRpc {
    fun enterFamily() = request("com.alipay.antfarm.enterFamily", json("fromAnn" to false, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "timeZoneId" to "Asia/Shanghai"))
    fun familyTaskTips(animals: JsonArray?) = request("com.alipay.antfarm.familyTaskTips", jsonObject("animals" to animals, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "taskSceneCode" to "ANTFARM_FAMILY_TASK", "timeZoneId" to "Asia/Shanghai"))
    fun familyReceiveFarmTaskAward(taskId: String?) = request("com.alipay.antfarm.receiveFarmTaskAward", json("awardType" to "FAMILY_INTIMACY", "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "taskId" to taskId, "taskSceneCode" to "ANTFARM_FAMILY_TASK"))
    fun familyAwardList() = request("com.alipay.antfarm.familyAwardList", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5"))
    fun receiveFamilyAward(rightId: String?) = request("com.alipay.antfarm.receiveFamilyAward", json("requestType" to "NORMAL", "rightId" to rightId, "sceneCode" to "ANTFARM", "source" to "H5"))
    fun assignFamilyMember(assignAction: String?, beAssignUser: String?) = request("com.alipay.antfarm.assignFamilyMember", json("assignAction" to assignAction, "beAssignUser" to beAssignUser, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5"))
    fun sendChat(chatCardType: String?, receiverUserId: String?) = request("com.alipay.antfarm.sendChat", json("chatCardType" to chatCardType, "receiverUserId" to receiverUserId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5"))
    fun deliverSubjectRecommend(friendUserIdList: JsonArray?) = request("com.alipay.antfarm.deliverSubjectRecommend", jsonObject("friendUserIds" to friendUserIdList, "requestType" to "NORMAL", "sceneCode" to "ChickFamily", "source" to "H5"))
    fun openAiPrivatePolicy() = request("com.alipay.antfarm.OpenPrivatePolicy", jsonObject("privatePolicyIdList" to jsonArray(JsonPrimitive("AI_CHICK_PRIVATE_POLICY")), "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to VERSION))
    fun deliverContentExpand(ariverRpcTraceId: String?, eventId: String?, eventName: String?, memo: String?, resultCode: String?, sceneId: String?, sceneName: String?, success: Boolean, friendUserIdList: JsonArray?) = request("com.alipay.antfarm.DeliverContentExpand", jsonObject("ariverRpcTraceId" to ariverRpcTraceId, "eventId" to eventId, "eventName" to eventName, "friendUserIds" to friendUserIdList, "memo" to memo, "requestType" to "NORMAL", "resultCode" to resultCode, "sceneCode" to "ANTFARM", "sceneId" to sceneId, "sceneName" to sceneName, "source" to "H5", "success" to success))
    fun queryExpandContent(deliverId: String?) = request("com.alipay.antfarm.QueryExpandContent", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "deliverId" to deliverId))
    fun deliverMsgSend(groupId: String?, friendUserIds: JsonArray?, content: String?, deliverId: String?) = request("com.alipay.antfarm.DeliverMsgSend", jsonObject("content" to content, "deliverId" to deliverId, "friendUserIds" to friendUserIds, "groupId" to groupId, "mode" to "AI", "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "spaceType" to "ChickFamily"))
    fun syncFamilyStatus(groupId: String?, operType: String?, syncUserIds: String?) = request("com.alipay.antfarm.syncFamilyStatus", jsonObject("groupId" to groupId, "operType" to operType, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "syncUserIds" to jsonArray(syncUserIds?.let(::JsonPrimitive) ?: JsonNull)))
    fun inviteFriendVisitFamily(receiverUserId: JsonArray?) = request("com.alipay.antfarm.inviteFriendVisitFamily", jsonObject("bizType" to "FAMILY_SHARE", "receiverUserId" to receiverUserId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5"))
    fun familyEatTogether(groupId: String?, friendUserIdList: JsonArray?, cuisines: JsonArray?) = request("com.alipay.antfarm.familyEatTogether", jsonObject("cuisines" to cuisines, "friendUserIds" to friendUserIdList, "groupId" to groupId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "spaceType" to "ChickFamily"))
    fun queryRecentFarmFood(queryNum: Int) = request("com.alipay.antfarm.queryRecentFarmFood", json("queryNum" to queryNum, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5"))
    fun feedFriendAnimal(friendFarmId: String?, groupId: String?) = request("com.alipay.antfarm.feedFriendAnimal", json("friendFarmId" to friendFarmId, "groupId" to groupId, "requestType" to "NORMAL", "sceneCode" to "ChickFamily", "source" to "H5", "spaceType" to "ChickFamily"))
    fun queryFamilyDrawActivity() = request("com.alipay.antfarm.queryFamilyDrawActivity", json("bizType" to "ANTFARM_GAME_CENTER", "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5"))
    fun familyDraw() = request("com.alipay.antfarm.familyDraw", json("bizType" to "ANTFARM_GAME_CENTER", "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5"))
    fun familyBatchInviteP2P(inviteP2PVOList: JsonArray?, sceneCode: String?) = request("com.alipay.antiep.batchInviteP2P", jsonObject("inviteP2PVOList" to inviteP2PVOList, "requestType" to "RPC", "sceneCode" to sceneCode, "source" to "antfarm"))
    fun familyDrawSignReceiveFarmTaskAward(taskId: String?) = request("com.alipay.antfarm.receiveFarmTaskAward", json("awardType" to "FAMILY_DRAW_TIME", "bizType" to "ANTFARM_GAME_CENTER", "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "taskId" to taskId, "taskSceneCode" to "ANTFARM_FAMILY_DRAW_TASK"))
    fun familyShareP2PPanelInfo(sceneCode: String?) = request("com.alipay.antiep.shareP2PPanelInfo", json("requestType" to "RPC", "source" to "antfarm", "sceneCode" to sceneCode))
    fun familyDrawListFarmTask() = request("com.alipay.antfarm.listFarmTask", json("bizType" to "ANTFARM_GAME_CENTER", "requestType" to "NORMAL", "sceneCode" to "ANTFARM_FAMILY_DRAW_TASK", "signSceneCode" to "", "source" to "H5", "taskSceneCode" to "ANTFARM_FAMILY_DRAW_TASK"))
    fun giftFamilyDrawFragment(giftUserId: String?, giftNum: Int) = request("com.alipay.antfarm.giftFamilyDrawFragment", json("bizType" to "ANTFARM_GAME_CENTER", "giftNum" to giftNum, "giftUserId" to giftUserId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5"))

    private const val VERSION = "1.8.2302070202.46"
    private fun json(vararg values: Pair<String, Any?>): JsonObject = jsonObject(*values)
    private fun request(method: String, payload: JsonObject) = RequestManager.requestString(method, jsonArray(payload).toString())
}
