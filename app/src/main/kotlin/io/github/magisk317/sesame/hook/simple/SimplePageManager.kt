package io.github.magisk317.sesame.hook.simple

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.github.magisk317.sesame.util.StructuredLog as Log
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.sesame.hook.SesameHooks
import io.github.magisk317.sesame.util.GlobalThreadPools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import io.github.magisk317.sesame.ui.MainActivity
import io.github.magisk317.sesame.BuildConfig

/**
 * A simplified PageManager - only keeps Activity monitoring and Dialog tracking.
 */
@SuppressLint("StaticFieldLeak")
object SimplePageManager {

    private const val TAG = "SimplePageManager"

    private var mContextRef: WeakReference<Context>? = null
    private var mClassLoader: ClassLoader? = null
    private var topActivityRef: WeakReference<Activity>? = null

    private val activityFocusHandlerMap = ConcurrentHashMap<String, ActivityFocusHandler>()

    val handler = Handler(Looper.getMainLooper())

    private var taskDuration = 500
    private var hasPendingActivityTask = false
    private var disable = false

    private val dialogs = ArrayList<WeakReference<android.app.Dialog>>()
    private var windowMonitorEnabled = false
    private val myTabHookInstalled = AtomicBoolean(false)
    private val lastMyTabOpenAt = AtomicLong(0L)
    private val pendingMyTabLongPresses = WeakHashMap<View, PendingMyTabLongPress>()

    private class PendingMyTabLongPress(
        val action: Runnable,
    ) {
        var triggered = false
    }

    interface ActivityFocusHandler {
        suspend fun handleActivity(activity: Activity, root: SimpleViewImage): Boolean
    }

    init {
        enablePageMonitor()
    }

    fun getContext(): Context? = mContextRef?.get()

    fun getClassLoader(): ClassLoader? = mClassLoader

    fun getTopActivity(): Activity? = topActivityRef?.get()

    /**
     * 清理会话级页面状态。Hook 仍然存在，但旧 Activity 和待处理标记不能跨会话复用。
     */
    fun resetForSession() {
        mContextRef = null
        mClassLoader = null
        topActivityRef = null
        hasPendingActivityTask = false
        dialogs.clear()
        lastMyTabOpenAt.set(0L)
        synchronized(pendingMyTabLongPresses) {
            pendingMyTabLongPresses.values.forEach { handler.removeCallbacks(it.action) }
            pendingMyTabLongPresses.clear()
        }
    }

    /**
     * Opens the module entry point when Alipay's bottom "我的" tab is held.
     * The hook is intentionally placed at performLongClick so the existing
     * click path and Alipay's own touch handling remain unchanged.
     */
    fun installMyTabLongPressHook() {
        if (!myTabHookInstalled.compareAndSet(false, true)) return
        try {
            SesameHooks.findAndHookMethod(
                View::class.java,
                "performLongClick",
                object : io.github.magisk317.xposed.MethodHook() {
                    override fun beforeHookedMethod(
                        param: io.github.magisk317.xposed.MethodHookParam,
                    ) {
                        val view = param.thisObject as? View ?: return
                        if (!isBottomMyTab(view)) return

                        val activity = findActivity(view.context) ?: return
                        val now = System.currentTimeMillis()
                        if (now - lastMyTabOpenAt.get() < LONG_PRESS_DEBOUNCE_MS) {
                            param.result = true
                            return
                        }
                        lastMyTabOpenAt.set(now)
                        try {
                            launchModuleActivity(activity)
                            Log.i(TAG, "长按底部[我的]，打开芝麻粒")
                            param.result = true
                        } catch (t: Throwable) {
                            lastMyTabOpenAt.set(0L)
                            Log.e(TAG, "长按[我的]打开模块失败", t)
                        }
                    }
                },
            )

            SesameHooks.findAndHookMethod(
                View::class.java,
                "dispatchTouchEvent",
                android.view.MotionEvent::class.java,
                object : io.github.magisk317.xposed.MethodHook() {
                    override fun beforeHookedMethod(
                        param: io.github.magisk317.xposed.MethodHookParam,
                    ) {
                        val view = param.thisObject as? View ?: return
                        val event = param.args.firstOrNull() as? MotionEvent ?: return
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                if (!isBottomMyTabContainer(view)) return
                                val action = Runnable {
                                    val state = synchronized(pendingMyTabLongPresses) {
                                        pendingMyTabLongPresses[view]
                                    } ?: return@Runnable
                                    if (state.triggered) return@Runnable
                                    state.triggered = true
                                    openModuleFromLongPress(view)
                                }
                                val state = PendingMyTabLongPress(action)
                                synchronized(pendingMyTabLongPresses) {
                                    pendingMyTabLongPresses.remove(view)?.let { handler.removeCallbacks(it.action) }
                                    pendingMyTabLongPresses[view] = state
                                }
                                handler.postDelayed(action, LONG_PRESS_TIMEOUT_MS)
                            }

                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                val state = synchronized(pendingMyTabLongPresses) {
                                    pendingMyTabLongPresses.remove(view)
                                } ?: return
                                handler.removeCallbacks(state.action)
                                if (state.triggered) param.result = true
                            }
                        }
                    }
                },
            )
            Log.i(TAG, "已安装底部[我的]长按 Hook")
        } catch (t: Throwable) {
            myTabHookInstalled.set(false)
            Log.e(TAG, "安装底部[我的]长按 Hook 失败", t)
        }
    }

    private fun isBottomMyTab(view: View): Boolean {
        if (!view.isShown || !view.isEnabled || view.width <= 0 || view.height <= 0) return false
        val label = (view as? TextView)?.text?.toString()
            ?: view.contentDescription?.toString()
            ?: return false
        if (label.trim() != MY_TAB_LABEL) return false

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val displayHeight = view.resources.displayMetrics.heightPixels
        return location[1] >= (displayHeight * BOTTOM_TAB_MIN_FRACTION).toInt()
    }

    private fun isBottomMyTabContainer(view: View): Boolean {
        if (!view.isShown || !view.isEnabled || view.width <= 0 || view.height <= 0) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val displayHeight = view.resources.displayMetrics.heightPixels
        if (location[1] < (displayHeight * BOTTOM_TAB_MIN_FRACTION).toInt()) return false
        return view is ViewGroup && containsMyLabel(view)
    }

    private fun containsMyLabel(view: ViewGroup): Boolean {
        for (index in 0 until view.childCount) {
            val child = view.getChildAt(index)
            if (isBottomMyTab(child)) return true
            if (child is ViewGroup && containsMyLabel(child)) return true
        }
        return false
    }

    private fun openModuleFromLongPress(view: View) {
        val activity = findActivity(view.context) ?: return
        val now = System.currentTimeMillis()
        if (now - lastMyTabOpenAt.get() < LONG_PRESS_DEBOUNCE_MS) return
        lastMyTabOpenAt.set(now)
        try {
            launchModuleActivity(activity)
            Log.i(TAG, "长按底部[我的]，打开芝麻粒")
        } catch (t: Throwable) {
            lastMyTabOpenAt.set(0L)
            Log.e(TAG, "长按[我的]打开模块失败", t)
        }
    }

    private fun launchModuleActivity(sourceActivity: Activity) {
        val intent = Intent().apply {
            component = ComponentName(
                BuildConfig.APPLICATION_ID,
                MainActivity::class.java.name,
            )
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra("launchSource", "alipay_my_long_press")
        }
        val launchContext = sourceActivity.applicationContext
        Log.i(TAG, "启动模块入口: ${intent.component}")
        launchContext.startActivity(intent)
        Log.i(TAG, "模块入口 startActivity 已返回")
    }

    private fun findActivity(context: Context?): Activity? {
        var current = context
        while (current is android.content.ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    private const val MY_TAB_LABEL = "我的"
    private const val BOTTOM_TAB_MIN_FRACTION = 0.65f
    private const val LONG_PRESS_DEBOUNCE_MS = 1_500L
    private const val LONG_PRESS_TIMEOUT_MS = 600L

    fun setTaskDuration(duration: Int) {
        taskDuration = duration
    }

    fun setDisable(disabled: Boolean) {
        disable = disabled
    }

    fun addHandler(activityClassName: String, handler: ActivityFocusHandler) {
        activityFocusHandlerMap[activityClassName] = handler
    }

    fun removeHandler(activityClassName: String) {
        activityFocusHandlerMap.remove(activityClassName)
    }

    fun getDialogs(): ArrayList<WeakReference<android.app.Dialog>> = dialogs

    fun enableWindowMonitoring(classLoader: ClassLoader? = null) {
        if (classLoader != null) {
            mClassLoader = classLoader
        }
        Log.i(
            TAG,
            "启用窗口监控被调用，窗口监控已启用: $windowMonitorEnabled, 类加载器: ${mClassLoader?.javaClass?.name}"
        )
        if (!windowMonitorEnabled) {
            enableWindowMonitor()
            windowMonitorEnabled = true
        }
    }

    /**
     * 尝试在对话框中查找视图
     */
    @SuppressLint("UseKtx")
    fun tryGetTopView(xpath: String): SimpleViewImage? {
        // Log.d(TAG, "tryGetTopView 搜索 xpath: $xpath, 对话框数量: ${dialogs.size}")
        dialogs.removeIf { it.get() == null }
        for (dialogWeakReference in dialogs) {
            val dialog = dialogWeakReference.get() ?: continue
            if (!dialog.isShowing) {
                continue
            }
            val decorView = dialog.window?.decorView ?: continue
            Log.d(TAG, "  - 对话框: ${dialog.javaClass.name}, 正在显示: ${dialog.isShowing}")
            debugPrintAllTextViews(decorView, 0)
            val viewImage = SimpleViewImage(decorView)
            val results = SimpleXpathParser.evaluate(viewImage, xpath)
            if (results.isNotEmpty()) {
                return results[0]
            }
        }
        return null
    }

    /**
     * 打印所有 TextView 的文本内容（用于调试）
     */
    private fun debugPrintAllTextViews(view: View, depth: Int) {
        val indent = "  ".repeat(depth)
        if (view is android.widget.TextView) {
            val text = view.text?.toString() ?: ""
            val contentDesc = view.contentDescription?.toString() ?: ""
            if (text.isNotEmpty() || contentDesc.isNotEmpty()) {
                Log.d(
                    TAG,
                    "${indent}文本视图[${view.javaClass.simpleName}] 文本='$text' 内容描述='$contentDesc'"
                )
            }
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                debugPrintAllTextViews(view.getChildAt(i), depth + 1)
            }
        }
    }

    /**
     * 启用 Activity 监控
     */
    private fun enablePageMonitor() {
        try {
            SesameHooks.findAndHookMethod(
                Application::class.java,
                "dispatchActivityResumed",
                Activity::class.java,
                object : MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val activity = param.args[0] as Activity
                        topActivityRef = WeakReference(activity)
                        if (mContextRef?.get() == null) {
                            mContextRef = WeakReference(activity.applicationContext)
                        }
                        mClassLoader = activity.classLoader
                        triggerActivity()
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "挂钩 Activity->dispatchActivityResumed 错误: ", e)
        }
    }

    /**
     * 如果对话框不存在则添加到监控列表
     */
    private fun addDialogIfNotExists(dialog: android.app.Dialog, source: String) {
        if (!dialogs.any { it.get() === dialog }) {
            dialogs.add(WeakReference(dialog))
            Log.d(TAG, "对话框已从 $source 添加，总数: ${dialogs.size}")
            triggerDialogProcessing()
        } else {
            Log.d(TAG, "对话框从 $source 已存在于列表中")
        }
    }

    /**
     * 挂钩对话框构造函数
     */
    private fun hookDialogConstructor(vararg parameterTypes: Any) {
        val parameterTypesString = parameterTypes.joinToString(",") {
            if (it is Class<*>) it.simpleName else it.toString()
        }
        try {
            SesameHooks.findAndHookConstructor(
                "android.app.Dialog",
                getClassLoader(),
                *parameterTypes,
                object : MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dialog = param.thisObject as android.app.Dialog
                        addDialogIfNotExists(dialog, "构造函数($parameterTypesString)")
                    }
                }
            )
           // Log.i(TAG, "挂钩对话框构造函数($parameterTypesString) 成功")
        } catch (e: Throwable) {
            Log.e(TAG, "挂钩对话框构造函数($parameterTypesString) 错误: ", e)
        }
    }

    /**
     * 启用对话框监控
     */
    private fun enableWindowMonitor() {
        Log.i(TAG, "启用窗口监控被调用，类加载器: ${mClassLoader?.javaClass?.name}")
        hookDialogConstructor("android.content.Context")
        hookDialogConstructor("android.content.Context", Int::class.java)
        hookDialogConstructor(
            "android.content.Context",
            "boolean",
            "android.content.DialogInterface.OnCancelListener"
        )

        try {
            val captchaDialogClass = HookHelpers.findClass(
                "com.alipay.rdssecuritysdk.v3.captcha.view.CaptchaDialog",
                getClassLoader()
            )
            SesameHooks.findAndHookMethod(
                captchaDialogClass,
                "show",
                object : MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dialog = param.thisObject as android.app.Dialog
                        addDialogIfNotExists(dialog, "CaptchaDialog.show()")
                    }
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "挂钩 CaptchaDialog.show() 错误: ", e)
        }
    }

    /**
     * 触发 Activity 处理
     */
    private fun triggerActivity() {
        triggerPendingActivityHandler("Activity 已恢复")
    }

    /**
     * 触发 Dialog 处理
     */
    private fun triggerDialogProcessing() {
        triggerPendingActivityHandler("Dialog 已创建")
    }

    /**
     * 触发待处理的 Activity 处理器
     */
    private fun triggerPendingActivityHandler(source: String) {
        val activity = topActivityRef?.get() ?: run {
            Log.i(TAG, "无法从 $source 触发处理器，未找到顶层 Activity")
            return
        }
        val handler = activityFocusHandlerMap[activity.javaClass.name]
        if (handler == null) {
            // Activity monitoring observes every Alipay page, while handlers
            // are registered only for pages with pending work (captcha, etc.).
            // An unmatched page is normal noise and is not a failed task.
            if (hasPendingActivityTask) {
                Log.d(TAG, "待处理页面未找到 ${activity.javaClass.name} 的处理器，来源: $source")
            }
            return
        }
        if (hasPendingActivityTask) {
            Log.d(TAG, "跳过从 $source 触发，已有待处理任务")
            return
        }
        hasPendingActivityTask = true
        Log.i(TAG, "从 $source 触发 ${activity.javaClass.name} 的处理器")
        triggerActivityActive(activity, handler, 0)
    }

    /**
     * 延迟触发 Activity 处理
     */
    private fun triggerActivityActive(
        activity: Activity,
        activityFocusHandler: ActivityFocusHandler,
        triggerCount: Int
    ) {
        if (disable) {
            Log.i(TAG, "页面触发管理器已禁用")
            hasPendingActivityTask = false
            return
        }
        GlobalThreadPools.execute(context = Dispatchers.Main) {
            try {
                delay(taskDuration.toLong())
                hasPendingActivityTask = false
                if (activityFocusHandler.handleActivity(activity, SimpleViewImage(activity.window.decorView))) {
                    return@execute
                }
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                Log.e(TAG, "处理 Activity 出错: ${activity.javaClass.name}", throwable)
            } finally {
                // GlobalThreadPools 在会话切换时会取消此任务，不能把待处理状态留给新会话。
                hasPendingActivityTask = false
            }
            // The handler can finish a blocking step after the session was
            // cancelled. Do not schedule the retry into the new session.
            currentCoroutineContext().ensureActive()
            if (triggerCount > 10) {
              //  Log.w(TAG, "Activity 事件触发失败次数过多: ${activityFocusHandler.javaClass}")
                return@execute
            }
            triggerActivityActive(activity, activityFocusHandler, triggerCount + 1)
        }
    }
}
