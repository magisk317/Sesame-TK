package io.github.magisk317.sesame.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.sesame.ui.extension.WatermarkLayer
import io.github.magisk317.sesame.ui.screen.ExtendScreen
import io.github.magisk317.sesame.ui.theme.AppTheme
import io.github.magisk317.sesame.ui.theme.ThemeManager
import io.github.magisk317.uikit.shell.BaseActivity as UiKitBaseActivity

class ExtendActivity : UiKitBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
            AppTheme(dynamicColor = isDynamicColor) {
                WatermarkLayer {
                    ExtendScreen(onBackClick = { finish() })
                }
            }

        }
    }
}
