package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.data.Status.Companion.canMemberSignInToday
import io.github.magisk317.sesame.data.Status.Companion.memberSignInToday
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.getLong
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Runs the member sign-in and browse-task RPC workflow. */
class MemberTaskService(
    private val collectPointCertificates: suspend () -> Unit,
) {
    suspend fun sign() {
        try {
            if (canMemberSignInToday(UserMap.currentUid)) {
                val response = MemberTaskRpc.querySigninCalendar()
                delay(500)
                val result = response.toJsonObject()
                if (ResChecker.checkRes(TAG + "会员签到失败:", result)) {
                    StructuredLog.other(
                        "会员签到📅[${result.getString("signinPoint")}积分]#已签到${result.getString("signinSumDay")}天"
                    )
                    memberSignInToday(UserMap.currentUid)
                } else {
                    StructuredLog.record(result.getString("resultDesc"))
                    StructuredLog.record(response)
                }
            }
            collectPointCertificates()
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "doMemberSign err", error)
        }
    }

    suspend fun runAvailable() {
        try {
            val response = MemberTaskRpc.queryAllStatusTaskList()
            delay(500)
            val result = response.toJsonObject()
            if (!ResChecker.checkRes(TAG, result)) {
                StructuredLog.error(TAG + ".runAvailable", "会员任务响应失败: ${result.getString("resultDesc")}")
                return
            }
            if (!result.has("availableTaskList")) return
            val tasks = result.getJSONArray("availableTaskList")
            for (index in tasks.indices) process(tasks.getJSONObject(index))
        } catch (error: Throwable) {
            StructuredLog.printStackTrace(TAG, "runAvailable err", error)
        }
    }

    private suspend fun process(task: JsonObject) {
        val config = task.getJSONObject("taskConfigInfo")
        val name = config.getString("name")
        val id = config.getLong("id")
        val award = config.getJSONObject("awardParam").getString("awardParamPoint")
        val target = config.getJSONArray("targetBusiness").getString(0).split('#')
        if (target.size < 3) {
            StructuredLog.error(TAG, "processTask target param err: ${target.joinToString()}")
            return
        }
        delay(16000)
        val response = MemberTaskRpc.executeTask(target[2], target[1], target[0], id)
        val result = response.toJsonObject()
        if (!ResChecker.checkRes(TAG + "执行会员任务失败:", result)) {
            StructuredLog.error(TAG, "执行任务失败: ${result.optString("resultDesc")}")
            return
        }
        if (isFinished(id)) StructuredLog.other("会员任务🎖️[$name]#获得积分$award")
    }

    private suspend fun isFinished(taskId: Long): Boolean {
        return try {
            val response = MemberTaskRpc.queryAllStatusTaskList()
            delay(500)
            val result = response.toJsonObject()
            if (!ResChecker.checkRes(TAG + "查询会员任务状态失败:", result)) {
                StructuredLog.error(TAG, "会员任务响应失败: ${result.getString("resultDesc")}")
            }
            if (!result.has("availableTaskList")) return true
            val tasks = result.getJSONArray("availableTaskList")
            for (index in tasks.indices) {
                if (taskId == tasks.getJSONObject(index).getJSONObject("taskConfigInfo").getLong("id")) {
                    return false
                }
            }
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private companion object {
        const val TAG = "MemberTaskService"
    }
}
