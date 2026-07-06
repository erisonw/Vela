package com.vela.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

object VelaWidgetUpdater {
    suspend fun updateAll(context: Context) {
        VelaTodayWidget().updateAll(context)
    }
}
