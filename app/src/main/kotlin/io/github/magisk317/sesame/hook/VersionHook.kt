package io.github.magisk317.sesame.hook

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.sesame.data.General
import io.github.magisk317.sesame.entity.AlipayVersion
import io.github.magisk317.sesame.util.StructuredLog.printStackTrace
import io.github.magisk317.sesame.util.StructuredLog.record
import kotlin.concurrent.Volatile

/**
 * 版本号 Hook 工具类
 * 用于在应用启动早期拦截并获取目标应用版本信息
 */
object VersionHook {
    private const val TAG = "VersionHook"

    /**
     * -- GETTER --
     * 获取已捕获的版本信息
     *
     */
    // 缓存捕获的版本信息
    @Volatile
    private var capturedVersion: AlipayVersion? = null

    @Volatile
    private var hookInstalled = false

    /**
     * 在 loadPackage 阶段尽早安装 Hook
     *
     * @param classLoader 类加载器
     */
    fun installHook(classLoader: ClassLoader?) {
        // 防止重复安装
        if (hookInstalled) {
            record(TAG, "⚠️ Hook 已安装,跳过")
            return
        }

        try {
            SesameHooks.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                classLoader,
                "getPackageInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val packageInfo = param.result as PackageInfo?

                            // 只处理目标应用的包信息
                            if (packageInfo != null &&
                                General.PACKAGE_NAME == packageInfo.packageName
                            ) {
                                val versionName = packageInfo.versionName
                                val longVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                                val versionCode = (longVersionCode).toInt()

                                // 只在第一次捕获时记录日志
                                if (capturedVersion == null && versionName != null) {
                                    capturedVersion = AlipayVersion(versionName)
                                    record(
                                        TAG, "✓ 捕获目标应用版本: " + versionName +
                                                " (code: " + versionCode +
                                                ", longCode: " + longVersionCode + ")"
                                    )
                                }
                            }
                        } catch (t: Throwable) {
                            // 静默处理异常,避免影响应用正常运行
                            printStackTrace(TAG, t)
                        }
                    }
                }
            )

            hookInstalled = true
            record(TAG, "✓ 版本号 Hook 安装成功")
        } catch (t: Throwable) {
            record(TAG, "❌ 安装版本号 Hook 失败")
            printStackTrace(TAG, t)
        }
    }

    /**
     * 检查是否已成功捕获版本号
     *
     * @return true: 已捕获, false: 未捕获
     */
    fun hasVersion(): Boolean {
        return capturedVersion != null
    }

    /**
     * 获取已捕获的版本信息
     *
     * @return 已捕获的版本信息，如果未捕获则返回 null
     */
    fun getCapturedVersion(): AlipayVersion? {
        return capturedVersion
    }

    /**
     * 重置捕获状态 (用于测试或重新初始化)
     */
    fun reset() {
        capturedVersion = null
        hookInstalled = false
        record(TAG, "🔄 版本号 Hook 状态已重置")
    }
}
