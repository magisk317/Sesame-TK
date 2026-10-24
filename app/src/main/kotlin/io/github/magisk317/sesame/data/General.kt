package io.github.magisk317.sesame.data

/**
 * 类实用工具类。
 * 提供了一些常量，这些常量代表了项目中使用的一些关键类的完整名称。
 */
object General {
    /**
     * 目标应用客户端的包名。
     */
    const val PACKAGE_NAME: String = "com.eg.android.AlipayGphone"

    /**
     * 当前使用的服务类名。
     */
    const val CURRENT_USING_SERVICE: String = "com.alipay.dexaop.power.RuntimePowerService"

    /**
     * 当前使用的活动（Activity）类名。
     */
    const val CURRENT_USING_ACTIVITY: String = "com.eg.android.AlipayGphone.AlipayLogin"

    const val CURRENT_USING_SCHEME_ACTIVITY = "com.alipay.mobile.framework.service.common.SchemeStartActivity"

    /**
     * JSON对象的类名。
     */
    const val JSON_OBJECT_NAME: String = "com.alibaba.fastjson.JSONObject"

    /**
     * H5页面的类名。
     */
    const val H5PAGE_NAME: String = "com.alipay.mobile.h5container.api.H5Page"
    const val MODULE_PACKAGE_NAME: String = "io.github.magisk317.sesame"
    // Source package remains stable while the install/application id is migrated.
    const val MODULE_PACKAGE_UI_ICON: String = "io.github.magisk317.sesame.ui.MainActivityAlias"
}
