package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** Payload construction for the annual-review component protocol. */
object AnnualReviewRpcPayloads {
    @JvmField
    val OPERATION_IDENTIFY = "independent_component_program2025111803036407"

    @JvmField
    val COMPONENT_PREFIX = "independent_component_task_reward_v2_02888775"

    @JvmField
    val QUERY_COMPONENT = "${COMPONENT_PREFIX}_independent_component_task_reward_query"

    @JvmField
    val APPLY_COMPONENT = "${COMPONENT_PREFIX}_independent_component_task_reward_apply"

    @JvmField
    val PROCESS_COMPONENT = "${COMPONENT_PREFIX}_independent_component_task_reward_process"

    @JvmField
    val GET_REWARD_COMPONENT = "${COMPONENT_PREFIX}_independent_component_task_reward_get_reward"

    fun query(): String = invoke(QUERY_COMPONENT, jsonObject())

    fun apply(code: String): String = invoke(
        APPLY_COMPONENT,
        jsonObject("code" to code, "consultAfterLuckDraw" to "false", "skipLuckDrawConsult" to "true"),
    )

    fun process(code: String, recordNo: String): String = invoke(
        PROCESS_COMPONENT,
        jsonObject("code" to code, "recordNo" to recordNo),
    )

    fun getReward(code: String, recordNo: String): String = invoke(
        GET_REWARD_COMPONENT,
        jsonObject(
            "code" to code, "consultAfterLuckDraw" to "false", "recordNo" to recordNo,
            "skipLuckDrawConsult" to "true",
        ),
    )

    private fun invoke(component: String, componentPayload: JsonObject): String {
        val body = jsonObject(
            "channel" to "share", "cityCode" to "110000",
            "operationParamIdentify" to OPERATION_IDENTIFY, "source" to component,
            "components" to jsonObject(component to componentPayload),
        )
        return jsonArray(body).toString()
    }
}
