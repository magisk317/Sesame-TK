package io.github.magisk317.sesame.ui.screen.content

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.magisk317.sesame.BuildConfig
import io.github.magisk317.sesame.entity.UserEntity
import io.github.magisk317.sesame.ui.MainActivity
import io.github.magisk317.sesame.ui.ManualTaskActivity
import io.github.magisk317.sesame.ui.RpcDebugActivity
import io.github.magisk317.sesame.ui.compose.CommonAlertDialog
import io.github.magisk317.sesame.ui.screen.components.SettingsItem
import io.github.magisk317.sesame.ui.screen.components.UserItemCard
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun SettingsContent(
    userList: List<UserEntity>,
    onNavigateToSettings: (UserEntity) -> Unit,
    onEvent: (MainActivity.MainUiEvent) -> Unit
) {
    // 状态定义在最外层
    var showClearConfigDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 使用 Box 或 Column 包裹，或者直接平铺
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. 列表内容
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),//在列表内容的四周（上、下、左、右）各添加 16dp 的内边距
            verticalArrangement = Arrangement.spacedBy(8.dp)//在每个列表项之间添加 固定间距
        ) {
            item {
                SettingsSectionHeader(text = "账号配置")
            }

            if (userList.isEmpty()) {
                item {
                    AppText(
                        text = "暂无已载入的用户配置。",
                        role = AppTextRole.Body,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(userList) { user ->
                    UserItemCard(user = user, onClick = { onNavigateToSettings(user) })
                }
            }

            // 扩展功能
            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(text = "扩展功能")
            }

            item {
                SettingsItem(
                    title = "扩展功能",
                    icon = Icons.Rounded.Extension,
                    onClick = { onEvent(MainActivity.MainUiEvent.OpenExtend) }
                )
            }

            if (BuildConfig.DEBUG) {
                item {
                    SettingsItem(
                        title = "RPC 调试工具",
                        icon = Icons.Rounded.BugReport,
                        onClick = {
                            context.startActivity(Intent(context, RpcDebugActivity::class.java))
                        }
                    )
                }

                item {
                    SettingsItem(
                        title = "手动调度任务",
                        icon = Icons.Rounded.SatelliteAlt,
                        onClick = {
                            context.startActivity(Intent(context, ManualTaskActivity::class.java))
                        }
                    )
                }
            }

            // 危险操作
            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader(text = "数据")
            }

            item {
                SettingsItem(
                    title = "清除所有配置",
                    subtitle = "重置所有模块数据",
                    icon = Icons.Rounded.DeleteForever,
                    isDanger = true,
                    onClick = { showClearConfigDialog = true }
                )
            }
        }

        // 2. 弹窗放在 LazyColumn 外面 (同级)
        if (showClearConfigDialog) {
            CommonAlertDialog(
                showDialog = true,
                onDismissRequest = { showClearConfigDialog = false },
                onConfirm = {
                    onEvent(MainActivity.MainUiEvent.ClearConfig)
                },
                title = "⚠️ 警告",
                text = "🤔❗ 确认清除所有模块配置？\n此操作无法撤销❗❗❗",
                icon = Icons.Outlined.Warning,
                iconTint = MaterialTheme.colorScheme.error,
                confirmText = "确认清除",
                confirmButtonColor = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    AppText(
        text = text,
        role = AppTextRole.Subtitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
