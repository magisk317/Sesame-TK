package io.github.magisk317.sesame.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.sesame.SesameApplication.Companion.PREFERENCES_KEY
import io.github.magisk317.sesame.entity.RpcDebugCodec
import io.github.magisk317.sesame.entity.RpcDebugEntity
import io.github.magisk317.sesame.entity.RpcDebugItemRaw
import io.github.magisk317.sesame.ui.LogViewerActivity
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.JsonUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 弹窗状态
sealed class RpcDialogState {
    data object None : RpcDialogState()
    data class Edit(
        val item: RpcDebugEntity?,
        val initialJson: String,
        val initialDesc: String,
        val initialName: String
    ) : RpcDialogState()

    data class DeleteConfirm(val item: RpcDebugEntity) : RpcDialogState()
    data class RestoreConfirm(val items: List<RpcDebugEntity>) : RpcDialogState()


}

class RpcDebugViewModel(application: Application) : AndroidViewModel(application) {



    private val prefs = application.getSharedPreferences(PREFERENCES_KEY, Context.MODE_PRIVATE)
    // UI State
    private val _items = MutableStateFlow<List<RpcDebugEntity>>(emptyList())
    val items = _items.asStateFlow()

    private val _dialogState = MutableStateFlow<RpcDialogState>(RpcDialogState.None)
    val dialogState = _dialogState.asStateFlow()

    init {
        loadItems()
        if (_items.value.isEmpty() && !prefs.contains("rpc_debug_items")) {
            loadDefaultItems()
        }
    }

    // --- 加载与保存 ---

    private fun loadItems() {
        try {
            val jsonString = prefs.getString("rpc_debug_items", null)
            if (jsonString != null) {
                val list = RpcDebugCodec.decodeList(jsonString)
                _items.value = list
            }
        } catch (e: Exception) {
            StructuredLog.e("RpcDebug", "Load failed", e)
        }
    }

    private fun saveItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = RpcDebugCodec.encodeList(_items.value)
                prefs.edit { putString("rpc_debug_items", jsonString) }
            } catch (e: Exception) {
                StructuredLog.e("RpcDebug", "Save failed", e)
            }
        }
    }

    // --- 业务操作 ---

    /**
     * 显示添加 RPC 调试项弹窗
     */
    fun showAddDialog(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        // 1. 修复 RpcDebugItemRaw 报错：传入默认空值
        val (name, method, requestData, description) = try {
            parseJsonFields(clipText)
        } catch (e: Exception) {
            // ✓ 修复点：传入空参数
            RpcDebugItemRaw("", "", null, "")
        }
        // 2. 准备 JSON 字符串 (用于填充输入框)
        val initialJson = if (method.isNotEmpty()) {
            try {
                val map = mapOf("methodName" to method, "requestData" to requestData)
                JsonUtil.formatJson(map, true)
            } catch (e: Exception) {
                ""
            }
        } else {
            ""
        }
        // 注意：Name 目前无法通过这种方式预填充（因为 item 为 null），如果需要预填充 Name，
        // 这里建议由用户手动输入 Name，或者只预填充 JSON 和 描述。
        _dialogState.value = RpcDialogState.Edit(null, initialJson, description, name)
    }

    /**
     * 显示编辑 RPC 调试项弹窗
     */
    fun showEditDialog(item: RpcDebugEntity) {
        val json = try {
            // 编辑时不把 description 放进 JSON 编辑框，而是单独显示
            val map = mapOf("methodName" to item.method, "requestData" to item.requestData)
            JsonUtil.formatJson(map, true)
        } catch (_: Exception) {
            "{}"
        }

        _dialogState.value = RpcDialogState.Edit(item, json, item.description, item.name)
    }

    fun showDeleteDialog(item: RpcDebugEntity) {
        _dialogState.value = RpcDialogState.DeleteConfirm(item)
    }

    fun dismissDialog() {
        _dialogState.value = RpcDialogState.None
    }

    // 保存逻辑
    fun saveItem(name: String, description: String, jsonText: String, editingItem: RpcDebugEntity?) {
        try {
            // 解析 JSON 编辑框的内容 (这里面只包含 method 和 data)
            val (_, method, requestData, _) = parseJsonFields(jsonText)

            // name 和 description 从独立输入框取
            val finalName = name.ifEmpty { method } // 如果没填名字，用 method 代替

            if (method.isEmpty()) {
                ToastUtil.makeText("methodName 不能为空", 0).show()
                return
            }

            val currentList = _items.value.toMutableList()

            if (editingItem != null) {
                // 编辑模式
                val index = currentList.indexOf(editingItem)
                if (index != -1) {
                    editingItem.name = finalName
                    editingItem.description = description // 更新描述
                    editingItem.method = method
                    editingItem.requestData = requestData
                    _items.value = ArrayList(currentList)
                }
            } else {
                // 新增模式
                val newItem = RpcDebugEntity(
                    id = System.currentTimeMillis().toString(),
                    name = finalName,
                    description = description,
                    method = method,
                    requestData = requestData
                )
                currentList.add(newItem)
                _items.value = currentList
            }
            saveItems()
            dismissDialog()
            ToastUtil.makeText("保存成功", 0).show()
        } catch (e: Exception) {
            ToastUtil.makeText("JSON 格式错误: ${e.message}", 1).show()
        }
    }

    fun deleteItem(item: RpcDebugEntity) {
        val list = _items.value.toMutableList()
        list.remove(item)
        _items.value = list
        saveItems()
        dismissDialog()
    }

    fun runRpcItem(item: RpcDebugEntity, activityContext: Context) {
        viewModelScope.launch {
            try {
                val logFile = Files.getDebugLogFile()
                Files.clearFile(logFile)
                val intent = Intent("com.eg.android.AlipayGphone.sesame.rpctest").apply {
                    setPackage(io.github.magisk317.sesame.data.General.PACKAGE_NAME)
                    putExtra("method", item.method)
                    putExtra("data", item.getRequestDataString())
                    putExtra("type", "Rpc")
                }
                activityContext.sendBroadcast(intent)
                ToastUtil.makeText("已发送: ${item.getDisplayName()}", Toast.LENGTH_SHORT).show()
                // JSONL is appended synchronously; poll briefly for the first record.
                var waitCount = 0
                val maxWait = 30 // 最多等待 3 秒（30 * 100ms）
                while (waitCount < maxWait) {
                    delay(100)
                    if (logFile.exists() && logFile.length() > 0) {
                        // 日志文件有内容了，再等待一小段时间确保写入完成
                        delay(200)
                        break
                    }
                    waitCount++
                }
                // 跳转日志
                try {
                    val logIntent = Intent(activityContext, LogViewerActivity::class.java).apply {
                        data = logFile.toUri()
                    }
                    activityContext.startActivity(logIntent)
                } catch (_: Exception) {
                    ToastUtil.makeText("无法打开日志", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                ToastUtil.makeText("执行失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- 工具功能 ---

    fun backupToClipboard(context: Context) {
        if (_items.value.isEmpty()) return
        try {
            val json = JsonUtil.formatJson(_items.value, true)
            copyToClipboard("RPC Backup", json, context)
            ToastUtil.makeText("已备份到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            ToastUtil.makeText("备份失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun tryRestoreFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        if (!text.trim().startsWith("[")) {
            ToastUtil.makeText(context, "剪贴板不是数组格式", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val list = RpcDebugCodec.decodeList(text)
            _dialogState.value = RpcDialogState.RestoreConfirm(list)
        } catch (_: Exception) {
            ToastUtil.makeText(context, "解析失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun confirmRestore(newItems: List<RpcDebugEntity>) {
        // 补全 ID
        newItems.forEach { if (it.id.isEmpty()) it.id = System.currentTimeMillis().toString() }
        _items.value = newItems
        saveItems()
        dismissDialog()
        ToastUtil.makeText("恢复成功", Toast.LENGTH_SHORT).show()
    }

    fun loadDefaultItems() {
        val defaultList = listOf(
            RpcDebugEntity(
                name = "雇佣黄金鸡",
                method = "com.alipay.antfarm.hireAnimal",
                requestData = io.github.magisk317.sesame.util.JsonUtil.toNode("[{\"hireActionType\":\"HIRE_IN_SELF_FARM\",\"sceneCode\":\"ANTFARM\"}]"),
                description = "这是一个雇佣黄金鸡的操作,可以让你雇佣一个黄金的鸡"
            )
        )
        // 简单合并逻辑：略
        _items.value = defaultList
        saveItems()
    }

    fun shareItem(item: RpcDebugEntity, context: Context) {
        try {
            val map = mapOf(
                "Name" to item.name,
                "Description" to item.description, // 分享出去
                "methodName" to item.method,
                "requestData" to item.requestData
            )
            val json = JsonUtil.formatJson(map, true)
            copyToClipboard("RPC Item", json, context)
            ToastUtil.makeText("已复制完整配置", 0).show()
        } catch (_: Exception) {
        }
    }

    private fun copyToClipboard(label: String, text: String, context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    fun parseJsonFields(json: String): RpcDebugItemRaw {
        StructuredLog.d("RpcDebug", "尝试解析 JSON: $json")
        return try {
            RpcDebugCodec.parseFields(json)
        } catch (e: Exception) {
            StructuredLog.e("RpcDebug", "JSON 解析失败", e)
            RpcDebugItemRaw("", "", null, "")
        }
    }

    fun formatJsonFromRaw(data: Any): String {
        return try {
            JsonUtil.formatJson(data, true)
        } catch (e: Exception) {
            "{}"
        }
    }

    /**
     * 尝试格式化 JSON 字符串
     * @return 格式化后的 JSON，如果解析失败返回 null
     */
    fun tryFormatJson(jsonStr: String): String? {
        if (jsonStr.isBlank()) return null
        return try {
            // 1. 先解析成通用对象 (Map 或 List)
            RpcDebugCodec.format(jsonStr)
        } catch (e: Exception) {
            // 解析失败（格式错误），返回 null 让 UI 提示
            null
        }
    }
}
