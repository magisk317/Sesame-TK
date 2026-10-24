package io.github.magisk317.sesame.hook.internal

import android.content.Context
import android.content.Intent
import io.github.magisk317.sesame.entity.AlipayVersion
import io.github.magisk317.sesame.hook.VersionHook
import io.github.magisk317.sesame.hook.simple.SimplePageManager
import io.github.magisk317.sesame.hook.simple.SimplePageManager.ActivityFocusHandler
import io.github.magisk317.sesame.hook.Captcha1Handler
import io.github.magisk317.sesame.hook.Captcha2Handler
import io.github.magisk317.sesame.util.AssetUtil.checkerDestFile
import io.github.magisk317.sesame.util.AssetUtil.copyStorageSoFileToPrivateDir
import io.github.magisk317.sesame.util.AssetUtil.dexkitDestFile
import io.github.magisk317.sesame.util.Detector
import io.github.magisk317.sesame.util.StructuredLog
import android.annotation.SuppressLint
import java.io.File

/**
 * 从 ApplicationHook 抽出的叶子簇：原生库 + DexKit + 版本检测 + 滑块页面注册。
 * 仅依赖 appContext / classLoader / alipayVersion，无会话态耦合。
 * alipayVersion 作为可变状态由调用方持有并传入，结果写回 ApplicationHook.alipayVersion。
 */
internal object NativeDexKitLoader {
    private const val TAG = "NativeDexKitLoader"

    @JvmStatic
    fun initVersionInfo(context: Context, packageName: String?): AlipayVersion {
        val version = if (VersionHook.hasVersion()) {
            VersionHook.getCapturedVersion() ?: AlipayVersion("")
        } else {
            try {
                val pInfo = context.packageManager.getPackageInfo(packageName!!, 0)
                AlipayVersion(pInfo.versionName.toString())
            } catch (_: Exception) {
                AlipayVersion("")
            }
        }
        StructuredLog.record(TAG, "📦 目标应用版本(Hook): $version")
        return version
    }

    fun loadLibs(context: Context) {
        loadNativeLibs(context, checkerDestFile)
        loadNativeLibs(context, dexkitDestFile)
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun loadNativeLibs(context: Context, soFile: File) {
        try {
            val finalSoFile = copyStorageSoFileToPrivateDir(context, soFile)
            if (finalSoFile != null) {
                System.load(finalSoFile.absolutePath)
            } else {
                Detector.loadLibrary(soFile.getName().replace(".so", "").replace("lib", ""))
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "载入so库失败: " + soFile.getName(), e)
        }
    }

    /**
     * 滑块验证 hook 注册。alipayVersion 由调用方传入（应取自 ApplicationHook.alipayVersion）。
     */
    fun initSimplePageManager(classLoader: ClassLoader, alipayVersion: AlipayVersion) {
        if (shouldEnableSimplePageManager(alipayVersion)) {
            SimplePageManager.enableWindowMonitoring(classLoader)
            SimplePageManager.addHandler("com.alipay.mobile.nebulax.xriver.activity.XRiverActivity", Captcha1Handler() as ActivityFocusHandler)
            SimplePageManager.addHandler("com.eg.android.AlipayGphone.AlipayLogin", Captcha2Handler() as ActivityFocusHandler)
        }
    }

    /**
     * 检查目标应用版本是否需要启用 SimplePageManager 功能。
     * @return true 表示版本低于等于 10.6.58.99999，需要启用；false 表示不需要
     */
    @JvmStatic
    fun shouldEnableSimplePageManager(alipayVersion: AlipayVersion): Boolean {
        if (!VersionHook.hasVersion() || alipayVersion.toString().isEmpty()) {
            return false
        }

        val maxSupported = AlipayVersion("10.6.58.99999")
        if (alipayVersion > maxSupported) {
            // 只有在不支持时才打印警告
            StructuredLog.record(TAG, "目标应用版本 $alipayVersion 高于 10.6.58，不支持自动过滑块验证")
            return false
        }

        return true
    }
}
