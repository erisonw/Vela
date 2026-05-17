package com.vela.app.feature.home

import android.content.Context

object TomorrowPreviewStore {
    private const val PreferencesName = "vela_tomorrow_preview"
    private const val LastShownDateKey = "last_shown_date"

    fun wasShownToday(context: Context, today: String): Boolean =
        context
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(LastShownDateKey, null) == today

    fun markShownToday(context: Context, today: String) {
        context
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(LastShownDateKey, today)
            .apply()
    }
}
