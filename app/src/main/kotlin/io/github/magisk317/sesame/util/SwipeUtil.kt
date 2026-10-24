package io.github.magisk317.sesame.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.magisk317.sesame.util.StructuredLog as Log
import androidx.core.net.toUri
import kotlinx.coroutines.runBlocking

/**
 * 滑动操作工具类
 */
object SwipeUtil {

    private const val TAG = "SwipeUtil"

    /**
     * 启动目标应用
     * 供 Java 调用，使用 runBlocking 保证同步返回
     */
    @JvmStatic
    fun startAlipay(context: Context): Boolean = runBlocking {
        // 1. 尝试 Shell 启动 (仅当是 Root 或 Shizuku 时)
        if (tryStartWithShell(context)) {
            return@runBlocking true
        }

        // 2. 降级：Intent 启动
        Log.d(TAG, "Shell启动失败或无权限，尝试Intent启动")
        if (startByIntent(context)) {
            return@runBlocking true
        }

        // 3. 降级：Scheme 启动
        Log.d(TAG, "Intent启动失败，尝试Scheme启动")
        return@runBlocking startBySchemeSync(context)
    }

    /**
     * 尝试使用 Shell 启动目标应用
     */
    private suspend fun tryStartWithShell(context: Context): Boolean {
        try {
            // 1. 获取当前状态
            val status = CommandUtil.serviceStatus.value

            // 2. 判断是否可用
            if (status !is CommandUtil.ServiceStatus.Active) {
                return false
            }

            // 优化命令：使用 --user current 兼容分身
            val cmd = "am start --user current -n com.eg.android.AlipayGphone/com.eg.android.AlipayGphone.AlipayLogin"
            val result = CommandUtil.executeCommand(context, cmd)
            return !result.isNullOrBlank()
        } catch (e: Exception) {
            Log.e(TAG, "Shell 启动异常: ${e.message}")
            return false
        }
    }

    @JvmStatic
    fun startByIntent(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.eg.android.AlipayGphone",
                    "com.eg.android.AlipayGphone.AlipayLogin"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Intent 启动成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Intent 启动失败: ${e.message}")
            false
        }
    }

    @JvmStatic
    fun startBySchemeSync(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, "alipays://platformapi/startapp?appId=".toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Scheme 启动成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Scheme 启动失败: ${e.message}")
            false
        }
    }
}
