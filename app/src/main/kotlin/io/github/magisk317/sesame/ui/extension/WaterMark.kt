package io.github.magisk317.sesame.ui.extension

import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import io.github.magisk317.sesame.BuildConfig
import io.github.magisk317.sesame.util.FansirsqiUtil
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.TimeUtil
import kotlinx.coroutines.delay

private fun readConfiguredUserIds(): List<String?> =
    runCatching { FansirsqiUtil.getFolderList(Files.CONFIG_DIR.absolutePath) }
        .getOrDefault(emptyList())

@Composable
fun WatermarkLayer(
    modifier: Modifier = Modifier,
    uidList: List<String?> = readConfiguredUserIds(),
    autoRefresh: Boolean = true,
    refreshIntervalMs: Long = 1000L,
    refreshTrigger: Any? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { 13.sp.toPx() }
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f).toArgb()

    var currentTime by remember { mutableStateOf(TimeUtil.getFormatDateTime()) }

    if (autoRefresh) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(refreshIntervalMs)
                currentTime = TimeUtil.getFormatDateTime()
            }
        }
    }

    val watermarkLines = remember(uidList, currentTime, refreshTrigger) {
        val uidLines = if (uidList.isEmpty()) {
            listOf("未载入账号", "请启用模块后重启一次目标应用", "确保模块生成对应账号配置")
        } else {
            uidList.mapIndexed { index, uid -> "UID${index + 1}: $uid" }
        }
        listOf("免费模块仅供学习,勿在国内平台传播!!") +
            uidLines +
            listOf(
                "Now: $currentTime",
                "Ver: ${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE}",
                "Build: ${BuildConfig.BUILD_DATE}",
            )
    }

    Box(
        modifier = modifier.drawWithCache {
            // ... (Paint 和 draw 逻辑保持不变，完全不需要改动) ...
            val paint = Paint().apply {
                color = textColor
                textSize = textSizePx
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val fontHeight = paint.fontSpacing
            val blockHeight = fontHeight * watermarkLines.size

            onDrawWithContent {
                drawContent()
                drawContext.canvas.nativeCanvas.apply {
                    withSave {
                        val width = size.width
                        val height = size.height
                        // Rotate each block around its own center. Rotating the whole
                        // canvas moves the upper/lower blocks away from the center line.
                        val blockCenters = floatArrayOf(height * 0.18f, height * 0.5f, height * 0.82f)
                        blockCenters.forEach { blockCenter ->
                            withSave {
                                rotate(-30f, width / 2f, blockCenter)
                                val firstBaseline = blockCenter - blockHeight / 2f - paint.ascent()
                                watermarkLines.forEachIndexed { lineIndex, line ->
                                    drawText(line, width / 2f, firstBaseline + lineIndex * fontHeight, paint)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        content()
    }
}
