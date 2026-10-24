package io.github.magisk317.sesame.task.antOcean

/** Ocean task and replica-task RPCs. */
object OceanTaskRpc {
    @JvmStatic fun receiveTaskAward(sceneCode: String, taskType: String) = request("com.alipay.antiep.receiveTaskAward", json("ignoreLimit" to false, "requestType" to "RPC", "sceneCode" to sceneCode, "source" to FOREST_SOURCE, "taskType" to taskType, "uniqueId" to id()))
    @JvmStatic fun finishTask(sceneCode: String, taskType: String) = request("com.alipay.antiep.finishTask", json("outBizNo" to "${taskType}_${io.github.magisk317.sesame.util.RandomUtil.nextDouble()}", "requestType" to "RPC", "sceneCode" to sceneCode, "source" to "ANTFOCEAN", "taskType" to taskType, "uniqueId" to id()))
    @JvmStatic fun queryTaskList() = request("alipay.antocean.ocean.h5.queryTaskList", json("extend" to kotlinx.serialization.json.buildJsonObject { }, "fromAct" to "dynamic_task", "sceneCode" to "ANTOCEAN_TASK", "source" to FOREST_SOURCE, "uniqueId" to id(), "version" to VERSION))
    @JvmStatic fun queryReplicaTaskList() = request("alipay.antocean.ocean.h5.queryTaskList", json("fromAct" to "dynamic_task", "sceneCode" to "ANTOCEAN_AVATAR_TASK", "source" to "seaAreaList", "uniqueId" to id(), "version" to VERSION))
    @JvmStatic fun receiveReplicaTaskAward(taskType: String) = request("com.alipay.antiep.receiveTaskAward", json("ignoreLimit" to "false", "requestType" to "RPC", "sceneCode" to "ANTOCEAN_AVATAR_TASK", "source" to "ANTFOCEAN", "taskType" to taskType, "uniqueId" to id()))
    @JvmStatic fun queryReplicaHome() = request("alipay.antocean.ocean.h5.queryReplicaHome", json("replicaCode" to "avatar", "source" to "seaAreaList", "uniqueId" to id()))
    @JvmStatic fun unlockReplicaPhase(replicaCode: String, replicaPhaseCode: String) = request("alipay.antocean.ocean.h5.unLockReplicaPhase", json("replicaCode" to replicaCode, "replicaPhaseCode" to replicaPhaseCode, "source" to "senlinzuoshangjiao", "uniqueId" to id(), "version" to "20220707"))

    private fun request(method: String, payload: kotlinx.serialization.json.JsonObject) = OceanRpcSupport.request(method, payload)
    private fun id() = OceanRpcSupport.id()
    private fun json(vararg values: Pair<String, Any?>) = OceanRpcSupport.json(*values)
    private const val FOREST_SOURCE = OceanRpcSupport.FOREST_SOURCE
    private const val VERSION = OceanRpcSupport.VERSION
}
