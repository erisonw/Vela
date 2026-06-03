package com.vela.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vela.app.data.mock.MockVelaRepository
import kotlin.concurrent.thread

class EventAdvicePrepareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = EventNotificationScheduler.eventIdFrom(intent) ?: return
        val pendingResult = goAsync()
        thread(name = "vela-event-advice-prepare") {
            runCatching {
                MockVelaRepository.initialize(context.applicationContext)
                MockVelaRepository.prepareEventAdvice(eventId)
            }
            pendingResult.finish()
        }
    }
}
