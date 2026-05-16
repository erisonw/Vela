package com.vela.app.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.data.mock.MockVelaRepository
import com.vela.app.data.model.Event
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CalendarUiState(
    val events: List<Event> = emptyList(),
) {
    val eventCount: Int = events.size
    val importedCount: Int = events.count { it.sourceSessionId != null }
}

class CalendarViewModel : ViewModel() {
    private val repository = MockVelaRepository

    val uiState: StateFlow<CalendarUiState> = repository.events
        .map { events -> CalendarUiState(events = events) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CalendarUiState(),
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    eventId: String?,
    onBackClick: () -> Unit,
    viewModel: CalendarViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Calendar") },
                navigationIcon = {
                    if (eventId != null) {
                        TextButton(onClick = onBackClick) {
                            Text(text = "Back")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "App calendar",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = eventId?.let { "Showing event from deep link: $it" }
                            ?: "Mock events created by the app shell and import flow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CalendarStats(uiState = uiState)
                }
            }

            if (uiState.events.isEmpty()) {
                item {
                    EmptyCalendarCard()
                }
            } else {
                items(uiState.events, key = { it.id }) { event ->
                    CalendarEventRow(
                        event = event,
                        isHighlighted = event.id == eventId,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarStats(uiState: CalendarUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(
            label = "Events",
            value = uiState.eventCount.toString(),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Imported",
            value = uiState.importedCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun CalendarEventRow(
    event: Event,
    isHighlighted: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${event.startAt.toDisplayDate()} | ${event.startAt.toDisplayTime()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            event.location?.name?.let { locationName ->
                Text(
                    text = locationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isHighlighted) {
                Text(
                    text = "Opened from deep link",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun EmptyCalendarCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "No calendar events",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Imported mock candidates will appear here after confirmation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun String.toDisplayDate(): String = substringBefore("T")

private fun String.toDisplayTime(): String = substringAfter("T", this)
    .take(5)
