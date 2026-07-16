package com.vela.app.data.repository

import com.vela.app.data.ai.AiInputAttachment
import com.vela.app.data.ai.AiVoiceRecording
import com.vela.app.data.ai.VoiceTranscriptionResult
import com.vela.app.data.model.Event
import com.vela.app.data.model.EventAdvice
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.ImportSession
import com.vela.app.data.model.ImportTarget
import com.vela.app.data.model.UserPreferences
import com.vela.app.data.model.WidgetSnapshot
import kotlinx.coroutines.flow.StateFlow

interface VelaRepository {
    val importSession: StateFlow<ImportSession>
    val eventCandidates: StateFlow<List<EventCandidate>>
    val events: StateFlow<List<Event>>
    val eventAdvices: StateFlow<Map<String, EventAdvice>>
    val widgetSnapshot: StateFlow<WidgetSnapshot>
    val userPreferences: StateFlow<UserPreferences>

    suspend fun submitImportText(text: String): ImportSubmissionResult
    suspend fun submitImportImage(attachment: AiInputAttachment): ImportSubmissionResult
    suspend fun transcribeVoice(recording: AiVoiceRecording): VoiceTranscriptionResult
    fun addManualCandidate(candidate: EventCandidate)
    fun toggleCandidateSelection(candidateId: String)
    fun updateCandidate(candidate: EventCandidate)
    fun rejectCandidate(candidateId: String)
    fun importCandidate(candidateId: String): ImportResult
    fun importSelectedCandidates(): ImportResult
    suspend fun submitFloatingImportImage(
        attachment: AiInputAttachment,
        target: ImportTarget,
    ): FloatingImportSubmissionResult
    fun importFloatingCandidates(
        candidates: List<EventCandidate>,
        target: ImportTarget,
    ): ImportResult
    fun addEvent(event: Event)
    fun updateEvent(event: Event)
    fun deleteEvent(eventId: String)
    suspend fun prepareEventAdvice(eventId: String): EventAdvice?
    fun eventAdviceFor(eventId: String): EventAdvice?
    suspend fun rescheduleReminders()
    fun updateDefaultReminderMinutes(minutesBefore: Int?)
    fun updateWeatherLocation(latitude: Double, longitude: Double)
    fun updateAiServiceConfig(
        endpoint: String,
        apiKey: String,
        textModel: String,
        visionModel: String,
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

data class FloatingImportSubmissionResult(
    val isSuccess: Boolean,
    val message: String,
    val candidates: List<EventCandidate> = emptyList(),
)
