package com.vela.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun showDateTimePicker(
    context: Context,
    currentValue: String,
    onSelected: (String) -> Unit,
) {
    val initialDateTime = currentValue
        .takeIf { it.isNotBlank() }
        ?.let { value -> runCatching { OffsetDateTime.parse(value) }.getOrNull() }
        ?: OffsetDateTime.now(ZoneOffset.ofHours(8)).withSecond(0).withNano(0)

    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val selectedDateTime = LocalDate
                        .of(year, month + 1, dayOfMonth)
                        .atTime(hour, minute)
                        .atOffset(ZoneOffset.ofHours(8))
                    onSelected(selectedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                },
                initialDateTime.hour,
                initialDateTime.minute,
                true,
            ).show()
        },
        initialDateTime.year,
        initialDateTime.monthValue - 1,
        initialDateTime.dayOfMonth,
    ).show()
}
