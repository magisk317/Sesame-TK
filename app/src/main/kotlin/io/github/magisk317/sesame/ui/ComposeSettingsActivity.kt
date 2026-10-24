package io.github.magisk317.sesame.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.sesame.data.General
import io.github.magisk317.sesame.data.Config
import io.github.magisk317.sesame.entity.AlipayUser
import io.github.magisk317.sesame.entity.MapperEntity
import io.github.magisk317.sesame.model.ModelConfig
import io.github.magisk317.sesame.model.SelectModelFieldFunc
import io.github.magisk317.sesame.ui.extension.WatermarkLayer
import io.github.magisk317.sesame.ui.screen.ComposeSettingsScreen
import io.github.magisk317.sesame.ui.screen.components.ConfirmActionDialog
import io.github.magisk317.sesame.ui.screen.components.SelectEntityDialog
import io.github.magisk317.sesame.ui.screen.components.SelectListMode
import io.github.magisk317.sesame.ui.theme.AppTheme
import io.github.magisk317.sesame.ui.theme.ThemeManager
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.LanguageUtil
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.PortUtil
import io.github.magisk317.sesame.util.StringUtil
import io.github.magisk317.sesame.util.ToastUtil
import io.github.magisk317.uikit.shell.BaseActivity as UiKitBaseActivity
import java.util.ArrayList

/** Current Compose + magisk-ui-kit settings host. */
class ComposeSettingsActivity : UiKitBaseActivity() {
    private var userId: String? = null
    private var userName: String? = null

    private var modelConfigs by mutableStateOf<List<ModelConfig>>(emptyList())
    private var loadError by mutableStateOf<String?>(null)
    private var showDeleteConfirm by mutableStateOf(false)
    private var showOneWayFriends by mutableStateOf(false)

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            PortUtil.handleExport(this, result.data!!.data, userId)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            PortUtil.handleImport(this, result.data!!.data, userId)
            bootstrapModels()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent?.getStringExtra("userId")
        userName = intent?.getStringExtra("userName")

        LanguageUtil.setLocale(this)
        bootstrapModels()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    saveAndMaybeFinish(finishAfterSave = true)
                }
            },
        )

        setContent {
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
            AppTheme(dynamicColor = isDynamicColor) {
                WatermarkLayer {
                    ComposeSettingsScreen(
                        userId = userId,
                        userName = userName,
                        modelConfigs = modelConfigs,
                        loadError = loadError,
                        onBack = { saveAndMaybeFinish(finishAfterSave = true) },
                        onSave = { saveAndMaybeFinish(finishAfterSave = false) },
                        onChanged = { saveImmediately() },
                        onExport = {
                            val exportIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                putExtra(
                                    Intent.EXTRA_TITLE,
                                    "[${userName ?: "default"}]-config.json",
                                )
                            }
                            exportLauncher.launch(exportIntent)
                        },
                        onImport = {
                            val importIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            importLauncher.launch(importIntent)
                        },
                        onDeleteConfig = { showDeleteConfirm = true },
                        onShowOneWayFriends = { showOneWayFriends = true },
                        onCopyUserId = {
                            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("userId", userId.orEmpty()))
                            ToastUtil.showToastWithDelay(this, "复制成功！", 100)
                        },
                    )

                    if (showDeleteConfirm) {
                        ConfirmActionDialog(
                            title = "警告",
                            message = "确认删除该配置？",
                            confirmText = "删除",
                            onConfirm = { deleteConfig() },
                            onDismiss = { showDeleteConfirm = false },
                        )
                    }

                    if (showOneWayFriends) {
                        val friends: List<MapperEntity> = ArrayList(AlipayUser.getList { user -> user.friendStatus != 1 })
                        SelectEntityDialog(
                            title = "单向好友列表",
                            entities = friends,
                            selectFunc = SelectModelFieldFunc.newMapInstance(),
                            mode = SelectListMode.SHOW,
                            hasCount = false,
                            onDismiss = { showOneWayFriends = false },
                            onChanged = {},
                        )
                    }
                }
            }
        }
    }

    private fun bootstrapModels() {
        try {
            SettingsSession.load(userId)
            modelConfigs = ArrayList(io.github.magisk317.sesame.model.Model.getModelConfigMap().values)
            loadError = null
        } catch (t: Throwable) {
            StructuredLog.error(TAG, "bootstrapModels failed: ${t.message}")
            StructuredLog.printStackTrace(TAG, t)
            loadError = t.message ?: t.javaClass.simpleName
            modelConfigs = emptyList()
        }
    }

    private fun deleteConfig() {
        if (Files.deleteConfig(userId)) {
            ToastUtil.makeText(this, "配置删除成功", android.widget.Toast.LENGTH_SHORT).show()
            finish()
        } else {
            ToastUtil.makeText(this, "配置删除失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndMaybeFinish(finishAfterSave: Boolean) {
        try {
            val wasModified = Config.isModify(userId)
            if (Config.save(userId, true)) {
                ToastUtil.showToastWithDelay(this, "保存成功！", 100)
                if (wasModified && !StringUtil.isEmpty(userId)) {
                    val intent = Intent("com.eg.android.AlipayGphone.sesame.restart").apply {
                        setPackage(General.PACKAGE_NAME)
                        putExtra("userId", userId)
                    }
                    sendBroadcast(intent)
                }
            }
            SettingsSession.saveMaps(userId)
            if (finishAfterSave) {
                finish()
            }
        } catch (t: Throwable) {
            StructuredLog.printStackTrace(TAG, t)
            StructuredLog.error(TAG, "save failed: ${t.message}")
            ToastUtil.showToastWithDelay(this, "保存失败！", 100)
        }
    }

    /** Persist field changes immediately; the normal back/save path still writes once more. */
    private fun saveImmediately() {
        runCatching {
            Config.save(userId, false)
            SettingsSession.saveMaps(userId)
        }.onFailure {
            StructuredLog.printStackTrace(TAG, "实时保存失败", it)
        }
    }

    companion object {
        private const val TAG = "ComposeSettingsActivity"
    }
}
