package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.jsonArray
import io.github.magisk317.sesame.util.jsonObject
import kotlinx.serialization.json.JsonObject

/** Member sign-in and generic member-task RPCs. */
object MemberTaskRpc {
    fun querySigninCalendar(): String = request(
        "com.alipay.amic.biz.rpc.signin.h5.queryMemberSigninCalendar",
        jsonObject("autoSignIn" to true, "invitorUserId" to "", "sceneCode" to "QUERY"),
    )

    fun executeTask(bizParam: String, bizSubType: String, bizType: String, taskConfigId: Long?): String = request(
        "alipay.antmember.biz.rpc.membertask.h5.executeTask",
        jsonObject(
            "bizOutNo" to TimeUtil.getFormatDate().replace("-", ""), "bizParam" to bizParam,
            "bizSubType" to bizSubType, "bizType" to bizType,
            "sourcePassMap" to jsonObject("innerSource" to "", "source" to "myTab", "unid" to ""),
            "syncProcess" to true, "taskConfigId" to taskConfigId,
        ),
    )

    fun queryAllStatusTaskList(): String = request(
        "alipay.antmember.biz.rpc.membertask.h5.queryAllStatusTaskList",
        jsonObject("sourceBusiness" to "signInAd", "sourcePassMap" to jsonObject("innerSource" to "", "source" to "myTab", "unid" to "")),
    )

    private fun request(method: String, payload: JsonObject): String = RequestManager.requestString(method, jsonArray(payload).toString())
}
