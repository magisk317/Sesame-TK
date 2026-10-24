package io.github.magisk317.sesame.ui.compose

import io.github.magisk317.sesame.ui.sesameDialogWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import io.github.magisk317.uikit.surface.AppAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.magisk317.sesame.ui.extension.parseHtml
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.surface.AppTextButton

@Composable
fun CommonAlertDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    text: String, // C++ 传来的原始 HTML 字符串
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    confirmText: String = "确认",
    dismissText: String = "取消",
    confirmButtonColor: Color = MaterialTheme.colorScheme.primary,
    showCancelButton: Boolean = true
) {
    if (showDialog) {
        AppAlertDialog(
            modifier = Modifier.sesameDialogWidth(),
            onDismissRequest = onDismissRequest,
            title = {
                if (icon != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(icon, null, tint = iconTint)
                        AppText(text = title, role = AppTextRole.Title)
                    }
                } else {
                    AppText(text = title, role = AppTextRole.Title)
                }
            },
            text = {
                // 🔥 关键修改：在这里调用 .parseHtml()
                // 这会将 HTML 里的 <font color="red"> 变成 Compose 的红色样式
                AppText(
                    text = text.parseHtml(),
                    role = AppTextRole.Body
                )
            },
            confirmButton = {
                AppTextButton(
                    text = confirmText,
                    onClick = {
                        onConfirm()
                        onDismissRequest()
                    },
                    color = confirmButtonColor,
                )
            },
            // 🔥 关键修改：只有当 showCancelButton 为 true 时才显示
            dismissButton = if (showCancelButton) {
                {
                    AppTextButton(text = dismissText, onClick = onDismissRequest)
                }
            } else null, // 传 null 就不会显示取消按钮
        )
    }
}