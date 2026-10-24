package io.github.magisk317.sesame.ui.extension

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.net.toUri
import io.github.magisk317.sesame.entity.UserEntity
import io.github.magisk317.sesame.ui.ComposeSettingsActivity
import io.github.magisk317.sesame.util.Detector
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.ToastUtil

/**
 * Open [url] externally.
 * Align with xinyi-relay BrowserUtils: Custom Tabs first, then ACTION_VIEW.
 * Telegram invite `t.me/+...` is normalized to `%2B` so path parsing stays valid.
 */
fun Context.openUrl(url: String) {
    val normalized = normalizeExternalUrl(url)
    val uri = normalized.toUri()
    StructuredLog.record("openUrl", "open $normalized")

    val customTabsPackage = resolveCustomTabsPackage()
    val customTabsOk = runCatching {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (customTabsPackage != null) {
            customTabsIntent.intent.setPackage(customTabsPackage)
        }
        customTabsIntent.launchUrl(this, uri)
    }.onFailure {
        StructuredLog.printStackTrace("openUrl/customTabs", it)
    }.isSuccess
    if (customTabsOk) return

    runCatching {
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(viewIntent, "打开链接").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        StructuredLog.printStackTrace("openUrl/view", it)
        Toast.makeText(this, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
    }
}

private fun normalizeExternalUrl(url: String): String {
    // Keep invite hash intact; only encode the leading + in the path.
    return if (url.startsWith("https://t.me/+")) {
        "https://t.me/%2B" + url.removePrefix("https://t.me/+")
    } else {
        url
    }
}

private fun Context.resolveCustomTabsPackage(): String? {
    val httpIntent = Intent(Intent.ACTION_VIEW, "https://t.me".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val activities = packageManager.queryIntentActivities(httpIntent, 0)
    val supporting = activities.mapNotNull { resolve ->
        val pkg = resolve.activityInfo?.packageName ?: return@mapNotNull null
        val serviceIntent = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(pkg)
        if (packageManager.resolveService(serviceIntent, 0) != null) pkg else null
    }
    return when {
        "com.android.chrome" in supporting -> "com.android.chrome"
        "com.chrome.beta" in supporting -> "com.chrome.beta"
        "com.chrome.dev" in supporting -> "com.chrome.dev"
        supporting.isNotEmpty() -> supporting.first()
        else -> null
    }
}

/** Error-log access no longer has a second, hard-coded access gate. */
fun Context.executeWithVerification(action: () -> Unit) = action()



fun Context.performNavigationToSettings(user: UserEntity) {
    if (Detector.loadLibrary("checker")) {
        StructuredLog.record("载入用户配置 ${user.showName}")
        try {
            val intent = Intent(this, ComposeSettingsActivity::class.java).apply {
                putExtra("userId", user.userId)
                putExtra("userName", user.showName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.showToast(this, "无法启动设置页面: ${e.message}")
        }
    } else {
        Detector.tips(this, "缺少必要依赖！")
    }
}
