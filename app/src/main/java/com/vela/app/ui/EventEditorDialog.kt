package com.vela.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vela.app.data.model.Event
import com.vela.app.data.model.DefaultReminderMinutes
import com.vela.app.data.model.Location
import com.vela.app.data.model.remindersFromPreset
import com.vela.app.data.model.selectedReminderMinutes
import com.vela.app.data.model.validateEventInput
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun EventEditorDialog(
    title: String,
    confirmText: String,
    initialEvent: Event?,
    defaultReminderMinutes: Int? = DefaultReminderMinutes,
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit,
) {
    val context = LocalContext.current
    val stateKey = initialEvent?.id ?: "new-event"
    var eventTitle by remember(stateKey) { mutableStateOf(initialEvent?.title.orEmpty()) }
    var startAt by remember(stateKey) { mutableStateOf(initialEvent?.startAt.orEmpty()) }
    var endAt by remember(stateKey) { mutableStateOf(initialEvent?.endAt.orEmpty()) }
    var locationName by remember(stateKey) { mutableStateOf(initialEvent?.location?.name.orEmpty()) }
    var description by remember(stateKey) { mutableStateOf(initialEvent?.description.orEmpty()) }
    var reminderMinutes by remember(stateKey) {
        mutableStateOf(
            if (initialEvent == null) {
                defaultReminderMinutes
            } else {
                selectedReminderMinutes(initialEvent.reminders)
            },
        )
    }
    val validation = validateEventInput(eventTitle, startAt, endAt)
    val hasInput = eventTitle.isNotBlank() || startAt.isNotBlank() || endAt.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasInput && validation.message != null) {
                    Text(
                        text = validation.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showDateTimePicker(context, startAt) { selectedTime ->
                                startAt = selectedTime
                                if (endAt.isBlank()) {
                                    endAt = selectedTime.plusDefaultDuration()
                                }
                            }
                        },
                    ) {
                        Text(text = "选开始")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showDateTimePicker(context, endAt) { selectedTime ->
                                endAt = selectedTime
                            }
                        },
                    ) {
                        Text(text = "选结束")
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = eventTitle,
                    onValueChange = { eventTitle = it },
                    label = { Text(text = "标题 *") },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = startAt,
                    onValueChange = { startAt = it },
                    label = { Text(text = "开始时间 *") },
                    placeholder = { Text(text = "2026-05-18T10:00:00+08:00") },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = endAt,
                    onValueChange = { endAt = it },
                    label = { Text(text = "结束时间") },
                    placeholder = { Text(text = "2026-05-18T11:00:00+08:00") },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text(text = "地点") },
                    singleLine = true,
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(text = "备注") },
                    minLines = 2,
                )
                ReminderSelector(
                    selectedMinutes = reminderMinutes,
                    onSelected = { reminderMinutes = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation.isValid,
                onClick = {
                    onSave(
                        Event(
                            id = initialEvent?.id ?: "event-manual-${System.currentTimeMillis()}",
                            title = eventTitle.trim(),
                            startAt = startAt.trim(),
                            endAt = endAt.trim().ifBlank { null },
                            timezone = initialEvent?.timezone ?: "Asia/Shanghai",
                            location = locationName.trim().takeIf { it.isNotBlank() }?.let {
                                initialEvent?.location?.copy(name = it) ?: Location(name = it)
                            },
                            description = description.trim().ifBlank { null },
                            reminders = remindersFromPreset(reminderMinutes),
                            sourceSessionId = initialEvent?.sourceSessionId,
                        ),
                    )
                },
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}

private fun String.plusDefaultDuration(): String =
    runCatching {
        OffsetDateTime
            .parse(this)
            .plusHours(1)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }.getOrDefault("")
