package io.github.magisk317.sesame.util

//import android.R
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.magisk317.sesame.data.RuntimeInfo
import io.github.magisk317.sesame.hook.Toast
import io.github.magisk317.sesame.model.BaseModel
import kotlin.concurrent.Volatile

@SuppressLint("StaticFieldLeak")
object Notify {
    private val TAG: String = Notify::class.java.getSimpleName()

    @SuppressLint("StaticFieldLeak")
    @Volatile
    var context: Context? = null
    private const val NOTIFICATION_ID = 99
    private const val ERROR_NOTIFICATION_ID = 98
    private const val CHANNEL_ID = "io.github.magisk317.sesame.ANTFOREST_NOTIFY_CHANNEL"
    private var mNotifyManager: NotificationManager? = null

    @SuppressLint("StaticFieldLeak")
    private var builder: NotificationCompat.Builder? = null

    @Volatile
    private var isNotificationStarted = false

    private var lastUpdateTime: Long = 0
    private var nextExecTimeCache: Long = 0
    private var titleText: String? = ""
    private var contentText = ""

    private fun statusBarIcon(): androidx.core.graphics.drawable.IconCompat {
        val bitmap = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawCircle(36f, 36f, 24f, paint)
        paint.style = Paint.Style.FILL
        Path().apply {
            moveTo(36f, 16f)
            lineTo(42f, 30f)
            lineTo(56f, 36f)
            lineTo(42f, 42f)
            lineTo(36f, 56f)
            lineTo(30f, 42f)
            lineTo(16f, 36f)
            lineTo(30f, 30f)
            close()
        }.also { canvas.drawPath(it, paint) }
        return androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap)
    }


    private fun checkPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                StructuredLog.error(TAG, "Missing POST_NOTIFICATIONS permission to send new notification$context")
                Toast.show("请在设置中开启目标应用通知权限")
                return false
            }
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            StructuredLog.error(TAG, "Notifications are disabled for this app.$context")
            Toast.show("请在设置中开启目标应用通知权限")
            return false
        }
        return true
    }

    @JvmStatic
    @Synchronized
    fun start(context: Context) {
        try {
            if (checkPermission(context)) {
                // Stop the previous notification before replacing its context.
                // stop() clears all state, so assigning first would clear the
                // newly supplied context as well.
                stop()
                Notify.context = context
                titleText = "🚀 启动中"
                contentText = "🔔 暂无消息"
                lastUpdateTime = System.currentTimeMillis()
                mNotifyManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                val it = Intent(Intent.ACTION_VIEW)
                it.setData("alipays://platformapi/startapp?appId=".toUri())
                val pi = PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                if (true) {
                    val notificationChannel = NotificationChannel(CHANNEL_ID, "🔔 芝麻粒能量提醒", NotificationManager.IMPORTANCE_LOW)
                    notificationChannel.enableLights(false)
                    notificationChannel.enableVibration(false)
                    notificationChannel.setShowBadge(false)
                    mNotifyManager!!.createNotificationChannel(notificationChannel)
                }
                builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                    .setSmallIcon(statusBarIcon())
                    .setLargeIcon(BitmapFactory.decodeResource(context.resources, android.R.drawable.sym_def_app_icon))
                    .setContentTitle(titleText)
                    .setContentText(contentText)
                    .setSubText("芝麻粒")
                    .setAutoCancel(false)
                    .setContentIntent(pi)
                if (BaseModel.enableOnGoing.value) {
                    builder!!.setOngoing(true)
                }
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder!!.build())
                isNotificationStarted = true
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }

    /**
     * 停止通知。 移除通知并停止前台服务。
     */
    @JvmStatic
    @Synchronized
    fun stop() {
        try {
            val currentContext = context
            if (currentContext != null) {
                if (currentContext is Service) {
                    if (true) {
                        currentContext.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                    } else {
                        currentContext.stopSelf()
                    }
                }
                NotificationManagerCompat.from(currentContext).cancel(NOTIFICATION_ID)
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        } finally {
            mNotifyManager = null
            isNotificationStarted = false
            builder = null
            context = null
        }
    }

    /**
     * 更新通知文本。 更新通知的标题和内容文本，并发送通知。
     *
     * @param status 要更新的状态文本。
     */
    @JvmStatic
    @Synchronized
    fun updateStatusText(status: String?) {
        var status = status
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            val forestPauseTime = RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime)
            if (forestPauseTime > System.currentTimeMillis()) {
                status = "❌ 触发异常，等待至" + TimeUtil.getCommonDate(forestPauseTime) + "恢复运行"
            }
            titleText = status
            sendText(true)
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }

    /**
     * 更新下一次执行时间的文本。
     *
     * @param nextExecTime 下一次执行的时间。
     */
    @JvmStatic
    @Synchronized
    fun updateNextExecText(nextExecTime: Long) {
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            if (nextExecTime != -1L) {
                nextExecTimeCache = nextExecTime
            }
            titleText = if (nextExecTimeCache > 0) "⏰ 下次执行 " + TimeUtil.getTimeStr(nextExecTimeCache) else ""
            sendText(false)
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }

    /**
     * 更新上一次执行的文本。
     *
     * @param content 上一次执行的内容。
     */
    @JvmStatic
    @Synchronized
    fun updateLastExecText(content: String?) {
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            contentText = "📌 上次执行 " + TimeUtil.getTimeStr(System.currentTimeMillis()) + "\n🌾 " + content
            sendText(false)
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }


    /**
     * 设置状态文本为执行中。
     */
    @JvmStatic
    @Synchronized
    fun setStatusTextExec() {
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            val forestPauseTime = RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime)

            if (forestPauseTime > System.currentTimeMillis()) {
                titleText = "❌ 触发异常，等待至" + TimeUtil.getCommonDate(forestPauseTime) + "恢复运行"
            }
            titleText = "⚙️ 芝麻粒正在施工中..."
            if (builder != null) {
                builder!!.setContentTitle(titleText)
            }
            sendText(true)
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }

    /**
     * 设置状态文本为已禁用
     */
    @JvmStatic
    @Synchronized
    fun setStatusTextDisabled() {
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            builder!!.setContentTitle("🚫 芝麻粒已禁用")
            if (!StringUtil.isEmpty(contentText)) {
                builder!!.setContentText(contentText)
            }
            builder!!.setProgress(0, 0, false)
            sendText(true)
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }

    @JvmStatic
    fun setStatusTextExec(content: String?) {
        updateStatusText("🔥 $content 运行中...")
    }

    /**
     * 发送文本更新。 更新通知的内容文本，并重新发送通知。
     *
     * @param force 是否强制刷新
     */
    @Synchronized
    private fun sendText(force: Boolean) {
        if (!isNotificationStarted || context == null || builder == null || mNotifyManager == null) return
        try {
            if (!force && System.currentTimeMillis() - lastUpdateTime < 500) {
                return
            }
            lastUpdateTime = System.currentTimeMillis()
            if (builder != null) {
                builder!!.setContentTitle(titleText)
                if (!StringUtil.isEmpty(contentText)) {
                    builder!!.setContentText(contentText)
                }
                mNotifyManager!!.notify(NOTIFICATION_ID, builder!!.build())
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }

    @SuppressLint("StaticFieldLeak")
    @JvmStatic
    @Synchronized
    fun sendNewNotification(title: String?, content: String?) {
        try {
            if (context == null) {
                StructuredLog.error(TAG, "Context is null in sendErrorNotification, cannot proceed.")
                return
            }
            if (!checkPermission(context!!) || !isNotificationStarted) return
            mNotifyManager = context!!.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            if (true) {
                val notificationChannel = NotificationChannel(CHANNEL_ID, "‼️ 芝麻粒异常通知", NotificationManager.IMPORTANCE_LOW)
                mNotifyManager!!.createNotificationChannel(notificationChannel)
            }
            val errorBuilder = NotificationCompat.Builder(context!!, CHANNEL_ID)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setSmallIcon(statusBarIcon())
                .setLargeIcon(BitmapFactory.decodeResource(context!!.resources, android.R.drawable.sym_def_app_icon))
                .setContentTitle(title)
                .setContentText(content)
                .setSubText("芝麻粒")
                .setAutoCancel(true)
            if (context is Service) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    NotificationManagerCompat.from(context!!).notify(ERROR_NOTIFICATION_ID, errorBuilder.build())
                } else {
                    (context as Service).startForeground(ERROR_NOTIFICATION_ID, errorBuilder.build())
                }
            } else {
                NotificationManagerCompat.from(context!!).notify(ERROR_NOTIFICATION_ID, errorBuilder.build())
            }
        } catch (e: Exception) {
            StructuredLog.printStackTrace(e)
        }
    }
}
