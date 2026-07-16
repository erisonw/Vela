package com.vela.app.notification

import android.content.Context
import com.vela.app.data.model.Event

interface ReminderScheduler {
    fun ensureReady()

    fun scheduleAll(events: List<Event>)

    fun scheduleEvent(event: Event)

    fun cancelEvent(
        eventId: String,
        extraReminderMinutes: Collection<Int> = emptyList(),
    )
}

class AndroidReminderScheduler(context: Context) : ReminderScheduler {
    private val appContext = context.applicationContext

    override fun ensureReady() {
        EventNotificationScheduler.ensureChannel(appContext)
    }

    override fun scheduleAll(events: List<Event>) {
        EventNotificationScheduler.scheduleAll(appContext, events)
    }

    override fun scheduleEvent(event: Event) {
        EventNotificationScheduler.scheduleEvent(appContext, event)
    }

    override fun cancelEvent(
        eventId: String,
        extraReminderMinutes: Collection<Int>,
    ) {
        EventNotificationScheduler.cancelEvent(
            context = appContext,
            eventId = eventId,
            extraReminderMinutes = extraReminderMinutes,
        )
    }
}
