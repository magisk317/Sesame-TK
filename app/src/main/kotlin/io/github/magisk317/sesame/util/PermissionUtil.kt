package io.github.magisk317.sesame.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.magisk317.sesame.hook.ApplicationHook
import io.github.magisk317.sesame.ui.MainActivity

/**
 * 权限工具类，用于检查和请求所需权限。
 * minSdk 32：按 Android 12L+ 行为处理。
 */
object PermissionUtil {
    private val TAG: String = PermissionUtil::class.java.simpleName

    private const val REQUEST_NOTIFICATION = 2

    fun checkFilePermissions(context: Context): Boolean {
        return Environment.isExternalStorageManager()
    }

    fun checkOrRequestFilePermissions(activity: MainActivity): Boolean {
        if (checkFilePermissions(activity)) return true
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:${activity.packageName}".toUri()
            }
            startActivitySafely(activity, intent, Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "请求文件权限失败", e)
        }
        return false
    }

    @JvmStatic
    fun checkAlarmPermissions(context: Context? = null): Boolean {
        val ctx = context ?: contextSafely ?: return false
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return alarmManager?.canScheduleExactAlarms() == true
    }

    @JvmStatic
    fun checkOrRequestAlarmPermissions(context: Context): Boolean {
        if (checkAlarmPermissions(context)) return true
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
            }
            startActivitySafely(context, intent, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "请求闹钟权限失败", e)
        }
        return false
    }

    @JvmStatic
    fun checkBatteryPermissions(context: Context? = null): Boolean {
        val ctx = context ?: contextSafely ?: return false
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    }

    @SuppressLint("BatteryLife")
    @JvmStatic
    fun checkOrRequestBatteryPermissions(context: Context): Boolean {
        if (checkBatteryPermissions(context)) return true
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
            }
            startActivitySafely(context, intent, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "请求电池优化权限失败", e)
        }
        return false
    }

    fun checkNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun checkOrRequestNotificationPermission(activity: AppCompatActivity): Boolean {
        if (checkNotificationPermission(activity)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION,
            )
        }
        return false
    }

    private fun startActivitySafely(context: Context, preferred: Intent, fallbackAction: String) {
        try {
            preferred.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(preferred)
        } catch (_: ActivityNotFoundException) {
            try {
                val fallback = Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            } catch (e: Exception) {
                StructuredLog.printStackTrace(TAG, "打开设置页失败", e)
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(TAG, "未知错误", e)
        }
    }

    private val contextSafely: Context?
        get() = try {
            if (ApplicationHook.isHooked) ApplicationHook.appContext else null
        } catch (_: Exception) {
            null
        }
}
