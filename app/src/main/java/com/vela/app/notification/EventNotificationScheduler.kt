package com.vela.app.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vela.app.data.model.Event
import com.vela.app.data.model.Reminder
import com.vela.app.data.model.ReminderPresetMinutes
import com.vela.app.data.model.reminderLabel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object EventNotificationScheduler {
    const val ChannelId = "vela_event_reminders"
    private const val ChannelName = "日程提醒"
    private const val ActionEventReminder = "com.vela.app.action.EVENT_REMINDER"
    private const val ExtraEventId = "event_id"
    private const val ExtraTitle = "title"
    private const val ExtraTime = "time"
    private const val ExtraLocation = "location"
    private const val ExtraReminderText = "reminder_text"
    private val TimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val existingChannel = manager.getNotificationChannel(ChannelId)
        if (existingChannel != null) {
            return
        }
        val channel = NotificationChannel(
            ChannelId,
            ChannelName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "日程开始前提醒"
        }
        manager.createNotificationChannel(channel)
    }

    fun scheduleAll(context: Context, events: List<Event>) {
        ensureChannel(context)
        events.forEach { event ->
            scheduleEvent(context, event)
        }
    }

    fun scheduleEvent(context: Context, event: Event) {
        cancelEvent(context, event.id)
        if (event.reminders.isEmpty()) {
            return
        }
        ensureChannel(context)
        event.reminders.forEach { reminder ->
            scheduleReminder(context, event, reminder)
        }
    }

    fun cancelEvent(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        ReminderPresetMinutes.filterNotNull().forEach { minutesBefore ->
            val pendingIntent = reminderPendingIntent(
                context = context,
                eventId = eventId,
                minutesBefore = minutesBefore,
                flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun scheduleReminder(
        context: Context,
        event: Event,
        reminder: Reminder,
    ) {
        val minutesBefore = reminder.minutesBefore
        val triggerAtMillis = event.startAt.toReminderMillis(minutesBefore) ?: return
        if (triggerAtMillis <= System.currentTimeMillis()) {
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = scheduledPendingIntent(
            context = context,
            event = event,
            eventId = event.id,
            reminder = reminder,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun reminderPendingIntent(
        context: Context,
        eventId: String,
        minutesBefore: Int,
        flags: Int,
    ): PendingIntent? {
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            action = ActionEventReminder
            putExtra(ExtraEventId, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(eventId, minutesBefore),
            intent,
            flags,
        )
    }

    private fun scheduledPendingIntent(
        context: Context,
        event: Event,
        eventId: String,
        reminder: Reminder,
        flags: Int,
    ): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode(eventId, reminder.minutesBefore),
            buildReminderIntent(context, event, reminder),
            flags,
        )

    private fun buildReminderIntent(context: Context, event: Event, reminder: Reminder): Intent =
        Intent(context, EventReminderReceiver::class.java).apply {
            action = ActionEventReminder
            putExtra(ExtraEventId, event.id)
            putExtra(ExtraTitle, event.title)
            putExtra(ExtraTime, event.startAt.toDisplayTime())
            putExtra(ExtraLocation, event.location?.name.orEmpty())
            putExtra(ExtraReminderText, reminder.label ?: reminderLabel(reminder.minutesBefore))
        }

    fun requestCode(eventId: String, minutesBefore: Int): Int =
        31 * eventId.hashCode() + minutesBefore

    fun eventIdFrom(intent: Intent): String? = intent.getStringExtra(ExtraEventId)

    fun titleFrom(intent: Intent): String = intent.getStringExtra(ExtraTitle).orEmpty()

    fun timeFrom(intent: Intent): String = intent.getStringExtra(ExtraTime).orEmpty()

    fun locationFrom(intent: Intent): String = intent.getStringExtra(ExtraLocation).orEmpty()

    fun reminderTextFrom(intent: Intent): String = intent.getStringExtra(ExtraReminderText).orEmpty()

    private fun String.toReminderMillis(minutesBefore: Int): Long? =
        runCatching {
            OffsetDateTime.parse(this)
                .minusMinutes(minutesBefore.toLong())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()

    private fun String.toDisplayTime(): String =
        runCatching {
            OffsetDateTime.parse(this).format(TimeFormatter)
        }.getOrDefault(this)
}
