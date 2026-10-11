package com.aazhou.mtforum

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aazhou.mtforum.data.model.Notice

/**
 * 新消息系统通知。
 *
 * 通知权限（Android 13+ 的 POST_NOTIFICATIONS）没给时 [show] 直接跳过：不发比
 * 弹一条空通知好，红点照旧由未读状态驱动，不依赖这里。
 */
object NoticeNotifier {

    private const val CHANNEL_ID = "notice"
    private const val NOTIFICATION_ID = 1001

    fun show(context: Context, notices: List<Notice>) {
        if (notices.isEmpty()) return
        createChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            // 只带一个标记：具体跳到哪由 MainActivity/AppRoot 决定（未登录仍留在首页）
            putExtra(MainActivity.EXTRA_OPEN_NOTICE, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 原文里带帖子标题与「查看」之类的操作词，通知里只留前几条正文，避免一屏放不下
        val lines = notices.take(3).map { it.text.replace(Regex("\\s+"), " ") }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mail_line)
            .setContentTitle("${notices.size} 条新消息提醒")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** 消息已看过：撤掉通知，别在通知栏留着一条数不对的「N 条新消息提醒」。 */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "消息提醒",
            // IMPORTANCE_DEFAULT：有新消息时提示音/状态栏都有，但不打断当前操作
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}
