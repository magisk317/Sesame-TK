package io.github.magisk317.sesame.task.antForest

import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.task.TaskStatus
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ResChecker
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.hook.keepalive.SmartSchedulerManager
import io.github.magisk317.sesame.util.getJSONObject
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.opt
import io.github.magisk317.sesame.util.optBoolean
import io.github.magisk317.sesame.util.optInt
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optLong
import io.github.magisk317.sesame.util.optString
import io.github.magisk317.sesame.util.toJsonElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 森林寻宝任务处理类 (每天自动执行, 完成后标记)
 */
class ForestChouChouLe {

    internal enum class BackendTaskAction {
        EXCHANGE_VITALITY,
        FINISH_ANTIEP,
        FINISH_XLIGHT,
        FINISH_OPEN_GREEN,
        UNSUPPORTED
    }

    internal data class PendingTask(
        val name: String,
        val status: String,
        val requiresManualAction: Boolean
    )

    internal data class TaskCompletionSummary(
        val totalTasks: Int,
        val completedTasks: Int,
        val malformedTasks: Int,
        val pendingTasks: List<PendingTask>
    )

    internal data class TaskLoopResult(
        val statusConfirmed: Boolean,
        val awardsPending: Boolean,
    ) {
        val canStartLottery: Boolean
            get() = statusConfirmed && !awardsPending
    }

    companion object {
        private const val TAG = "ForestChouChouLe"
        private const val SOURCE = "task_entry"
        private const val TASK_STATE_DELAY_MS = 3_000L
        private const val LOTTERY_DELAY_MS = 2_000L
        private const val CLAIM_FOLLOW_UP_DELAY_MS = 30_000L
        private const val MAX_TASK_ROUNDS = 8
        private const val BATCH_DRAW_COUNT = 10
        private val claimFollowUpScheduled = AtomicBoolean(false)

        // 场景代码常量
        private const val SCENE_NORMAL = "ANTFOREST_NORMAL_DRAW"
        private const val SCENE_ACTIVITY = "ANTFOREST_ACTIVITY_DRAW"
        private val SUPPORTED_SCENE_CODES = setOf(SCENE_NORMAL, SCENE_ACTIVITY)

        // 2026-08-09 实测返回 code=400000040，不支持当前后台完成 RPC。
        // 任务类型明确为外部页面或游戏时，不调用通用 finish RPC。
        private val BLOCKED_TYPES = setOf(
            "FOREST_NORMAL_DRAW_SHARE",
            "FOREST_ACTIVITY_DRAW_SHARE",
            "FOREST_ACTIVITY_DRAW_SQYT_1", // 2026-08-09 code=400000040，鱼塘外部页面
            "FOREST_ACTIVITY_DRAW_LHY_ZHWUFU", // 2026-08-09 code=400000040，灵画师游戏
            "FOREST_ACTIVITY_DRAW_ZCYLT_ZHWUFU", // 2026-08-09 code=400000040，外部游戏
            "FOREST_ACTIVITY_DRAW_YXZY_ZHWUFU" // 2026-08-09 未验证，外部游戏
        )

        // 屏蔽的任务名称关键词
        // 不包含“玩一玩”：当前的 ZBT_LLRW_ACTIVITY 就是可后台完成的浏览任务。
        private val BLOCKED_NAMES = setOf("玩游戏得", "开宝箱", "鱼塘")

        // These task types were verified against the live activity response.
        private val VERIFIED_XLIGHT_TASK_TYPES = setOf(
            // Live RPC probe: FE electric formula costume, 2026-08-09.
            "FOREST_NORMAL_DRAW_XLIGHT_1",
            // Live RPC probe: Yellow Crane Tower theme costume returned 100000000, 2026-08-09.
            "FOREST_ACTIVITY_DRAW_XLIGHT_2",
        )

        // 已通过真实响应验证的 com.alipay.antiep.finishTask 任务。
        // 该协议与 XLIGHT 使用同一个 RPC，但任务类型不是 XLIGHT，单独维护避免
        // 后续把“有相同 RPC”误当成“同一业务协议”。
        private val VERIFIED_ANTIEP_TASK_TYPES = setOf(
            "SSBHC_LLRW_NORMAL"
        )

        // Only task types observed in the current live task responses or in a
        // still-valid protocol probe belong here. Older campaign task types
        // must fall through to UNSUPPORTED after they disappear from the list.
        private val VERIFIED_OPEN_GREEN_TASK_TYPES = setOf(
            "ZBT_LLRW_ACTIVITY",
            "FOREST_NORMAL_DRAW_SLLXX",
            "FOREST_NORMAL_DRAW_QKSL",
            // Live RPC probe returned 100000000, 2026-08-09.
            "HBGWB_LLRW_ACTIVITY",
            // Live RPC probe returned 100000000 and awardInfo, 2026-08-10.
            "FOREST_ACTIVITY_DRAW_DINGYUE",
            // Live RPC probe returned 100000000 and awardInfo, 2026-08-10.
            "MHJLR_LLRW_ACTIVITY",
            // Live HP observation: finish, award claim, and two draws succeeded.
            "FOREST_ACTIVITY_DRAW_TBQD",
            "FOREST_ACTIVITY_DRAW_SLJYD",
        )

        // Current normal-campaign types are still probe candidates. The
        // current task title is blocked as an external game, so these are
        // intentionally not promoted until a live completion is observed.
        private val PROBE_OPEN_GREEN_TASK_TYPES = setOf(
            "FKSSJ_LJRWtanxian_NORMAL",
            "FOREST_NORMAL_DRAW_BWXRK_ZH",
        )

        // 当前任务列表中观察到、但尚未有成功 finish RPC 响应的类型。
        // 显式登记是为了防止后续维护时误加入通用完成白名单。
        private val OBSERVED_UNVERIFIED_TASK_TYPES = setOf(
            // 2026-08-09 code=400000040，不支持当前 finishTask RPC。
            "SGDMX_QDRW_NORMAL",
            "SGBHSD_QDRW_CCL_new",
            "FOREST_NORMAL_DRAW_BWXRK_ZH",
            "WDHYSJ_QDRW_NORMAL",
            "FOREST_NORMAL_DRAW_YYDWLXQ_ZH",
            "WGDS_LLRW_ACTIVITY",
            "FOREST_ACTIVITY_DRAW_YXZY_ZHWUFU"
        )

        /**
         * 抽奖场景数据类
         */
        private data class Scene(
            val id: String,
            val code: String,
            val name: String,
            val flag: String
        ) {
            val taskCode get() = "${code}_TASK"
        }

        // 扩展函数：简化 JSON 解析和检查
        private fun String.toJson(): JsonObject? =
            runCatching { toJsonElement() as? JsonObject }.getOrNull()
        private fun JsonObject.check(): Boolean = ResChecker.checkRes(TAG, this)

        /** Keep completion state isolated when Alipay rotates a campaign. */
        internal fun completionFlag(sceneCode: String, activityId: String): String {
            val scene = when (sceneCode) {
                SCENE_NORMAL -> "normal"
                SCENE_ACTIVITY -> "activity"
                else -> sceneCode.lowercase(Locale.ROOT)
            }
            return "forest::chouChouLe::$scene::$activityId::completed"
        }

        // 动态获取抽奖场景配置
        private fun getScenes(): List<Scene> {
            val defaultScenes = listOf(
                // Last known live fallback from the 2026-08-08 enter-draw response.
                // Dynamic discovery above remains authoritative whenever available.
                Scene("2026070201", SCENE_NORMAL, "FE电动方程式装扮", completionFlag(SCENE_NORMAL, "2026070201")),
                Scene("20260722", SCENE_ACTIVITY, "黄鹤楼主题装扮", completionFlag(SCENE_ACTIVITY, "20260722"))
            )

            return runCatching {
                val scenes = mutableListOf<Scene>()
                fun appendScenes(response: JsonObject?) {
                    if (response == null || !response.check()) return
                    val drawSceneGroups = response.optJSONArray("drawSceneGroups") ?: return

                    for (i in 0 until drawSceneGroups.length()) {
                        val sceneGroup = drawSceneGroups.optJSONObject(i) ?: continue
                        val drawActivity = sceneGroup.optJSONObject("drawActivity") ?: continue

                        val activityId = drawActivity.optString("activityId")
                        val sceneCode = drawActivity.optString("sceneCode")
                        if (activityId.isBlank() || sceneCode !in SUPPORTED_SCENE_CODES) continue
                        if (scenes.any { it.code == sceneCode }) continue

                        // drawActivity.name is the current campaign name (for example,
                        // the FE or Yellow Crane Tower costume), while the group name
                        // only identifies the generic normal/activity tab.
                        val name = drawActivity.optString("name")
                            .ifBlank { sceneGroup.optString("name", "未知活动") }
                        val flag = completionFlag(sceneCode, activityId)
                        scenes.add(Scene(activityId, sceneCode, name, flag))
                    }
                }

                // Current Alipay returns both campaigns from the normal entry. Some
                // builds return only the requested tab, so query the activity entry
                // once more when the first response did not include it.
                appendScenes(
                    ForestDrawRpc.enter("", SCENE_NORMAL, SOURCE)
                        .toJson()
                )
                if (scenes.none { it.code == SCENE_ACTIVITY }) {
                    appendScenes(
                        ForestDrawRpc.enter("", SCENE_ACTIVITY, SOURCE)
                            .toJson()
                    )
                }
                if (scenes.none { it.code == SCENE_NORMAL }) {
                    appendScenes(
                        ForestDrawRpc.enter("", SCENE_NORMAL, SOURCE)
                            .toJson()
                    )
                }

                if (scenes.isEmpty()) defaultScenes else scenes
            }.getOrElse {
                StructuredLog.printStackTrace(TAG, "获取抽奖场景配置失败, 使用默认配置", it)
                defaultScenes
            }
        }
    }

    suspend fun chouChouLe() {
        try {
            val scenes = getScenes()
            if (scenes.all { Status.hasFlagToday(it.flag) }) {
                StructuredLog.record("⏭️ 今天所有森林寻宝任务已完成, 跳过执行")
                return
            }

            StructuredLog.record("森林寻宝🎁 场景数: ${scenes.size}")
            scenes.forEach {
                processScene(it)
                delay(100L)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "执行异常", t)
        }
    }

    /** Claim-only entry used by the global award poller. */
    suspend fun claimPendingAwards() {
        scanPendingAwards()
    }

    private suspend fun processScene(s: Scene) {
        try {
            if (Status.hasFlagToday(s.flag)) {
                StructuredLog.record("⏭️ ${s.name} 今天已完成, 跳过")
                return
            }

            StructuredLog.record("森林寻宝🎁 场景: ${s.name}")

            // 1. 检查活动有效期
            val enterResp = ForestDrawRpc.enter(s.id, s.code, SOURCE).toJson()
            if (enterResp == null || !enterResp.check()) return

            val drawActivity = enterResp.optJSONObject("drawActivity")
            if (drawActivity != null) {
                val now = System.currentTimeMillis()
                val startTime = drawActivity.optLong("startTime")
                val endTime = drawActivity.optLong("endTime")
                if (now !in startTime..endTime) {
                    StructuredLog.record("⛔ ${s.name} 活动不在有效期内, 跳过")
                    return
                }
            }

            // 2. 循环处理任务 (执行 -> 领取)。领奖状态未确认前不进入抽奖，
            // 避免把尚未领取的机会消耗在后续流程中。
            val taskResult = processTasksLoop(s)
            if (!taskResult.canStartLottery) {
                if (taskResult.awardsPending) {
                    scheduleClaimFollowUp()
                    StructuredLog.record("${s.name} 仍有待领取奖励, 暂停抽奖并等待下次复查")
                } else {
                    StructuredLog.record("${s.name} 任务状态未确认, 暂停抽奖并留待下次执行")
                }
                return
            }

            // 3. 执行抽奖
            val lotteryCompleted = processLottery(s)

            // 4. 最终检查完成状态
            checkCompletion(s, lotteryCompleted)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "${s.name} 处理异常", t)
        }
    }

    private suspend fun processTasksLoop(s: Scene): TaskLoopResult {
        var loop = 0
        while (loop < MAX_TASK_ROUNDS) {
            loop++
            StructuredLog.record("${s.name} 第 $loop 轮任务检查")
            val tasksResp = ForestDrawRpc.list(s.taskCode, SOURCE).toJson()
                ?: return TaskLoopResult(statusConfirmed = false, awardsPending = true)
            if (!tasksResp.check()) {
                return TaskLoopResult(statusConfirmed = false, awardsPending = true)
            }

            val taskList = tasksResp.optJSONArray("taskInfoList")
                ?: return TaskLoopResult(statusConfirmed = false, awardsPending = true)
            var hasChange = false
            val hasFinishedTask = hasFinishedTask(taskList)

            for (i in 0 until taskList.length()) {
                val task = taskList.optJSONObject(i) ?: continue
                if (processSingleTask(s, task)) {
                    hasChange = true
                }
            }

            if (!hasChange) {
                if (hasFinishedTask) {
                    delay(TASK_STATE_DELAY_MS)
                    continue
                }
                StructuredLog.record("森林寻宝🎁 ${s.name} 本轮无任务状态变更")
                return TaskLoopResult(statusConfirmed = true, awardsPending = false)
            }
            delay(TASK_STATE_DELAY_MS)
        }

        // A successful claim can still be reported as FINISHED while the task
        // list is catching up. Make one final read before allowing the lottery.
        val finalResponse = ForestDrawRpc.list(s.taskCode, SOURCE).toJson()
            ?: return TaskLoopResult(statusConfirmed = false, awardsPending = true)
        if (!finalResponse.check()) {
            return TaskLoopResult(statusConfirmed = false, awardsPending = true)
        }
        val finalTasks = finalResponse.optJSONArray("taskInfoList")
            ?: return TaskLoopResult(statusConfirmed = false, awardsPending = true)
        return TaskLoopResult(
            statusConfirmed = true,
            awardsPending = hasFinishedTask(finalTasks),
        )
    }

    private fun hasFinishedTask(taskList: JsonArray): Boolean = (0 until taskList.length()).any { index ->
        taskList.optJSONObject(index)
            ?.optJSONObject("taskBaseInfo")
            ?.optString("taskStatus") == TaskStatus.FINISHED.name
    }

    /**
     * 执行抽奖逻辑
     */
    private suspend fun processLottery(s: Scene): Boolean {
        val enterResp = ForestDrawRpc.enter(s.id, s.code, SOURCE).toJson() ?: return false
        if (!enterResp.check()) return false

        val drawAsset = enterResp.optJSONObject("drawAsset") ?: run {
            StructuredLog.error(TAG, "${s.name} 抽奖资产缺失, 本次不标记完成")
            return false
        }
        var balance = drawAsset.drawBalance() ?: run {
            StructuredLog.error(TAG, "${s.name} 抽奖余额缺失, 本次不标记完成")
            return false
        }
        val total = drawAsset.optInt("totalTimes", 0)
        val batchDrawEnabled = enterResp.optJSONObject("drawScene")
            ?.optJSONObject("extInfo")
            ?.optString("openBatchDraw") == "Y"

        StructuredLog.record("${s.name} 剩余抽奖次数: $balance / $total")

        var retry = 0
        // 最多抽50次，防止死循环
        while (balance > 0 && retry < 50) {
            retry++
            val drawCount = drawRequestCount(balance, batchDrawEnabled)
            StructuredLog.record(
                if (drawCount > 1) {
                    "${s.name} 连抽 $drawCount 次 (剩余 $balance)"
                } else {
                    "${s.name} 第 $retry 次抽奖"
                },
            )

            val drawResp = if (drawCount > 1) {
                ForestDrawRpc.batchDraw(
                    s.id,
                    s.code,
                    SOURCE,
                    UserMap.currentUid,
                    drawCount,
                ).toJson()
            } else {
                ForestDrawRpc.draw(s.id, s.code, SOURCE, UserMap.currentUid).toJson()
            }
            if (drawResp == null || !drawResp.check()) {
                return false
            }

            val nextBalance = drawResp.optJSONObject("drawAsset")?.drawBalance()
            if (nextBalance == null) {
                StructuredLog.error(TAG, "${s.name} 抽奖响应缺少余额, 本次不标记完成")
                return false
            }
            if (nextBalance >= balance) {
                StructuredLog.error(
                    TAG,
                    "${s.name} 抽奖响应余额未减少 ($balance -> $nextBalance), 停止重试以避免重复抽奖",
                )
                return false
            }
            balance = nextBalance
            logDrawPrizes(s.name, drawResp, balance)

            if (balance > 0) delay(LOTTERY_DELAY_MS)
        }

        if (balance > 0) {
            StructuredLog.record("${s.name} 仍有 $balance 次抽奖机会, 下次继续")
            return false
        }
        return true
    }

    private fun logDrawPrizes(sceneName: String, response: JsonObject, balance: Int) {
        val results = response.optJSONArray("drawResultList")
        if (results != null && results.length() > 0) {
            for (index in 0 until results.length()) {
                val prize = results.optJSONObject(index)?.optJSONObject("prizeVO") ?: continue
                logPrize(sceneName, prize, balance)
            }
            return
        }

        val prize = response.optJSONObject("prizeVO")
            ?: response.optJSONObject("firstAward")?.optJSONObject("prizeVO")
        if (prize != null) logPrize(sceneName, prize, balance)
    }

    private fun logPrize(sceneName: String, prize: JsonObject, balance: Int) {
        val name = prize.optString("prizeName", "未知奖品")
        val num = prize.optInt("prizeNum", 1)
        StructuredLog.forest("$sceneName 🎁 [获得: $name * $num] 剩余次数: $balance")
    }

    /**
     * 检查是否所有任务都已完成，并设置 Flag
     */
    private fun checkCompletion(s: Scene, lotteryCompleted: Boolean) {
        val resp = ForestDrawRpc.list(s.taskCode, SOURCE).toJson() ?: return
        if (!resp.check()) return

        val taskList = resp.optJSONArray("taskInfoList") ?: return
        val summary = evaluateTaskCompletion(taskList)

        for (task in summary.pendingTasks) {
            val reason = if (task.requiresManualAction) "待手动完成" else "未完成"
            StructuredLog.record("${s.name} $reason: ${task.name} [${task.status}]")
        }
        if (summary.malformedTasks > 0) {
            StructuredLog.error(TAG, "${s.name} 有 ${summary.malformedTasks} 个任务数据无法确认, 本次不标记完成")
        }

        StructuredLog.record("${s.name} 进度: ${summary.completedTasks} / ${summary.totalTasks}")
        if (shouldMarkSceneComplete(lotteryCompleted, summary)) {
            Status.setFlagToday(s.flag)
            StructuredLog.record("✓ ${s.name} 全部完成 (${summary.completedTasks}/${summary.totalTasks})")
        } else if (!lotteryCompleted) {
            StructuredLog.record("⚠️ ${s.name} 抽奖尚未完成, 保留今日重试")
        } else if (summary.malformedTasks > 0) {
            StructuredLog.record("⚠️ ${s.name} 任务数据不完整, 保留今日重试")
        } else if (summary.totalTasks == 0) {
            StructuredLog.record("⚠️ ${s.name} 未发现可确认的有效任务, 保留今日重试")
        } else {
            StructuredLog.record("⚠️ ${s.name} 未全部完成")
        }
    }

    /**
     * 判断任务是否在屏蔽列表中
     */
    private fun isBlockedTask(taskType: String, taskName: String): Boolean {
        return BLOCKED_TYPES.any { taskType.contains(it) } ||
                BLOCKED_NAMES.any { taskName.contains(it) }
    }

    /**
     * 处理单个任务分发
     * @return 任务状态是否有变更
     */
    private suspend fun processSingleTask(s: Scene, task: JsonObject): Boolean {
        val baseInfo = task.optJSONObject("taskBaseInfo") ?: return false
        val taskStatus = baseInfo.optString("taskStatus")
        val taskType = baseInfo.optString("taskType")
        if (taskType.isBlank() || taskStatus.isBlank()) {
            StructuredLog.error(TAG, "${s.name} 任务数据缺少类型或状态, 跳过执行")
            return false
        }

        val taskName = parseTaskName(taskType, baseInfo.optString("bizInfo")) ?: run {
            StructuredLog.error(TAG, "${s.name} 任务信息无法解析, 跳过执行: $taskType")
            return false
        }
        val taskCode = baseInfo.optString("sceneCode")
        val taskRights = task.optJSONObject("taskRights")
        val rightsTimes = taskRights?.optInt("rightsTimes", 0) ?: 0
        val rightsTimesLimit = taskRights?.optInt("rightsTimesLimit", 0) ?: 0

        if (taskStatus == TaskStatus.TODO.name && rightsTimesLimit > 0 && rightsTimes >= rightsTimesLimit) {
            StructuredLog.record("${s.name} 任务次数已达上限: $taskName [$rightsTimes/$rightsTimesLimit]")
            return false
        }

        if (taskStatus == TaskStatus.TODO.name && isBlockedTask(taskType, taskName)) {
            StructuredLog.record("${s.name} 待手动完成: $taskName [$taskStatus]")
            return false
        }

        val backendAction = backendTaskAction(taskType)
        if (taskStatus == TaskStatus.TODO.name && backendAction == BackendTaskAction.UNSUPPORTED) {
            StructuredLog.record("${s.name} 后台完成接口未验证, 保留待处理: $taskName [$taskType]")
            return false
        }

        StructuredLog.record("${s.name} 任务: $taskName [$taskStatus]")

        return when (taskStatus) {
            TaskStatus.TODO.name -> handleTodoTask(s, taskName, taskCode, taskType, backendAction)
            TaskStatus.FINISHED.name -> handleFinishedTask(s, taskName, taskCode, taskType)
            else -> false
        }
    }

    private suspend fun handleTodoTask(
        s: Scene,
        name: String,
        code: String,
        type: String,
        action: BackendTaskAction,
    ): Boolean {
        return when (action) {
            BackendTaskAction.EXCHANGE_VITALITY -> {
                StructuredLog.record("${s.name} 兑换活力值: $name")
                val res = ForestDrawRpc.exchange(s.id, s.code, SOURCE, code, type).toJson()
                if (res != null && res.check()) {
                    StructuredLog.forest("${s.name} 🧾 $name 兑换成功")
                    true
                } else {
                    false
                }
            }

            BackendTaskAction.FINISH_ANTIEP,
            BackendTaskAction.FINISH_XLIGHT,
            BackendTaskAction.FINISH_OPEN_GREEN -> {
                // The server accepted the live-probed task immediately; the
                // old 30-second UI dwell did not contribute to completion.
                StructuredLog.record("${s.name} 后台执行任务: $name")
                val result = if (action == BackendTaskAction.FINISH_XLIGHT ||
                    action == BackendTaskAction.FINISH_ANTIEP) {
                    ForestDrawRpc.finishAdTask(type, code)
                } else {
                    ForestDrawRpc.finishTask(type, code)
                }
                val resJson = result.toJson()
                if (resJson != null && resJson.check()) {
                    StructuredLog.forest("${s.name} 🧾 $name")
                    // finishTask 成功只代表任务状态已推进，奖励仍可能需要单独领取。
                    // 先立即领取；若服务端尚未完成状态同步，后续列表扫描会重试。
                    claimTaskAward(s, name, code, type)
                    true
                } else {
                    StructuredLog.error(TAG, "${s.name} 后台任务失败: $name")
                    false
                }
            }

            BackendTaskAction.UNSUPPORTED -> {
                StructuredLog.record("${s.name} 后台完成接口未验证, 保留待处理: $name [$type]")
                false
            }
        }
    }

    private suspend fun handleFinishedTask(s: Scene, name: String, code: String, type: String): Boolean {
        StructuredLog.record("${s.name} 领取奖励: $name")
        delay(TASK_STATE_DELAY_MS)
        return claimTaskAward(s, name, code, type)
    }

    private fun claimTaskAward(s: Scene, name: String, code: String, type: String): Boolean {
        val res = runCatching {
            ForestDrawRpc.receive(
                SOURCE,
                code.ifBlank { s.taskCode },
                type,
            ).toJson()
        }.getOrNull()
        return if (res != null && res.check()) {
            StructuredLog.forest("${s.name} 🧾 $name 奖励领取成功")
            true
        } else {
            StructuredLog.error(TAG, "${s.name} 奖励领取失败, 将在下一次扫描重试: $name")
            false
        }
    }

    /** Schedule a claim-only retry so a delayed server state never leaves a reward stranded. */
    private fun scheduleClaimFollowUp() {
        if (!claimFollowUpScheduled.compareAndSet(false, true)) return
        val taskId = SmartSchedulerManager.schedule(
            CLAIM_FOLLOW_UP_DELAY_MS,
            "森林抽抽乐领奖复查",
        ) {
            claimFollowUpScheduled.set(false)
            GlobalThreadPools.execute {
                scanPendingAwards()
            }
        }
        if (taskId < 0) {
            claimFollowUpScheduled.set(false)
        }
    }

    private suspend fun scanPendingAwards() {
        try {
            getScenes().forEach { scene ->
                repeat(3) {
                    val response = ForestDrawRpc.list(scene.taskCode, SOURCE).toJson()
                    if (response == null || !response.check()) return@repeat
                    val tasks = response.optJSONArray("taskInfoList") ?: return@repeat
                    var foundFinished = false
                    var claimed = false
                    for (i in 0 until tasks.length()) {
                        val baseInfo = tasks.optJSONObject(i)?.optJSONObject("taskBaseInfo") ?: continue
                        if (baseInfo.optString("taskStatus") != TaskStatus.FINISHED.name) continue
                        foundFinished = true
                        val type = baseInfo.optString("taskType")
                        val name = parseTaskName(type, baseInfo.optString("bizInfo")) ?: type
                        if (claimTaskAward(scene, name, baseInfo.optString("sceneCode"), type)) {
                            claimed = true
                        }
                    }
                    if (!foundFinished || !claimed) return@repeat
                    delay(TASK_STATE_DELAY_MS)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, "领奖复查异常", t)
        }
    }

    internal fun evaluateTaskCompletion(taskList: JsonArray): TaskCompletionSummary {
        var totalTasks = 0
        var completedTasks = 0
        var malformedTasks = 0
        val pendingTasks = mutableListOf<PendingTask>()

        for (i in 0 until taskList.length()) {
            val task = taskList.optJSONObject(i)
            val baseInfo = task?.optJSONObject("taskBaseInfo")
            if (baseInfo == null) {
                malformedTasks++
                continue
            }

            val taskType = baseInfo.optString("taskType")
            val taskStatus = baseInfo.optString("taskStatus")
            if (taskType.isBlank() || taskStatus.isBlank()) {
                malformedTasks++
                continue
            }

            val bizInfoStr = baseInfo.optString("bizInfo")
            val taskName = parseTaskName(taskType, bizInfoStr)
            if (taskName == null) {
                malformedTasks++
                continue
            }

            totalTasks++
            val taskRights = task.optJSONObject("taskRights")
            val rightsTimes = taskRights?.optInt("rightsTimes", 0) ?: 0
            val rightsTimesLimit = taskRights?.optInt("rightsTimesLimit", 0) ?: 0
            val reachedRightsLimit = rightsTimesLimit > 0 && rightsTimes >= rightsTimesLimit
            if (taskStatus == TaskStatus.RECEIVED.name || reachedRightsLimit) {
                completedTasks++
            } else {
                pendingTasks.add(
                    PendingTask(
                        taskName,
                        taskStatus,
                        isBlockedTask(taskType, taskName) ||
                                backendTaskAction(taskType) == BackendTaskAction.UNSUPPORTED
                    )
                )
            }
        }

        return TaskCompletionSummary(totalTasks, completedTasks, malformedTasks, pendingTasks)
    }

    internal fun parseTaskName(taskType: String, bizInfo: String): String? {
        if (taskType.isBlank()) return null
        if (bizInfo.isBlank()) return taskType
        val info = bizInfo.toJsonElement() as? JsonObject ?: return null
        return info.optString("title", taskType).ifBlank { taskType }
    }

    internal fun backendTaskAction(taskType: String): BackendTaskAction = when {
        taskType == "NORMAL_DRAW_EXCHANGE_VITALITY" -> BackendTaskAction.EXCHANGE_VITALITY
        VERIFIED_ANTIEP_TASK_TYPES.contains(taskType) -> BackendTaskAction.FINISH_ANTIEP
        VERIFIED_XLIGHT_TASK_TYPES.contains(taskType) -> BackendTaskAction.FINISH_XLIGHT
        VERIFIED_OPEN_GREEN_TASK_TYPES.contains(taskType) -> BackendTaskAction.FINISH_OPEN_GREEN
        PROBE_OPEN_GREEN_TASK_TYPES.contains(taskType) -> BackendTaskAction.FINISH_OPEN_GREEN
        OBSERVED_UNVERIFIED_TASK_TYPES.contains(taskType) -> BackendTaskAction.UNSUPPORTED
        else -> BackendTaskAction.UNSUPPORTED
    }

    internal fun JsonObject.drawBalance(): Int? {
        val rawValue = this["blance"] ?: this["balance"] ?: return null
        val primitive = rawValue as? JsonPrimitive ?: return null
        if (primitive.isString) return primitive.content.toIntOrNull()?.takeIf { it >= 0 }
        return primitive.content.toDoubleOrNull()?.let { value ->
            if (value.isFinite() && value >= 0 && value <= Int.MAX_VALUE && value % 1.0 == 0.0) {
                value.toInt()
            } else null
        }
    }

    internal fun shouldMarkSceneComplete(
        lotteryCompleted: Boolean,
        summary: TaskCompletionSummary
    ): Boolean = lotteryCompleted &&
            summary.malformedTasks == 0 &&
            summary.totalTasks > 0 &&
            summary.completedTasks == summary.totalTasks

    internal fun drawRequestCount(balance: Int, batchDrawEnabled: Boolean): Int =
        if (batchDrawEnabled && balance > BATCH_DRAW_COUNT) BATCH_DRAW_COUNT else 1
}
