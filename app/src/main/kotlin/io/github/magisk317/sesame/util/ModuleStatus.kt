package io.github.magisk317.sesame.util

import io.github.magisk317.uikit.surface.ActivationStatus
import io.github.magisk317.uikit.surface.ModuleActivationDetector

/**
 * Module status detection — LSPosed only.
 *
 * Returns [ActivationStatus.Activated] when the module is loaded by LSPosed,
 * [ActivationStatus.NotActivated] otherwise. Consistent with XSC/xinyi.
 */
object ModuleStatus : ModuleActivationDetector {

    private var cachedStatus: ActivationStatus? = null

    override fun detectActivation(): ActivationStatus {
        return cachedStatus ?: ActivationStatus.NotActivated
    }

    /**
     * Called from the hook layer when the module is loaded by LSPosed.
     * Replaces the default "Not Activated" status with the detected framework info.
     */
    fun onLsposedActivated(frameworkVersion: String, apiVersion: Int) {
        cachedStatus = ActivationStatus.Activated(
            frameworkName = "LSPosed",
            frameworkVersion = frameworkVersion,
            apiVersion = apiVersion,
        )
    }

    /** Hook-layer entry: detect LSPosed from ClassLoader. */
    fun detectFramework(classLoader: ClassLoader): String {
        return when {
            checkClass(classLoader, "de.robv.android.xposed.XposedInit") -> "LSPosed"
            else -> "Unknown"
        }
    }

    private fun checkClass(cl: ClassLoader, className: String): Boolean {
        return try {
            Class.forName(className, false, cl)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
