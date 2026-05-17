package com.vela.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vela.app.data.model.ReminderPresetMinutes
import com.vela.app.data.model.reminderLabel

@Composable
fun ReminderSelector(
    selectedMinutes: Int?,
    onSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "提醒",
            style = MaterialTheme.typography.labelLarge,
        )
        ReminderPresetMinutes.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { minutes ->
                    val label = minutes?.let { reminderLabel(it) } ?: "无提醒"
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = selectedMinutes == minutes,
                                onClick = { onSelected(minutes) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedMinutes == minutes,
                            onClick = { onSelected(minutes) },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
