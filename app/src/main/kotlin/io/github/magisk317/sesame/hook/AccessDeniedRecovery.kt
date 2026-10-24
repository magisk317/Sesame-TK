package io.github.magisk317.sesame.hook

import android.app.Activity
import io.github.magisk317.sesame.hook.simple.SimpleViewImage
import io.github.magisk317.sesame.util.CommandUtil
import io.github.magisk317.sesame.util.GlobalThreadPools
import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicLong

/** Recovers from Alipay's proxy/VPN denial page without screenshot or UI clicks. */
internal object AccessDeniedRecovery {
    private const val TAG = "AccessDeniedRecovery"
    private const val BACK_COMMAND = "input keyevent KEYCODE_BACK"
    private const val RECOVERY_COOLDOWN_MS = 5_000L
    private val lastRecoveryAt = AtomicLong(0L)

    suspend fun handle(activity: Activity, root: SimpleViewImage): Boolean {
        root.xpath2One("//android.widget.TextView[contains(@text,'访问被拒绝')]")
            ?: root.xpath2One("//android.widget.TextView[contains(@text,'请检查是否使用了代理软件或VPN')]")
            ?: return false
        val now = System.currentTimeMillis()
        val previous = lastRecoveryAt.get()
        if (now - previous < RECOVERY_COOLDOWN_MS) return true
        if (!lastRecoveryAt.compareAndSet(previous, now)) return true

        val feedbackCode = root.xpath2One("//android.widget.TextView[contains(@text,'反馈码:')]")?.getText()
        StructuredLog.w(TAG, "检测到支付宝访问被拒绝，${feedbackCode ?: "未找到反馈码"}，发送 KEYCODE_BACK")
        GlobalThreadPools.execute(context = Dispatchers.IO) {
            val result = CommandUtil.executeCommand(activity, BACK_COMMAND)
            if (result == null) {
                StructuredLog.error(TAG, "发送 KEYCODE_BACK 失败：CommandService 不可用")
                lastRecoveryAt.set(0L)
            } else {
                StructuredLog.i(TAG, "已发送 KEYCODE_BACK，返回访问被拒绝页面的上一页")
            }
        }
        return true
    }
}
