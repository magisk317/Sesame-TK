package io.github.magisk317.sesame.util

import android.content.Context
import android.content.pm.PackageManager
import io.github.magisk317.sesame.BuildConfig

object Detector {
    private const val TAG = "Detector"


    private fun init(context: Context) = fansirsqi.xposed.sesame.util.Detector.init(context)
    fun tips(context: Context, message: String?) =
        fansirsqi.xposed.sesame.util.Detector.tips(context, message)
    fun isEmbeddedNative(context: Context): Boolean =
        fansirsqi.xposed.sesame.util.Detector.isEmbeddedNative(context)
    fun dangerous(context: Context) = fansirsqi.xposed.sesame.util.Detector.dangerous(context)

    /**
     * 生成wua
     */
    fun genWua(): String = ""
    fun loadLibraryWithContextNative(context: Context, libraryName: String): Boolean =
        fansirsqi.xposed.sesame.util.Detector.loadLibraryWithContextNative(context, libraryName)
    private fun getApiUrlWithKey(key: Int): String =
        fansirsqi.xposed.sesame.util.Detector.getApiUrlWithKey(key)


    fun loadLibrary(libraryName: String): Boolean {
        try {
            System.loadLibrary(libraryName)
            return true
        } catch (e: UnsatisfiedLinkError) {
            StructuredLog.error(TAG, "loadLibrary${e.message}")
            return false
        }
    }

    fun getApiUrl(key: Int): String {
        return if (BuildConfig.DEBUG) {
            getApiUrlWithKey(0x11)
        } else {
            getApiUrlWithKey(key)
        }
    }

    /**
     * 检测是否通过LSPatch运行
     */
    private fun isRunningInLSPatch(context: Context): Boolean {
        try {
            // 检查应用元数据中是否有LSPatch标记
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.containsKey("lspatch") == true
            return appInfo.metaData?.containsKey("lspatch") == true
        } catch (e: Exception) {
            StructuredLog.error(TAG, "检查LSPatch运行环境时出错: ${e.message}")
            return false
        }
    }


    /**
     * 检测模块是否在合法环境中运行
     */
    fun isLegitimateEnvironment(context: Context): Boolean {
        val isRunningInLSPatch = isRunningInLSPatch(context)
        if (!isRunningInLSPatch) {
            return false
        }
        val isEmbedded = isEmbeddedNative(context)
        StructuredLog.record(TAG, "isEmbedded: $isEmbedded")
        return isEmbedded
    }


    fun initDetector(context: Context) {
        try {
            init(context)
        } catch (e: Exception) {
            StructuredLog.error(TAG, "initDetector ${e.message}")
        }
    }

    private fun getApkPath(context: Context, packageName: String): String? {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            StructuredLog.record(TAG, "appInfo.sourceDir: " + appInfo.sourceDir)
            return appInfo.sourceDir
        } catch (_: PackageManager.NameNotFoundException) {
            StructuredLog.record(TAG, "Package not found: $packageName")
            return null
        }
    }

}
