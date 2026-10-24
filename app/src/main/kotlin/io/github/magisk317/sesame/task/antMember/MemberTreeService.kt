package io.github.magisk317.sesame.task.antMember

import io.github.magisk317.sesame.util.CoroutineUtils
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.StructuredLog.record
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.has
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlin.math.max

/** Coordinates the independent phases of the Zhima Tree task. */
class MemberTreeService {
    private val taskExecutor = MemberTreeTaskExecutor()

    suspend fun processTask(task: JsonObject) = taskExecutor.process(task)

    suspend fun run() = CoroutineUtils.run {
        try {
            homeTasks()
            rentGreenTasks()
            purification()
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    private suspend fun homeTasks() = CoroutineUtils.run {
        try {
            val response = ZhimaTreeRpc.home() ?: return@run
            val json = response.toJsonObject()
            if (!ResChecker.checkRes(TAG, json)) return@run
            val result = json.optJSONObject("extInfo")
                ?.optJSONObject("zhimaTreeHomePageQueryResult") ?: return@run
            listOf("browseTaskList", "taskStatusList").forEach { key ->
                val tasks = result.optJSONArray(key) ?: return@forEach
                for (index in 0 until tasks.length()) {
                    processTask(tasks.getJSONObject(index))
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    private suspend fun rentGreenTasks() = CoroutineUtils.run {
        try {
            val response = ZhimaTreeRpc.queryRentGreenTaskList() ?: return@run
            val json = response.toJsonObject()
            if (!ResChecker.checkRes(TAG, json)) return@run
            val tasks = json.optJSONObject("extInfo")
                ?.optJSONObject("taskDetailList")
                ?.optJSONArray("taskDetailList") ?: return@run
            for (index in 0 until tasks.length()) {
                processTask(tasks.getJSONObject(index))
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    private suspend fun purification() = CoroutineUtils.run {
        try {
            val response = ZhimaTreeRpc.home() ?: return@run
            val json = response.toJsonObject()
            if (!ResChecker.checkRes(TAG, json)) return@run
            val result = json.optJSONObject("extInfo")
                ?.optJSONObject("zhimaTreeHomePageQueryResult") ?: return@run
            val score = result.optInt("purificationScore", result.optInt("currentCleanNum", 0))
            var treeCode = "ZHIMA_TREE"
            var clicks = score / 100
            val trees = result.optJSONArray("trees")
            if (trees != null && trees.length() > 0) {
                val tree = trees.getJSONObject(0)
                treeCode = tree.optString("treeCode", treeCode)
                if (tree.has("remainPurificationClickNum")) {
                    clicks = max(0, tree.optInt("remainPurificationClickNum", clicks))
                }
            }
            if (clicks <= 0) {
                record("芝麻树🌳[无需净化] 净化值不足（当前: ${score}g，可点击: ${clicks}次）")
                return@run
            }
            record("芝麻树🌳[净化] 可点击 $clicks 次")
            for (index in 0 until clicks) {
                val cleanResponse = ZhimaTreeRpc.cleanAndPush(treeCode) ?: break
                val cleanJson = cleanResponse.toJsonObject()
                if (cleanJson.optString("errorCode") == "20030422") {
                    record("芝麻树🌳[净化] 已满级，停止净化")
                    break
                }
                if (!ResChecker.checkRes(TAG, cleanJson)) break
                val ext = cleanJson.optJSONObject("extInfo") ?: continue
                val detail = ext.optJSONObject("zhimaTreeCleanAndPushResult")
                var newScore = detail?.optInt("purificationScore", -1) ?: -1
                if (newScore == -1) newScore = ext.optInt("purificationScore", score - (index + 1) * 100)
                val growth = detail?.optJSONObject("currentTreeInfo")?.optInt("scoreSummary", -1) ?: -1
                val growthText = if (growth == -1) "" else "|成长:$growth"
                StructuredLog.forest("芝麻树🌳[净化]第${index + 1}次 |剩:${newScore}g$growthText")
                delay(1500)
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
        }
    }

    private companion object {
        const val TAG = "MemberTreeService"
    }
}
