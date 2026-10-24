package io.github.magisk317.sesame.task.antOrchard

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import io.github.magisk317.sesame.util.maps.UserMap
/** RPCs used by the Golden Bean entrance in the HP sample. */
object AntGoldenBeanRpc {
    const val VERSION = "20260723.01"
    const val SOURCE = "babafarm"
    const val BIZ_TYPE = "MASTER"

    internal fun indexArgs(): String = args(
        jsonObject("bizType" to BIZ_TYPE, "darwinSceneList" to jsonArray(), "source" to SOURCE, "version" to VERSION)
    )

    fun index(): String = RequestManager.requestString("com.alipay.goldenbean.index", indexArgs())

    internal fun signArgs(signKey: String): String = args(
        jsonObject("bizType" to BIZ_TYPE, "signKey" to signKey, "source" to SOURCE, "version" to VERSION)
    )

    fun sign(signKey: String): String = RequestManager.requestString("com.alipay.goldenbean.sign", signArgs(signKey))

    internal fun fortuneDrawArgs(): String = args(
        jsonObject("bizType" to BIZ_TYPE, "source" to SOURCE, "version" to VERSION)
    )

    fun fortuneDraw(): String = RequestManager.requestString("com.alipay.goldenbean.fortuneDraw", fortuneDrawArgs())

    internal fun manureExchangeArgs(amount: Int): String = args(
        jsonObject("bizType" to BIZ_TYPE, "exchangeBeanAmount" to amount, "source" to SOURCE, "version" to VERSION)
    )

    fun manureExchange(amount: Int): String =
        RequestManager.requestString("com.alipay.goldenbean.manureExchange", manureExchangeArgs(amount))

    internal fun receiveTaskAwardArgs(taskType: String): String = args(
        jsonObject(
            "bizInfo" to jsonObject("bizType" to BIZ_TYPE), "bizType" to BIZ_TYPE,
            "ignoreLimit" to true, "sceneCode" to "GOLDEN_BEAN_MASTER_TASK",
            "source" to SOURCE, "taskType" to taskType, "version" to VERSION,
        )
    )

    fun receiveTaskAward(taskType: String): String = RequestManager.requestString(
        "com.alipay.antieptask.receiveTaskAwardantorchard", receiveTaskAwardArgs(taskType)
    )

    internal fun finishTaskArgs(taskType: String, userId: String): String = args(
        jsonObject(
            "bizType" to BIZ_TYPE, "finishBusinessInfo" to jsonObject("bizType" to BIZ_TYPE),
            "outBizNo" to userId + System.currentTimeMillis(),
            "sceneCode" to "GOLDEN_BEAN_MASTER_TASK", "source" to SOURCE,
            "taskType" to taskType, "version" to VERSION,
        )
    )

    fun finishTask(taskType: String): String = RequestManager.requestString(
        "com.alipay.antieptask.finishTaskantorchard",
        finishTaskArgs(taskType, UserMap.currentUid ?: "")
    )

    private fun args(value: kotlinx.serialization.json.JsonObject): String = jsonArray(value).toString()
}
