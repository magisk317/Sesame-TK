package io.github.magisk317.sesame.service

import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.sesame.util.StructuredLog as Log
import com.niki.cmd.Shell
import com.niki.cmd.ShizukuShell
import com.niki.cmd.model.bean.ShellResult
import io.github.magisk317.sesame.service.patch.SafeRootShell
import rikka.shizuku.Shizuku

class ShellManager(context: Context) {

    companion object {
        private const val TAG = "ShellManager"
    }

    var onStateChanged: ((String) -> Unit)? = null

    // 1. 移除 UserShell，只保留特权 Shell
    private val executors = listOf(
        SafeRootShell(),
        ShizukuShell(context)
    )

    // 使用 Volatile 确保多线程下的可见性
    @Volatile
    private var selectedShell: Shell? = null

    /**
     * 获取当前使用的 Shell 名称
     */
    val selectedName: String
        get() = selectedShell?.javaClass?.simpleName ?: "no_executor"


    private fun notifyChange() {
        val currentType = selectedName // 获取当前类型 (SafeRootShell/Shizuku/no_executor)
        Log.d(TAG, "Shell状态变更 -> $currentType")
        onStateChanged?.invoke(currentType)
    }

    /**
     * 2. 新增 reset 方法
     * 用于强制重置选择状态（例如 Shizuku 授权后）
     */
    fun reset() {
        selectedShell = null
        Log.d(TAG, "ShellManager 已重置，下次执行将重新选择 Executor")
        notifyChange() // 🔥 通知：重置了
    }

    private suspend fun selectExecutor() {
        // 如果已经选中且可用，直接返回
        if (selectedShell != null && selectedShell!!.isAvailable()) return

        Log.d(TAG, "正在寻找可用的 Root 或 Shizuku Shell...")

        for (shell in executors) {
            try {
                // 3. 针对 Shizuku 做特殊检查，防止未授权时报错或假死
                if (shell is ShizukuShell) {
                    if (!isShizukuReady()) {
                        Log.d(TAG, "跳过 ShizukuShell: 未授权或服务未运行")
                        continue
                    }
                }

                if (shell.isAvailable()) {
                    selectedShell = shell
                    notifyChange() // 🔥 通知：选中了新 Shell
                    Log.i(TAG, "✓ 成功选中 Shell: ${shell.javaClass.simpleName}")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shell ${shell.javaClass.simpleName} 检测失败: ${e.message}")
            }
        }
        // 如果都失败了，置空
        selectedShell = null
        notifyChange() // 🔥 通知：变成 None 了
    }

    /**
     * 检查 Shizuku 是否就绪
     */
    fun isShizukuReady(): Boolean {
        return try {
            val isBinderAlive = Shizuku.pingBinder()
            val hasPermission = if (isBinderAlive) Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED else false
            Log.d(TAG, "ShizukuCheck: isBinderAlive: $isBinderAlive, hasPermission: $hasPermission, PID: ${android.os.Process.myPid()}")
            return isBinderAlive && hasPermission
        } catch (e: Exception) {
            Log.e(TAG, "isShizukuReady", e)
            false
        }
    }

    /**
     * 执行命令
     */
    suspend fun exec(command: String): ShellResult {
        selectExecutor()
        val shell = selectedShell ?: return ShellResult( "", "No valid Root/Shizuku shell found.",-1)
        Log.d(TAG, "执行命令: $command (via $selectedName)")
        return shell.exec(command, 5_000L)
    }
}
