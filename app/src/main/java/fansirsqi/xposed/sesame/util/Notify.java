package fansirsqi.xposed.sesame.util;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import fansirsqi.xposed.sesame.data.RuntimeInfo;
import fansirsqi.xposed.sesame.model.BaseModel;
import lombok.Getter;

public class Notify {
  @SuppressLint("StaticFieldLeak")
  public static Context context;
  private static final int NOTIFICATION_ID = 99;
  private static final String CHANNEL_ID = "fansirsqi.xposed.sesame.ANTFOREST_NOTIFY_CHANNEL";
  private static NotificationManager mNotifyManager;
  private static Notification.Builder builder;

  @Getter private static volatile long lastNoticeTime = 0;
  private static String titleText = "";
  private static String contentText = "";

  @SuppressLint("ObsoleteSdkInt")//抑制SdkInt警告
  public static void start(Context context) {
    try {
      Notify.context = context;
      Notify.stop();
      titleText = "🚀 启动中";
      contentText = "🔔 暂无消息";
      mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      Intent it = new Intent(Intent.ACTION_VIEW);
      it.setData(Uri.parse("alipays://platformapi/startapp?appId="));
      PendingIntent pi = PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, "🔔 芝麻粒能量提醒", NotificationManager.IMPORTANCE_LOW);
        notificationChannel.enableLights(false);
        notificationChannel.enableVibration(false);
        notificationChannel.setShowBadge(false);
        mNotifyManager.createNotificationChannel(notificationChannel);
        builder = new Notification.Builder(context, CHANNEL_ID);
      } else {
        //安卓8.0以下
        builder = new Notification.Builder(context).setPriority(Notification.PRIORITY_LOW);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setCategory(Notification.CATEGORY_NAVIGATION);
      builder
          .setSmallIcon(android.R.drawable.sym_def_app_icon)
          .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), android.R.drawable.sym_def_app_icon))
          .setSubText("芝麻粒")
          .setAutoCancel(false)
          .setContentIntent(pi);
      if (BaseModel.getEnableOnGoing().getValue()) {
        builder.setOngoing(true);
      }
      Notification mNotification = builder.build();
      if (context instanceof Service) {
        ((Service) context).startForeground(NOTIFICATION_ID, mNotification);
      } else {
        mNotifyManager.notify(NOTIFICATION_ID, mNotification);
      }
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
  }

  /** 停止通知。 移除通知并停止前台服务。 */
  public static void stop() {
    try {
      if (context instanceof Service) {
        ((Service) context).stopForeground(true);
      } else {
        if (mNotifyManager != null) {
          mNotifyManager.cancel(NOTIFICATION_ID);
        } else if (context != null) {
          NotificationManager systemService = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
          if (systemService != null) {
            systemService.cancel(NOTIFICATION_ID);
          }
        }
      }
      mNotifyManager = null;
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
  }

  /**
   * 更新通知文本。 更新通知的标题和内容文本，并发送通知。
   *
   * @param status 要更新的状态文本。
   */
  public static void updateStatusText(String status) {
    try {
      long forestPauseTime = RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime);

      if (forestPauseTime > System.currentTimeMillis()) {
        status = "\uD83D\uDE08 触发异常，等待至" + TimeUtil.getCommonDate(forestPauseTime) + "恢复运行";
      }

      titleText = status;
      lastNoticeTime = System.currentTimeMillis();
      sendText();
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
  }

  /**
   * 更新下一次执行时间的文本。
   *
   * @param nextExecTime 下一次执行的时间。
   */
  public static void updateNextExecText(long nextExecTime) {
    try {
      titleText = nextExecTime > 0 ? "⏰ 下次施工时间 " + TimeUtil.getTimeStr(nextExecTime) : "";
      sendText();
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
  }

  /**
   * 更新上一次执行的文本。
   *
   * @param content 上一次执行的内容。
   */
  public static void updateLastExecText(String content) {
    try {
      contentText = "📌 上次施工时间 " + TimeUtil.getTimeStr(System.currentTimeMillis()) + " \n🔔 " + content;
      lastNoticeTime = System.currentTimeMillis();
      sendText();
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
  }

  /** 设置状态文本为执行中。 */
  public static void setStatusTextExec() {
    updateStatusText("⚙️ 芝麻粒正在施工中...");
  }

  /** 发送文本更新。 更新通知的内容文本，并重新发送通知。 */
  private static void sendText() {
    try {
      builder.setContentTitle(titleText);
      if (!StringUtil.isEmpty(contentText)) {
        builder.setContentText(contentText);
      }
      mNotifyManager.notify(NOTIFICATION_ID, builder.build());
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
  }


  @SuppressLint("ObsoleteSdkInt")
  public static void sendNewNotification(Context context, String title, String content, int newNotificationId) {
    try {
      NotificationManager notifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      Notification.Builder newBuilder;
      Intent it = new Intent(Intent.ACTION_VIEW);
      it.setData(Uri.parse("alipays://platformapi/startapp?appId="));
      PendingIntent pi = PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID,"🔔 芝麻粒其他提醒",NotificationManager.IMPORTANCE_HIGH);
        notifyManager.createNotificationChannel(notificationChannel);
        newBuilder = new Notification.Builder(context, CHANNEL_ID);
      } else {
        newBuilder = new Notification.Builder(context);
      }
      // 配置新通知的样式
      newBuilder
              .setSmallIcon(android.R.drawable.sym_def_app_icon)
              .setContentTitle(title)
              .setContentText(content)
              .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), android.R.drawable.sym_def_app_icon))
              .setAutoCancel(true)
              .setContentIntent(pi)
              .setPriority(Notification.PRIORITY_HIGH);
      // 发送新通知
      Notification newNotification = newBuilder.build();
      notifyManager.notify(newNotificationId, newNotification);
    } catch (Exception e) {
      Log.printStackTrace(e);
    }
  }

}
