package io.github.magisk317.sesame.xp

import io.github.magisk317.sesame.util.StructuredLog as Log
import io.github.magisk317.sesame.data.General
import io.github.magisk317.sesame.hook.ApplicationHook
import io.github.magisk317.sesame.service.RemotePrefsStore
import io.github.magisk317.sesame.hook.XposedEnv
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.BaseLibXposedEntry
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.LibXposedHookApi
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.XposedRuntime

/**
 * LibXposed 102 entry — same wiring style as XposedSmsCode / xinyi-relay.
 */
class LibXposedEntry : BaseLibXposedEntry {

    @Suppress("unused", "UnusedParameter")
    constructor(xposed: XposedInterface, loadedParam: ModuleLoadedParam) : super(xposed, loadedParam)

    constructor() : super()

    override val logTag: String = TAG

    private val applicationHook = ApplicationHook()

    override val hooks: List<BaseHook> = listOf(
        object : BaseHook() {
            override fun onLoadPackage(param: LoadParam) {
                if (param.packageName != General.PACKAGE_NAME) return
                XposedEnv.classLoader = param.classLoader
                XposedEnv.packageName = param.packageName
                XposedEnv.processName = param.processName
                applicationHook.loadPackage(param)
            }
        },
    )

    override fun installModuleRuntime(module: XposedModule, hookApi: LibXposedHookApi) {
        XposedRuntime.install(module, hookIdPrefix = "sesame")
        HookEnv.init(hookApi)
        applicationHook.xposedInterface = module
        RemotePrefsStore.installProvider { module.getRemotePreferences(RemotePrefsStore.GROUP) }
        Log.i(TAG, "module runtime installed api=${module.apiVersion} framework=${module.frameworkName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName == General.PACKAGE_NAME) {
            runCatching {
                XposedEnv.appInfo = param.applicationInfo
            }
        }
        super.onPackageLoaded(param)
    }

    private companion object {
        private const val TAG = "SesameLibXposedEntry"
    }
}
