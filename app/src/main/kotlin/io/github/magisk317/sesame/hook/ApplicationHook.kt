package io.github.magisk317.sesame.hook

import android.annotation.SuppressLint
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Handler
import android.os.Looper
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.sesame.BuildConfig
import io.github.magisk317.sesame.data.Config
import io.github.magisk317.sesame.data.db.TaskStatsRepository
import io.github.magisk317.sesame.data.General
import io.github.magisk317.sesame.data.RuntimeInfo
import io.github.magisk317.sesame.data.Status
import io.github.magisk317.sesame.data.Status.Companion.load
import io.github.magisk317.sesame.data.Status.Companion.save
import io.github.magisk317.sesame.entity.AlipayVersion
import io.github.magisk317.sesame.service.HostRuntimeStore
import io.github.magisk317.sesame.service.RemotePrefsStore
import io.github.magisk317.sesame.hook.Toast.show
import io.github.magisk317.sesame.hook.TokenHooker.start
import io.github.magisk317.sesame.hook.XposedEnv.processName
import io.github.magisk317.sesame.hook.internal.AlipayMiniMarkHelper
import io.github.magisk317.sesame.hook.internal.BroadcastDispatcher
import io.github.magisk317.sesame.hook.internal.LocationHelper
import io.github.magisk317.sesame.hook.internal.AuthCodeHelper
import io.github.magisk317.sesame.hook.internal.CalendarDayRollover
import io.github.magisk317.sesame.hook.internal.NativeDexKitLoader
import io.github.magisk317.sesame.hook.internal.SecurityBodyHelper
import io.github.magisk317.sesame.hook.keepalive.SmartSchedulerManager
import io.github.magisk317.sesame.hook.keepalive.SmartSchedulerManager.cleanup
import io.github.magisk317.sesame.hook.keepalive.SmartSchedulerManager.schedule
import io.github.magisk317.sesame.hook.rpc.bridge.NewRpcBridge
import io.github.magisk317.sesame.hook.rpc.bridge.OldRpcBridge
import io.github.magisk317.sesame.hook.rpc.bridge.RpcBridge
import io.github.magisk317.sesame.hook.rpc.bridge.RpcVersion
import io.github.magisk317.sesame.hook.rpc.debug.DebugRpc
import io.github.magisk317.sesame.hook.rpc.intervallimit.RpcIntervalLimit.clearIntervalLimit
import io.github.magisk317.sesame.hook.server.ModuleHttpServerManager.startIfNeeded
import io.github.magisk317.sesame.hook.server.DebugServerCredentials
import io.github.magisk317.sesame.hook.server.ModuleHttpServerManager.stop as stopDebugHttpServer
import io.github.magisk317.sesame.hook.simple.SimplePageManager.installMyTabLongPressHook
import io.github.magisk317.sesame.hook.simple.SimplePageManager.resetForSession as resetPageSession
import io.github.magisk317.sesame.model.BaseModel.Companion.batteryPerm
import io.github.magisk317.sesame.model.BaseModel.Companion.checkInterval
import io.github.magisk317.sesame.model.BaseModel.Companion.debugMode
import io.github.magisk317.sesame.model.BaseModel.Companion.destroyData
import io.github.magisk317.sesame.model.BaseModel.Companion.execAtTimeList
import io.github.magisk317.sesame.model.BaseModel.Companion.lite2GameCapture
import io.github.magisk317.sesame.model.BaseModel.Companion.newRpc
import io.github.magisk317.sesame.model.BaseModel.Companion.sendHookData
import io.github.magisk317.sesame.model.BaseModel.Companion.sendHookDataUrl
import io.github.magisk317.sesame.model.BaseModel.Companion.wakenAtTimeList
import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.task.MainTask
import io.github.magisk317.sesame.task.MainTask.Companion.newInstance
import io.github.magisk317.sesame.task.ModelTask.Companion.stopAllTask
import io.github.magisk317.sesame.task.TaskRunnerAdapter
import io.github.magisk317.sesame.task.PendingAwardPoller
import io.github.magisk317.sesame.task.antForest.AntForest
import io.github.magisk317.sesame.entitlement.EntitlementGate
import io.github.magisk317.sesame.telemetry.SesameTelemetry
import io.github.magisk317.sesame.task.antForest.EnergyWaitingManager.resetForSession as resetEnergyWaitingSession
import io.github.magisk317.sesame.task.customTasks.CustomTask
import io.github.magisk317.sesame.task.customTasks.ManualTask
import io.github.magisk317.sesame.task.customTasks.ManualTaskModel
import io.github.magisk317.sesame.util.TaskRuntimeStore
import io.github.magisk317.sesame.util.Detector
import io.github.magisk317.sesame.util.Detector.loadLibrary
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.GlobalThreadPools.execute
import io.github.magisk317.sesame.util.GlobalThreadPools.shutdownAndRestart
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TimeFormatter
import io.github.magisk317.sesame.util.StructuredLogStore
import io.github.magisk317.sesame.util.StructuredLog.error
import io.github.magisk317.sesame.util.StructuredLog.printStackTrace
import io.github.magisk317.sesame.util.StructuredLog.record
import io.github.magisk317.sesame.util.ModuleStatus
import io.github.magisk317.sesame.util.Notify
import io.github.magisk317.sesame.util.Notify.stop
import io.github.magisk317.sesame.util.Notify.updateStatusText
import io.github.magisk317.sesame.util.PermissionUtil
import io.github.magisk317.sesame.util.PermissionUtil.checkBatteryPermissions
import io.github.magisk317.sesame.util.StatusManager.updateStatus
import io.github.magisk317.sesame.util.TimeUtil
import io.github.magisk317.sesame.util.maps.UserMap
import io.github.magisk317.sesame.util.maps.UserMap.currentUid
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.lang.AutoCloseable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.Calendar
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.Volatile

class ApplicationHook {
    var xposedInterface: XposedInterface? = null
        set(value) {
            field = value
            Companion.moduleInterface = value
        }

    private object AlipayClasses {
        const val APPLICATION: String = "com.alipay.mobile.framework.AlipayApplication"
        const val SOCIAL_SDK: String = "com.alipay.mobile.personalbase.service.SocialSdkContactService"
        const val LAUNCHER_ACTIVITY: String = "com.alipay.mobile.quinox.LauncherActivity"
        const val SERVICE: String = "android.app.Service"
        const val LOADED_APK: String = "android.app.LoadedApk"
    }

    private class TaskLock : AutoCloseable {
        private val delegate = SessionLifecycle.TaskLock()

        override fun close() {
            delegate.close()
        }
    }

    // --- 入口方法 ---
    fun loadPackage(lpparam: LoadParam) {
        if (General.PACKAGE_NAME != lpparam.packageName) return
        val apkPath = runCatching { XposedEnv.appInfo.sourceDir }.getOrNull()
            ?: runCatching { appContext?.applicationInfo?.sourceDir }.getOrNull()
            ?: ""
        handleHookLogic(
            lpparam.classLoader,
            lpparam.packageName,
            apkPath,
            lpparam
        )
    }

    @SuppressLint("PrivateApi")
    private fun handleHookLogic(loader: ClassLoader?, packageName: String, apkPath: String, rawParam: Any?) {
        classLoader = loader
        // 1. 进程检查（remote prefs via libxposed runtime when needed）
        resolveProcessName(rawParam)
        if (isLite2Process()) {
            if (BuildConfig.DEBUG) {
                hookLite2CaptureLifecycle()
            }
            return
        }
        if (!shouldHookProcess()) {
            return
        }

        if (isHooked) return
        isHooked = true

        // 3. 基础环境 Hook
        ModuleStatus.detectFramework(classLoader!!)
        updateStatus(ModuleStatus.detectFramework(classLoader!!), packageName)
        VersionHook.installHook(classLoader)
        initReflection(classLoader!!)

        // 4. 功能模块 Hook
        try {
            CaptchaHook.setupHook(classLoader!!)
        } catch (t: Throwable) {
            printStackTrace(TAG, "验证码Hook初始化失败", t)
        }

        // 5. 核心生命周期 Hook
        hookApplicationAttach(packageName)
        hookLauncherResume()
        hookServiceLifecycle(apkPath)

        HookUtil.hookOtherService(classLoader!!)
    }

    private fun resolveProcessName(rawParam: Any?) {
        when (rawParam) {
            is LoadParam -> finalProcessName = rawParam.processName.ifBlank { processName }
            else -> finalProcessName = processName
        }
    }

    private fun isLite2Process(): Boolean =
        finalProcessName == "${General.PACKAGE_NAME}:lite2"

    private fun initializeRuntimeStorage(context: Context) {
        TaskRuntimeStore.initialize(context)
        HostRuntimeStore.initialize(context)
    }

    /**
     * lite2 只安装调试捕获，不进入主进程的任务、服务和调度生命周期。
     */
    private fun hookLite2CaptureLifecycle() {
        try {
            SesameHooks.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args.firstOrNull() as? Context ?: return
                        appContext = context
                        StructuredLog.init(context)
                        initializeRuntimeStorage(context)
                        Model.initAllModel()
                        Config.load(RemotePrefsStore.getActiveUserId(), allowFileFallback = false)

                        val enabled = newRpc.value && debugMode.value && lite2GameCapture.value
                        StructuredLog.capture(
                            TAG,
                            "lite2 调试抓包开关: process=$finalProcessName enabled=$enabled " +
                                "newRpc=${newRpc.value} debugMode=${debugMode.value} " +
                                "lite2GameCapture=${lite2GameCapture.value}",
                        )
                        if (enabled) {
                            Lite2DebugCaptureHook.install(
                                classLoader!!,
                                sendHookData.value,
                                sendHookDataUrl.value.orEmpty(),
                            )
                        }
                    }
                },
            )
        } catch (t: Throwable) {
            StructuredLog.capture(
                TAG,
                "lite2 调试抓包生命周期 Hook 失败: ${t.javaClass.simpleName}",
            )
        }
    }

    private fun shouldHookProcess(): Boolean {
        val isMainProcess = General.PACKAGE_NAME == finalProcessName
        return isMainProcess
//            record(TAG, "跳过辅助进程: $finalProcessName")
    }

    private fun initReflection(loader: ClassLoader) {
        try {
            HookHelpers.findClass(AlipayClasses.APPLICATION, loader)
            HookHelpers.findClass(AlipayClasses.SOCIAL_SDK, loader)
        } catch (_: Throwable) {
            // ignore
        }

        try {
            @SuppressLint("PrivateApi") val loadedApkClass = loader.loadClass(AlipayClasses.LOADED_APK)
            deoptimizeClass(loadedApkClass)
        } catch (_: Throwable) {
            // ignore
        }
    }

    private fun hookApplicationAttach(packageName: String?) {
        try {
            SesameHooks.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        appContext = param.args[0] as Context?
                        mainHandler = Handler(Looper.getMainLooper())
                        StructuredLog.init(appContext!!)
                        initializeRuntimeStorage(appContext!!)
                        SesameTelemetry.initialize(appContext!!)
                        TaskStatsRepository.initialize(appContext!!)
                        ensureScheduler()
                        SecurityBodyHelper.init(classLoader!!)
                        AlipayMiniMarkHelper.init(classLoader!!)
                        LocationHelper.init(classLoader!!)
                        AuthCodeHelper.init(classLoader!!)
                        AuthCodeHelper.getAuthCode("2021005114632037" )

                        alipayVersion = NativeDexKitLoader.initVersionInfo(appContext!!, packageName)
                        NativeDexKitLoader.loadLibs(appContext!!)
                        // 特殊版本处理
                        if (VersionHook.hasVersion() && alipayVersion.compareTo(AlipayVersion("10.7.26.8100")) == 0) {
                            HookUtil.fuckAccounLimit(classLoader!!)
                        }

                        NativeDexKitLoader.initSimplePageManager(classLoader!!, alipayVersion)
                    }
                })
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "Hook attach failed", e)
        }
    }

    private fun hookLauncherResume() {
        try {
            SesameHooks.findAndHookMethod(
                AlipayClasses.LAUNCHER_ACTIVITY,
                classLoader,
                "onResume",
                object : MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!EntitlementGate.isAutomationAllowed()) {
                            if (init) destroyHandler()
                            showModuleLoadStatus()
                            record(TAG, "⛔ 未激活，跳过自动化会话初始化")
                            return
                        }
                        // Some current Alipay builds do not reliably deliver the
                        // Application.attach callback after the module is loaded.
                        // The registration itself is idempotent.
                        val targetUid = HookUtil.getUserId(classLoader!!)
                        if (targetUid == null) {
                            show("用户未登录")
                            return
                        }
                        // Launcher can resume before Alipay creates the hooked service.
                        // Wait for the service lifecycle callback instead of reporting a
                        // failed initialization and tearing down an empty session.
                        if (service == null) return
                        if (!init) {
                            if (initHandler()) init = true
                            return
                        }
                        val currentUid = currentUid
                        if (targetUid != currentUid) {
                            if (currentUid != null) {
                                initHandler()
                                lastExecTime = 0
                                show("用户已切换")
                                return
                            }
                            HookUtil.hookUser(classLoader!!)
                        }
                    }
                })
        } catch (t: Throwable) {
            printStackTrace(TAG, "Hook Launcher failed", t)
        }
    }

    private fun hookServiceLifecycle(apkPath: String) {
        try {
            SesameHooks.findAndHookMethod(AlipayClasses.SERVICE, classLoader, "onCreate", object : MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val appService = param.thisObject as Service
                    if (General.CURRENT_USING_SERVICE != appService.javaClass.getCanonicalName()) {
                        return
                    }

                    service = appService
                    appContext = appService.applicationContext
                    StructuredLog.init(appContext!!)
                    initializeRuntimeStorage(appContext!!)
                    SesameTelemetry.initialize(appContext!!)
                    TaskStatsRepository.initialize(appContext!!)
                    if (!EntitlementGate.isAutomationAllowed()) {
                        if (init) destroyHandler(appService)
                        showModuleLoadStatus()
                        record(TAG, "⛔ 未激活，跳过自动化会话初始化")
                        return
                    }
                    ensureScheduler()
                    // Application.attach may already have run before the Xposed hook
                    // was installed. Rebind helpers at the first reliable lifecycle point.
                    SecurityBodyHelper.init(classLoader!!)

                    if (Detector.isLegitimateEnvironment(appContext!!)) {
                        Detector.dangerous(appContext!!)
                        return
                    }

                    // DexKitBridge invokes native code during construction. Ensure its
                    // native library is loaded before creating the first bridge.
                    loadLibs()
                    DexKitBridge.create(apkPath).use { _ ->
                        record(TAG, "Hook DexKit successfully")
                    }
                    dayCalendar = Calendar.getInstance()
                    if (initHandler()) {
                        init = true
                    }
                }
            })

            SesameHooks.findAndHookMethod(AlipayClasses.SERVICE, classLoader, "onDestroy", object : MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val s = param.thisObject as Service
                    if (General.CURRENT_USING_SERVICE == s.javaClass.getCanonicalName()) {
                        updateStatusText("目标应用前台服务被销毁")
                        destroyHandler(s)
                        restartByBroadcast()
                    }
                }
            })
        } catch (t: Throwable) {
            printStackTrace(TAG, "Hook Service failed", t)
        }
    }

    private fun initVersionInfo(packageName: String?) {
        alipayVersion = NativeDexKitLoader.initVersionInfo(appContext!!, packageName)
    }

    private fun loadLibs() {
        NativeDexKitLoader.loadLibs(appContext!!)
    }

    // 滑块验证hook注册
    private fun initSimplePageManager() {
        NativeDexKitLoader.initSimplePageManager(classLoader!!, alipayVersion)
    }

    companion object : SessionContract {
        const val TAG: String = "ApplicationHook" // 简化TAG
        var finalProcessName: String? = ""

        @JvmField
        var classLoader: ClassLoader? = null

        @JvmField
        @get:JvmStatic
        @Volatile
        var appContext: Context? = null

        // 任务锁已迁入 SessionLifecycle（TaskLock），此处保留字段以兼容潜在残留引用。
        private val taskLock = Any()

        private val isTaskRunning: Boolean
            get() = SessionLifecycle.isTaskRunning

        @Volatile
        private var loadStatusToastShown = false

        /** Reports only after the module has reached a real target-process lifecycle callback. */
        private fun showModuleLoadStatus() {
            if (loadStatusToastShown) return
            loadStatusToastShown = true
            val context = appContext ?: return
            val active = EntitlementGate.isAutomationAllowed()
            val message = if (active) {
                "已加载已激活"
            } else {
                "已加载未激活"
            }
            show(message, force = true)
            record(TAG, "模块加载状态: $message")
        }

        @JvmStatic
        var alipayVersion: AlipayVersion = AlipayVersion("")

        @get:JvmStatic
        @Volatile
        var isHooked: Boolean = false

        @Volatile
        var moduleInterface: io.github.libxposed.api.XposedInterface? = null
            private set

        /**
         * 检查目标应用版本是否需要启用SimplePageManager功能
         * @return true表示版本低于等于10.6.58.99999，需要启用；false表示不需要
         */
        @JvmStatic
        fun shouldEnableSimplePageManager(): Boolean =
            NativeDexKitLoader.shouldEnableSimplePageManager(alipayVersion)

        private var init: Boolean
            get() = SessionLifecycle.initialized
            set(value) { SessionLifecycle.initialized = value }

        @JvmStatic
        override fun isSessionActive(expectedGeneration: Long?): Boolean =
            SessionLifecycle.isSessionActive(expectedGeneration)

        @JvmStatic
        override fun currentSessionGeneration(): Long = SessionLifecycle.currentSessionGeneration()

        /**
         * 会话代号。主任务回调会捕获创建它时的代号，避免旧会话的晚到回调
         * 在新会话已经初始化后继续执行。
         */
        private var sessionGeneration: Long
            get() = SessionLifecycle.generation
            set(value) { SessionLifecycle.publishGeneration(value) }

        @Volatile
        var dayCalendar: Calendar?

        /**
         * BroadcastDispatcher 的 handler 实现：把 receiver 的各分支动作委托回
         * ApplicationHook 的 initHandler / reOpenApp 等。receiver 本身不再持有
         * 这些状态，只通过接口回调。
         */
        private val broadcastActionHandler = object : BroadcastDispatcher.BroadcastActionHandler {
            override fun onRestart(targetUserId: String?) {
                if (!EntitlementGate.isAutomationAllowed()) {
                    record(TAG, "⛔ 未激活，忽略重启广播")
                    return
                }
                val currentUserId = HookUtil.getUserId(classLoader!!)
                if (targetUserId != null && targetUserId != currentUserId) {
                    record(TAG, "忽略非当前用户的重启广播: target=$targetUserId, current=$currentUserId")
                    return
                }
                initHandler()
            }

            override fun onReLogin() {
                reOpenApp()
            }

            override fun onRpcTest(method: String?, data: String?, type: String?) {
                if (!EntitlementGate.isAutomationAllowed()) {
                    record(TAG, "⛔ 未激活，忽略 RPC 测试")
                    return
                }
                execute({
                    record(TAG, "RPC测试: method=$method data=$data type=$type")
                    try {
                        val rpc = DebugRpc()
                        rpc.start(method, data, type)
                    } catch (_: Throwable) { /* ignore */
                    }
                })
            }

            override fun onManualTask(intent: android.content.Intent) {
                if (!EntitlementGate.isAutomationAllowed()) {
                    record(TAG, "⛔ 未激活，忽略手动任务")
                    return
                }
                val taskName = intent.getStringExtra("task")
                if (taskName != null) {
                    val normalizedTaskName = taskName.replace("+", "_")
                    try {
                        val task = CustomTask.valueOf(normalizedTaskName)
                        val extraParams = HashMap<String, Any>()
                        when (task) {
                            CustomTask.FOREST_WHACK_MOLE -> {
                                extraParams["whackMoleMode"] = intent.getIntExtra("whackMoleMode", 1)
                                extraParams["whackMoleGames"] = intent.getIntExtra("whackMoleGames", 5)
                            }

                            CustomTask.FOREST_ENERGY_RAIN -> {
                                extraParams["exchangeEnergyRainCard"] = intent.getBooleanExtra("exchangeEnergyRainCard", false)
                            }

                            CustomTask.FARM_SPECIAL_FOOD -> {
                                extraParams["specialFoodCount"] = intent.getIntExtra("specialFoodCount", 0)
                            }

                            CustomTask.FARM_USE_TOOL -> {
                                extraParams["toolType"] = intent.getStringExtra("toolType") ?: ""
                                extraParams["toolCount"] = intent.getIntExtra("toolCount", 1)
                            }

                            else -> {
                                record(TAG, "❌ 无效的任务指令: $taskName")
                            }
                        }
                        ManualTask.runSingle(task, extraParams)
                    } catch (e: Exception) {
                        record(TAG, "❌ 无效的任务指令: $taskName -> ${e.message}")
                    }
                } else {
                    for (model in Model.getModelArray()) {
                        if (model is ManualTaskModel) {
                            model.startTask(true, 1)
                            break
                        }
                    }
                }
            }
        }

        private val broadcastEnvironment = object : BroadcastDispatcher.BroadcastEnvironment {
            override val finalProcessName: String?
                get() = Companion.finalProcessName
        }

        var offline: Boolean
            get() = SessionLifecycle.offline
            set(value) { SessionLifecycle.offline = value }


        @Volatile
        private var batteryPermissionChecked = false

        @SuppressLint("StaticFieldLeak")
        @Volatile
        var service: Service? = null

        var mainHandler: Handler? = null

        var mainTask: MainTask?
            get() = SessionLifecycle.mainTask
            set(value) { SessionLifecycle.mainTask = value }
        private var taskRunnerAdapter: TaskRunnerAdapter?
            get() = SessionLifecycle.taskRunnerAdapter
            set(value) { SessionLifecycle.taskRunnerAdapter = value }

        var rpcBridge: RpcBridge?
            get() = SessionLifecycle.rpcBridge
            set(value) { SessionLifecycle.rpcBridge = value }

        @JvmStatic
        override fun isRpcBridgeAvailable(expectedGeneration: Long?): Boolean =
            SessionLifecycle.isRpcBridgeAvailable(expectedGeneration)

        /**
         * 在 bridge 的读锁范围内执行请求，确保会话销毁不会在反射调用中途卸载 bridge。
         */
        @JvmStatic
        override fun <T> withRpcBridge(
            expectedGeneration: Long?,
            block: (RpcBridge) -> T,
        ): T? = SessionLifecycle.withRpcBridge(expectedGeneration, block)

        private var rpcVersion: RpcVersion?
            get() = SessionLifecycle.rpcVersion
            set(value) { SessionLifecycle.rpcVersion = value }

        var lastExecTime: Long
            get() = SessionLifecycle.lastExecTime
            set(value) { SessionLifecycle.lastExecTime = value }

        var nextExecutionTime: Long
            get() = SessionLifecycle.nextExecutionTime
            set(value) { SessionLifecycle.nextExecutionTime = value }
        private const val MAX_INACTIVE_TIME: Long = 3600000 // 1小时

        init {
            SessionLifecycle.installConfigurationResetReader(RemotePrefsStore::getConfigurationResetGeneration)
            SessionLifecycle.installConfigurationResetAvailableReader(RemotePrefsStore::available)
            dayCalendar = Calendar.getInstance()
            CalendarDayRollover.resetToMidnight(dayCalendar!!)
        }

        @Synchronized
        private fun runMainTaskLogic(expectedGeneration: Long) {
            try {
                TaskLock().use { _ ->
                    if (!init || service == null || expectedGeneration != sessionGeneration || !Config.isLoaded()) {
                        return
                    }
                    if (!EntitlementGate.isAutomationAllowed()) {
                        record(TAG, "⛔ 授权失效，销毁自动化会话")
                        destroyHandler()
                        return
                    }
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastExecTime < 2000) {
                        record(TAG, "⚠️ 间隔过短，跳过")
                        schedule(checkInterval.value.toLong(), "间隔重试") {
                            if (isSessionActive(expectedGeneration)) {
                                execHandler(expectedGeneration)
                            }
                        }
                        return
                    }

                    val currentUid = currentUid
                    val targetUid = HookUtil.getUserId(classLoader!!)
                    if (targetUid == null || targetUid != currentUid) {
                        reOpenApp()
                        return
                    }

                    lastExecTime = currentTime
                    taskRunnerAdapter?.stop()
                    taskRunnerAdapter = TaskRunnerAdapter().also { it.run() }
                }
            } catch (e: IllegalStateException) {
                record(TAG, "⚠️ " + e.message)
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, e)
            }
        }

        // --- 辅助方法 ---
        private fun ensureScheduler() {
            if (appContext != null) {
                SmartSchedulerManager.initialize(appContext!!)
            }
        }

        fun deoptimizeClass(c: Class<*>) {
            val module = moduleInterface ?: return
            for (m in c.declaredMethods) {
                if (m.name == "makeApplicationInner") {
                    runCatching { module.deoptimize(m) }
                }
            }
        }


        fun scheduleNextExecutionInternal(lastTime: Long) {
            try {
                if (!EntitlementGate.isAutomationAllowed()) {
                    if (init) destroyHandler()
                    return
                }
                val expectedGeneration = sessionGeneration
                checkInactiveTime()
                val checkInterval = checkInterval.value
                val execAtTimeList = execAtTimeList.value
                if (execAtTimeList.contains("-1")) {
                    record(TAG, "定时执行未开启")
                    return
                }
                var delayMillis = checkInterval.toLong()
                var targetTime: Long = 0
                val lastCal = TimeUtil.getCalendarByTimeMillis(lastTime)
                val nextCal = TimeUtil.getCalendarByTimeMillis(lastTime + checkInterval)
                for (timeStr in execAtTimeList) {
                    val execCal = TimeUtil.getTodayCalendarByTimeStr(timeStr)
                    if (execCal != null && lastCal < execCal && nextCal > execCal) {
                        record(TAG, "设置定时执行:$timeStr")
                        targetTime = execCal.getTimeInMillis()
                        delayMillis = targetTime - lastTime
                        break
                    }
                }
                val forestPauseTime = RuntimeInfo.getInstance()
                    .getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime)
                if (forestPauseTime > lastTime &&
                    (targetTime == 0L || forestPauseTime < targetTime)) {
                    targetTime = forestPauseTime
                    delayMillis = forestPauseTime - lastTime
                    record(TAG, "森林异常等待将在到期后立即重试：${TimeUtil.getCommonDate(forestPauseTime)}")
                }
                nextExecutionTime = if (targetTime > 0) targetTime else (lastTime + delayMillis)
                ensureScheduler()
                schedule(delayMillis, "轮询任务") {
                    if (isSessionActive(expectedGeneration)) {
                        execHandler(expectedGeneration)
                    }
                }
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, "scheduleNextExecution failed", e)
            }
        }

        // --- 初始化核心逻辑 ---
        @Synchronized
        private fun initHandler(): Boolean {
            try {
                if (!EntitlementGate.isAutomationAllowed()) {
                    record(TAG, "⛔ 未激活，拒绝初始化自动化会话")
                    if (init) destroyHandler()
                    showModuleLoadStatus()
                    return false
                }
                if (init) destroyHandler()
                RequestManager.reset()
                val nextSessionGeneration = SessionLifecycle.generation + 1

                ensureScheduler()
                Model.initAllModel()

                if (service == null) return initializationFailed("目标服务不可用")
                val userId = HookUtil.getUserId(classLoader!!)
                if (userId == null) {
                    show("用户未登录")
                    return initializationFailed("用户未登录")
                }

                HookUtil.hookUser(classLoader!!)

                val configurationResetGeneration = RemotePrefsStore.getConfigurationResetGeneration()
                Config.load(userId, allowFileFallback = false)
                if (!Config.isLoaded()) return initializationFailed("配置未加载")

                Notify.start(service!!)
                setWakenAtTimeAlarm()

                val newBridge = if (newRpc.value) NewRpcBridge() else OldRpcBridge()
                // 桥读写锁已迁入 SessionLifecycle：先标 ready=false，再持写锁换桥。
                SessionLifecycle.withBridgeWriteLock {
                    rpcBridge?.unload()
                    rpcBridge = null
                    newBridge.load()
                    rpcVersion = newBridge.getVersion()
                    StructuredLog.record(TAG, "RPC 桥接初始化完成: process=$processName version=$rpcVersion implementation=${newBridge.javaClass.simpleName}")
                    // 仅在 load 完整成功后发布，调用方不会拿到半初始化 bridge。
                    rpcBridge = newBridge
                }

                if (newRpc.value && debugMode.value) {
                    HookUtil.hookRpcBridgeExtension(classLoader!!, sendHookData.value, sendHookDataUrl.value.orEmpty())
                    HookUtil.hookDefaultBridgeCallback(classLoader!!)
                    XRiverBridgeObserver.install(classLoader!!)
                }

                start(userId)
                checkBatteryPermission()

                Model.bootAllModel(classLoader)
                load(userId)
                updateDay()

                mainTask = newInstance("主任务") {
                    runMainTaskLogic(nextSessionGeneration)
                }

                showModuleLoadStatus()

                // 会话态就绪：设代际、init=true、ready=true、offline=false（保持原顺序）。
                SessionLifecycle.completeInitialization(
                    nextGeneration = nextSessionGeneration,
                    resetGeneration = configurationResetGeneration,
                )

                // Award claiming is an independent lightweight flow. It must
                // start even when the normal runner is blocked by once-daily.
                PendingAwardPoller.start()

                // 仅在会话完整初始化后开放调试入口，失败的初始化不会留下旧服务。
                if (BuildConfig.DEBUG) {
                    try {
                        // token 每安装生成（见 DebugServerCredentials），不再硬编码进 APK；
                        // 服务器仅绑 127.0.0.1（ModuleHttpServer），不可被局域网访问。
                        startIfNeeded(8080, DebugServerCredentials.getOrCreate(), processName, General.PACKAGE_NAME)
                        BroadcastDispatcher.bind(broadcastActionHandler, broadcastEnvironment)
                        registerBroadcastReceiver(appContext!!)
                    } catch (_: Throwable) { /* ignore */
                    }
                }

                execHandler()
                return true
            } catch (th: Throwable) {
                printStackTrace(TAG, "startHandler", th)
                initializationFailed("初始化异常: ${th.javaClass.simpleName}")
                return false
            }
        }

        private fun initializationFailed(reason: String): Boolean {
            record(TAG, "初始化失败，清理当前会话: $reason")
            destroyHandler()
            return false
        }

        private fun checkBatteryPermission() {
            if (!batteryPerm.value || batteryPermissionChecked) return

            val hasPermission = checkBatteryPermissions(appContext)
            batteryPermissionChecked = true
            if (!hasPermission) {
                record(TAG, "无后台运行权限，2秒后申请")
                mainHandler!!.postDelayed({
                    if (!init || service == null || appContext == null) return@postDelayed
                    if (!PermissionUtil.checkOrRequestBatteryPermissions(appContext!!)) {
                        show("请授予目标应用始终在后台运行权限")
                    }
                }, 2000)
            }
        }

        @Synchronized
        fun destroyHandler(serviceBeingDestroyed: Service? = null) {
            val shouldClearService = serviceBeingDestroyed != null && service === serviceBeingDestroyed
            try {
                // 先让所有可能晚到的调度回调失效，再取消旧任务。
                SessionLifecycle.beginDestruction()
                PendingAwardPoller.stop()
                RequestManager.reset()
                stopDebugHttpServer()
                stopTaskRunner()
                shutdownAndRestart()
                resetEnergyWaitingSession()
                resetPageSession()
                stopHandler()

                // 桥读写锁已迁入 SessionLifecycle：持写锁卸载会话资源。
                SessionLifecycle.withBridgeWriteLock {
                    // 等待已经进入 bridge 的请求结束后，再卸载会话资源。
                    destroyData()
                    Status.unload()
                    stop()
                    clearIntervalLimit()
                    Config.unload()
                    UserMap.unload()
                    Model.destroyAllModel()

                    cleanup()

                    // 注销广播接收器
                    unregisterBroadcastReceiver(appContext)

                    SessionLifecycle.clearBridgeAndTask()
                }
                stopAllTask()

                batteryPermissionChecked = false
            } catch (th: Throwable) {
                printStackTrace(TAG, "stopHandler err:", th)
            } finally {
                if (shouldClearService && service === serviceBeingDestroyed) {
                    // 账号切换会复用当前 Service；只有真实 onDestroy 才清空它。
                    service = null
                }
            }
        }

        @Synchronized
        fun execHandler(expectedGeneration: Long? = null) {
            if (!EntitlementGate.isAutomationAllowed()) {
                if (init) {
                    record(TAG, "⛔ 授权失效，销毁自动化会话")
                    destroyHandler()
                }
                return
            }
            if (!isSessionActive(expectedGeneration)) return
            mainTask?.startTask(false)
        }

        private fun stopHandler() {
            stopTaskRunner()
            if (mainTask != null) mainTask!!.stopTask()
            stopAllTask()
        }

        private fun stopTaskRunner() {
            taskRunnerAdapter?.stop()
            taskRunnerAdapter = null
        }

        // --- 杂项方法 ---
        private fun checkInactiveTime() {
            if (lastExecTime == 0L) return
            val inactiveTime: Long = System.currentTimeMillis() - lastExecTime
            if (inactiveTime > MAX_INACTIVE_TIME) {
                record(TAG, "⚠️ 检测到长时间未执行(${TimeFormatter.formatDuration(inactiveTime)})，重新登录")
                reOpenApp()
            }
        }

        fun updateDay() {
            val now = Calendar.getInstance()
            val (newCal, changed) = CalendarDayRollover.checkAndRollover(now, dayCalendar)
            if (changed) {
                dayCalendar = newCal
                record(TAG, "日期更新")
                setWakenAtTimeAlarm()
            }
            try {
                save(now)
            } catch (_: Exception) {
            }
        }

        fun sendBroadcast(action: String?) {
            BroadcastDispatcher.sendBroadcast(appContext, action)
        }

        fun sendBroadcastShell(api: String?, message: String?) {
            BroadcastDispatcher.sendBroadcastShell(appContext, api, message)
        }

        @JvmStatic
        fun reLoginByBroadcast() {
            BroadcastDispatcher.reLoginByBroadcast(appContext)
        }

        fun restartByBroadcast() {
            BroadcastDispatcher.restartByBroadcast(appContext)
        }

        // 离线恢复防饿死：离线期间每个被拦截的 RPC 都会触发 reOpenApp，
        // 而 SmartScheduler 同名任务互斥会不断重置 20s 计时器，导致恢复任务
        // 永远无法执行。同代会话内已有待执行的恢复任务时直接跳过重调度。
        @Volatile
        private var pendingReLoginGeneration: Long = -1

        fun reOpenApp() {
            ensureScheduler()
            val expectedGeneration = sessionGeneration
            synchronized(this) {
                if (pendingReLoginGeneration == expectedGeneration) return
                pendingReLoginGeneration = expectedGeneration
            }
            schedule(20000L, "重新登录") {
                try {
                    pendingReLoginGeneration = -1
                    if (!isSessionActive(expectedGeneration)) return@schedule
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setClassName(General.PACKAGE_NAME, General.CURRENT_USING_ACTIVITY)
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    offline = true
                    if (appContext != null) appContext!!.startActivity(intent)
                } catch (e: Exception) {
                    error(TAG, "重启Activity失败: " + e.message)
                }
            }
        }

        // --- 定时唤醒 ---
        private fun setWakenAtTimeAlarm(expectedGeneration: Long = sessionGeneration) {
            if (appContext == null) return
            ensureScheduler()

            val wakenAtTimeList = wakenAtTimeList.value
            if (wakenAtTimeList.contains("-1")) return

            // 1. 每日0点
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            CalendarDayRollover.resetToMidnight(calendar)
            val delayToMidnight = calendar.getTimeInMillis() - System.currentTimeMillis()

            if (delayToMidnight > 0) {
                schedule(delayToMidnight, "每日0点任务") {
                    if (!isSessionActive(expectedGeneration)) return@schedule
                    record(TAG, "⏰ 0点任务触发")
                    updateDay()
                    execHandler(expectedGeneration)
                    setWakenAtTimeAlarm(expectedGeneration) // 递归设置明天
                }
            }

            // 2. 自定义时间
            val now = Calendar.getInstance()
            for (timeStr in wakenAtTimeList) {
                try {
                    val target = TimeUtil.getTodayCalendarByTimeStr(timeStr)
                    if (target != null && target > now) {
                        val delay = target.getTimeInMillis() - System.currentTimeMillis()
                        schedule(delay, "自定义: $timeStr") {
                            if (!isSessionActive(expectedGeneration)) return@schedule
                            record(TAG, "⏰ 自定义触发: $timeStr")
                            execHandler(expectedGeneration)
                        }
                    }
                } catch (_: Exception) { /* ignore */
                }
            }
        }

        fun registerBroadcastReceiver(context: Context) {
            BroadcastDispatcher.register(context)
        }

        fun unregisterBroadcastReceiver(context: Context?) {
            BroadcastDispatcher.unregister(context)
        }
    }
}
