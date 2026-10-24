package io.github.magisk317.sesame.task.antOcean

/** Dada daily-question RPCs used by the ocean task flow. */
object OceanDadaRpc {
    @JvmStatic fun getQuestion() = request("com.alipay.reading.game.dada.openDailyAnswer.getQuestion", json("activityId" to "363", "dadaVersion" to "1.3.0", "version" to 1))
    @JvmStatic fun record() = request("com.alipay.reading.game.dada.mdap.record", json("behavior" to "visit", "dadaVersion" to "1.3.0", "version" to "1"))
    @JvmStatic fun submitAnswer(answer: String, questionId: String) = request("com.alipay.reading.game.dada.openDailyAnswer.submitAnswer", json("activityId" to "363", "answer" to answer, "dadaVersion" to "1.3.0", "outBizId" to "ANTOCEAN_DATI_PINTU_722_new", "questionId" to questionId, "version" to "1"))

    private fun request(method: String, payload: kotlinx.serialization.json.JsonObject) = OceanRpcSupport.request(method, payload)
    private fun json(vararg values: Pair<String, Any?>) = OceanRpcSupport.json(*values)
}
