package io.github.magisk317.sesame.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.magisk317.sesame.SesameApplication.Companion.PREFERENCES_KEY
import io.github.magisk317.sesame.SesameApplication.Companion.hasPermissions
import io.github.magisk317.sesame.ui.extension.NativeComposeBridge
import io.github.magisk317.sesame.ui.extension.WatermarkLayer
import io.github.magisk317.sesame.ui.extension.openUrl
import io.github.magisk317.sesame.ui.extension.performNavigationToSettings
import io.github.magisk317.sesame.ui.screen.MainScreen
import io.github.magisk317.sesame.ui.theme.AppTheme
import io.github.magisk317.sesame.ui.theme.ThemeManager
import io.github.magisk317.sesame.ui.viewmodel.MainViewModel
import io.github.magisk317.sesame.util.CommandUtil
import io.github.magisk317.sesame.util.Detector
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.IconManager
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.PermissionUtil
import io.github.magisk317.sesame.util.ToastUtil
import io.github.magisk317.sesame.service.StorageReset
import io.github.magisk317.uikit.shell.BaseActivity as UiKitBaseActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.File

class MainActivity : UiKitBaseActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Shizuku 监听器
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 1234) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                ToastUtil.showToast(this, "Shizuku 授权成功！")

                // 关键修改：
                lifecycleScope.launch {
                    CommandUtil.executeCommand(this@MainActivity, "echo init_shizuku")
                    delay(200)
                    viewModel.refreshDeviceInfo(this@MainActivity)
                }
            } else {
                ToastUtil.showToast(this, "Shizuku 授权被拒绝")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StructuredLog.record("MainActivity", "模块入口已创建, source=${intent?.getStringExtra("launchSource")}")

        initializeMainUi()
    }

    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    private fun initializeMainUi() {


        // 2. 检查权限并初始化逻辑
        hasPermissions = PermissionUtil.checkOrRequestFilePermissions(this)
        if (hasPermissions) {
            viewModel.initAppLogic()
            initNativeDetector()
        }

        // 3. 初始化 Shizuku
        setupShizuku()

        // 4. 同步图标状态
        val prefs = getSharedPreferences(PREFERENCES_KEY, MODE_PRIVATE)
        IconManager.syncIconState(this, prefs.getBoolean("is_icon_hidden", false))


        // 5. 设置 Compose 内容
        setContent {
            // 收集 ViewModel 状态
            val oneWord by viewModel.oneWord.collectAsStateWithLifecycle()
            val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
            val moduleStatus by viewModel.moduleStatus.collectAsStateWithLifecycle()
            val userList by viewModel.userList.collectAsStateWithLifecycle()
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()

            // AppTheme 会处理状态栏颜色
            AppTheme(dynamicColor = isDynamicColor) {
                WatermarkLayer(
                    uidList = userList.map { it.userId },
                ) {
                    MainScreen(
                        oneWord = oneWord,
                        activeUserName = activeUser?.showName ?: "未载入",
                        moduleStatus = moduleStatus,
                        viewModel = viewModel,
                        userList = userList,
                        onNavigateToSettings = { selectedUser ->
                            performNavigationToSettings(selectedUser)
                        },
                        onEvent = { event -> handleEvent(event) }
                    )
                }
            }
        }
    }

    // 在 Activity 中执行 Native 检测
    private fun initNativeDetector() {
        try {
            if (Detector.loadLibrary("checker")) {
                Detector.initDetector(this)
            }
        } catch (e: Exception) {
            StructuredLog.error("MainActivity", "Native detector init failed: ${e.message}")
        }
    }

    /**
     * 定义 UI 事件
     */
    sealed class MainUiEvent {
        data object RefreshOneWord : MainUiEvent()
        data object OpenForestLog : MainUiEvent()
        data object OpenFarmLog : MainUiEvent()
        data object OpenBaseLog : MainUiEvent()
        data object OpenSportsLog : MainUiEvent()
        data object OpenOrchardLog : MainUiEvent()
        data object OpenStallLog : MainUiEvent()
        data object OpenMemberLog : MainUiEvent()
        data object OpenGithub : MainUiEvent()
        data object OpenErrorLog : MainUiEvent()
        data object OpenOtherLog : MainUiEvent()
        data object OpenAllLog : MainUiEvent()
        data object OpenDebugLog : MainUiEvent()
        data class ToggleIconHidden(val isHidden: Boolean) : MainUiEvent()
        data object OpenCaptureLog : MainUiEvent()
        data object OpenExtend : MainUiEvent()
        data object ClearConfig : MainUiEvent()
    }

    /**
     * 统一处理事件
     */
    private fun handleEvent(event: MainUiEvent) {
        when (event) {
            MainUiEvent.RefreshOneWord -> viewModel.fetchOneWord()
            MainUiEvent.OpenForestLog -> openLogFile(Files.getForestLogFile())
            MainUiEvent.OpenFarmLog -> openLogFile(Files.getFarmLogFile())
            MainUiEvent.OpenBaseLog -> openLogFile(Files.getBaseLogFile())
            MainUiEvent.OpenSportsLog -> openLogFile(Files.getSportsLogFile())
            MainUiEvent.OpenOrchardLog -> openLogFile(Files.getOrchardLogFile())
            MainUiEvent.OpenStallLog -> openLogFile(Files.getStallLogFile())
            MainUiEvent.OpenMemberLog -> openLogFile(Files.getMemberLogFile())
            MainUiEvent.OpenOtherLog -> openLogFile(Files.getOtherLogFile())
            MainUiEvent.OpenGithub -> openUrl("https://github.com/Fansirsqi/Sesame-TK")
            MainUiEvent.OpenErrorLog -> executeWithVerification { openLogFile(Files.getErrorLogFile()) }
            MainUiEvent.OpenAllLog -> openLogFile(Files.getRecordLogFile())
            MainUiEvent.OpenDebugLog -> openLogFile(Files.getDebugLogFile())
            is MainUiEvent.ToggleIconHidden -> {
                val shouldHide = event.isHidden
                getSharedPreferences(PREFERENCES_KEY, MODE_PRIVATE).edit { putBoolean("is_icon_hidden", shouldHide) }
                viewModel.syncIconState(shouldHide)
                Toast.makeText(this, "设置已保存，可能需要重启桌面才能生效", Toast.LENGTH_SHORT).show()
            }

            MainUiEvent.OpenCaptureLog -> openLogFile(Files.getCaptureLogFile())
            MainUiEvent.OpenExtend -> startActivity(Intent(this, ExtendActivity::class.java))
            MainUiEvent.ClearConfig -> {
                val reset = StorageReset.clearAll(this)
                if (reset.completed) {
                    ToastUtil.showToast(this, "🙂 清空配置成功")
                    viewModel.refreshUserConfigs()
                } else if (reset.localCleared) {
                    ToastUtil.showToast(this, "已清空本地配置，等待模块连接后完成同步")
                    viewModel.refreshUserConfigs()
                } else {
                    ToastUtil.showToast(this, "😭 清空配置失败")
                }
            }
        }
    }

    // --- 辅助方法 (替代 BaseActivity) ---

    private fun setupShizuku() {
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        if (Shizuku.pingBinder() && checkSelfPermission(ShizukuProvider.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(1234)
        }
    }

    override fun onResume() {
        super.onResume()
        StructuredLog.record("MainActivity", "模块入口已恢复")
        if (hasPermissions) {
            viewModel.refreshUserConfigs()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
    }

    private fun openLogFile(logFile: File) {
        if (!logFile.exists()) {
            ToastUtil.showToast(this, "日志文件不存在: ${logFile.name}")
            return
        }
        val intent = Intent(this, LogViewerActivity::class.java).apply {
            data = logFile.toUri()
        }
        startActivity(intent)
    }

    private fun executeWithVerification(block: () -> Unit) {
        // 如果需要生物识别验证，可以在这里添加逻辑
        // 目前直接执行
        block()
    }


}
