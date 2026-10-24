package io.github.magisk317.sesame.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.sesame.data.Config
import io.github.magisk317.sesame.entity.UserEntity
import io.github.magisk317.sesame.model.Model
import io.github.magisk317.sesame.service.ActiveUserStore
import io.github.magisk317.sesame.task.customTasks.CustomTask
import io.github.magisk317.sesame.ui.extension.WatermarkLayer
import io.github.magisk317.sesame.ui.screen.ManualTaskScreen
import io.github.magisk317.sesame.ui.theme.AppTheme
import io.github.magisk317.sesame.ui.theme.ThemeManager
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.ToastUtil
import io.github.magisk317.uikit.shell.BaseActivity as UiKitBaseActivity

/**
 * 手动任务 Fragment (Compose 实现)
 * 采用列表展示所有可用的子任务，点击即可运行
 */
class ManualTaskActivity : UiKitBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 初始化配置
        ensureConfigLoaded()

        setContent {
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
            AppTheme(dynamicColor = isDynamicColor) {
                WatermarkLayer {
                    ManualTaskScreen(
                        onBackClick = { finish() },
                        onTaskClick = { task, params -> runTask(task, params) }
                    )
                }
            }
        }
    }

    private fun ensureConfigLoaded() {
        Model.initAllModel()
        ActiveUserStore.read(this)?.let { uid ->
            Config.load(uid)
        }
    }

    private fun runTask(task: CustomTask, params: Map<String, Any>) {
        try {
            val intent = Intent("com.eg.android.AlipayGphone.sesame.manual_task").apply {
                setPackage(io.github.magisk317.sesame.data.General.PACKAGE_NAME)
            }
            intent.putExtra("task", task.name)
            params.forEach { (key, value) ->
                when (value) {
                    is Int -> intent.putExtra(key, value)
                    is String -> intent.putExtra(key, value)
                    is Boolean -> intent.putExtra(key, value)
                }
            }
            sendBroadcast(intent)
            ToastUtil.showToast(this, "🚀 已发送指令: ${task.displayName}")
            openRecordLog()
        } catch (e: Exception) {
            ToastUtil.showToast(this, "❌ 发送失败: ${e.message}")
        }
    }

    private fun openRecordLog() {
        val logFile = Files.getRecordLogFile()
        if (!logFile.exists()) {
            ToastUtil.showToast(this, "日志文件尚未生成")
            return
        }
        val intent = Intent(this, LogViewerActivity::class.java).apply {
            data = logFile.toUri()
        }
        startActivity(intent)
    }
}
