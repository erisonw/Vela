package com.vela.app.feature.importchat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.data.model.ChatMessage
import com.vela.app.data.model.ChatMessageRole
import com.vela.app.data.mock.MockVelaRepository
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.ImportSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ImportChatUiState(
    val session: ImportSession? = null,
    val candidates: List<EventCandidate> = emptyList(),
) {
    val highlightedDates: List<HighlightedImportDate> = candidates
        .asSequence()
        .filter { it.isSelectedForImport }
        .map { it.startAt.toDisplayDate() }
        .groupingBy { it }
        .eachCount()
        .map { (date, count) -> HighlightedImportDate(date = date, selectedCount = count) }
        .sortedBy { it.date }
        .toList()

    val selectedCandidateCount: Int = candidates.count { it.isSelectedForImport }
}

data class HighlightedImportDate(
    val date: String,
    val selectedCount: Int,
)

class ImportChatViewModel : ViewModel() {
    private val repository = MockVelaRepository

    val uiState: StateFlow<ImportChatUiState> = combine(
        repository.importSession,
        repository.eventCandidates,
    ) { session, candidates ->
        ImportChatUiState(
            session = session,
            candidates = candidates,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ImportChatUiState(),
    )

    fun toggleCandidate(candidateId: String) {
        repository.toggleCandidateSelection(candidateId)
    }

    fun importSelected() {
        repository.importSelectedCandidates()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportChatScreen(
    onCalendarClick: () -> Unit,
    viewModel: ImportChatViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "AI Import") })
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
                        text = "Chat import review",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Review the AI candidates before they become real app events.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SessionStatusRow(uiState = uiState)
                }
            }

            uiState.session?.messages?.let { messages ->
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }
            }

            item {
                MiniCalendar(dates = uiState.highlightedDates)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = uiState.selectedCandidateCount > 0,
                        onClick = {
                            viewModel.importSelected()
                            onCalendarClick()
                        },
                    ) {
                        Text(text = "Import selected")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onCalendarClick,
                    ) {
                        Text(text = "Open calendar")
                    }
                }
            }

            items(uiState.candidates, key = { it.id }) { candidate ->
                CandidateRow(
                    candidate = candidate,
                    onToggle = { viewModel.toggleCandidate(candidate.id) },
                )
            }
        }
    }
}

@Composable
private fun SessionStatusRow(uiState: ImportChatUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusTile(
            label = "Status",
            value = uiState.session?.status?.name ?: "Loading",
            modifier = Modifier.weight(1f),
        )
        StatusTile(
            label = "Selected",
            value = "${uiState.selectedCandidateCount}/${uiState.candidates.size}",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusTile(
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
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = MaterialTheme.shapes.large,
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (isUser) "You" else "Vela AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MiniCalendar(dates: List<HighlightedImportDate>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Selected dates",
                style = MaterialTheme.typography.titleMedium,
            )
            if (dates.isEmpty()) {
                Text(
                    text = "No selected candidates yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(dates, key = { it.date }) { highlightedDate ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = highlightedDate.date.takeLast(5),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    text = "${highlightedDate.selectedCount} selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: EventCandidate,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = candidate.isSelectedForImport,
                onCheckedChange = { onToggle() },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${candidate.startAt.toDisplayDate()} | ${candidate.startAt.toDisplayTime()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                candidate.location?.name?.let { locationName ->
                    Text(
                        text = locationName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                candidate.confidence?.let { confidence ->
                    Text(
                        text = "Confidence ${(confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun String.toDisplayDate(): String = substringBefore("T")

private fun String.toDisplayTime(): String = substringAfter("T", this)
    .take(5)
