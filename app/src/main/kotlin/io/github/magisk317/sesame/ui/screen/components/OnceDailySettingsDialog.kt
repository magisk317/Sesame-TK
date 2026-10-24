package io.github.magisk317.sesame.ui.screen.components

import io.github.magisk317.sesame.ui.sesameDialogWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.sesame.entity.MapperEntity
import io.github.magisk317.sesame.model.CustomSettings
import io.github.magisk317.sesame.model.OnceDailyMode
import io.github.magisk317.sesame.model.OnceDailyTimeInput
import io.github.magisk317.sesame.model.SelectModelFieldFunc
import io.github.magisk317.sesame.ui.viewmodel.OnceDailySettingsDraft
import io.github.magisk317.sesame.ui.viewmodel.OnceDailySettingsPage
import io.github.magisk317.uikit.preference.AppRadioButton
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

private data class ModeOption(
    val mode: OnceDailyMode,
    val title: String,
    val summary: String,
)

private val modeOptions = listOf(
    ModeOption(
        mode = OnceDailyMode.DISABLED,
        title = "关闭",
        summary = "每轮都运行所有已启用任务",
    ),
    ModeOption(
        mode = OnceDailyMode.ENABLED,
        title = "开启",
        summary = "当日首轮完成后跳过黑名单模块",
    ),
    ModeOption(
        mode = OnceDailyMode.AUTOMATIC,
        title = "自动",
        summary = "启用单次模式，但在设定时间段恢复全量运行",
    ),
)

@Composable
fun OnceDailyAccountPickerDialog(
    accounts: List<CustomSettings.Account>,
    onAccountSelected: (CustomSettings.Account) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        modifier = Modifier.sesameDialogWidth(),
        onDismissRequest = onDismiss,
        title = { AppText("选择目标账号") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(accounts, key = CustomSettings.Account::userId) { account ->
                    AppArrowItem(
                        title = account.displayName,
                        onClick = { onAccountSelected(account) },
                    )
                }
            }
        },
        dismissButton = {
            AppTextButton(text = "取消", onClick = onDismiss)
        },
    )
}

@Composable
fun OnceDailySettingsDialog(
    page: OnceDailySettingsPage,
    draft: OnceDailySettingsDraft,
    modules: List<MapperEntity>,
    onModeChanged: (OnceDailyMode) -> Unit,
    onBlacklistChanged: (Set<String>) -> Unit,
    onFullRunTimesChanged: (String) -> Unit,
    onEditBlacklist: () -> Unit,
    onEditFullRunTimes: () -> Unit,
    onSave: () -> Unit,
    onBackToAccounts: () -> Unit,
    onBackToOverview: () -> Unit,
) {
    when (page) {
        OnceDailySettingsPage.OVERVIEW -> OnceDailyOverviewDialog(
            draft = draft,
            onModeChanged = onModeChanged,
            onEditBlacklist = onEditBlacklist,
            onEditFullRunTimes = onEditFullRunTimes,
            onSave = onSave,
            onBack = onBackToAccounts,
        )

        OnceDailySettingsPage.BLACKLIST -> {
            val selection = remember(draft.account.userId, draft.blacklistedModuleIds) {
                DraftSelection(draft.blacklistedModuleIds, onBlacklistChanged)
            }
            SelectEntityDialog(
                title = "黑名单 | ${draft.account.displayName}",
                entities = modules,
                selectFunc = selection,
                mode = SelectListMode.CHECK,
                hasCount = false,
                onDismiss = onBackToOverview,
                onChanged = {},
            )
        }

        OnceDailySettingsPage.FULL_RUN_TIMES -> TextInputDialog(
            title = "非单次运行时间点 | ${draft.account.displayName}",
            initialValue = draft.fullRunTimes.joinToString(","),
            label = "时间点（多个用逗号隔开）",
            validate = OnceDailyTimeInput::validationError,
            onConfirm = onFullRunTimesChanged,
            onDismiss = onBackToOverview,
        )
    }
}

@Composable
private fun OnceDailyOverviewDialog(
    draft: OnceDailySettingsDraft,
    onModeChanged: (OnceDailyMode) -> Unit,
    onEditBlacklist: () -> Unit,
    onEditFullRunTimes: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    AppAlertDialog(
        modifier = Modifier.sesameDialogWidth(),
        onDismissRequest = onBack,
        title = { AppText("每日单次运行设置") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppText(
                    text = "账号：${draft.account.displayName}",
                    role = AppTextRole.Subtitle,
                )
                Spacer(Modifier.height(8.dp))
                modeOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeChanged(option.mode) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppRadioButton(
                            selected = draft.mode == option.mode,
                            onClick = { onModeChanged(option.mode) },
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        ) {
                            AppText(option.title, role = AppTextRole.Body)
                            AppText(
                                text = option.summary,
                                role = AppTextRole.BodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                AppArrowItem(
                    title = "黑名单模块",
                    summary = "已选 ${draft.blacklistedModuleIds.size} 项",
                    onClick = onEditBlacklist,
                )
                AppArrowItem(
                    title = "非单次运行时间点",
                    summary = draft.fullRunTimes.joinToString(", ").ifBlank { "未设置" } +
                        "（每个时间点起持续一个执行间隔）",
                    onClick = onEditFullRunTimes,
                )
            }
        },
        confirmButton = {
            AppTextButton(text = "保存", onClick = onSave)
        },
        dismissButton = {
            AppTextButton(text = "返回", onClick = onBack)
        },
    )
}

private class DraftSelection(
    selectedIds: Set<String>,
    private val onChanged: (Set<String>) -> Unit,
) : SelectModelFieldFunc {
    private val selectedIds = LinkedHashSet(selectedIds)

    override fun clear() {
        selectedIds.clear()
        emit()
    }

    override fun get(id: String?): Int = 0

    override fun add(id: String?, count: Int?) {
        if (id != null && selectedIds.add(id)) emit()
    }

    override fun remove(id: String?) {
        if (id != null && selectedIds.remove(id)) emit()
    }

    override fun contains(id: String?): Boolean = id != null && id in selectedIds

    private fun emit() {
        onChanged(LinkedHashSet(selectedIds))
    }
}
