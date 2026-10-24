package io.github.magisk317.sesame.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.sesame.data.Config
import io.github.magisk317.sesame.entity.UserEntity
import io.github.magisk317.sesame.service.ActiveUserStore
import io.github.magisk317.sesame.service.ConnectionState
import io.github.magisk317.sesame.service.LsposedServiceManager
import io.github.magisk317.sesame.service.StorageReset
import io.github.magisk317.sesame.ui.screen.DeviceInfoUtil
import io.github.magisk317.sesame.util.AssetUtil
import io.github.magisk317.sesame.util.CommandUtil
import io.github.magisk317.sesame.util.DirectoryWatcher
import io.github.magisk317.sesame.util.FansirsqiUtil
import io.github.magisk317.sesame.util.Files
import io.github.magisk317.sesame.util.IconManager
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.TaskRuntimeStore
import io.github.magisk317.sesame.util.maps.UserMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面 ViewModel
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {



    // --- 内部状态定义 ---
    sealed class ModuleStatus {
        data object Loading : ModuleStatus()
        data object NotActivated : ModuleStatus()
        data class Activated(
            val frameworkName: String,     // 框架名称 (LSPosed, LSPatch...)
            val frameworkVersion: String,  // 版本号 (LSPosed才有，其他可能为空)
            val apiVersion: Int            // API版本
        ) : ModuleStatus()
    }



    companion object {
        const val TAG = "MainViewModel"
    }

    private val _oneWord = MutableStateFlow("正在获取句子...")
    val oneWord: StateFlow<String> = _oneWord.asStateFlow()

    private val _isOneWordLoading = MutableStateFlow(false)
    val isOneWordLoading = _isOneWordLoading.asStateFlow()

    private val _moduleStatus = MutableStateFlow<ModuleStatus>(ModuleStatus.Loading)
    val moduleStatus: StateFlow<ModuleStatus> = _moduleStatus.asStateFlow()

    private val _activeUser = MutableStateFlow<UserEntity?>(null)
    val activeUser: StateFlow<UserEntity?> = _activeUser.asStateFlow()

    private val _userList = MutableStateFlow<List<UserEntity>>(emptyList())
    val userList: StateFlow<List<UserEntity>> = _userList.asStateFlow()

    private val _deviceInfo = MutableStateFlow<Map<String, String>?>(null)
    val deviceInfo = _deviceInfo.asStateFlow()

    // --- 监听器 ---

    // 监听 LSPosed 服务连接 (仅用于更新详细版本信息)
    private val serviceListener: (ConnectionState) -> Unit = { state ->
        if (state is ConnectionState.Connected) {
            StorageReset.flushPendingRemoteReset(getApplication())
            viewModelScope.launch(Dispatchers.IO) {
                Config.migrateFilesToRemote(
                    FansirsqiUtil.getFolderList(Files.CONFIG_DIR.absolutePath),
                )
            }
        }
        refreshModuleFrameworkStatus()
    }


    private var isInitialized = false

    fun initAppLogic() {
        if (isInitialized) return
        isInitialized = true

        viewModelScope.launch(Dispatchers.IO) {
            initEnvironment()
            copyAssets()

            // 加载初始数据
            refreshUserConfigs()
            fetchOneWord()
            // 初始检查状态
            refreshModuleFrameworkStatus()
            refreshActiveUser()
            // 🔥 新增：触发 CommandService 连接
            // 连接成功后，AIDL 回调会自动更新 serviceStatus
            CommandUtil.connect(getApplication())

            // 注册监听
            LsposedServiceManager.addConnectionListener(serviceListener)
            startConfigDirectoryObserver()
        }
    }

    override fun onCleared() {
        CommandUtil.unbind(getApplication())
        LsposedServiceManager.removeConnectionListener(serviceListener)
        super.onCleared()
    }



    /**
     * 刷新模块框架激活状态 — 仅 LSPosed，与 XSC/xinyi 同步。
     */
    private fun refreshModuleFrameworkStatus() {
        val lspState = LsposedServiceManager.connectionState

        if (lspState is ConnectionState.Connected) {
            _moduleStatus.value = ModuleStatus.Activated(
                frameworkName = lspState.service.frameworkName,
                frameworkVersion = lspState.service.frameworkVersion,
                apiVersion = lspState.service.apiVersion
            )
        } else {
            _moduleStatus.value = ModuleStatus.NotActivated
        }
    }

    /**
     * 刷新当前激活用户
     * 从远程用户 ID 和本地用户文件读取
     */
    private fun refreshActiveUser() {
        try {
            val activeUserId = ActiveUserStore.read(getApplication())
            _activeUser.value = activeUserId?.let(UserMap::readSelf)
        } catch (e: Exception) {
            StructuredLog.e(TAG, "Read active user failed", e)
            _activeUser.value = null
        }
    }

    @OptIn(FlowPreview::class)
    private fun startConfigDirectoryObserver() {
        viewModelScope.launch(Dispatchers.IO) {
            DirectoryWatcher.observeDirectoryChanges(Files.CONFIG_DIR)
                .debounce(100)
                .collectLatest {
                    refreshUserConfigs()
                    refreshActiveUser()
                }
        }
    }

    /**
     * 刷新用户配置
     */
    fun refreshUserConfigs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val latestUserIds = FansirsqiUtil.getFolderList(Files.CONFIG_DIR.absolutePath)
                val newList = mutableListOf<UserEntity>()
                for (userId in latestUserIds) {
                    UserMap.loadSelf(userId)
                    UserMap.get(userId)?.let { newList.add(it) }
                }
                _userList.value = newList
            } catch (e: Exception) {
                StructuredLog.e(TAG, "Error reloading user configs", e)
            }
        }
    }


    fun refreshDeviceInfo(context: Context) {
        viewModelScope.launch {
            val info = DeviceInfoUtil.showInfo(context)
            _deviceInfo.value = info
        }
    }

    private fun initEnvironment() {
        try {
            TaskRuntimeStore.initialize(getApplication())
            LsposedServiceManager.init()
        } catch (e: Exception) {
            StructuredLog.e(TAG, "Environment init failed", e)
        }
    }

    private fun copyAssets() {
        try {
            val ctx = getApplication<Application>()
            AssetUtil.copySoFileToStorage(ctx, AssetUtil.checkerDestFile)
            AssetUtil.copySoFileToStorage(ctx, AssetUtil.dexkitDestFile)
        } catch (e: Exception) {
            StructuredLog.e(TAG, "Asset copy error", e)
        }
    }

    fun fetchOneWord() {
        viewModelScope.launch {
            _isOneWordLoading.value = true
            val startTime = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) { FansirsqiUtil.getOneWord() }
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < 2500) delay(500 - elapsedTime)
            _oneWord.value = result
            _isOneWordLoading.value = false
        }
    }

    fun syncIconState(isHidden: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            IconManager.syncIconState(getApplication(), isHidden)
        }
    }
}
