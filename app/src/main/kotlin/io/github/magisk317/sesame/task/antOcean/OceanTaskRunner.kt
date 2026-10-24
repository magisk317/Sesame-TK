package io.github.magisk317.sesame.task.antOcean

import io.github.magisk317.sesame.entity.RpcEntity
import io.github.magisk317.sesame.hook.RequestManager
import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.TaskRuntimeStore
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonObject
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optJSONObject
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Application service for ocean task completion and award claiming. */
object OceanTaskRunner {
    private const val TAG = "AntOcean"
    private val blockedSerializer = SetSerializer(String.serializer())
    @JvmStatic
    fun run() {
        try {
            val attempts = ConcurrentHashMap<String, AtomicInteger>()
            val blocked = TaskRuntimeStore.getOrCreate("badOceanTaskSet", emptySet(), blockedSerializer).toMutableSet()
            if (blocked.isEmpty()) {
                blocked.addAll(listOf("DEMO", "DEMO1"))
                TaskRuntimeStore.put("badOceanTaskSet", blocked, blockedSerializer)
            }
            while (true) {
                var changed = false
                val response = OceanTaskRpc.queryTaskList().toJsonObject()
                if (!ResChecker.checkRes("$TAG 查询海洋任务列表失败:", response)) {
                    StructuredLog.record(TAG, "查询任务列表失败：${response.optString("resultDesc")}")
                    return
                }
                val tasks = response.optJSONArray("antOceanTaskVOList") ?: return
                for (index in 0 until tasks.length()) {
                    val task = tasks.optJSONObject(index) ?: continue
                    val scene = task.optString("sceneCode")
                    val type = task.optString("taskType")
                    if (scene.isEmpty() || type.isEmpty()) continue
                    val info = task.optString("bizInfo").toJsonObject()
                    val title = info.optString("taskTitle", type)
                    val award = info.optString("awardCount", "0")
                    when (task.optString("taskStatus")) {
                        TaskStatus.FINISHED.name -> {
                            val result = OceanTaskRpc.receiveTaskAward(scene, type).toJsonObject()
                            if (ResChecker.checkRes("$TAG 领取海洋任务奖励失败:", result)) {
                                StructuredLog.forest("海洋奖励🌊[$title]# $award 拼图")
                                changed = true
                            }
                        }
                        TaskStatus.TODO.name -> {
                            if (OceanTaskPolicy.isBlocked(blocked, title, type)) continue
                            if (title.contains("答题")) {
                                changed = answerQuestion() || changed
                                continue
                            }
                            // 可重复任务(如逛一逛市集 rightsTimesLimit=3)按剩余次数放宽重试上限，
                            // 次数用尽时直接跳过，避免被误判"未推动状态"而写进持久黑名单。
                            val rightsTimes = task.optString("rightsTimes").toIntOrNull() ?: 0
                            val rightsTimesLimit = (task.optString("rightsTimesLimit").toIntOrNull() ?: 1).coerceAtLeast(1)
                            val remaining = (rightsTimesLimit - rightsTimes).coerceAtLeast(0)
                            if (remaining <= 0) continue
                            if (OceanTaskPolicy.shouldSkipGenericFinish(info, type)) {
                                StructuredLog.record(TAG, "海洋任务🌊[$title]依赖外部页面/游戏且未验证通用 RPC，跳过")
                                continue
                            }
                            val key = "${scene}_$type"
                            val count = attempts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
                            val result = OceanTaskRpc.finishTask(scene, type).toJsonObject()
                            val unsupported = result.optString("code") == "400000040" || result.optString("desc").contains("不支持RPC完成")
                            if (unsupported) {
                                StructuredLog.error(TAG, "海洋任务🌊[$title]不支持RPC完成，已加入黑名单")
                                blocked.add(title)
                                TaskRuntimeStore.put("badOceanTaskSet", blocked, blockedSerializer)
                            } else if (count > remaining) {
                                StructuredLog.error(TAG, "海洋任务🌊[$title] finishTask 未推动任务状态，已加入黑名单")
                                blocked.add(type)
                                TaskRuntimeStore.put("badOceanTaskSet", blocked, blockedSerializer)
                            } else if (ResChecker.checkRes(TAG, result)) {
                                StructuredLog.record(TAG, "海洋任务🌊[$title] finishTask RPC成功，等待状态刷新")
                                changed = true
                            }
                        }
                    }
                    GlobalThreadPools.sleepCompat(500)
                }
                if (!changed) return
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "receiveTaskAward err:", t)
        }
    }

    @JvmStatic
    fun pollPendingAwards() {
        try {
            val response = OceanTaskRpc.queryTaskList().toJsonObject()
            if (!ResChecker.checkRes("$TAG 查询海洋任务列表失败:", response)) return
            val tasks = response.optJSONArray("antOceanTaskVOList") ?: return
            for (index in 0 until tasks.length()) {
                val task = tasks.optJSONObject(index) ?: continue
                if (task.optString("taskStatus") != TaskStatus.FINISHED.name) continue
                val award = OceanTaskRpc.receiveTaskAward(task.optString("sceneCode"), task.optString("taskType")).toJsonObject()
                if (ResChecker.checkRes("$TAG 领取海洋任务奖励失败:", award)) {
                    StructuredLog.forest("海洋奖励🌊[${task.optString("taskType")}]")
                }
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "海洋领奖轮询异常:", t)
        }
    }

    @JvmStatic
    fun answerQuestion(): Boolean {
        try {
            val question = OceanDadaRpc.getQuestion().toJsonObject()
            if (question.optBoolean("answered")) return true
            if (question.optInt("resultCode") != 200) {
                StructuredLog.error(TAG, "海洋获取问题失败：$question")
                return false
            }
            val options: JsonArray = question.optJSONArray("options") ?: return false
            val result = OceanDadaRpc.submitAnswer(options.optString(0), question.optString("questionId")).toJsonObject()
            if (result.optInt("resultCode") == 200) {
                StructuredLog.forest("$TAG 🌊海洋答题成功")
                return true
            }
            StructuredLog.error(TAG, "海洋答题失败：$result")
            return false
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "海洋答题错误", t)
            return false
        }
    }
}
