package com.vela.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vela.app.di.VelaGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EventAdvicePrepareReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = EventNotificationScheduler.eventIdFrom(intent) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                VelaGraph.repository.prepareEventAdvice(eventId)
            }
            pendingResult.finish()
        }
    }
}
