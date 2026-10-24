package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** Farm tools, NPC animals and chicken ornament RPC boundary. */
object FarmPropRpc {
    private const val VERSION = "1.8.2302070202.46"
    fun orchardRecallAnimal(animalId: String?, userId: String?) = request("com.alipay.antorchard.recallAnimal", json("animalId" to animalId, "orchardUserId" to userId, "requestType" to "NORMAL", "sceneCode" to "ORCHARD", "source" to "zhuangyuan_zhaohuixiaoji", "version" to "0.1.2403061630.6"))
    fun listToolTaskDetails() = request("com.alipay.antfarm.listToolTaskDetails", base())
    fun receiveToolTaskReward(rewardType: String?, rewardCount: Int, taskType: String?) = request("com.alipay.antfarm.receiveToolTaskReward", json("ignoreLimit" to false, "requestType" to "NORMAL", "rewardCount" to rewardCount, "rewardType" to rewardType, "sceneCode" to "ANTFARM", "source" to "H5", "taskType" to taskType, "version" to VERSION))
    fun hireAnimal(farmId: String?, animalId: String?) = request("com.alipay.antfarm.hireAnimal", json("friendFarmId" to farmId, "hireActionType" to "HIRE_IN_FRIEND_FARM", "hireAnimalId" to animalId, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "sendCardChat" to false, "source" to "H5", "version" to VERSION))
    fun hireNpcAnimal(animalId: String?, source: String?) = request("com.alipay.antfarm.hireAnimal", json("hireActionType" to "HIRE_IN_SELF_FARM", "hireAnimalId" to animalId, "isNpcAnimal" to true, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to source, "version" to VERSION))
    fun sendBackNpcAnimal(animalId: String?, currentFarmId: String?, masterFarmId: String?) = request("com.alipay.antfarm.sendBackAnimal", json("animalId" to animalId, "currentFarmId" to currentFarmId, "masterFarmId" to masterFarmId, "receiveNPCReward" to true, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "sendType" to "NORMAL", "source" to "H5", "version" to VERSION))
    fun listZhimaNpcFarmTask() = request("com.alipay.antfarm.listFarmTask", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "zhimaxiaoji_lianjin", "taskSceneCode" to "ANTFARM_ZHIMA_NPC_TASK", "version" to VERSION))
    fun receiveZhimaNpcFarmTaskAward(taskId: String?) = request("com.alipay.antfarm.receiveFarmTaskAward", json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "zhimaxiaoji_lianjin", "taskId" to taskId, "taskSceneCode" to "ANTFARM_ZHIMA_NPC_TASK", "version" to VERSION))
    fun listOrnaments() = request("com.alipay.antfarm.listOrnaments", json("pageNo" to "1", "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "setsType" to "ACHIEVEMENTSETS", "source" to "H5", "subType" to "sets", "type" to "apparels", "version" to VERSION))
    fun saveOrnaments(animalId: String?, farmId: String?, ornaments: String?) = request("com.alipay.antfarm.saveOrnaments", json("animalId" to animalId, "farmId" to farmId, "ornaments" to ornaments, "requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to VERSION))
    private fun base() = json("requestType" to "NORMAL", "sceneCode" to "ANTFARM", "source" to "H5", "version" to VERSION)
    private fun json(vararg values: Pair<String, Any?>): JsonObject = jsonObject(*values)
    private fun request(method: String, payload: JsonObject) = RequestManager.requestString(method, jsonArray(payload).toString())
}
