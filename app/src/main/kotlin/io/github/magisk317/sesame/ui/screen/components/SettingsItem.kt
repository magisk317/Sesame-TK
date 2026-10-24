package io.github.magisk317.sesame.ui.screen.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.magisk317.uikit.preference.AppArrowItem

/**
 * Thin Sesame wrapper over magisk-ui-kit [Item].
 * Keeps the historical icon/danger API while reusing shared preference chrome.
 */
@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    AppArrowItem(
        title = title,
        summary = subtitle.orEmpty(),
        modifier = Modifier,
        onClick = onClick,
        trailingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDanger) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
}
