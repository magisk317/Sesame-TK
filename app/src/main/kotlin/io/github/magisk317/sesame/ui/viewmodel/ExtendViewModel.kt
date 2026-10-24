package io.github.magisk317.sesame.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import io.github.magisk317.sesame.BuildConfig
import io.github.magisk317.sesame.R
import io.github.magisk317.sesame.model.CustomSettings
import io.github.magisk317.sesame.model.CustomSettingsDto
import io.github.magisk317.sesame.model.OnceDailyMode
import io.github.magisk317.sesame.model.OnceDailyTimeInput
import io.github.magisk317.sesame.util.TaskRuntimeStore
import io.github.magisk317.sesame.util.Detector
import io.github.magisk317.sesame.util.FansirsqiUtil
import io.github.magisk317.sesame.util.PhotoCache
import io.github.magisk317.sesame.util.PhotoCacheEntry
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ToastUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

// 定义菜单项数据类
data class MenuItem(
    val title: String,
    val onClick: () -> Unit
)

// 定义各种弹窗类型
sealed class ExtendDialog {
    data object None : ExtendDialog()

    // 清空图片确认框
    data class ClearPhotoConfirm(val count: Int) : ExtendDialog()

    // 写入光盘测试框
    data class WritePhotoTest(val message: String) : ExtendDialog()

    // 通用输入框 (用于获取任务运行态 / BaseUrl)
    data class InputDialog(
        val title: String,
        val initialValue: String = "",
        val onConfirm: (String) -> Unit
    ) : ExtendDialog()

    data class OnceDailyAccountPicker(
        val accounts: List<CustomSettings.Account>,
    ) : ExtendDialog()

    data class OnceDailySettings(
        val page: OnceDailySettingsPage,
        val draft: OnceDailySettingsDraft,
    ) : ExtendDialog()
}

enum class OnceDailySettingsPage {
    OVERVIEW,
    BLACKLIST,
    FULL_RUN_TIMES,
}

data class OnceDailySettingsDraft(
    val account: CustomSettings.Account,
    val mode: OnceDailyMode,
    val blacklistedModuleIds: Set<String>,
    val fullRunTimes: List<String>,
)

class ExtendViewModel : ViewModel() {
    // 列表项数据
    val menuItems = mutableStateListOf<MenuItem>()

    // 当前显示的弹窗状态
    private val _currentDialog = MutableStateFlow<ExtendDialog>(ExtendDialog.None)
    val currentDialog = _currentDialog.asStateFlow()

    val onceDailyModules = CustomSettings.getOnceDailyModules()
    private var onceDailyAccounts: List<CustomSettings.Account> = emptyList()

    // 初始化数据
    fun loadData(context: Context) {
        menuItems.clear()

        // 1. 广播类功能
        val debugTips = context.getString(R.string.debug_tips)

        fun addBroadcastItem(titleResId: Int, type: String) {
            menuItems.add(MenuItem(context.getString(titleResId)) {
                sendItemsBroadcast(context, type)
                ToastUtil.makeText(context, debugTips, 0).show()
            })
        }

        addBroadcastItem(R.string.query_the_remaining_amount_of_saplings, "getTreeItems")
        addBroadcastItem(R.string.search_for_new_items_on_saplings, "getNewTreeItems")
        addBroadcastItem(R.string.search_for_unlocked_regions, "queryAreaTrees")
        addBroadcastItem(R.string.search_for_unlocked_items, "getUnlockTreeItems")

        // 2. 清空图片
        menuItems.add(MenuItem(context.getString(R.string.clear_photo)) {
            val currentCount = PhotoCache.read().size
            _currentDialog.value = ExtendDialog.ClearPhotoConfirm(currentCount)
        })

        // 3. 每日单次运行
        menuItems.add(MenuItem("每日单次运行设置") {
            openOnceDailySettings(context)
        })

        // 4. Debug 功能
        if (BuildConfig.DEBUG) {

            menuItems.add(MenuItem("写入光盘") {
                _currentDialog.value = ExtendDialog.WritePhotoTest("xxxx")
            })

            menuItems.add(MenuItem("获取任务运行态字段") {
                _currentDialog.value = ExtendDialog.InputDialog("输入字段Key") { key ->
                    handleGetTaskRuntime(context, key)
                }
            })

            menuItems.add(MenuItem("获取BaseUrl") {
                _currentDialog.value = ExtendDialog.InputDialog("请输入Key") { input ->
                    handleGetBaseUrl(context, input)
                }
            })

            menuItems.add(MenuItem("TestShow") {
                ToastUtil.showToast(context, "shizuku:"+isShizukuReady().toString())
            })
        }
    }

    // --- 业务逻辑 ---

    fun dismissDialog() {
        _currentDialog.value = ExtendDialog.None
    }

    fun openOnceDailySettings(context: Context) {
        onceDailyAccounts = CustomSettings.getAccounts()
        if (onceDailyAccounts.isEmpty()) {
            ToastUtil.showToast(context, "未发现任何用户配置")
            return
        }
        _currentDialog.value = ExtendDialog.OnceDailyAccountPicker(onceDailyAccounts)
    }

    fun selectOnceDailyAccount(account: CustomSettings.Account) {
        val settings = CustomSettings.read(account.userId)
        _currentDialog.value = ExtendDialog.OnceDailySettings(
            page = OnceDailySettingsPage.OVERVIEW,
            draft = OnceDailySettingsDraft(
                account = account,
                mode = OnceDailyMode.from(settings),
                blacklistedModuleIds = LinkedHashSet(settings.onlyOnceDailyList),
                fullRunTimes = settings.autoHandleOnceDailyTimes.toList(),
            ),
        )
    }

    fun showOnceDailyAccounts() {
        _currentDialog.value = if (onceDailyAccounts.isEmpty()) {
            ExtendDialog.None
        } else {
            ExtendDialog.OnceDailyAccountPicker(onceDailyAccounts)
        }
    }

    fun showOnceDailyOverview() {
        updateOnceDailySettings { it.copy(page = OnceDailySettingsPage.OVERVIEW) }
    }

    fun showOnceDailyBlacklist() {
        updateOnceDailySettings { it.copy(page = OnceDailySettingsPage.BLACKLIST) }
    }

    fun showOnceDailyFullRunTimes() {
        updateOnceDailySettings { it.copy(page = OnceDailySettingsPage.FULL_RUN_TIMES) }
    }

    fun setOnceDailyMode(mode: OnceDailyMode) {
        updateOnceDailyDraft { it.copy(mode = mode) }
    }

    fun setOnceDailyBlacklist(moduleIds: Set<String>) {
        updateOnceDailyDraft { it.copy(blacklistedModuleIds = LinkedHashSet(moduleIds)) }
    }

    fun setOnceDailyFullRunTimes(raw: String) {
        val parsed = OnceDailyTimeInput.parse(raw) ?: return
        updateOnceDailyDraft { it.copy(fullRunTimes = parsed) }
    }

    fun saveOnceDailySettings(context: Context) {
        val dialog = _currentDialog.value as? ExtendDialog.OnceDailySettings ?: return
        val draft = dialog.draft
        val saved = CustomSettings.save(
            draft.account.userId,
            CustomSettingsDto(
                onlyOnceDaily = draft.mode.onlyOnceDaily,
                onlyOnceDailyList = LinkedHashSet(draft.blacklistedModuleIds),
                autoHandleOnceDaily = draft.mode.autoHandleOnceDaily,
                autoHandleOnceDailyTimes = draft.fullRunTimes.toList(),
            ),
        )
        ToastUtil.showToast(context, if (saved) "每日单次运行设置已保存" else "保存失败")
    }

    private fun updateOnceDailyDraft(transform: (OnceDailySettingsDraft) -> OnceDailySettingsDraft) {
        updateOnceDailySettings { dialog -> dialog.copy(draft = transform(dialog.draft)) }
    }

    private fun updateOnceDailySettings(
        transform: (ExtendDialog.OnceDailySettings) -> ExtendDialog.OnceDailySettings,
    ) {
        val current = _currentDialog.value as? ExtendDialog.OnceDailySettings ?: return
        _currentDialog.value = transform(current)
    }

    fun clearPhotos(context: Context) {
        val cleared = PhotoCache.clear()
        ToastUtil.showToast(context, if (cleared) "光盘行动图片清空成功" else "光盘行动图片清空失败")
        dismissDialog()
    }

    fun writePhotoTest(context: Context) {
        val newPhotoEntry = PhotoCacheEntry(
            before = "before${FansirsqiUtil.getRandomString(10)}",
            after = "after${FansirsqiUtil.getRandomString(10)}",
        )
        val existingPhotos = PhotoCache.read().toMutableList()
        existingPhotos.add(newPhotoEntry)
        val written = PhotoCache.write(existingPhotos)
        ToastUtil.showToast(context, if (written) "写入成功$newPhotoEntry" else "写入失败$newPhotoEntry")
        dismissDialog()
    }

    private fun handleGetTaskRuntime(context: Context, key: String) {
        val value: Any = try {
            TaskRuntimeStore.getRaw(key) ?: ""
        } catch (_: Exception) {
            ""
        }
        ToastUtil.showToast(context, "$value \n输入内容: $key")
        dismissDialog()
    }

    private fun handleGetBaseUrl(context: Context, input: String) {
        val key = input.toIntOrNull(16)
        if (key != null) {
            val output = Detector.getApiUrl(key)
            ToastUtil.showToast(context, "$output \n输入内容: $input")
        } else {
            ToastUtil.showToast(context, "输入内容: $input , 请输入正确的十六进制数字")
        }
        dismissDialog()
    }

    private fun sendItemsBroadcast(context: Context, type: String) {
        val intent = Intent("com.eg.android.AlipayGphone.sesame.rpctest").apply {
            setPackage(io.github.magisk317.sesame.data.General.PACKAGE_NAME)
            putExtra("method", "")
            putExtra("data", "")
            putExtra("type", type)
        }
        context.sendBroadcast(intent)
        StructuredLog.debug("ExtendViewModel", "扩展工具主动调用广播查询📢：$type")
    }

    private fun isShizukuReady(): Boolean {
        return try {
            val isBinderAlive = Shizuku.pingBinder()
            val hasPermission = if (isBinderAlive) Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED else false
            isBinderAlive && hasPermission
        } catch (_: Exception) {
            false
        }
    }
}
