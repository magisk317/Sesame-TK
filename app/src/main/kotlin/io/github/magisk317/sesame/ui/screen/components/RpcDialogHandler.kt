package io.github.magisk317.sesame.ui.screen.components

import io.github.magisk317.sesame.ui.sesameDialogWidth
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ImportExport
import io.github.magisk317.uikit.surface.AppAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.magisk317.sesame.ui.viewmodel.RpcDebugViewModel
import io.github.magisk317.sesame.ui.viewmodel.RpcDialogState
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ToastUtil
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.surface.AppTextButton

@Composable
fun RpcDialogHandler(state: RpcDialogState, viewModel: RpcDebugViewModel) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    when (state) {
        is RpcDialogState.None -> {}

        is RpcDialogState.Edit -> {
            var name by remember { mutableStateOf(state.initialName) }
            var description by remember { mutableStateOf(state.initialDesc) }
            var json by remember { mutableStateOf(state.initialJson) }

            Dialog(
                onDismissRequest = { viewModel.dismissDialog() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .sesameDialogWidth()
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // --- 顶部区域：标题 + 导入按钮 ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween // 标题在左，按钮在右
                        ) {
                            AppText(
                                text = if (state.item == null) "新建调试项" else "编辑调试项",
                                role = AppTextRole.Title
                            )

                            // 🔥 新增：导入按钮
                            TextButton(
                                onClick = {
                                    // 1. 读取剪贴板
                                    val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    if (clipText.isBlank()) {
                                        ToastUtil.makeText(context, "剪贴板为空", 0).show()
                                        return@TextButton
                                    }
                                    StructuredLog.d("RpcUI", "剪贴板原始内容: [$clipText]")
                                    val parsed = viewModel.parseJsonFields(clipText) // 假设您把这个方法改成了 public
                                    StructuredLog.d("RpcUI", "解析结果 Name: ${parsed.name}")
                                    name = parsed.name
                                    description = parsed.description
                                    json = try {
                                        if (parsed.method.isNotEmpty()) {
                                            val map = mapOf(
                                                "methodName" to parsed.method,
                                                "requestData" to parsed.requestData
                                            )
                                            viewModel.formatJsonFromRaw(map)
                                        } else {
                                            ""
                                        }
                                    } catch (e: Exception) {
                                        ""
                                    }

                                    ToastUtil.makeText(context, "已导入剪贴板数据", 0).show()
                                }
                            ) {
                                Icon(Icons.Default.ImportExport, null, modifier = Modifier.size(18.dp)) // 换个图标，或者用 ContentPaste
                                Spacer(Modifier.width(4.dp))
                                AppText("从剪贴板导入")
                            }
                        }

                        // ... 下面的输入框保持不变 ...

                        // 1. 名称输入框
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { AppText("名称 (可选)") },
                            placeholder = { AppText("例如：领森林能量") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // 2. 描述输入框
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { AppText("功能描述 (可选)") },
                            modifier = Modifier.fillMaxWidth(),
//                            minLines = 2,
                            maxLines = 4
                        )

                        // 3. JSON 数据输入框
                        OutlinedTextField(
                            value = json,
                            onValueChange = { json = it },
                            label = { AppText("RPC 数据 (JSON)") },
                            placeholder = {
                                Text(
                                    text = """
                                        {
                                          "methodName": "com.alipay.xxx",
                                          "requestData": [...]
                                        }
                                    """.trimIndent(),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(248.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val formatted = viewModel.tryFormatJson(json)
                                        if (formatted != null) {
                                            json = formatted
                                            ToastUtil.makeText(context, "✨ JSON 已格式化", 0).show()
                                        } else {
                                            ToastUtil.makeText(context, "格式错误", 0).show()
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, "格式化")
                                }
                            }
                        )

                        // 底部按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { viewModel.dismissDialog() }) {
                                AppText("取消")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.saveItem(name, description, json, state.item) },
                                modifier = Modifier.width(120.dp)
                            ) {
                                AppText("保存")
                            }
                        }
                    }
                }
            }
        }

        is RpcDialogState.DeleteConfirm -> {
            AppAlertDialog(
                modifier = Modifier.sesameDialogWidth(),
                onDismissRequest = { viewModel.dismissDialog() },
                title = { AppText("确认删除") },
                text = { AppText("确定要删除 \"${state.item.getDisplayName()}\" 吗？") },
                confirmButton = {
                    AppTextButton(
                        text = "删除",
                        onClick = { viewModel.deleteItem(state.item) },
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                dismissButton = {
                    AppTextButton(text = "取消", onClick = { viewModel.dismissDialog() })
                }
            )
        }

        is RpcDialogState.RestoreConfirm -> {
            AppAlertDialog(
                modifier = Modifier.sesameDialogWidth(),
                onDismissRequest = { viewModel.dismissDialog() },
                title = { AppText("确认恢复") },
                text = { AppText("将恢复 ${state.items.size} 项数据，当前列表将被覆盖。") },
                confirmButton = {
                    AppTextButton(text = "恢复", onClick = { viewModel.confirmRestore(state.items) })
                },
                dismissButton = {
                    AppTextButton(text = "取消", onClick = { viewModel.dismissDialog() })
                }
            )
        }
    }
}