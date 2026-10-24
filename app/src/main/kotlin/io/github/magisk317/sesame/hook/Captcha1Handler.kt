package io.github.magisk317.sesame.hook

import android.app.Activity
import io.github.magisk317.sesame.hook.simple.SimplePageManager
import io.github.magisk317.sesame.hook.simple.SimpleViewImage


/**
 * 验证码处理器 - 处理目标应用验证码页面
 * 使用精简版框架自动识别和处理各种验证码场景
 */
class Captcha1Handler : BaseCaptchaHandler(), SimplePageManager.ActivityFocusHandler {

    /**
     * 获取滑动路径配置 key
     */
    override fun getSlidePathKey(): String {
        return "slide_path_Sheng_Huo"
    }


}
