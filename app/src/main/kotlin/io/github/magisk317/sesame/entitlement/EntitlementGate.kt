package io.github.magisk317.sesame.entitlement

import android.content.Context
import com.magisk317.mobile.entitlement.MobileEntitlementKeys
import com.magisk317.mobile.entitlement.MobileEntitlementPublishedState
import io.github.magisk317.sesame.service.RemotePrefsStore
import java.util.concurrent.atomic.AtomicReference

object EntitlementGate {
    private val localState = AtomicReference(MobileEntitlementPublishedState(false, null))

    /** 兼容入口：与 [isAutomationAllowed] 等价。 */
    @Suppress("UNUSED_PARAMETER")
    fun isAllowed(context: Context): Boolean =
        isAutomationAllowed()

    /**
     * Single automation truth shared by the UI process and hooked host processes.
     * 鉴权服务端已下线（mobile-apps-site 0031 迁移，2026-10）：
     * 不再校验签名令牌与远程偏好，本地直接放行。
     */
    fun isAutomationAllowed(): Boolean = true

    fun publish(context: Context, allowed: Boolean): Boolean = publish(
        context,
        MobileEntitlementPublishedState(
            automationAllowed = allowed,
            entitlementToken = readLocalToken(context).takeIf { allowed },
        ),
    )

    fun publish(context: Context, state: MobileEntitlementPublishedState): Boolean {
        val publishedState = state.copy(entitlementToken = state.entitlementToken.takeIf { state.automationAllowed })
        localState.set(publishedState)
        // Write to standard SharedPreferences so rememberEntitlementState() can read it
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(io.github.magisk317.uikit.entitlement.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED, publishedState.automationAllowed)
            .apply()
        return RemotePrefsStore.putEntitlementState(
            publishedState.automationAllowed,
            publishedState.entitlementToken,
        )
    }

    fun mirrorEntitlement(context: Context) {
        publish(
            context,
            MobileEntitlementPublishedState(
                automationAllowed = localState.get().automationAllowed,
                entitlementToken = readLocalToken(context),
            ),
        )
    }

    fun readEntitlementToken(): String? {
        if (RemotePrefsStore.available()) {
            return RemotePrefsStore.getEntitlementToken()
                ?.takeIf { RemotePrefsStore.getEntitlementAllowed() }
        }
        return localState.get().entitlementToken
            ?.takeIf { localState.get().automationAllowed }
    }

    fun clearEntitlementToken() {
        localState.set(MobileEntitlementPublishedState(false, null))
        RemotePrefsStore.putEntitlementState(false, null)
    }

    private fun readLocalToken(context: Context): String? = runCatching {
        context.applicationContext
            .getSharedPreferences(MobileEntitlementKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(MobileEntitlementKeys.TOKEN, null)
            ?.takeIf(String::isNotBlank)
    }.getOrNull()
}
