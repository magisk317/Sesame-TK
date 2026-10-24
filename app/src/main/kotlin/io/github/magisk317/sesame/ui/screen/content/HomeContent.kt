package io.github.magisk317.sesame.ui.screen.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magisk317.sesame.ui.MainActivity
import io.github.magisk317.sesame.ui.screen.DeviceInfoCard
import io.github.magisk317.sesame.ui.screen.card.ModuleStatusCard
import io.github.magisk317.sesame.ui.screen.card.OneWordCard
import io.github.magisk317.sesame.ui.screen.card.ServicesStatusCard
import io.github.magisk317.sesame.ui.viewmodel.MainViewModel
import io.github.magisk317.sesame.util.CommandUtil.ServiceStatus
import io.github.magisk317.sesame.util.ToastUtil
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

@Composable
fun HomeContent(
    moduleStatus: MainViewModel.ModuleStatus,
    entitlementAllowed: Boolean,
    serviceStatus: ServiceStatus,
    deviceInfoMap: Map<String, String>?,
    oneWord: String,
    isOneWordLoading: Boolean,
    onOneWordClick: () -> Unit,
    onEvent: (MainActivity.MainUiEvent) -> Unit
) {
    val context = LocalContext.current
    var isServiceCardExpanded by remember { mutableStateOf(false) }

    var isStatusCardExpanded by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppText(
                    "本应用闭源免费，欢迎倒卖！！\n如果你没在闲鱼看到，欢迎过去宣传！",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    role = AppTextRole.Subtitle
                )
            }
        }
        // 1. 模块状态
        item {
            ModuleStatusCard(
                status = moduleStatus,
                entitlementAllowed = entitlementAllowed,
                expanded = isStatusCardExpanded,
                onClick = {
                    if (moduleStatus is MainViewModel.ModuleStatus.NotActivated) {
                        isStatusCardExpanded = !isStatusCardExpanded//此处不可省略
                    }
                }
            )
        }

        // 2. 服务权限
        item {
            ServicesStatusCard(
                status = serviceStatus,
                expanded = isServiceCardExpanded,
                onClick = {
                    if (serviceStatus is ServiceStatus.Inactive) {
                        isServiceCardExpanded = !isServiceCardExpanded //此处不可省略
                    }
                }
            )
        }

        // 3. 设备信息
        item {
            if (deviceInfoMap != null) {
                DeviceInfoCard(deviceInfoMap, moduleStatus)
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // 4. 一言
        item {
            OneWordCard( // 提取出的一言卡片组件
                oneWord = oneWord,
                isLoading = isOneWordLoading,
                onClick = onOneWordClick,
                onLongClick = {
                    onEvent(MainActivity.MainUiEvent.OpenDebugLog)
                    ToastUtil.showToast(context, "准备起飞🛫")
                }
            )
        }

    }
}
