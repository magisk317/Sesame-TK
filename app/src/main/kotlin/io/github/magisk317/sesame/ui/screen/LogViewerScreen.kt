package io.github.magisk317.sesame.ui.screen

import io.github.magisk317.sesame.ui.sesameDialogWidth
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.preference.AppCheckbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.magisk317.sesame.ui.compose.CommonAlertDialog
import io.github.magisk317.sesame.ui.viewmodel.LogField
import io.github.magisk317.sesame.ui.viewmodel.LogViewerViewModel
import io.github.magisk317.sesame.util.toJsonElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.surface.AppTextButton

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LogViewerScreen(
    filePath: String,
    onBackClick: () -> Unit,
    scrollToTopSignal: Int = 0,
    viewModel: LogViewerViewModel = viewModel()
) {

    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val visibleFields by viewModel.visibleFields.collectAsState()
    var textSizeSp by remember { mutableFloatStateOf(12f) }
    var formattedLines by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    io.github.magisk317.uikit.surface.ScrollToTopEffect(listState, scrollToTopSignal)
    val scope = rememberCoroutineScope()

    // 菜单显示状态
    var showMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    var showClearDialog by remember { mutableStateOf(false) }
    var showFieldsDialog by remember { mutableStateOf(false) }

    // 拦截返回键
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.search("")
    }

    // 自动滚动逻辑
    LaunchedEffect(filePath) {
        viewModel.loadLogs(filePath)
        viewModel.scrollEvent.collect { index ->
            if (index >= 0 && index < state.totalCount) {
                try {
                    listState.scrollToItem(index)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 智能自动滚动控制
    LaunchedEffect(listState.canScrollForward, listState.isScrollInProgress) {
        if (!state.isLoading && state.mappingList.isNotEmpty()) {
            if (!listState.canScrollForward) {
                viewModel.toggleAutoScroll(true)
            } else if (listState.isScrollInProgress) {
                viewModel.toggleAutoScroll(false)
            }
        }
    }

    // 自动聚焦
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    // ✓ 统一使用 Surface (背景) 和 OnSurface (前景)
                    containerColor = MaterialTheme.colorScheme.background,
//                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
//                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
//                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                        tooltip = { PlainTooltip { AppText("返回") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                viewModel.search("")
                            } else {
                                onBackClick()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                title = {
                    AnimatedContent(
                        targetState = isSearchActive,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        label = "TitleAnimation"
                    ) { searching ->
                        if (searching) {
                            TextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.search(it) },
                                placeholder = {
                                    Text(
                                        "Search...",
                                        // ✓ 修正颜色：使用 onSurfaceVariant (灰色)，因为背景是 Surface
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    // ✓ 修正光标颜色
                                    cursorColor = MaterialTheme.colorScheme.onSurface
                                ),
                                // ✓ 修正输入文字颜色
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { /* 收起键盘逻辑 */ })
                            )
                        } else {
                            Column {
                                AppText(
                                    File(filePath).name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    // ✓ 统一颜色
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                AppText(
                                    if (state.isLoading) "Loading..." else "${state.totalCount} lines",
                                    role = AppTextRole.BodySmall,
                                    // ✓ 统一颜色
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    AnimatedContent(
                        targetState = isSearchActive,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "ActionAnimation"
                    ) { searching ->
                        if (searching) {
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                                tooltip = { PlainTooltip { AppText("退出搜索") } },
                                state = rememberTooltipState()
                            ) {
                                IconButton(onClick = { viewModel.search("") }) {
                                    Icon(Icons.Default.Close, "Clear")
                                }
                            }
                        } else {
                            Row {
                                val autoScrollText = if (state.autoScroll) "暂停自动滚动" else "开启自动滚动"
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                                    tooltip = { PlainTooltip { AppText(autoScrollText) } },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(onClick = { viewModel.toggleAutoScroll(!state.autoScroll) }) {
                                        val icon = if (state.autoScroll) Icons.Default.Pause else Icons.Default.PlayArrow
                                        val tint = if (state.autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        Icon(icon, "AutoScroll", tint = tint)
                                    }
                                }

                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                                    tooltip = { PlainTooltip { AppText(if (formattedLines) "紧凑显示" else "格式化显示") } },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(onClick = { formattedLines = !formattedLines }) {
                                        Icon(
                                            Icons.Default.FormatAlignLeft,
                                            "FormatLog",
                                            tint = if (formattedLines) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }

                                Box {
                                    TooltipBox(
                                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                                        tooltip = { PlainTooltip { AppText("更多选项") } },
                                        state = rememberTooltipState()
                                    ) {
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(Icons.Default.MoreVert, "More Options")
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        // ... 菜单项保持不变，它们会自动使用 Theme 样式 ...
                                        DropdownMenuItem(
                                            text = { AppText("搜索日志") },
                                            onClick = { showMenu = false; isSearchActive = true },
                                            leadingIcon = { Icon(Icons.Default.Search, null) }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { AppText("日志字段") },
                                            onClick = { showMenu = false; showFieldsDialog = true },
                                            leadingIcon = { Icon(Icons.Default.Tune, null) }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { AppText("导出文件") },
                                            onClick = { showMenu = false; viewModel.exportLogFile(context) },
                                            leadingIcon = { Icon(Icons.Default.Share, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { AppText("清空日志", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showMenu = false
                                                showClearDialog= true
                                            },
                                            leadingIcon = { Icon(Icons.Default.CleaningServices, null, tint = MaterialTheme.colorScheme.error) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    )
    { padding ->
        // Body 内容
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) textSizeSp = (textSizeSp * zoom).coerceIn(8f, 50f)
                    }
                }
        ) {
            if (state.isLoading && state.mappingList.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    AppText(
                        "Loading...",
                        role = AppTextRole.Body,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                SelectionContainer {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxHeight()
                                .wrapContentWidth(unbounded = true),
                            contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
                        ) {
                            items(
                                count = state.totalCount,
                                key = { index -> index },
                                contentType = { 1 }
                            ) { index ->
                                LogLineItem(
                                    line = viewModel.getLineContent(index),
                                    visibleFields = visibleFields,
                                    formatted = formattedLines,
                                    searchQuery = state.searchQuery,
                                    fontSize = textSizeSp.sp,
                                    textColor = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
                DraggableScrollbar(listState = listState, totalItems = state.totalCount, modifier = Modifier.align(Alignment.CenterEnd))
            }

            if (!state.autoScroll && !state.isSearching) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp, bottom = 32.dp, end = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (listState.canScrollForward) {
                        SmallFloatingActionButton(
                            onClick = { viewModel.toggleAutoScroll(true) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, "Bottom")
                        }
                    }
                }
            }

            if (state.isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                        .pointerInput(Unit) {}
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        AppText(
                            "Searching...",
                            role = AppTextRole.Body,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }


    }
    // ✨ 挂载通用确认弹窗
    CommonAlertDialog(
        showDialog = showClearDialog,
        onDismissRequest = { showClearDialog = false },
        onConfirm = {
            // 🔥 确认后，执行清空逻辑
            viewModel.clearLogFile(context)
        },
        title = "⚠️ 警告",
        text = "🤔 确认清空当前日志文件？此操作无法撤销。",
        icon = Icons.Default.CleaningServices,
        iconTint = MaterialTheme.colorScheme.error,
        confirmText = "确认清空",
        confirmButtonColor = MaterialTheme.colorScheme.error
    )

    if (showFieldsDialog) {
        AppAlertDialog(
            modifier = Modifier.sesameDialogWidth(),
            onDismissRequest = { showFieldsDialog = false },
            title = { AppText("日志字段") },
            text = {
                Column {
                    LogField.entries.forEach { field ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppCheckbox(
                                checked = field in visibleFields,
                                onCheckedChange = { viewModel.toggleLogField(field) },
                            )
                            AppText(field.title)
                        }
                    }
                }
            },
            confirmButton = {
                AppTextButton(text = "恢复全部", onClick = { viewModel.resetLogFields() })
            },
        )
    }
}


@Composable
fun LogLineItem(
    line: String,
    visibleFields: Set<LogField>,
    formatted: Boolean,
    searchQuery: String,
    fontSize: TextUnit,
    textColor: Color,
) {
    val selectedLine = remember(line, visibleFields) { selectLogFields(line, visibleFields) }
    val displayedLine = remember(selectedLine, formatted) {
        if (formatted) formatLogLine(selectedLine) else compactPreview(selectedLine)
    }
    val searchHighlight = MaterialTheme.colorScheme.tertiary
    val searchText = MaterialTheme.colorScheme.onTertiary
    val keyColor = MaterialTheme.colorScheme.primary
    val stringColor = MaterialTheme.colorScheme.secondary
    val numberColor = MaterialTheme.colorScheme.tertiary
    val keywordColor = MaterialTheme.colorScheme.error

    val annotatedString = remember(displayedLine, searchQuery, keyColor, stringColor, numberColor, keywordColor) {
        buildAnnotatedString {
            var cursor = 0
            JSON_TOKEN_PATTERN.findAll(displayedLine).forEach { match ->
                append(displayedLine.substring(cursor, match.range.first))
                val token = match.value
                val isKey = token.startsWith('"') && displayedLine.substring(match.range.last + 1).trimStart().startsWith(':')
                val color = when {
                    isKey -> keyColor
                    token.startsWith('"') -> stringColor
                    token == "true" || token == "false" || token == "null" -> keywordColor
                    else -> numberColor
                }
                withStyle(SpanStyle(color = color)) { append(token) }
                cursor = match.range.last + 1
            }
            append(displayedLine.substring(cursor))

            if (searchQuery.isNotEmpty()) {
                val lowerLine = displayedLine.lowercase()
                val lowerQuery = searchQuery.lowercase()
                var startIndex = 0
                while (startIndex < lowerLine.length) {
                    val index = lowerLine.indexOf(lowerQuery, startIndex)
                    if (index < 0) break
                    addStyle(
                        SpanStyle(background = searchHighlight, color = searchText),
                        index,
                        (index + searchQuery.length).coerceAtMost(displayedLine.length),
                    )
                    startIndex = index + searchQuery.length.coerceAtLeast(1)
                }
            }
        }
    }

    Text(
        text = annotatedString,
        color = textColor,
        fontSize = fontSize,
        softWrap = formatted,
        maxLines = if (formatted) Int.MAX_VALUE else 1,
        style = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = fontSize * 1.2f
        ),
        modifier = Modifier
            .wrapContentWidth(unbounded = true)
            .padding(vertical = 0.dp) // 减少不必要的 padding
    )
}

private fun selectLogFields(line: String, visibleFields: Set<LogField>): String {
    return runCatching {
        val json = line.toJsonElement() as? JsonObject ?: return@runCatching line
        JsonObject(visibleFields.sortedBy { it.ordinal }.associate { field ->
            field.key to (json[field.key] ?: JsonNull)
        }).toString()
    }.getOrDefault(line)
}

private const val MAX_COMPACT_RENDER_CHARS = 4_096

private fun compactPreview(line: String): String {
    if (line.length <= MAX_COMPACT_RENDER_CHARS) return line
    return runCatching {
        val json = line.toJsonElement() as? JsonObject
            ?: return@runCatching line.take(MAX_COMPACT_RENDER_CHARS) + "... [内容过长，完整内容请导出]"
        val message = json["message"]?.jsonPrimitive?.content ?: ""
        if (message.length <= MAX_COMPACT_RENDER_CHARS) {
            line.take(MAX_COMPACT_RENDER_CHARS) + "..."
        } else {
            JsonObject(json + ("message" to JsonPrimitive(message.take(1_200) + "... [内容过长，完整内容请导出]"))).toString()
        }
    }.getOrElse {
        line.take(MAX_COMPACT_RENDER_CHARS) + "... [内容过长，完整内容请导出]"
    }
}

private val prettyLogJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

private fun formatLogLine(line: String): String = runCatching {
    val expanded = expandNestedJson(line.toJsonElement())
    if (expanded is JsonObject) prettyLogJson.encodeToString(JsonElement.serializer(), expanded) else line
}.getOrDefault(line)

/**
 * Capture logs commonly contain JSON encoded as strings inside another JSON
 * object (for example message -> Params -> requestData). Expand those values
 * only for the formatted viewer; the JSONL file remains compact and lossless.
 */
private fun expandNestedJson(value: JsonElement, depth: Int = 0): JsonElement {
    if (depth >= 12) return value

    return when (value) {
        is JsonObject -> JsonObject(value.mapValues { (_, child) -> expandNestedJson(child, depth + 1) })
        is JsonArray -> JsonArray(value.map { expandNestedJson(it, depth + 1) })

        is JsonPrimitive -> {
            val trimmed = value.content.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                val nested = trimmed.toJsonElement()
                if (nested !is JsonNull) expandNestedJson(nested, depth + 1) else value
            } else {
                value
            }
        }

    }
}

private val JSON_TOKEN_PATTERN = Regex(""""(?:\\\\.|[^"\\\\])*"|-?\\d+(?:\\.\\d+)?|true|false|null""")

@Composable
fun DraggableScrollbar(listState: LazyListState, totalItems: Int, modifier: Modifier = Modifier) {
    if (totalItems <= 0) return
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var isVisible by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState.isScrollInProgress, isDragging) {
        if (listState.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(1500); isVisible = false
        }
    }

    val alpha by animateFloatAsState(targetValue = if (isVisible) 1f else 0f, animationSpec = tween(durationMillis = 300), label = "Alpha")

    val scrollbarInfo by remember(totalItems, trackHeightPx) {
        derivedStateOf {
            if (trackHeightPx == 0f) return@derivedStateOf null
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) return@derivedStateOf null
            val visibleCount = visibleItemsInfo.size
            val firstVisible = listState.firstVisibleItemIndex
            val thumbSizeRatio = (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.05f, 1f)
            val thumbHeightPx = trackHeightPx * thumbSizeRatio
            val scrollableHeightPx = trackHeightPx - thumbHeightPx
            val progress = firstVisible.toFloat() / max(1, totalItems - visibleCount).toFloat()
            val thumbOffsetPx = scrollableHeightPx * progress.coerceIn(0f, 1f)
            Pair(thumbHeightPx, thumbOffsetPx)
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(30.dp)
            .alpha(alpha)
            .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    isDragging = true
                    if (trackHeightPx > 0f) {
                        val dragRatio = delta / trackHeightPx
                        val targetIndex = (listState.firstVisibleItemIndex + (dragRatio * totalItems)).toInt().coerceIn(0, totalItems - 1)
                        coroutineScope.launch { listState.scrollToItem(targetIndex) }
                    }
                },
                onDragStopped = { isDragging = false }
            )
    ) {
        if (scrollbarInfo != null) {
            val (thumbHeightPx, thumbOffsetPx) = scrollbarInfo!!
            val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
            val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .width(4.dp)
                    .height(thumbHeightDp)
                    .offset(y = thumbOffsetDp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            )
        }
    }
}
