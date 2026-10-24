package io.github.magisk317.sesame.task.antFarm

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

object DadaDailyRpcCall {
    @JvmStatic
    fun home(activityId: String?): String = request(
        "com.alipay.reading.game.dadaDaily.home",
        jsonObject("activityId" to (activityId?.toLongOrNull() ?: activityId), "dadaVersion" to "1.3.0", "version" to 1),
    )

    @JvmStatic
    fun submit(activityId: String?, answer: String?, questionId: Long?): String = request(
        "com.alipay.reading.game.dadaDaily.submit",
        jsonObject("activityId" to (activityId?.toLongOrNull() ?: activityId), "answer" to answer,
            "dadaVersion" to "1.3.0", "questionId" to questionId, "version" to 1),
    )

    private fun request(method: String, value: JsonObject): String =
        RequestManager.requestString(method, jsonArray(value).toString())
}
