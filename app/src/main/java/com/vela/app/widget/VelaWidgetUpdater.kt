package com.vela.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.vela.app.data.mock.MockVelaRepository

object VelaWidgetUpdater {
    suspend fun updateAll(context: Context) {
        MockVelaRepository.initialize(context)
        VelaTodayWidget().updateAll(context)
    }
}
