package io.github.magisk317.sesame.task.customTasks

import io.github.magisk317.sesame.hook.ApplicationHook
import io.github.magisk317.sesame.entitlement.EntitlementGate
import io.github.magisk317.sesame.model.ModelRegistry
import io.github.magisk317.sesame.task.antFarm.AntFarm
import io.github.magisk317.sesame.task.antForest.AntForest
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 手动任务执行器
 */
object ManualTask {

    /**
     * 手动任务流总开关
     */
    @Volatile
    var isManualEnabled = true

    /**
     * 标记手动任务是否正在运行，用于与自动任务互斥
     */
    @Volatile
    var isManualRunning = false
        private set

    private val stateLock = Any()

    /**
     * 为 Java 提供的非 suspend 启动接口
     */
    @JvmStatic
    @JvmOverloads
    fun runSingle(task: CustomTask, extraParams: Map<String, Any> = emptyMap()) {
        GlobalThreadPools.execute {
            run(listOf(task), extraParams)
        }
    }

    /**
     * 顺序执行选中的庄园子任务
     */
    suspend fun run(tasks: List<CustomTask>, extraParams: Map<String, Any> = emptyMap()) {
        if (!EntitlementGate.isAutomationAllowed()) {
            StructuredLog.record("ManualTask", "⛔ 授权无效，跳过手动任务流")
            return
        }
        if (!isManualEnabled) {
            StructuredLog.record("ManualTask", "⚠️ 手动任务流总开关已关闭，无法执行")
            return
        }

        if (tasks.isEmpty()) {
            StructuredLog.record("ManualTask", "⚠️ 未选中任何子任务")
            return
        }

        if (isManualRunning) {
            StructuredLog.record("ManualTask", "⚠️ 手动任务已在运行中，请勿重复启动")
            return
        }

        synchronized(stateLock) {
            if (isManualRunning) {
                StructuredLog.record("ManualTask", "⚠️ 手动任务已在运行中，请勿重复启动")
                return
            }
            isManualRunning = true
        }

        try {
            withContext(Dispatchers.IO) {
                for (task in tasks) {
                    try {
                        StructuredLog.record("ManualTask", "⏳ 正在执行: ${task.displayName}...")
                        when (task) {
                            // 森林类任务
                            CustomTask.FOREST_WHACK_MOLE -> {
                                val instance = getForestInstance()
                                if (instance != null) {
                                    val mode = extraParams["whackMoleMode"] as? Int ?: 1
                                    val games = extraParams["whackMoleGames"] as? Int ?: 5
                                    instance.manualWhackMole(mode, games)
                                } else {
                                    StructuredLog.record("ManualTask", "森林🌿 无法加载模块")
                                }
                            }

                            CustomTask.FOREST_ENERGY_RAIN -> {
                                val instance = getForestInstance()
                                if (instance != null) {
                                    val exchange = extraParams["exchangeEnergyRainCard"] as? Boolean ?: false
                                    instance.manualUseEnergyRain(exchange)
                                } else {
                                    StructuredLog.record("ManualTask", "森林🌿 无法加载模块")
                                }
                            }

                            // 庄园类任务
                            CustomTask.FARM_SEND_BACK_ANIMAL -> getFarmInstance()?.manualSendBackAnimal()
                            CustomTask.FARM_GAME_LOGIC -> getFarmInstance()?.manualFarmGameLogic()
                            CustomTask.FARM_CHOUCHOULE -> getFarmInstance()?.manualChouChouLeLogic()
                            CustomTask.FARM_SPECIAL_FOOD -> {
                                val count = extraParams["specialFoodCount"] as? Int ?: 0
                                getFarmInstance()?.manualUseSpecialFood(count)
                            }
                            CustomTask.FARM_USE_TOOL -> {
                                val toolType = extraParams["toolType"] as? String ?: ""
                                val toolCount = extraParams["toolCount"] as? Int ?: 1
                                getFarmInstance()?.manualUseFarmTool(toolType, toolCount)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        StructuredLog.record("ManualTask", "手动任务🛠️ ❌ ${task.displayName} 执行出错: ${e.message}")
                        StructuredLog.printStackTrace(e)
                    }
                }
            }
        } finally {
            synchronized(stateLock) {
                isManualRunning = false
            }
        }
    }

    /**
     * 按需获取并确保蚂蚁森林实例已加载
     */
    private fun getForestInstance(): AntForest? {
        val loader = ApplicationHook.classLoader ?: return null
        return runCatching {
            ModelRegistry.prepareAndBoot(AntForest::class.java, loader)
        }.onFailure {
            StructuredLog.printStackTrace("ManualTask", "按需加载森林模块失败", it)
        }.getOrNull()
    }

    /**
     * 按需获取并确保蚂蚁庄园实例已加载
     */
    private fun getFarmInstance(): AntFarm? {
        val loader = ApplicationHook.classLoader ?: return null
        return runCatching {
            ModelRegistry.prepareAndBoot(AntFarm::class.java, loader)
        }.onFailure {
            StructuredLog.printStackTrace("ManualTask", "按需加载庄园模块失败", it)
        }.getOrNull()
    }
}
