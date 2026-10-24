package io.github.magisk317.sesame.hook

import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.sesame.util.StructuredLog

/**
 * 目标应用滑块验证码Hook工具类（UI层拦截）
 * 
 * 核心策略：
 * Hook CaptchaDialog.show() - 阻止验证码对话框显示（UI层拦截）
 * 
 * 独立开关：
 * - enableCaptchaUIHook：UI层拦截开关（阻止对话框显示）
 * 
 * 使用方式：
 * CaptchaHook.setupHook(classLoader)
 * CaptchaHook.updateHooks(enableUI)  // 动态更新开关状态
 * 
 * @author ghostxx
 * @since 2025-10-23
 */
object CaptchaHook {
    private const val TAG = "CaptchaHook"

    
    /**
     * 验证码对话框类名
     */
    private const val CLASS_CAPTCHA_DIALOG = "com.alipay.rdssecuritysdk.v3.captcha.view.CaptchaDialog"
    
    /**
     * UI层Hook卸载器（用于动态控制）
     */
    private var uiHookUnhook: io.github.magisk317.xposed.HookHandle? = null
    
    /**
     * 保存ClassLoader供后续使用
     */
    private var savedClassLoader: ClassLoader? = null

    /**
     * 初始化Hook系统
     * 
     * @param classLoader 目标应用的ClassLoader
     */
    fun setupHook(classLoader: ClassLoader) {
        savedClassLoader = classLoader
        StructuredLog.record(TAG, "验证码Hook系统初始化完成")
        StructuredLog.record(TAG, "⚠️ Hook配置将在配置文件加载后同步")
        
        // 注意：此时配置文件还未加载，不能立即应用Hook
        // 实际的Hook应用会在BaseModel.boot()中进行
    }
    
    /**
     * 动态更新Hook开关状态
     * 
     * @param enableUI 是否启用UI层拦截
     */
    fun updateHooks(enableUI: Boolean) {
        val classLoader = savedClassLoader
        if (classLoader == null) {
            StructuredLog.error(TAG, "❌ ClassLoader未初始化，请先调用setupHook()")
            return
        }
        
        StructuredLog.record(TAG, "📝 更新验证码Hook状态:")
        StructuredLog.record(TAG, "  UI层拦截: ${if (enableUI) "✓ 开启" else "⛔ 关闭"}")
        
        // 先卸载所有现有Hook
        unhookAll()
        
        // 根据开关状态重新Hook
        if (enableUI) {
            StructuredLog.record(TAG, "  🔧 设置UI层拦截...")
            uiHookUnhook = hookCaptchaDialogShow(classLoader)
        } else {
            StructuredLog.record(TAG, "  ⚠️ 验证码拦截已关闭")
        }
        
        StructuredLog.record(TAG, "验证码Hook更新完成 ✓")
    }
    
    /**
     * 卸载所有Hook
     */
    private fun unhookAll() {
        uiHookUnhook?.unhook()
        uiHookUnhook = null
    }

    /**
     * 第一层拦截：阻止验证码对话框显示
     * 
     * Hook点: CaptchaDialog.show()
     * 作用: 阻止对话框显示，用户看不到验证码
     * 
     * @param classLoader 类加载器
     * @return Hook卸载器，失败时返回null
     */
    private fun hookCaptchaDialogShow(classLoader: ClassLoader): io.github.magisk317.xposed.HookHandle? {
        return try {
            val captchaDialogClass = HookHelpers.findClass(CLASS_CAPTCHA_DIALOG, classLoader)
            
            val unhook = SesameHooks.findAndHookMethod(
                captchaDialogClass,
                "show",
                object : MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // 阻止验证码对话框显示
                        param.result = null
                        StructuredLog.record(TAG, "✓ [UI层拦截] 已阻止验证码对话框显示")
                        StructuredLog.record(TAG, "  对话框: ${param.thisObject?.javaClass?.simpleName}")
                    }
                }
            )
            
            StructuredLog.record(TAG, "✓ Hook CaptchaDialog.show() 成功")
            unhook
        } catch (e: Throwable) {
            StructuredLog.error(TAG, "❌ Hook CaptchaDialog.show() 失败")
            StructuredLog.printStackTrace(TAG, e)
            null
        }
    }
}

