package io.github.magisk317.sesame.task.antTravel

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.net.toUri
import io.github.magisk317.sesame.hook.simple.SimplePageManager
import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Visits a task link for a bounded time without clicking inside the task page. */
internal object TravelRightsPageTaskExecutor {
    private const val TAG = "TravelRights"
    private const val PAGE_TIMEOUT_MS = 6_000L
    private const val DEFAULT_BROWSE_MS = 1_200L

    suspend fun execute(task: TravelRightsTaskState): Boolean {
        val url = task.jumpLink.trim()
        if (url.isBlank()) return false

        val origin = SimplePageManager.getTopActivity() ?: return false
        val originUrl = webViewUrl(origin)
        var opened = false
        try {
            withContext(Dispatchers.Main.immediate) {
                origin.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
            opened = awaitPage(origin, originUrl)
            if (!opened) return false

            val browseMs = task.browseTimeMs.coerceIn(DEFAULT_BROWSE_MS, 5_000L)
            delay(browseMs)
            return true
        } catch (t: Throwable) {
            StructuredLog.record(TAG, "出行任务[${task.title}]打开失败，切换下一个: ${t.javaClass.simpleName}")
            return false
        } finally {
            if (opened) closeCurrentPage(origin, originUrl)
        }
    }

    private suspend fun awaitPage(origin: Activity, originUrl: String): Boolean {
        return withTimeoutOrNull(PAGE_TIMEOUT_MS) {
            while (currentCoroutineContext().isActive) {
                val current = SimplePageManager.getTopActivity()
                val currentUrl = current?.let { webViewUrl(it) }.orEmpty()
                if (current != null && (current !== origin ||
                        originUrl.isNotBlank() && currentUrl.isNotBlank() && currentUrl != originUrl)
                ) {
                    return@withTimeoutOrNull true
                }
                delay(200L)
            }
            false
        } ?: false
    }

    private fun closeCurrentPage(origin: Activity, originUrl: String) {
        runCatching {
            val current = SimplePageManager.getTopActivity() ?: return
            val currentUrl = webViewUrl(current)
            val changed = current !== origin ||
                originUrl.isNotBlank() && currentUrl.isNotBlank() && currentUrl != originUrl
            if (changed) {
                @Suppress("DEPRECATION")
                current.onBackPressed()
            }
        }
    }

    private fun webViewUrl(activity: Activity): String {
        val webView = findWebView(activity.window?.decorView) ?: return ""
        return runCatching { webView.url.orEmpty() }.getOrDefault("")
    }

    private fun findWebView(view: View?): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
