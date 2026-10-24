package io.github.magisk317.sesame

import android.app.Application
import android.content.Intent
import io.github.magisk317.sesame.service.CommandService
import io.github.magisk317.sesame.service.LsposedServiceManager
import io.github.magisk317.sesame.ui.theme.ThemeManager
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ToastUtil
import io.github.magisk317.sesame.telemetry.SesameTelemetry

/**
 * 芝麻粒应用主类
 *
 * 负责应用初始化
 */
class SesameApplication : Application() {

    companion object {
        private const val TAG = "SesameApplication"
        public const val PREFERENCES_KEY = "sesame-tk"
        var hasPermissions: Boolean = false

    }

    override fun onCreate() {
        super.onCreate()
        ToastUtil.init(this) // 初始化全局 Context
        StructuredLog.init(this)
        SesameTelemetry.initialize(this)
        // Bind the LSPosed service so remote preferences (user configs) stay available.
        LsposedServiceManager.init()
        ThemeManager.init(this)
        startCommandService()
    }

    /**
     * 启动 CommandService
     */
    private fun startCommandService() {
        try {
            val intent = Intent(this, CommandService::class.java)
            startService(intent)
            StructuredLog.record(TAG, "✓ CommandService 已启动")
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "❌ CommandService 启动失败:", e)
        }
    }

}
