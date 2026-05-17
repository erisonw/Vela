package com.vela.app.data.model

const val DefaultReminderMinutes = 10

val ReminderPresetMinutes: List<Int?> = listOf(null, 0, 10, 30, 60)

fun reminderLabel(minutesBefore: Int): String = when (minutesBefore) {
    0 -> "开始时"
    10 -> "提前 10 分钟"
    30 -> "提前 30 分钟"
    60 -> "提前 1 小时"
    else -> "提前 $minutesBefore 分钟"
}

fun reminderId(minutesBefore: Int): String = "reminder-$minutesBefore-min"

fun remindersFromPreset(minutesBefore: Int?): List<Reminder> =
    minutesBefore?.let { minutes ->
        listOf(
            Reminder(
                id = reminderId(minutes),
                minutesBefore = minutes,
                label = reminderLabel(minutes),
            ),
        )
    }.orEmpty()

fun selectedReminderMinutes(reminders: List<Reminder>): Int? =
    reminders.firstOrNull()?.minutesBefore
