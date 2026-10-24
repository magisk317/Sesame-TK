package io.github.magisk317.sesame.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.magisk317.sesame.model.BaseModel.Companion.toastPerfix

object ToastUtil {
    private const val TAG = "ToastUtil"
    private var appContext: Context? = null

    fun init(context: Context?) {
        if (context != null) {
            appContext = context.applicationContext
        }
    }

    private val context: Context
        get() {
            checkNotNull(appContext) { "ToastUtil is not initialized. Call ToastUtil.init(context) in Application." }
            return appContext!!
        }

    fun showToast(message: String?) {
        showToast(context, message)
    }

    fun showToast(context: Context?, message: String?) {
        // 1. 修复逻辑错误：处理前缀拼接
        var finalMessage = message
        val prefix = toastPerfix.value

      //  StructuredLog.record(TAG, "prefix::$prefix")

        // 修复：必须同时满足 "不为空" 且 "不等于字符串null"
        if (!prefix.isNullOrBlank() && prefix != "null") {
            finalMessage = "$prefix:$message"
        }

        StructuredLog.record(TAG, "showToast::true::$finalMessage")
        val toast = Toast.makeText(context ?: this.context, finalMessage, Toast.LENGTH_SHORT)
        setToastGravity(toast)
        toast.show()
    }

    fun makeText(context: Context?, message: String?, duration: Int): Toast {
        var finalMessage = message
        val prefix = toastPerfix.value

       // StructuredLog.record(TAG, "prefix::$prefix")

        // 修复逻辑
        if (!prefix.isNullOrBlank() && prefix != "null") {
            finalMessage = "$prefix:$message"
        }

        val toast = Toast.makeText(context, finalMessage, duration)
        // 修复系统错误
        setToastGravity(toast)
        return toast
    }

    fun makeText(message: String?, duration: Int): Toast {
        return makeText(context, message, duration)
    }

    fun showToastWithDelay(context: Context?, message: String?, delayMillis: Int) {
        Handler(Looper.getMainLooper()).postDelayed({
            makeText(context, message, Toast.LENGTH_SHORT).show()
        }, delayMillis.toLong())
    }

    fun showToastWithDelay(message: String?, delayMillis: Int) {
        Handler(Looper.getMainLooper()).postDelayed({
            makeText(message, Toast.LENGTH_SHORT).show()
        }, delayMillis.toLong())
    }

    // 抽离设置 Gravity 的逻辑
    fun setToastGravity(toast: Toast) {
        // minSdk 32: standard makeText toasts cannot customize gravity.
    }
}
