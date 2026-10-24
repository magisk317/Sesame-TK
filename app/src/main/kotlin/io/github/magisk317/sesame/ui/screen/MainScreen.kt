package io.github.magisk317.sesame.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.sesame.SesameApplication.Companion.PREFERENCES_KEY
import io.github.magisk317.sesame.entity.UserEntity
import io.github.magisk317.sesame.ui.MainActivity
import io.github.magisk317.sesame.ui.navigation.BottomNavItem
import io.github.magisk317.sesame.ui.screen.content.HomeContent
import io.github.magisk317.sesame.ui.screen.content.LogsContent
import io.github.magisk317.sesame.ui.screen.content.SettingsContent
import io.github.magisk317.sesame.ui.viewmodel.MainViewModel
import io.github.magisk317.uikit.entitlement.rememberEntitlementState
import io.github.magisk317.sesame.util.CommandUtil.serviceStatus
import io.github.magisk317.uikit.surface.MainTabScaffold
import io.github.magisk317.uikit.surface.MainTabSpec
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import io.github.magisk317.uikit.surface.rememberIsCompactWidth
import io.github.magisk317.uikit.surface.rememberMainChromeController
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    oneWord: String,
    activeUserName: String,
    moduleStatus: MainViewModel.ModuleStatus,
    viewModel: MainViewModel,
    userList: List<UserEntity>,
    onNavigateToSettings: (UserEntity) -> Unit,
    onEvent: (MainActivity.MainUiEvent) -> Unit,
) {
    val context = LocalContext.current
    val entitlementAllowed = rememberEntitlementState()

    LaunchedEffect(Unit) {
        viewModel.refreshDeviceInfo(context)
    }

    // Keep historical tab order: Logs / Home / Settings, default to Home.
    val tabItems = remember {
        listOf(BottomNavItem.Logs, BottomNavItem.Home, BottomNavItem.Settings)
    }
    val tabs = remember(tabItems) {
        tabItems.map { item -> MainTabSpec(label = item.label, icon = item.icon) }
    }
    var selectedIndex by remember { mutableIntStateOf(tabItems.indexOf(BottomNavItem.Home)) }
    var scrollToTopSignal by remember { mutableIntStateOf(0) }

    val serviceStatus by serviceStatus.collectAsStateWithLifecycle()
    val isOneWordLoading by viewModel.isOneWordLoading.collectAsStateWithLifecycle()
    val prefs = context.getSharedPreferences(PREFERENCES_KEY, Context.MODE_PRIVATE)
    var isIconHidden by remember { mutableStateOf(prefs.getBoolean("is_icon_hidden", false)) }
    var showMenu by remember { mutableStateOf(false) }
    val deviceInfoMap by viewModel.deviceInfo.collectAsStateWithLifecycle()

    val currentScreen = tabItems[selectedIndex]
    val isCompact = rememberIsCompactWidth()
    val chromeController = rememberMainChromeController(
        isCompact = isCompact,
        compactChromeRouteAvailable = true,
        keepVisible = true,
        allowScrollHide = false,
        resetKey = selectedIndex,
    )

    MainTabScaffold(
        tabs = tabs,
        selectedIndex = selectedIndex,
        isCompact = isCompact,
        chromeController = chromeController,
        onTabSelected = { index -> selectedIndex = index },
        onTabReselected = { scrollToTopSignal++ },
        alwaysShowLabel = false,
        modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    AppText(
                        text = when (currentScreen) {
                            BottomNavItem.Home -> activeUserName
                            BottomNavItem.Logs -> "日志中心"
                            BottomNavItem.Settings -> "模块设置"
                        },
                        role = AppTextRole.Title,
                    )
                },
                colors = chromeTopAppBarColors(),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { AppText(if (isIconHidden) "显示应用图标" else "隐藏应用图标") },
                            onClick = {
                                isIconHidden = !isIconHidden
                                onEvent(MainActivity.MainUiEvent.ToggleIconHidden(isIconHidden))
                                showMenu = false
                            },
                        )
                    }
                },
            )

            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                when (currentScreen) {
                    BottomNavItem.Home -> HomeContent(
                        moduleStatus = moduleStatus,
                        entitlementAllowed = entitlementAllowed,
                        serviceStatus = serviceStatus,
                        deviceInfoMap = deviceInfoMap,
                        oneWord = oneWord,
                        isOneWordLoading = isOneWordLoading,
                        onOneWordClick = { onEvent(MainActivity.MainUiEvent.RefreshOneWord) },
                        onEvent = onEvent,
                    )

                    BottomNavItem.Logs -> LogsContent(
                        onEvent = onEvent,
                    )

                    BottomNavItem.Settings -> SettingsContent(
                        userList = userList,
                        onNavigateToSettings = onNavigateToSettings,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}
