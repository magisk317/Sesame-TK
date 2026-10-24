package io.github.magisk317.sesame.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.sesame.ui.extension.WatermarkLayer
import io.github.magisk317.sesame.ui.screen.RpcDebugScreen
import io.github.magisk317.sesame.ui.theme.AppTheme
import io.github.magisk317.sesame.ui.theme.ThemeManager
import io.github.magisk317.uikit.shell.BaseActivity as UiKitBaseActivity

class RpcDebugActivity : UiKitBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
            AppTheme(dynamicColor = isDynamicColor) {
                WatermarkLayer {
                    RpcDebugScreen(onBack = { finish() })
                }
            }

        }
    }
}
