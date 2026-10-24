package io.github.magisk317.sesame.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magisk317.sesame.BuildConfig
import io.github.magisk317.sesame.telemetry.SesameTelemetry
import io.github.magisk317.sesame.ui.viewmodel.MainViewModel
import io.github.magisk317.sesame.util.CommandUtil
import io.github.magisk317.sesame.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

@Composable
fun DeviceInfoCard(info: Map<String, String>, moduleStatus: MainViewModel.ModuleStatus) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            info.forEach { (label, value) ->
                if (label == "服务端设备 ID") {
                    var showFull by remember { mutableStateOf(false) }
                    val context = LocalContext.current
                    val displayValue = if (showFull) value else "**********************"
                    InfoRow(
                        label = label,
                        value = displayValue,
                        onClick = { showFull = !showFull },
                        onLongClick = {
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(label, value)
                            clipboardManager.setPrimaryClip(clip)
                            ToastUtil.showToast("服务端设备 ID 已复制")
                        },
                    )
                } else {
                    InfoRow(label = label, value = value)
                }
            }

            InfoRow(
                label = "Version",
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT_HASH})",
            )

            val moduleStatusDetail = when (moduleStatus) {
                is MainViewModel.ModuleStatus.Loading -> "Checking..."
                is MainViewModel.ModuleStatus.NotActivated ->
                    "Not Activated — open settings to activate"
                is MainViewModel.ModuleStatus.Activated -> buildString {
                    append("Activated by ${moduleStatus.frameworkName}")
                    if (moduleStatus.frameworkVersion.isNotBlank()) {
                        append(" ${moduleStatus.frameworkVersion}")
                    }
                    if (moduleStatus.apiVersion > 0) {
                        append(" API ${moduleStatus.apiVersion}")
                    }
                }
            }
            val moduleStatusColor = when (moduleStatus) {
                is MainViewModel.ModuleStatus.Loading -> MaterialTheme.colorScheme.onSurfaceVariant
                is MainViewModel.ModuleStatus.NotActivated -> MaterialTheme.colorScheme.error
                is MainViewModel.ModuleStatus.Activated -> MaterialTheme.colorScheme.primary
            }
            InfoRow(
                label = "Module Status",
                value = moduleStatusDetail,
                valueColor = moduleStatusColor,
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val interactionModifier = if (onClick != null && onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else if (onClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = {})
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(interactionModifier)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        AppText(
            text = label,
            modifier = Modifier.width(120.dp),
            role = AppTextRole.Footnote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppText(
            text = value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            role = AppTextRole.Body,
            color = valueColor,
        )
    }
}

object DeviceInfoUtil {

    private suspend fun getProp(context: Context, prop: String): String {
        return CommandUtil.executeCommand(context, "getprop $prop")?.trim() ?: ""
    }

    private suspend fun getDeviceName(context: Context): String {
        val candidates = listOf(
            "ro.product.marketname",//miui
            "ro.vendor.oplus.market.enname",//oplus
            "ro.vendor.oplus.market.name",//realme
            "ro.vivo.market.name",//vivo
            "ro.config.marketing_name",//huawei/honor
            "ro.product.model",//兜底
        )
        for (prop in candidates) {
            val value = getProp(context, prop)
            if (value.isNotBlank()) return value
        }
        return "${Build.BRAND} ${Build.MODEL}"
    }

    suspend fun showInfo(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        val deviceName = getDeviceName(context)
        val serverDeviceId = SesameTelemetry.readEntitlementDeviceId() ?: "未激活"

        mapOf(
            "Product" to "${Build.MANUFACTURER} ${Build.PRODUCT}",
            "Device" to deviceName,
            "Android Version" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "System Version" to "${Build.DISPLAY}",
            "服务端设备 ID" to serverDeviceId,
            "Build Date" to "${BuildConfig.BUILD_DATE} ${BuildConfig.BUILD_TIME}"
        )
    }
}
