package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** RPC gateway for game-center sign-in, tasks and point-ball claims. */
object GameCenterRpc {
    private const val SOURCE = "ch_alipaysearch__chsub_normal"

    fun querySignInBall(): String = request("com.alipay.gamecenteruprod.biz.rpc.v3.querySignInBall", json("source" to SOURCE))
    fun continueSignIn(): String = request("com.alipay.gamecenteruprod.biz.rpc.continueSignIn", json("sceneId" to "GAME_CENTER", "signType" to "NORMAL_SIGN", "source" to SOURCE))
    fun queryTaskList(): String = request("com.alipay.gamecenteruprod.biz.rpc.v4.queryTaskList", json("source" to SOURCE))
    fun queryPointBallList(): String = request("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList", json("source" to SOURCE))
    fun batchReceivePointBall(): String = request("com.alipay.gamecenteruprod.biz.rpc.v3.batchReceivePointBall", jsonObject())
    fun doTaskSend(taskId: String): String = request("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSend", json("taskId" to taskId))
    fun doTaskSignup(taskId: String): String = request("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSignup", json("source" to SOURCE, "taskId" to taskId))

    private fun request(method: String, payload: JsonObject): String = RequestManager.requestString(method, jsonArray(payload).toString())
    private fun json(vararg values: Pair<String, Any?>): JsonObject = jsonObject(*values)
}
