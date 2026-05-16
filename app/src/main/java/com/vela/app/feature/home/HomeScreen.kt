package com.vela.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.vela.app.data.model.WidgetSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val events: List<Event> = emptyList(),
    val widgetSnapshot: WidgetSnapshot? = null,
)

class HomeViewModel : ViewModel() {
    private val repository = MockVelaRepository

    val uiState: StateFlow<HomeUiState> = combine(
        repository.events,
        repository.widgetSnapshot,
    ) { events, widgetSnapshot ->
        HomeUiState(
            events = events,
            widgetSnapshot = widgetSnapshot,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onImportChatClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onEventClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "Vela") })
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
                        text = "Today",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Mock schedule data is wired through the app shell so the MVP flow can be tested end to end.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onImportChatClick,
                        ) {
                            Text(text = "AI Import")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onCalendarClick,
                        ) {
                            Text(text = "Calendar")
                        }
                    }
                }
            }

            item {
                uiState.widgetSnapshot?.let { snapshot ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = snapshot.title,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = snapshot.nextEvent?.title ?: "No upcoming event",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${snapshot.todayEvents.size} event(s) today | ${snapshot.pendingCandidateCount} selected import candidate(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "Updated ${snapshot.generatedAt.toDisplayTime()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Upcoming",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (uiState.events.isEmpty()) {
                item {
                    EmptyHomeCard(onImportChatClick = onImportChatClick)
                }
            } else {
                items(uiState.events, key = { it.id }) { event ->
                    EventRow(
                        event = event,
                        onClick = { onEventClick(event.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: Event,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.startAt.toDisplayDate(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        event.startAt.toDisplayTime(),
                        event.location?.name,
                    ).joinToString(" | "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClick) {
                Text(text = "Details")
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(onImportChatClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "No events yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Start with a mock AI import to populate the local calendar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onImportChatClick) {
                Text(text = "Go to import")
            }
        }
    }
}

private fun String.toDisplayDate(): String = substringBefore("T")

private fun String.toDisplayTime(): String = substringAfter("T", this)
    .take(5)
