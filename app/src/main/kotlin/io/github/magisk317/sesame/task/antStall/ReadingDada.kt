package io.github.magisk317.sesame.task.antStall

import io.github.magisk317.sesame.model.ModelGroup
import io.github.magisk317.sesame.task.AnswerAI.AnswerAI
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.getJSONArray
import io.github.magisk317.sesame.util.getString
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.toStringList
import kotlinx.serialization.json.JsonObject

/**
 * @file ReadingDada.kt
 * @brief 阅读答题功能模块
 * @author
 * @since 2023/08/22
 */
object ReadingDada {
    private const val TAG = "ReadingDada"

    val group: ModelGroup = ModelGroup.STALL

    /**
     * @brief 回答问题
     * @param bizInfo 业务信息JSON对象
     * @return 是否回答成功
     */
    fun answerQuestion(bizInfo: JsonObject): Boolean {
        try {
            // 获取任务跳转URL
            val taskJumpUrl = bizInfo.optString("taskJumpUrl").takeIf { it.isNotEmpty() }
                ?: bizInfo.getString("targetUrl")

            // 解析活动ID
            val activityId = taskJumpUrl.split("activityId%3D")[1].split("%26")[0]

            // 解析外部业务ID
            val outBizId = if (taskJumpUrl.contains("outBizId%3D")) {
                taskJumpUrl.split("outBizId%3D")[1].split("%26")[0]
            } else {
                ""
            }

            // 获取问题
            val questionResponse = ReadingDadaRpcCall.getQuestion(activityId)
            val questionJson = questionResponse.toJsonObject()

            if (questionJson.getString("resultCode") == "200") {
                val options = questionJson.getJSONArray("options")
                val question = questionJson.getString("title")

                // 使用AI获取答案
                var answer = AnswerAI.getAnswer(
                    question,
                    options.toStringList(),
                    "other"
                )

                // 如果AI未返回答案,使用第一个选项
                if (answer.isNullOrEmpty()) {
                    answer = options.getString(0)
                }

                // 提交答案
                val submitResponse = ReadingDadaRpcCall.submitAnswer(
                    activityId,
                    outBizId,
                    questionJson.getString("questionId"),
                    answer
                )

                val submitJson = submitResponse.toJsonObject()
                return if (submitJson.getString("resultCode") == "200") {
                    StructuredLog.record(TAG, "答题完成")
                    true
                } else {
                    StructuredLog.error(TAG, "答题失败")
                    false
                }
            } else {
                StructuredLog.error(TAG, "获取问题失败")
            }
        } catch (e: Throwable) {
            StructuredLog.printStackTrace(TAG, "answerQuestion err:", e)
        }
        return false
    }
}
