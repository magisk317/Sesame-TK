package io.github.magisk317.sesame.ui.screen.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.magisk317.sesame.util.CommandUtil.ServiceStatus
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesStatusCard(
    status: ServiceStatus, // 使用新定义的状态
    expanded: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp), // 稍微调整间距
        colors = CardDefaults.elevatedCardColors(
            containerColor = when (status) {
                is ServiceStatus.Active -> MaterialTheme.colorScheme.primary
                is ServiceStatus.Inactive -> MaterialTheme.colorScheme.errorContainer
                is ServiceStatus.Loading -> MaterialTheme.colorScheme.surfaceVariant
                else -> {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            }
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (status) {
                    is ServiceStatus.Active -> {
                        Icon(Icons.Outlined.CheckCircle, "已授权")
                        Column(Modifier.padding(start = 20.dp)) {
                            AppText(text = "滑块验证服务正常", role = AppTextRole.Subtitle)
                            AppText(text = "授权方式: ${status.type}", role = AppTextRole.Body)
                            Spacer(Modifier.height(4.dp))
                            AppText(text = "仅支持版本低于 10.6.58.xxxx的目标应用", role = AppTextRole.BodySmall)
                        }
                    }

                    is ServiceStatus.Inactive -> {
                        Icon(Icons.Outlined.Warning, "未授权")
                        Column(Modifier.padding(start = 20.dp)) {
                            AppText(text = "滑块验证服务不可用", role = AppTextRole.Subtitle)
                            AppText(text = "点击查看解决方案", role = AppTextRole.Body)
                        }
                    }

                    is ServiceStatus.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 20.dp)) {
                            AppText(text = "正在检查服务权限...", role = AppTextRole.Subtitle)
                        }
                    }

                    else -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Column(Modifier.padding(start = 20.dp)) {
                            AppText(text = "正在检查服务权限...", role = AppTextRole.Subtitle)
                        }
                    }
                }
            }

            // 展开内容：故障排查
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300))
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    AppText(text = "授权指南", role = AppTextRole.Subtitle, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "本模块需要后台执行 Shell 命令来处理滑块验证。\n\n" +
                                "可选方案：\n" +
                                "1. Shizuku (推荐)：免 Root，需安装 Shizuku APP 并激活。\n" +
                                "2. Root：如果你已 Root，请授予本应用 Root 权限。",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
