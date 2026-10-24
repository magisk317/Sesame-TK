package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.StructuredLog.record
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import java.util.regex.Pattern

/** Executes one Zhima Tree task, including the final reward claim. */
class MemberTreeTaskExecutor {
    suspend fun process(task: JsonObject) {
        try {
            val taskBaseInfo = task.optJSONObject("taskBaseInfo") ?: return
            var taskId = taskBaseInfo.optString("appletId")
            if (taskId.isEmpty()) taskId = task.optString("taskId")

            var title = taskBaseInfo.optString("appletName")
            if (title.isEmpty()) title = taskBaseInfo.optString("title", taskId)
            if (title.contains("邀请") || title.contains("下单") || title.contains("开通")) return

            val triggerType = task.optString("sendCampTriggerType", "")
            val prizeName = prizeName(task)
            when (task.optString("taskProcessStatus")) {
                "NONE_SIGNUP" -> {
                    record("芝麻树🌳[报名] $title ($triggerType) ${prizeName.takeIf { it.isNotEmpty() }?.let { "($it)" } ?: ""}")
                    action(taskId, "signup")
                }
                "NOT_DONE", "SIGNUP_COMPLETE" -> {
                    record("芝麻树🌳[任务] $title ($triggerType) ${prizeName.takeIf { it.isNotEmpty() }?.let { "($it)" } ?: ""}")
                    perform(taskId, title, prizeName, triggerType)
                }
                "TO_RECEIVE" -> if (action(taskId, "receive")) {
                    StructuredLog.forest("芝麻树🌳[领取奖励] $title #${prizeName.ifEmpty { "奖励已领取" }}")
                }
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, e)
        }
    }

    private suspend fun perform(taskId: String, title: String, prizeName: String, triggerType: String): Boolean {
        return try {
            val sendOk = action(taskId, "send")
            if (!sendOk) {
                record("芝麻树🌳[send失败] $title ($triggerType) taskId=$taskId")
                return false
            }
            delay(16_000L)
            if (!action(taskId, "receive")) return false
            StructuredLog.forest("芝麻树🌳[完成任务] $title #${prizeName.ifEmpty { "奖励已领取" }}")
            true
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, e)
            false
        }
    }

    private fun action(taskId: String, stageCode: String): Boolean = try {
        val response = ZhimaTreeRpc.finishRentGreenTask(taskId, stageCode) ?: return false
        val responseJson = response.toJsonObject()
        if (responseJson.optString("errorCode") == "20030422") {
            record("芝麻树🌳[$stageCode] 已满级，跳过任务")
            return false
        }
        val ok = ResChecker.checkRes(TAG, responseJson)
        if (!ok) {
            record("芝麻树🌳[$stageCode] 失败: ${responseJson.optString("errorCode")} ${responseJson.optString("errorMsg","").take(80)}")
        }
        ok
    } catch (e: Exception) {
        StructuredLog.printStackTrace(TAG, e)
        false
    }

    private fun prizeName(task: JsonObject): String {
        return try {
            val prizes = task.optJSONArray("validPrizeDetailDTO")
                ?: task.optJSONArray("prizeDetailDTOList")
            val rawName = prizes?.getJSONObject(0)?.optJSONObject("prizeBaseInfoDTO")
                ?.optString("prizeName", "").orEmpty()
            val parsed = when {
                rawName.contains("能量") -> match(rawName, "(森林)?能量(\\d+g?)")
                rawName.contains("净化值") -> match(rawName, "(\\d+净化值|净化值\\d+)")
                else -> rawName
            }
            if (parsed.isNotEmpty()) parsed else task.optJSONObject("taskExtProps")
                ?.optString("TASK_MORPHO_DETAIL")
                ?.toJsonObject()
                ?.optString("finishOneTaskGetPurificationValue", "")
                ?.takeIf { it.isNotEmpty() && it != "0" }
                ?.plus("净化值").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun match(value: String, expression: String): String {
        val matcher = Pattern.compile(expression).matcher(value)
        return if (matcher.find()) matcher.group(0).orEmpty() else value
    }

    private companion object {
        const val TAG = "MemberTreeTaskExecutor"
    }
}
