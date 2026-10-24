package io.github.magisk317.sesame.ui.screen.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.AlignVerticalTop
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.magisk317.sesame.ui.MainActivity
import io.github.magisk317.sesame.ui.screen.components.MenuButton

/**
 * 日志菜单 — 按 ModelGroup 业务分类，2列×5行竖排布局。
 *
 *   基础   运动
 *   森林   会员
 *   庄园   其他
 *   新村   框架
 *   农场   错误
 */
@Composable
fun LogsContent(
    onEvent: (MainActivity.MainUiEvent) -> Unit
) {
    data class LogButton(val label: String, val icon: ImageVector, val event: MainActivity.MainUiEvent)

    val buttons = listOf(
        LogButton("基础", Icons.Rounded.Home, MainActivity.MainUiEvent.OpenBaseLog),
        LogButton("森林", Icons.Rounded.Forest, MainActivity.MainUiEvent.OpenForestLog),
        LogButton("庄园", Icons.Rounded.Agriculture, MainActivity.MainUiEvent.OpenFarmLog),
        LogButton("新村", Icons.Rounded.Storefront, MainActivity.MainUiEvent.OpenStallLog),
        LogButton("农场", Icons.Rounded.Landscape, MainActivity.MainUiEvent.OpenOrchardLog),
        LogButton("运动", Icons.AutoMirrored.Rounded.DirectionsWalk, MainActivity.MainUiEvent.OpenSportsLog),
        LogButton("会员", Icons.Rounded.People, MainActivity.MainUiEvent.OpenMemberLog),
        LogButton("其他", Icons.Rounded.AlignVerticalTop, MainActivity.MainUiEvent.OpenOtherLog),
        LogButton("框架", Icons.Rounded.Settings, MainActivity.MainUiEvent.OpenAllLog),
        LogButton("错误", Icons.Rounded.BugReport, MainActivity.MainUiEvent.OpenErrorLog),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (row in buttons.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (btn in row) {
                    MenuButton(
                        text = btn.label,
                        icon = btn.icon,
                        modifier = Modifier.weight(1f),
                    ) { onEvent(btn.event) }
                }
            }
        }
    }
}
