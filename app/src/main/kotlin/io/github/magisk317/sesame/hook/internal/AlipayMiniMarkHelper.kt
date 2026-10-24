package io.github.magisk317.sesame.hook.internal

import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.sesame.util.StructuredLog

/**
 * 支付宝小程序游戏获取alipayminimark
 * 用于调用目标应用的 H5HttpUtils.getAlipayMiniMark 方法
 */
object AlipayMiniMarkHelper {
    private const val TAG = "AlipayMiniMarkHelper"
    private var classLoader: ClassLoader? = null
    private var unavailableLogged = false
    /**
     * 初始化 AlipayMiniMarkHelper
     * @param loader 应用类加载器
     */
    fun init(loader: ClassLoader) {
        classLoader = loader
        unavailableLogged = false
        StructuredLog.record(TAG, "AlipayMiniMarkHelper 初始化完成")
    }
    /**
     * 获取支付宝小程序标记
     * 通过调用 H5HttpUtils.getAlipayMiniMark 方法获取小程序标记
     *
     * @param str 游戏appid
     * @param str2 游戏版本号
     * @return 小程序标记字符串，失败返回空字符串
     */
    fun getAlipayMiniMark(str: String, str2: String): String {
        val loader = classLoader
        if (loader == null) {
            if (!unavailableLogged) {
                unavailableLogged = true
                StructuredLog.record(TAG, "ClassLoader 未初始化，跳过获取 alipayminimark")
            }
            return ""
        }
        return try {
            val h5HttpUtilsClass = HookHelpers.findClass(
                "com.alipay.mobile.nebula.util.H5HttpUtils",
                loader,
            )
            (HookHelpers.callStaticMethod(h5HttpUtilsClass, "getAlipayMiniMark", str, str2) as? String).orEmpty()
        } catch (e: Throwable) {
            if (!unavailableLogged) {
                unavailableLogged = true
                StructuredLog.record(TAG, "当前支付宝版本不支持 alipayminimark，跳过: ${e.javaClass.simpleName}")
            }
            ""
        }
    }

}