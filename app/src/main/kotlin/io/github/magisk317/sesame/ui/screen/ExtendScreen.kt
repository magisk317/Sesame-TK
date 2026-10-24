package io.github.magisk317.sesame.ui.screen

import io.github.magisk317.sesame.ui.sesameDialogWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.magisk317.sesame.ui.screen.components.OnceDailyAccountPickerDialog
import io.github.magisk317.sesame.ui.screen.components.OnceDailySettingsDialog
import io.github.magisk317.sesame.ui.screen.components.TextInputDialog
import io.github.magisk317.sesame.ui.viewmodel.ExtendDialog
import io.github.magisk317.sesame.ui.viewmodel.ExtendViewModel
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.text.AppText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtendScreen(
    onBackClick: () -> Unit,
    viewModel: ExtendViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentDialog by viewModel.currentDialog.collectAsStateWithLifecycle()

    // 初始化数据
    LaunchedEffect(Unit) {
        viewModel.loadData(context)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "扩展功能",
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionCard(
                    title = "扩展工具",
                    accordionMode = false,
                    sectionExpanded = true,
                    onExpandedChange = {},
                ) {
                    viewModel.menuItems.forEach { item ->
                        AppArrowItem(title = item.title, onClick = item.onClick)
                    }
                }
            }
        }

        DialogHandler(viewModel, currentDialog, context)
    }
}

@Composable
private fun DialogHandler(
    viewModel: ExtendViewModel,
    dialog: ExtendDialog,
    context: android.content.Context,
) {
    when (dialog) {
        is ExtendDialog.None -> {}

        is ExtendDialog.ClearPhotoConfirm -> {
            AppAlertDialog(
                modifier = Modifier.sesameDialogWidth(),
                onDismissRequest = { viewModel.dismissDialog() },
                title = { AppText("清空图片") },
                text = { AppText("确认清空 ${dialog.count} 组光盘行动图片？") },
                confirmButton = {
                    AppTextButton(text = "确定", onClick = { viewModel.clearPhotos(context) })
                },
                dismissButton = {
                    AppTextButton(text = "取消", onClick = { viewModel.dismissDialog() })
                }
            )
        }

        is ExtendDialog.WritePhotoTest -> {
            AppAlertDialog(
                modifier = Modifier.sesameDialogWidth(),
                onDismissRequest = { viewModel.dismissDialog() },
                title = { AppText("Test") },
                text = { AppText(dialog.message) },
                confirmButton = {
                    AppTextButton(text = "确定", onClick = { viewModel.writePhotoTest(context) })
                },
                dismissButton = {
                    AppTextButton(text = "取消", onClick = { viewModel.dismissDialog() })
                }
            )
        }

        is ExtendDialog.InputDialog -> {
            TextInputDialog(
                title = dialog.title,
                initialValue = dialog.initialValue,
                onConfirm = dialog.onConfirm,
                onDismiss = viewModel::dismissDialog,
            )
        }

        is ExtendDialog.OnceDailyAccountPicker -> OnceDailyAccountPickerDialog(
            accounts = dialog.accounts,
            onAccountSelected = viewModel::selectOnceDailyAccount,
            onDismiss = viewModel::dismissDialog,
        )

        is ExtendDialog.OnceDailySettings -> OnceDailySettingsDialog(
            page = dialog.page,
            draft = dialog.draft,
            modules = viewModel.onceDailyModules,
            onModeChanged = viewModel::setOnceDailyMode,
            onBlacklistChanged = viewModel::setOnceDailyBlacklist,
            onFullRunTimesChanged = viewModel::setOnceDailyFullRunTimes,
            onEditBlacklist = viewModel::showOnceDailyBlacklist,
            onEditFullRunTimes = viewModel::showOnceDailyFullRunTimes,
            onSave = { viewModel.saveOnceDailySettings(context) },
            onBackToAccounts = viewModel::showOnceDailyAccounts,
            onBackToOverview = viewModel::showOnceDailyOverview,
        )
    }
}
