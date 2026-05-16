package com.vela.app.data.mock

import com.vela.app.data.model.ChatMessage
import com.vela.app.data.model.ChatMessageRole
import com.vela.app.data.model.Event
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.ImportSession
import com.vela.app.data.model.ImportSessionStatus
import com.vela.app.data.model.Location
import com.vela.app.data.model.Reminder
import com.vela.app.data.model.WidgetSnapshot
import com.vela.app.data.repository.VelaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object MockVelaRepository : VelaRepository {
    private const val MockNow = "2026-05-16T09:00:00+08:00"

    private val mockReminder = Reminder(
        id = "reminder-10-min",
        minutesBefore = 10,
        label = "10 minutes before",
    )

    private val initialCandidates = listOf(
        EventCandidate(
            id = "candidate-standup",
            title = "Product standup",
            startAt = "2026-05-16T10:00:00+08:00",
            endAt = "2026-05-16T10:30:00+08:00",
            timezone = "Asia/Shanghai",
            location = Location(name = "Vela workspace", address = "Online"),
            description = "Extracted from the mock chat import flow.",
            sourceMessageIds = listOf("message-2"),
            reminders = listOf(mockReminder),
            confidence = 0.92f,
            isSelectedForImport = true,
        ),
        EventCandidate(
            id = "candidate-design-review",
            title = "Design review",
            startAt = "2026-05-17T14:00:00+08:00",
            endAt = "2026-05-17T15:00:00+08:00",
            timezone = "Asia/Shanghai",
            location = Location(name = "Conference Room A"),
            description = "Review MVP import and calendar surfaces.",
            sourceMessageIds = listOf("message-3"),
            reminders = listOf(mockReminder),
            confidence = 0.86f,
        ),
    )

    private val initialEvents = listOf(
        Event(
            id = "event-sync",
            title = "Vela MVP sync",
            startAt = "2026-05-16T16:00:00+08:00",
            endAt = "2026-05-16T16:45:00+08:00",
            timezone = "Asia/Shanghai",
            location = Location(name = "Vela workspace", address = "Online"),
            description = "Mock imported event for the app shell.",
            reminders = listOf(mockReminder),
            sourceSessionId = "session-mock-import",
        ),
    )

    private val _eventCandidates = MutableStateFlow(initialCandidates)
    override val eventCandidates: StateFlow<List<EventCandidate>> = _eventCandidates.asStateFlow()

    private val _events = MutableStateFlow(initialEvents)
    override val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _importSession = MutableStateFlow(
        ImportSession(
            id = "session-mock-import",
            createdAt = MockNow,
            updatedAt = MockNow,
            status = ImportSessionStatus.ReadyForReview,
            messages = listOf(
                ChatMessage(
                    id = "message-1",
                    sessionId = "session-mock-import",
                    role = ChatMessageRole.User,
                    content = "Please turn this chat into calendar events.",
                    createdAt = "2026-05-16T08:58:00+08:00",
                ),
                ChatMessage(
                    id = "message-2",
                    sessionId = "session-mock-import",
                    role = ChatMessageRole.Assistant,
                    content = "I found one likely product standup today.",
                    createdAt = "2026-05-16T08:59:00+08:00",
                ),
            ),
            candidates = initialCandidates,
        ),
    )
    override val importSession: StateFlow<ImportSession> = _importSession.asStateFlow()

    private val _widgetSnapshot = MutableStateFlow(createWidgetSnapshot(initialEvents, initialCandidates))
    override val widgetSnapshot: StateFlow<WidgetSnapshot> = _widgetSnapshot.asStateFlow()

    override fun toggleCandidateSelection(candidateId: String) {
        _eventCandidates.update { candidates ->
            candidates.map { candidate ->
                if (candidate.id == candidateId) {
                    candidate.copy(isSelectedForImport = !candidate.isSelectedForImport)
                } else {
                    candidate
                }
            }
        }
        syncSessionAndWidgetSnapshot()
    }

    override fun importSelectedCandidates() {
        val importedEvents = _eventCandidates.value
            .filter { it.isSelectedForImport }
            .map { candidate ->
                Event(
                    id = candidate.id.replace("candidate", "event"),
                    title = candidate.title,
                    startAt = candidate.startAt,
                    endAt = candidate.endAt,
                    timezone = candidate.timezone,
                    location = candidate.location,
                    description = candidate.description,
                    reminders = candidate.reminders,
                    sourceSessionId = _importSession.value.id,
                )
            }

        if (importedEvents.isNotEmpty()) {
            _events.update { currentEvents ->
                (currentEvents + importedEvents).distinctBy { it.id }.sortedBy { it.startAt }
            }
            _importSession.update { session ->
                session.copy(
                    updatedAt = MockNow,
                    status = ImportSessionStatus.Imported,
                    candidates = _eventCandidates.value,
                )
            }
            syncSessionAndWidgetSnapshot()
        }
    }

    private fun syncSessionAndWidgetSnapshot() {
        _importSession.update { session ->
            session.copy(
                updatedAt = MockNow,
                candidates = _eventCandidates.value,
            )
        }
        _widgetSnapshot.value = createWidgetSnapshot(_events.value, _eventCandidates.value)
    }

    private fun createWidgetSnapshot(
        events: List<Event>,
        candidates: List<EventCandidate>,
    ): WidgetSnapshot {
        val sortedEvents = events.sortedBy { it.startAt }
        return WidgetSnapshot(
            generatedAt = MockNow,
            title = "Today in Vela",
            todayEvents = sortedEvents.filter { it.startAt.startsWith("2026-05-16") },
            nextEvent = sortedEvents.firstOrNull(),
            pendingCandidateCount = candidates.count { it.isSelectedForImport },
        )
    }
}
