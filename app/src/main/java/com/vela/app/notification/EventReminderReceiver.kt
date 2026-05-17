package com.vela.app.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EventNotificationScheduler.ensureChannel(context)
        if (!NotificationPermissionState.canPostNotifications(context)) {
            return
        }
        val eventId = EventNotificationScheduler.eventIdFrom(intent) ?: return
        val title = EventNotificationScheduler.titleFrom(intent).ifBlank { "日程提醒" }
        val time = EventNotificationScheduler.timeFrom(intent)
        val location = EventNotificationScheduler.locationFrom(intent)
        val reminderText = EventNotificationScheduler.reminderTextFrom(intent)
        val body = listOf(reminderText, time, location)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .ifBlank { "日程即将开始" }
        val contentIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("vela://event/$eventId")).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = notificationBuilder(context)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        context
            .getSystemService(NotificationManager::class.java)
            .notify(eventId.hashCode(), notification)
    }

    private fun notificationBuilder(context: Context): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, EventNotificationScheduler.ChannelId)
        } else {
            Notification.Builder(context)
        }
}
