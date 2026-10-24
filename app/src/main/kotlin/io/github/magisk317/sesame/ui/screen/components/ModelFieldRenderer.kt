package io.github.magisk317.sesame.ui.screen.components

import io.github.magisk317.sesame.ui.sesameDialogWidth
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.magisk317.sesame.model.ModelField
import io.github.magisk317.sesame.model.modelFieldExt.BooleanModelField
import io.github.magisk317.sesame.model.modelFieldExt.ChoiceModelField
import io.github.magisk317.sesame.model.modelFieldExt.EmptyModelField
import io.github.magisk317.sesame.model.modelFieldExt.IntegerModelField
import io.github.magisk317.sesame.model.modelFieldExt.ListModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectAndCountOneModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectModelField
import io.github.magisk317.sesame.model.modelFieldExt.SelectOneModelField
import io.github.magisk317.sesame.model.modelFieldExt.StringModelField
import io.github.magisk317.sesame.model.modelFieldExt.TextModelField
import io.github.magisk317.sesame.ui.extension.openUrl
import io.github.magisk317.sesame.ui.settings.SettingsModelAccess
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.preference.SingleChoiceOptionDialog
import io.github.magisk317.uikit.preference.StateSwitchItem

/**
 * Full Compose renderer for every ModelField type used by Sesame settings.
 */
@Composable
fun ModelFieldRenderer(
    field: ModelField<*>,
    displayName: String = field.name,
    revision: Int,
    onChanged: () -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val observedRevision = revision
    val context = LocalContext.current
    val title = displayName
    val summary = field.desc.orEmpty()

    when (field) {
        is EmptyModelField -> {
            var showConfirm by remember(field.code) { mutableStateOf(false) }
            AppArrowItem(
                title = title,
                summary = summary.ifBlank { "操作项" },
                onClick = {
                    if (field.hasAction()) {
                        showConfirm = true
                    } else {
                        Toast.makeText(context, "无配置项", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            if (showConfirm) {
                ConfirmActionDialog(
                    title = "警告",
                    message = "确认执行该操作？",
                    onConfirm = {
                        runCatching(field::runAction)
                            .onFailure { StructuredLog.printStackTrace("ModelFieldRenderer", it) }
                    },
                    onDismiss = { showConfirm = false },
                )
            }
        }

        is BooleanModelField -> {
            StateSwitchItem(
                title = title,
                summary = summary,
                checked = field.value == true,
                onCheckedChange = { enabled ->
                    field.value = enabled
                    onChanged()
                },
            )
        }

        is ChoiceModelField -> {
            var showDialog by remember(field.code) { mutableStateOf(false) }
            val options = SettingsModelAccess.choiceOptions(field).toList()
            val selected = field.value.coerceAtLeast(0)
            val selectedLabel = options.getOrNull(selected.coerceAtMost((options.size - 1).coerceAtLeast(0)))
                ?: selected.toString()
            AppArrowItem(
                title = title,
                summary = listOfNotNull(
                    summary.takeIf { it.isNotBlank() },
                    selectedLabel.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                onClick = { showDialog = true },
            )
            if (showDialog && options.isNotEmpty()) {
                SingleChoiceOptionDialog(
                    title = title,
                    options = options,
                    selectedIndex = selected.coerceIn(0, options.lastIndex),
                    onSelectionChange = { index ->
                        field.value = index
                        onChanged()
                        showDialog = false
                    },
                    onDismissRequest = { showDialog = false },
                    modifier = androidx.compose.ui.Modifier.sesameDialogWidth(),
                )
            }
        }

        is IntegerModelField -> {
            var showDialog by remember(field.code) { mutableStateOf(false) }
            val min = SettingsModelAccess.intMinLimit(field)
            val max = SettingsModelAccess.intMaxLimit(field)
            val configValue = field.getConfigValue().orEmpty()
            val limitHint = buildString {
                if (min != null) append("min=$min")
                if (min != null && max != null) append(", ")
                if (max != null) append("max=$max")
            }
            AppArrowItem(
                title = title,
                summary = listOfNotNull(
                    summary.takeIf { it.isNotBlank() },
                    "当前: $configValue",
                    limitHint.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                onClick = { showDialog = true },
            )
            if (showDialog) {
                TextInputDialog(
                    title = title,
                    initialValue = configValue,
                    label = "整数值",
                    validate = { raw ->
                        val parsed = raw.trim().toIntOrNull()
                            ?: return@TextInputDialog "请输入有效整数"
                        when {
                            min != null && parsed < min -> "不能小于 $min"
                            max != null && parsed > max -> "不能大于 $max"
                            else -> null
                        }
                    },
                    onConfirm = { raw ->
                        field.setConfigValue(raw.trim())
                        onChanged()
                    },
                    onDismiss = { showDialog = false },
                )
            }
        }

        is StringModelField -> {
            var showDialog by remember(field.code) { mutableStateOf(false) }
            AppArrowItem(
                title = title,
                summary = listOfNotNull(
                    summary.takeIf { it.isNotBlank() },
                    field.getConfigValue()?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                onClick = { showDialog = true },
            )
            if (showDialog) {
                TextInputDialog(
                    title = title,
                    initialValue = field.value.orEmpty(),
                    label = "字符串",
                    onConfirm = { raw ->
                        field.value = raw
                        onChanged()
                    },
                    onDismiss = { showDialog = false },
                )
            }
        }

        is TextModelField.UrlTextModelField -> {
            AppArrowItem(
                title = title,
                summary = listOfNotNull(
                    summary.takeIf { it.isNotBlank() },
                    field.getConfigValue()?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                onClick = {
                    val url = field.getConfigValue()
                    if (!url.isNullOrBlank()) {
                        context.openUrl(url)
                    }
                },
            )
        }

        is TextModelField.ReadOnlyTextModelField, is TextModelField -> {
            var showDialog by remember(field.code) { mutableStateOf(false) }
            AppArrowItem(
                title = title,
                summary = listOfNotNull(
                    summary.takeIf { it.isNotBlank() },
                    field.getConfigValue()?.takeIf { it.isNotBlank() }?.take(80),
                ).joinToString(" · "),
                onClick = { showDialog = true },
            )
            if (showDialog) {
                ReadOnlyTextDialog(
                    title = title,
                    content = field.getConfigValue().orEmpty(),
                    onDismiss = { showDialog = false },
                )
            }
        }

        is SelectOneModelField -> {
            var showDialog by remember(field.code) { mutableStateOf(false) }
            val entities = remember(field.code, revision) { SettingsModelAccess.expandEntities(field) }
            AppArrowItem(
                title = title,
                summary = listOfNotNull(
                    summary.takeIf { it.isNotBlank() },
                    field.value?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                onClick = { showDialog = true },
            )
            if (showDialog) {
                SelectEntityDialog(
                    title = title,
                    entities = entities,
                    selectFunc = field,
                    mode = SelectListMode.RADIO,
                    hasCount = false,
                    onDismiss = { showDialog = false },
                    onChanged = onChanged,
                )
            }
        }

        is SelectModelField -> {
            var showDialog by remember(field.code) { mutableStateOf(false) }
            val entities = remember(field.code, revision) { SettingsModelAccess.expandEntities(field) }
            AppArrowItem(
                title = title,
                summary = listOfNotNull(
                    summary.takeIf { it.isNotBlank() },
                    "已选 ${field.value.size} 项",
                ).joinToString(" · "),
                onClick = { showDialog = true },
            )
            if (showDialog) {
                SelectEntityDialog(
                    title = title,
                    entities = entities,
                    selectFunc = field,
                    mode = SelectListMode.CHECK,
                    hasCount = false,
                    onDismiss = { showDialog = false },
                    onChanged = onChanged,
                )
            }
        }

        is SelectAndCountOneModelField -> {
            var showDialog by remember(field.code) { mutableStateOf(false) }
            val entities = remember(field.code, revision) { SettingsModelAccess.expandEntities(field) }
            val selectedId = field.value.key
            val selectedCount = field.value.value
            AppArrowItem(
                title = title,
                summary = listOfNotNull(
                    summary.takeIf { it.isNotBlank() },
                    selectedId?.let { id -> "$id ×${selectedCount ?: 0}" },
                ).joinToString(" · "),
                onClick = { showDialog = true },
            )
            if (showDialog) {
                SelectEntityDialog(
                    title = title,
                    entities = entities,
                    selectFunc = field,
                    mode = SelectListMode.RADIO,
                    hasCount = true,
                    onDismiss = { showDialog = false },
                    onChanged = onChanged,
                )
            }
        }

        is SelectAndCountModelField -> {
            var showDialog by remember(field.code) { mutableStateOf(false) }
            val entities = remember(field.code, revision) { SettingsModelAccess.expandEntities(field) }
            AppArrowItem(
                title = title,
                summary = listOfNotNull(
                    summary.takeIf { it.isNotBlank() },
                    "已选 ${field.value.size} 项",
                ).joinToString(" · "),
                onClick = { showDialog = true },
            )
            if (showDialog) {
                SelectEntityDialog(
                    title = title,
                    entities = entities,
                    selectFunc = field,
                    mode = SelectListMode.CHECK,
                    hasCount = true,
                    onDismiss = { showDialog = false },
                    onChanged = onChanged,
                )
            }
        }

        is ListModelField -> {
            var showDialog by remember(field.code) { mutableStateOf(false) }
            AppArrowItem(
                title = title,
                summary = listOfNotNull(
                    summary.takeIf { it.isNotBlank() },
                    "列表 ${field.value.size} 项",
                ).joinToString(" · "),
                onClick = { showDialog = true },
            )
            if (showDialog) {
                StringListEditDialog(
                    title = title,
                    initialItems = field.value.orEmpty().filterNotNull(),
                    onConfirm = { items ->
                        field.value = ArrayList(items)
                        onChanged()
                    },
                    onDismiss = { showDialog = false },
                )
            }
        }

        else -> {
            AppArrowItem(
                title = title.ifBlank { field.code.orEmpty() },
                summary = listOfNotNull(
                    "暂不支持此字段类型",
                    field.getType(),
                    summary.takeIf { it.isNotBlank() },
                    field.getConfigValue()?.takeIf { it.isNotBlank() }?.take(60),
                ).joinToString(" · "),
                enabled = false,
                onClick = {},
            )
        }
    }
}
