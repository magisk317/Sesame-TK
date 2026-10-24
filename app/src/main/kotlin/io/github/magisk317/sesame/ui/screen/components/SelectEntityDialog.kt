package io.github.magisk317.sesame.ui.screen.components

import io.github.magisk317.sesame.ui.sesameDialogWidth
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.magisk317.sesame.entity.AreaCode
import io.github.magisk317.sesame.entity.CooperateEntity
import io.github.magisk317.sesame.entity.MapperEntity
import io.github.magisk317.sesame.model.SelectModelFieldFunc
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.maps.CooperateMap
import io.github.magisk317.sesame.util.maps.IdMapManager
import io.github.magisk317.uikit.preference.AppCheckbox
import io.github.magisk317.uikit.preference.AppRadioButton
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.WorkspaceSearchField
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole

enum class SelectListMode {
    RADIO,
    CHECK,
    SHOW,
}

private data class RemovalRequest(
    val entity: MapperEntity,
    val removeFromCooperateMap: Boolean,
)

private data class EntityAction(
    val label: String,
    val deepLinkPrefix: String,
)

private val entityActions = listOf(
    EntityAction(
        label = "查看森林",
        deepLinkPrefix = "alipays://platformapi/startapp?saId=10000007&qrcode=https%3A%2F%2F60000002.h5app.alipay.com%2Fwww%2Fhome.html%3FuserId%3D",
    ),
    EntityAction(
        label = "查看庄园",
        deepLinkPrefix = "alipays://platformapi/startapp?saId=10000007&qrcode=https%3A%2F%2F66666674.h5app.alipay.com%2Fwww%2Findex.htm%3Fuid%3D",
    ),
    EntityAction(
        label = "查看资料",
        deepLinkPrefix = "alipays://platformapi/startapp?appId=20000166&actionType=profile&userId=",
    ),
)

private const val SELECT_ENTITY_DIALOG_TAG = "SelectEntityDialog"

private fun openEntityAction(context: Context, action: EntityAction, entity: MapperEntity) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, (action.deepLinkPrefix + entity.id).toUri()))
    }.onFailure { error ->
        StructuredLog.printStackTrace(
            SELECT_ENTITY_DIALOG_TAG,
            "Unable to open entity action",
            error,
        )
        Toast.makeText(context, "无法打开支付宝页面", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SelectEntityDialog(
    title: String,
    entities: List<MapperEntity>,
    selectFunc: SelectModelFieldFunc,
    mode: SelectListMode,
    hasCount: Boolean,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var countTarget by remember { mutableStateOf<MapperEntity?>(null) }
    var countDraft by remember { mutableStateOf("1") }
    var actionTarget by remember { mutableStateOf<MapperEntity?>(null) }
    var removalRequest by remember { mutableStateOf<RemovalRequest?>(null) }

    val filtered = remember(entities, query, revision) {
        val q = query.trim()
        if (q.isEmpty()) {
            entities
        } else {
            entities.filter { entity ->
                entity.name.contains(q, ignoreCase = true) ||
                    entity.id.contains(q, ignoreCase = true) ||
                    entity.getPinyin().any { it.contains(q, ignoreCase = true) }
            }
        }
    }

    fun bump() {
        revision++
        onChanged()
    }

    AppAlertDialog(
        modifier = Modifier
            .sesameDialogWidth()
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
        onDismissRequest = onDismiss,
        title = { AppText(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp, max = 560.dp),
            ) {
                WorkspaceSearchField(
                    query = query,
                    placeholder = "搜索名称 / ID / 拼音",
                    onValueChange = { query = it },
                )
                if (mode == SelectListMode.CHECK && !hasCount) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppTextButton(
                            text = "全选",
                            onClick = {
                                filtered.forEach { entity ->
                                    if (selectFunc.contains(entity.id) != true) {
                                        selectFunc.add(entity.id, 0)
                                    }
                                }
                                bump()
                            },
                        )
                        AppTextButton(
                            text = "反选",
                            onClick = {
                                filtered.forEach { entity ->
                                    if (selectFunc.contains(entity.id) == true) {
                                        selectFunc.remove(entity.id)
                                    } else {
                                        selectFunc.add(entity.id, 0)
                                    }
                                }
                                bump()
                            },
                        )
                        AppTextButton(
                            text = "清空",
                            onClick = {
                                selectFunc.clear()
                                bump()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                AppText(
                    text = "共 ${filtered.size} 项" + if (mode != SelectListMode.SHOW) {
                        " · 已选 ${entities.count { selectFunc.contains(it.id) == true }}"
                    } else {
                        ""
                    },
                    role = AppTextRole.BodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                ) {
                    items(filtered, key = { it.id + "/" + it.name }) { entity ->
                        val selected = selectFunc.contains(entity.id) == true
                        val count = selectFunc.get(entity.id)
                        val onEntityClick = {
                            when (mode) {
                                SelectListMode.SHOW -> Unit
                                SelectListMode.RADIO -> {
                                    if (hasCount) {
                                        countDraft = (count ?: 1).toString()
                                        countTarget = entity
                                    } else {
                                        selectFunc.clear()
                                        selectFunc.add(entity.id, 0)
                                        bump()
                                    }
                                }
                                SelectListMode.CHECK -> {
                                    if (hasCount) {
                                        if (selected) {
                                            countDraft = (count ?: 1).toString()
                                            countTarget = entity
                                        } else {
                                            countDraft = "1"
                                            countTarget = entity
                                        }
                                    } else if (selected) {
                                        selectFunc.remove(entity.id)
                                        bump()
                                    } else {
                                        selectFunc.add(entity.id, 0)
                                        bump()
                                    }
                                }
                            }
                        }
                        val onEntityLongClick: (() -> Unit)? = if (entity is AreaCode) {
                            null
                        } else {
                            {
                                if (entity is CooperateEntity) {
                                    removalRequest = RemovalRequest(entity, removeFromCooperateMap = true)
                                } else {
                                    actionTarget = entity
                                }
                            }
                        }
                        val interactionSource = remember(entity.id, entity.name) { MutableInteractionSource() }
                        val gestureModifier = when {
                            mode == SelectListMode.SHOW && onEntityLongClick != null -> Modifier.combinedClickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {},
                                onLongClick = onEntityLongClick,
                            )
                            mode == SelectListMode.SHOW -> Modifier
                            onEntityLongClick != null -> Modifier.combinedClickable(
                                onClick = onEntityClick,
                                onLongClick = onEntityLongClick,
                            )
                            else -> Modifier.clickable(onClick = onEntityClick)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(gestureModifier)
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when (mode) {
                                SelectListMode.SHOW -> Unit
                                SelectListMode.RADIO -> {
                                    AppRadioButton(
                                        selected = selected,
                                        onClick = {
                                            if (hasCount) {
                                                countDraft = (count ?: 1).toString()
                                                countTarget = entity
                                            } else {
                                                selectFunc.clear()
                                                selectFunc.add(entity.id, 0)
                                                bump()
                                            }
                                        },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                SelectListMode.CHECK -> {
                                    AppCheckbox(
                                        checked = selected,
                                        onCheckedChange = { checked ->
                                            if (hasCount) {
                                                if (checked) {
                                                    countDraft = (count ?: 1).toString()
                                                    countTarget = entity
                                                } else {
                                                    selectFunc.remove(entity.id)
                                                    bump()
                                                }
                                            } else if (checked) {
                                                selectFunc.add(entity.id, 0)
                                                bump()
                                            } else {
                                                selectFunc.remove(entity.id)
                                                bump()
                                            }
                                        },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                AppText(
                                    text = entity.name.ifBlank { entity.id },
                                    role = AppTextRole.Body,
                                )
                                if (entity.id.isNotBlank() && entity.id != entity.name) {
                                    AppText(
                                        text = entity.id,
                                        role = AppTextRole.BodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            if (hasCount && selected) {
                                AppText(
                                    text = "×${count ?: 0}",
                                    role = AppTextRole.Body,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(text = "完成", onClick = onDismiss)
        },
        dismissButton = {
            AppTextButton(text = "关闭", onClick = onDismiss)
        },
    )

    val target = countTarget
    if (target != null) {
        AppAlertDialog(
            modifier = Modifier.sesameDialogWidth(),
            onDismissRequest = { countTarget = null },
            title = { AppText(target.name.ifBlank { target.id }) },
            text = {
                OutlinedTextField(
                    value = countDraft,
                    onValueChange = { countDraft = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { AppText("次数 / 克数") },
                )
            },
            confirmButton = {
                AppTextButton(
                    text = "确定",
                    onClick = {
                        val parsed = countDraft.toIntOrNull() ?: return@AppTextButton
                        if (parsed < 0) return@AppTextButton
                        if (mode == SelectListMode.RADIO) {
                            selectFunc.clear()
                        }
                        if (parsed == 0 && mode == SelectListMode.CHECK) {
                            selectFunc.remove(target.id)
                        } else {
                            selectFunc.add(target.id, parsed)
                        }
                        countTarget = null
                        bump()
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = "取消",
                    onClick = { countTarget = null },
                )
            },
        )
    }

    val actionEntity = actionTarget
    if (actionEntity != null) {
        AppAlertDialog(
            modifier = Modifier.sesameDialogWidth(),
            onDismissRequest = { actionTarget = null },
            title = { AppText("选项") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    entityActions.forEach { action ->
                        AppTextButton(
                            text = action.label,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                actionTarget = null
                                openEntityAction(context, action, actionEntity)
                            },
                        )
                    }
                    // SHOW lists (for example one-way friends) use a temporary
                    // selection map, so "delete" would only remove an item
                    // from that throwaway map and appear to do nothing. Keep
                    // this menu limited to the three real navigation actions.
                    if (mode != SelectListMode.SHOW) {
                        AppTextButton(
                            text = "删除",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                actionTarget = null
                                removalRequest = RemovalRequest(actionEntity, removeFromCooperateMap = false)
                            },
                        )
                    }
                }
            },
            dismissButton = {
                AppTextButton(text = "取消", onClick = { actionTarget = null })
            },
        )
    }

    val pendingRemoval = removalRequest
    if (pendingRemoval != null) {
        AppAlertDialog(
            modifier = Modifier.sesameDialogWidth(),
            onDismissRequest = { removalRequest = null },
            title = { AppText("删除 ${pendingRemoval.entity.name}") },
            confirmButton = {
                AppTextButton(
                    text = "确定",
                    onClick = {
                        // Close first so a failed map/selection operation cannot leave
                        // a stale confirmation dialog or allow a second submission.
                        removalRequest = null
                        val removed = runCatching {
                            if (pendingRemoval.removeFromCooperateMap) {
                                IdMapManager
                                    .getInstance(CooperateMap::class.java)
                                    .remove(pendingRemoval.entity.id)
                            }
                            selectFunc.remove(pendingRemoval.entity.id)
                        }.onFailure { error ->
                            StructuredLog.printStackTrace(
                                SELECT_ENTITY_DIALOG_TAG,
                                "Unable to remove entity",
                                error,
                            )
                            Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                        }.isSuccess
                        if (removed) {
                            bump()
                        }
                    },
                )
            },
            dismissButton = {
                AppTextButton(text = "取消", onClick = { removalRequest = null })
            },
        )
    }

    // Keep dialog recomposing when selection changes through side channels.
    LaunchedEffect(revision) { /* no-op trigger */ }
}
