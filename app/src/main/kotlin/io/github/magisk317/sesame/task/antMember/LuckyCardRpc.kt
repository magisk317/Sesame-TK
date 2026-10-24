package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject

/** RPC boundary for the two HAOYUNKA card series. */
object LuckyCardRpc {
    private const val CONSULT_METHOD = "com.alipay.pcreditcardmarket.consultLuckyCard"
    private const val OPEN_METHOD = "com.alipay.pcreditcardmarket.openLuckyCard"
    private const val TASK_QUERY_METHOD = "com.alipay.pcreditbfweb.sdk.task.query"
    private const val PCCP_ID = "PCCP_2025062404229513342"

    internal fun consultPayload(): String =
        jsonArray(
            jsonObject(
                "benefitCampId" to "",
                "benefitScene" to "",
                "bizScene" to "HAOYUNKA",
                "chInfo" to "",
                "channel" to "",
                "inflationCampId" to "",
                "pccpId" to PCCP_ID,
                "taskCampId" to "",
            ),
        ).toString()

    fun consult(): String = RequestManager.requestString(CONSULT_METHOD, consultPayload())

    fun open(): String = RequestManager.requestString(
        OPEN_METHOD,
        jsonArray(
            jsonObject("requestFrom" to "pcreditcardweb"),
        ).toString(),
    )

    /** Returns the ordinary/rare card task list shown by the HAOYUNKA page. */
    internal fun queryTasksPayload(): String =
        jsonArray(
            jsonObject(
                "appletId" to "PCCP_2024120204226387059",
                "bizScene" to "HAOYUNKA_DAILY",
                "bizSceneFrom" to "creditCard",
                "extInfo" to jsonObject("version" to 1),
                "requestFrom" to "pccp",
            ),
        ).toString()

    fun queryTasks(): String = RequestManager.requestString(TASK_QUERY_METHOD, queryTasksPayload())
}
