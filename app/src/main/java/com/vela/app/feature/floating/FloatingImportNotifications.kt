package com.vela.app.feature.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.vela.app.MainActivity
import com.vela.app.R

object FloatingImportNotifications {
    const val OverlayNotificationId = 3101
    const val CaptureNotificationId = 3102
    private const val ChannelId = "vela_floating_import"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(ChannelId) != null) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                "悬浮窗导入",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "用于悬浮窗截图导入和一次性截屏识别。"
            },
        )
    }

    fun foregroundNotification(
        context: Context,
        title: String,
        text: String,
    ): Notification {
        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, ChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
