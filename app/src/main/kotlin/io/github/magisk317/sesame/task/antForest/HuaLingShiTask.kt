package io.github.magisk317.sesame.task.antForest

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.net.toUri
import io.github.magisk317.sesame.hook.simple.SimplePageManager
import io.github.magisk317.sesame.util.StructuredLog
import io.github.magisk317.sesame.util.length
import io.github.magisk317.sesame.util.optJSONArray
import io.github.magisk317.sesame.util.optJSONObject
import io.github.magisk317.sesame.util.optString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.resume

/**
 * Drives the real game-task page used by the forest vitality task.
 *
 * The page is a WebView and does not expose the three actions as normal
 * Android views. We click the visible DOM action by its stable Chinese label,
 * then let AntForest re-query the server before awarding the task.
 */
object HuaLingShiTask {
    const val TASK_TYPE = "mokuai_senlin_hlz"
    private const val PAGE_MARKER = "gameTaskSuspend"
    private const val PAGE_TIMEOUT_MS = 30_000L
    private const val JAVASCRIPT_TIMEOUT_MS = 5_000L
    private const val ACTION_DELAY_MS = 1_200L

    internal data class Action(val keyword: String, val count: Int)

    internal val actions = listOf(
        Action("坐骑召唤", 3),
        Action("灵田施肥", 1),
        Action("武道会", 3),
    )

    /**
     * Returns true only when every required DOM action was found and clicked.
     * Server-side completion is deliberately checked by AntForest afterwards.
     */
    suspend fun execute(taskJumpUrl: String): Boolean {
        if (taskJumpUrl.isBlank()) {
            StructuredLog.error("HuaLingShiTask", "灵画师任务缺少跳转地址")
            return false
        }

        val activity = SimplePageManager.getTopActivity()
        if (activity == null) {
            StructuredLog.error("HuaLingShiTask", "灵画师任务无法找到支付宝前台 Activity")
            return false
        }

        withContext(Dispatchers.Main.immediate) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, taskJumpUrl.toUri()))
        }

        val webView = awaitGameWebView() ?: run {
            StructuredLog.error("HuaLingShiTask", "未找到灵画师 gameTaskSuspend WebView")
            return false
        }

        for (action in actions) {
            repeat(action.count) { index ->
                val clicked = evaluateBoolean(webView, clickScript(action.keyword))
                if (!clicked) {
                    StructuredLog.error(
                        "HuaLingShiTask",
                        "未找到灵画师子任务控件: ${action.keyword} (${index + 1}/${action.count})",
                    )
                    return false
                }
                StructuredLog.record("HuaLingShiTask", "已点击灵画师子任务 ${action.keyword} (${index + 1}/${action.count})")
                delay(ACTION_DELAY_MS)
            }
        }

        return true
    }

    internal fun clickScript(keyword: String): String {
        val escapedKeyword = keyword
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        return """
            (function() {
              const keyword = '$escapedKeyword';
              const visible = (el) => {
                const rect = el.getBoundingClientRect();
                const style = window.getComputedStyle(el);
                return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
              };
              const candidates = Array.from(document.querySelectorAll('button,a,[role="button"],div,span'))
                .filter((el) => visible(el) && (el.innerText || '').includes(keyword))
                .filter((el) => !Array.from(el.children).some((child) => (child.innerText || '').includes(keyword)))
                .sort((a, b) => (a.innerText || '').length - (b.innerText || '').length);
              const target = candidates[0];
              if (!target) return false;
              target.click();
              return true;
            })();
        """.trimIndent()
    }

    internal fun isTaskFinished(response: JsonObject, taskType: String = TASK_TYPE): Boolean {
        val taskGroups = response.optJSONArray("forestTasksNew") ?: return false
        for (groupIndex in 0 until taskGroups.length()) {
            val group = taskGroups.optJSONObject(groupIndex) ?: continue
            val taskList = group.optJSONArray("taskInfoList") ?: continue
            for (taskIndex in 0 until taskList.length()) {
                val task = taskList.optJSONObject(taskIndex) ?: continue
                val baseInfo = task.optJSONObject("taskBaseInfo") ?: continue
                if (baseInfo.optString("taskType") == taskType) {
                    return baseInfo.optString("taskStatus") == "FINISHED"
                }
            }
        }
        return false
    }

    private suspend fun awaitGameWebView(): WebView? {
        var found: WebView? = null
        withTimeoutOrNull(PAGE_TIMEOUT_MS) {
            while (found == null && currentCoroutineContext().isActive) {
                val activity = SimplePageManager.getTopActivity()
                val webView = withContext(Dispatchers.Main.immediate) {
                    activity?.window?.decorView?.let(::findWebView)
                }
                if (webView != null) {
                    val url = withContext(Dispatchers.Main.immediate) { webView.url.orEmpty() }
                    if (url.contains(PAGE_MARKER) && evaluateBoolean(webView, pageReadyScript())) {
                        found = webView
                    }
                }
                if (found == null) {
                    delay(300L)
                }
            }
        }
        return found
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private suspend fun evaluateBoolean(webView: WebView, script: String): Boolean {
        val result = withTimeoutOrNull(JAVASCRIPT_TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<String?> { continuation ->
                    try {
                        webView.evaluateJavascript(script) { value ->
                            if (continuation.isActive) {
                                continuation.resume(value)
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(error))
                        }
                    }
                }
            }
        } ?: return false
        return result == "true" || result == "\"true\""
    }

    private fun pageReadyScript(): String = """
        (function() {
          const text = document.body ? (document.body.innerText || '') : '';
          return text.includes('坐骑召唤') && text.includes('灵田施肥') && text.includes('武道会');
        })();
    """.trimIndent()
}
