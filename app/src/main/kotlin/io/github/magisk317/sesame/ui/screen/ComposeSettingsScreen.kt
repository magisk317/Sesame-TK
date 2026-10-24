package io.github.magisk317.sesame.ui.screen

import io.github.magisk317.sesame.ui.sesameDialogWidth
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import io.github.magisk317.uikit.surface.AppAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import io.github.magisk317.uikit.common.AppSnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.sesame.model.ModelConfig
import io.github.magisk317.sesame.ui.screen.components.ModelFieldRenderer
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.foundation.AppLinearLoadingIndicator
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.uikit.preference.SectionHeader
import kotlinx.coroutines.launch
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSettingsScreen(
    userId: String?,
    userName: String?,
    modelConfigs: List<ModelConfig>,
    loadError: String?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onChanged: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteConfig: () -> Unit,
    onShowOneWayFriends: () -> Unit,
    onCopyUserId: () -> Unit,
) {
    val snackbarHostState = remember { AppSnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val settingsGroups = remember(modelConfigs) { buildSettingsGroups(modelConfigs) }
    var selectedIndex by remember(settingsGroups) { mutableIntStateOf(0) }
    var fieldRevision by remember { mutableIntStateOf(0) }
    var expandedModelCodes by remember(modelConfigs) {
        mutableStateOf(
            settingsGroups
                .filter { it.configs.size == 1 }
                .mapNotNull { it.configs.single().code }
                .toSet(),
        )
    }
    var pendingSearchTarget by remember { mutableStateOf<SettingsSearchTarget?>(null) }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { settingsGroups.size },
    )
    val tabListState = rememberLazyListState()

    // 切换 tab 时立即保存前一个 tab 的修改：pager 页面离开视口即被销毁，
    // 在这里统一触发一次保存，避免依赖每个字段各自的 onChanged 时机。
    var lastActivePage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pagerState.currentPage) {
        val currentPage = pagerState.currentPage
        if (lastActivePage in settingsGroups.indices && lastActivePage != currentPage) {
            onChanged()
        }
        lastActivePage = currentPage
        selectedIndex = currentPage
        if (currentPage in settingsGroups.indices) {
            val item = tabListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == currentPage }
            if (item != null) {
                val viewportCenter = (
                    tabListState.layoutInfo.viewportStartOffset +
                        tabListState.layoutInfo.viewportEndOffset
                ) / 2
                val itemCenter = item.offset + item.size / 2
                val delta = itemCenter - viewportCenter
                if (delta != 0) {
                    tabListState.animateScrollBy(delta.toFloat())
                }
            } else {
                // Off-screen tabs need one smooth visibility animation. A second
                // centering animation would create the visible left-then-center jump.
                tabListState.animateScrollToItem(currentPage)
            }
        }
    }

    val searchResults = remember(settingsGroups, searchQuery) {
        buildSettingsSearchResults(settingsGroups, searchQuery)
    }

    val title = buildString {
        append("设置")
        if (!userName.isNullOrBlank()) {
            append(": ")
            append(userName)
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { AppText(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索设置")
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { AppText("保存") },
                                onClick = {
                                    menuExpanded = false
                                    onSave()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已尝试保存配置")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { AppText("导出配置") },
                                onClick = {
                                    menuExpanded = false
                                    onExport()
                                },
                            )
                            DropdownMenuItem(
                                text = { AppText("导入配置") },
                                onClick = {
                                    menuExpanded = false
                                    onImport()
                                },
                            )
                            DropdownMenuItem(
                                text = { AppText("删除配置") },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteConfig()
                                },
                            )
                            DropdownMenuItem(
                                text = { AppText("单向好友") },
                                onClick = {
                                    menuExpanded = false
                                    onShowOneWayFriends()
                                },
                            )
                            DropdownMenuItem(
                                text = { AppText("复制用户ID") },
                                onClick = {
                                    menuExpanded = false
                                    onCopyUserId()
                                },
                            )
                        }
                    },
                )
            },
            snackbarHost = {
                AppSnackbarHost(hostState = snackbarHostState)
            },
        ) { innerPadding ->
            when {
                loadError != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            text = "配置加载失败\n$loadError",
                            role = AppTextRole.Body,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                modelConfigs.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        AppLinearLoadingIndicator(modifier = Modifier.fillMaxWidth())
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppText("正在加载模型配置…")
                        }
                    }
                }

                else -> {
                    val safeIndex = selectedIndex.coerceIn(settingsGroups.indices)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        LazyRow(
                            state = tabListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            itemsIndexed(
                                items = settingsGroups,
                                key = { _, section -> section.group.code },
                            ) { index, section ->
                                FilterChip(
                                    modifier = Modifier.padding(end = 8.dp),
                                    selected = index == safeIndex,
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    label = {
                                        AppText(section.group.displayName)
                                    },
                                )
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                        ) { page ->
                            val selectedGroup = settingsGroups[page]
                            val groupListState = rememberLazyListState()

                            LaunchedEffect(
                                pendingSearchTarget,
                                pagerState.currentPage,
                                selectedGroup.group,
                            ) {
                                val target = pendingSearchTarget ?: return@LaunchedEffect
                                if (pagerState.currentPage != page || target.group != selectedGroup.group) {
                                    return@LaunchedEffect
                                }
                                val modelIndex = selectedGroup.configs.indexOfFirst {
                                    it.code == target.modelCode
                                }
                                if (modelIndex >= 0) {
                                    expandedModelCodes = expandedModelCodes + target.modelCode
                                    groupListState.animateScrollToItem(modelIndex + 1)
                                    pendingSearchTarget = null
                                }
                            }

                            LazyColumn(
                                state = groupListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                item {
                                    SectionHeader(text = "${selectedGroup.group.displayName}设置")
                                }

                                if (selectedGroup.configs.isEmpty()) {
                                    item(key = "empty-${selectedGroup.group.code}") {
                                        AppText(
                                            text = "暂无${selectedGroup.group.displayName}设置",
                                            role = AppTextRole.Body,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }

                                itemsIndexed(
                                    items = selectedGroup.configs,
                                    key = { index, config ->
                                        config.code?.takeIf(String::isNotBlank)
                                            ?: "${selectedGroup.group.code}-$index"
                                    },
                                ) { index, config ->
                                    val modelCode = config.code?.takeIf(String::isNotBlank)
                                        ?: "${selectedGroup.group.code}-$index"
                                    val expanded = modelCode in expandedModelCodes
                                    val fieldLayout = remember(config) {
                                        buildSettingsFieldLayout(config)
                                    }
                                    SectionCard(
                                        title = config.name.orEmpty(),
                                        accordionMode = true,
                                        sectionExpanded = expanded,
                                        onExpandedChange = {
                                            expandedModelCodes = if (expanded) {
                                                expandedModelCodes - modelCode
                                            } else {
                                                expandedModelCodes + modelCode
                                            }
                                        },
                                    ) {
                                        fieldLayout.enable?.let { enable ->
                                            ModelFieldRenderer(
                                                field = enable.field,
                                                displayName = enable.displayName,
                                                revision = fieldRevision,
                                                onChanged = {
                                                    fieldRevision++
                                                    onChanged()
                                                },
                                            )
                                        }
                                        fieldLayout.regularFields.forEach { item ->
                                            ModelFieldRenderer(
                                                field = item.field,
                                                displayName = item.displayName,
                                                revision = fieldRevision,
                                                onChanged = {
                                                    fieldRevision++
                                                    onChanged()
                                                },
                                            )
                                        }
                                        fieldLayout.sections.forEach { fieldSection ->
                                            SectionHeader(
                                                text = fieldSection.title,
                                                modifier = Modifier.padding(
                                                    start = 16.dp,
                                                    end = 16.dp,
                                                    top = 12.dp,
                                                    bottom = 4.dp,
                                                ),
                                            )
                                            fieldSection.fields.forEach { item ->
                                                ModelFieldRenderer(
                                                    field = item.field,
                                                    displayName = item.displayName,
                                                    revision = fieldRevision,
                                                    onChanged = {
                                                        fieldRevision++
                                                        onChanged()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                if (!userId.isNullOrBlank()) {
                                    item {
                                        AppText(
                                            text = "userId: $userId",
                                            role = AppTextRole.BodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (searchOpen) {
        AppAlertDialog(
            modifier = Modifier.sesameDialogWidth(),
            onDismissRequest = { searchOpen = false },
            title = { AppText("搜索设置") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { AppText("输入选项名称、代码或描述") },
                    )
                    if (searchQuery.isNotBlank()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (searchResults.isEmpty()) {
                                item { AppText("没有匹配的设置") }
                            } else {
                                itemsIndexed(
                                    items = searchResults,
                                    key = { _, result ->
                                        with(result.target) {
                                            "${group.code}/$modelCode/${fieldCode ?: "@model"}"
                                        }
                                    },
                                ) { _, result ->
                                    TextButton(
                                        onClick = {
                                            val groupIndex = settingsGroups.indexOfFirst {
                                                it.group == result.target.group
                                            }
                                            if (groupIndex >= 0) {
                                                searchOpen = false
                                                expandedModelCodes =
                                                    expandedModelCodes + result.target.modelCode
                                                pendingSearchTarget = result.target
                                                scope.launch {
                                                    pagerState.animateScrollToPage(groupIndex)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            AppText(result.fieldName ?: result.modelName)
                                            AppText(
                                                text = if (result.fieldName == null) {
                                                    result.groupName
                                                } else {
                                                    "${result.groupName} · ${result.modelName}"
                                                },
                                                role = AppTextRole.BodySmall,
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { searchOpen = false }) {
                    AppText("关闭")
                }
            },
        )
    }
}
