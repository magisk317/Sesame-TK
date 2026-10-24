package io.github.magisk317.sesame.ui.screen.components

import io.github.magisk317.sesame.ui.sesameDialogWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.text.AppText

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    label: String = "内容",
    singleLine: Boolean = true,
    validate: (String) -> String? = { null },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initialValue) { mutableStateOf(initialValue) }
    var validationError by remember(initialValue) { mutableStateOf<String?>(null) }
    AppAlertDialog(
        modifier = Modifier.sesameDialogWidth(),
        onDismissRequest = onDismiss,
        title = { AppText(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    validationError = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                label = { AppText(label) },
                isError = validationError != null,
                supportingText = validationError?.let { message ->
                    { AppText(message) }
                },
            )
        },
        confirmButton = {
            AppTextButton(
                text = "确定",
                onClick = {
                    validationError = validate(draft)
                    if (validationError == null) {
                        onConfirm(draft)
                        onDismiss()
                    }
                },
            )
        },
        dismissButton = {
            AppTextButton(text = "取消", onClick = onDismiss)
        },
    )
}

@Composable
fun ReadOnlyTextDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        modifier = Modifier.sesameDialogWidth(),
        onDismissRequest = onDismiss,
        title = { AppText(title) },
        text = {
            AppText(
                text = content.ifBlank { "（空）" },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            AppTextButton(text = "关闭", onClick = onDismiss)
        },
    )
}

@Composable
fun StringListEditDialog(
    title: String,
    initialItems: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initialItems) {
        mutableStateOf(initialItems.joinToString("\n"))
    }
    AppAlertDialog(
        modifier = Modifier.sesameDialogWidth(),
        onDismissRequest = onDismiss,
        title = { AppText(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 360.dp),
                label = { AppText("每行一项") },
            )
        },
        confirmButton = {
            AppTextButton(
                text = "确定",
                onClick = {
                    val items = draft
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                    onConfirm(items)
                    onDismiss()
                },
            )
        },
        dismissButton = {
            AppTextButton(text = "取消", onClick = onDismiss)
        },
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        modifier = Modifier.sesameDialogWidth(),
        onDismissRequest = onDismiss,
        title = { AppText(title) },
        text = { AppText(message) },
        confirmButton = {
            AppTextButton(
                text = confirmText,
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            )
        },
        dismissButton = {
            AppTextButton(text = "取消", onClick = onDismiss)
        },
    )
}
