package io.github.magisk317.sesame.hook.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.magisk317.sesame.util.GlobalThreadPools.execute
import io.github.magisk317.sesame.util.StructuredLog.record
import io.github.magisk317.sesame.util.StructuredLog.printStackTrace

/**
 * 广播分发器。
 *
 * 从 [io.github.magisk317.sesame.hook.ApplicationHook] 抽取的广播接收器、注册/注销、
 * 发送等叶子逻辑。receiver 的重启/重登/RPC 测试/手动任务动作依赖 ApplicationHook 的
 * 运行时状态（classLoader、initHandler、reOpenApp、DebugRpc、CustomTask 等），因此通过
 * [BroadcastActionHandler] 接口注入，由 ApplicationHook 在初始化时绑定实现。
 */
object BroadcastDispatcher {

    private const val TAG = "ApplicationHook" // 与 ApplicationHook.TAG 保持一致，沿用既有日志前缀

    /**
     * 广播动作常量。原 `BroadcastActions` 私有对象内联至此处。
     */
    object Actions {
        const val RESTART: String = "com.eg.android.AlipayGphone.sesame.restart"
        const val RE_LOGIN: String = "com.eg.android.AlipayGphone.sesame.reLogin"
        const val STATUS: String = "com.eg.android.AlipayGphone.sesame.status"
        const val RPC_TEST: String = "com.eg.android.AlipayGphone.sesame.rpctest"
        const val MANUAL_TASK: String = "com.eg.android.AlipayGphone.sesame.manual_task"
    }

    /**
     * 由 ApplicationHook 实现并注入。receiver 的各分支动作委托至此接口，
     * 避免直接访问 ApplicationHook 的私有状态（classLoader、initHandler、reOpenApp 等）。
     */
    interface BroadcastActionHandler {
        /** RESTART 广播触发：校验 targetUserId 后执行重启（initHandler）。 */
        fun onRestart(targetUserId: String?)
        /** RE_LOGIN 广播触发：重开目标应用。 */
        fun onReLogin()
        /** RPC_TEST 广播触发：调用 DebugRpc。 */
        fun onRpcTest(method: String?, data: String?, type: String?)
        /** MANUAL_TASK 广播触发：执行手动任务，intent 携带 task 名与各任务专属参数。 */
        fun onManualTask(intent: Intent)
    }

    /**
     * 提供 finalProcessName，用于在 receiver 中过滤 widgetProvider 进程。
     */
    interface BroadcastEnvironment {
        val finalProcessName: String?
    }

    @Volatile
    private var handler: BroadcastActionHandler? = null

    @Volatile
    private var environment: BroadcastEnvironment? = null

    /**
     * 注入 handler 与 environment。由 ApplicationHook 在初始化时调用。
     */
    fun bind(handler: BroadcastActionHandler, environment: BroadcastEnvironment) {
        this.handler = handler
        this.environment = environment
    }

    /** 广播接收器实例，用于注销。 */
    private var mBroadcastReceiver: AlipayBroadcastReceiver? = null

    internal class AlipayBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action ?: return

            val env = environment
            if (env != null && env.finalProcessName != null && env.finalProcessName!!.endsWith(":widgetProvider")) {
                return  // 忽略小组件进程
            }

            when (action) {
                Actions.RESTART -> execute(Runnable {
                    val targetUserId = intent.getStringExtra("userId")
                    handler?.onRestart(targetUserId)
                })

                Actions.RE_LOGIN -> handler?.onReLogin()
                Actions.RPC_TEST -> {
                    val method = intent.getStringExtra("method")
                    val data = intent.getStringExtra("data")
                    val type = intent.getStringExtra("type")
                    handler?.onRpcTest(method, data, type)
                }
                Actions.MANUAL_TASK -> {
                    record(TAG, "🚀 收到手动庄园任务指令")
                    execute {
                        handler?.onManualTask(intent)
                    }
                }
            }
        }
    }

    /**
     * 发送指定 action 的广播。
     */
    fun sendBroadcast(context: Context?, action: String?) {
        if (context != null) context.sendBroadcast(Intent(action))
    }

    /**
     * 发送 shell 广播（供 BaseCaptchaHandler 等通过 adb shell 触发滑动）。
     */
    fun sendBroadcastShell(context: Context?, api: String?, message: String?) {
        if (context == null) return
        val intent = Intent("io.github.magisk317.sesame.SHELL")
        intent.putExtra(api, message)
        context.sendBroadcast(intent, null)
    }

    fun reLoginByBroadcast(context: Context?) {
        sendBroadcast(context, Actions.RE_LOGIN)
    }

    fun restartByBroadcast(context: Context?) {
        sendBroadcast(context, Actions.RESTART)
    }

    /**
     * 注册广播接收器。保留 S2 安全修复：TIRAMISU 及以上使用 RECEIVER_NOT_EXPORTED，
     * 13 以下使用 ContextCompat.RECEIVER_NOT_EXPORTED，避免第三方 app 驱动 rpctest/manual_task。
     */
    fun register(context: Context) {
        if (mBroadcastReceiver != null) return  // 防止重复注册

        try {
            mBroadcastReceiver = AlipayBroadcastReceiver()
            val filter = IntentFilter()
            filter.addAction(Actions.RESTART)
            filter.addAction(Actions.RE_LOGIN)
            filter.addAction(Actions.STATUS)
            filter.addAction(Actions.RPC_TEST)
            filter.addAction(Actions.MANUAL_TASK)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 安全修复（S2）：原实现误用 RECEIVER_EXPORTED，任何第三方 app 可广播
                // ...sesame.rpctest / ...manual_task 驱动支付宝 RPC / 触发任意任务。
                // 与 13 前分支保持一致，使用 RECEIVER_NOT_EXPORTED。
                context.registerReceiver(mBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ContextCompat.registerReceiver(
                    context,
                    mBroadcastReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }
            record(TAG, "BroadcastReceiver registered")
        } catch (th: Throwable) {
            mBroadcastReceiver = null
            printStackTrace(TAG, "Register Receiver failed", th)
        }
    }

    fun unregister(context: Context?) {
        if (mBroadcastReceiver == null || context == null) return
        try {
            context.unregisterReceiver(mBroadcastReceiver)
            record(TAG, "BroadcastReceiver unregistered")
        } catch (_: Throwable) {
            // ignore: receiver not registered
        } finally {
            mBroadcastReceiver = null
        }
    }
}
