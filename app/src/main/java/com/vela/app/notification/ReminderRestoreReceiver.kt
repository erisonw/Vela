package com.vela.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vela.app.data.mock.MockVelaRepository

class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supportedAction = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!supportedAction) {
            return
        }

        MockVelaRepository.initialize(context)
    }
}
