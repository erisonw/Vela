package com.vela.app.data.repository

import com.vela.app.data.ai.AiInputAttachment
import com.vela.app.data.model.Event
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.ImportSession
import com.vela.app.data.model.UserPreferences
import com.vela.app.data.model.WidgetSnapshot
import kotlinx.coroutines.flow.StateFlow

interface VelaRepository {
    val importSession: StateFlow<ImportSession>
    val eventCandidates: StateFlow<List<EventCandidate>>
    val events: StateFlow<List<Event>>
    val widgetSnapshot: StateFlow<WidgetSnapshot>
    val userPreferences: StateFlow<UserPreferences>

    fun submitImportText(text: String): ImportSubmissionResult
    fun submitImportImage(attachment: AiInputAttachment): ImportSubmissionResult
    fun submitImportFile(attachments: List<AiInputAttachment>): ImportSubmissionResult
    fun submitImportVoice(): ImportSubmissionResult
    fun submitNaturalLanguageEdit(instruction: String): ImportSubmissionResult
    fun addManualCandidate(candidate: EventCandidate)
    fun toggleCandidateSelection(candidateId: String)
    fun updateCandidate(candidate: EventCandidate)
    fun rejectCandidate(candidateId: String)
    fun importCandidate(candidateId: String): ImportResult
    fun importSelectedCandidates(): ImportResult
    fun addEvent(event: Event)
    fun updateEvent(event: Event)
    fun deleteEvent(eventId: String)
    fun updateDefaultReminderMinutes(minutesBefore: Int?)
    fun updateWeatherLocation(latitude: Double, longitude: Double)
    fun updateAiServiceConfig(
        endpoint: String,
        apiKey: String,
        textModel: String,
        visionModel: String,
        documentModel: String,
        voiceModel: String,
    )
    fun refreshWeather()
}

data class ImportSubmissionResult(
    val isSuccess: Boolean,
    val message: String,
)

data class ImportResult(
    val importedCount: Int,
    val blockedReasons: List<String> = emptyList(),
) {
    val isSuccess: Boolean = importedCount > 0 && blockedReasons.isEmpty()
}
