package com.vela.app.data.repository

import android.content.Context
import com.vela.app.data.ai.AiClientFactory
import com.vela.app.data.ai.AiClientProvider
import com.vela.app.data.ai.AiEventAdviceRequest
import com.vela.app.data.ai.AiEventAdviceResult
import com.vela.app.data.ai.AiExtractionRequest
import com.vela.app.data.ai.AiExtractionResult
import com.vela.app.data.ai.AiInputAttachment
import com.vela.app.data.ai.AiInputType
import com.vela.app.data.ai.AiVoiceRecording
import com.vela.app.data.ai.VoiceTranscriptionResult
import com.vela.app.data.local.EventStore
import com.vela.app.data.local.VelaDataStore
import com.vela.app.data.model.ChatMessage
import com.vela.app.data.model.ChatMessageRole
import com.vela.app.data.model.Event
import com.vela.app.data.model.EventAdvice
import com.vela.app.data.model.EventAdviceStatus
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.EventCandidateReviewStatus
import com.vela.app.data.model.ImportSession
import com.vela.app.data.model.ImportSessionStatus
import com.vela.app.data.model.ImportTarget
import com.vela.app.data.model.UserPreferences
import com.vela.app.data.model.WidgetSnapshot
import com.vela.app.data.model.remindersFromPreset
import com.vela.app.data.model.validateEventInput
import com.vela.app.data.time.TimeProvider
import com.vela.app.data.time.VelaClock
import com.vela.app.data.weather.WeatherHint
import com.vela.app.data.weather.DefaultWeatherService
import com.vela.app.data.weather.WeatherService
import com.vela.app.notification.AndroidReminderScheduler
import com.vela.app.notification.ReminderScheduler
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DefaultVelaRepository internal constructor(
    private val eventStore: VelaDataStore,
    private val aiClientFactory: AiClientProvider,
    private val timeProvider: TimeProvider = VelaClock,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val reminderScheduler: ReminderScheduler,
    private val weatherService: WeatherService,
) : VelaRepository {
    constructor(
        context: Context,
        eventStore: VelaDataStore = EventStore(context),
        aiClientFactory: AiClientProvider = AiClientFactory(),
        timeProvider: TimeProvider = VelaClock,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) : this(
        eventStore = eventStore,
        aiClientFactory = aiClientFactory,
        timeProvider = timeProvider,
        scope = scope,
        reminderScheduler = AndroidReminderScheduler(context),
        weatherService = DefaultWeatherService,
    )

    @Volatile
    private var cachedWeatherHint: WeatherHint

    private var weatherRefreshJob: Job? = null

    private val _eventCandidates = MutableStateFlow(emptyList<EventCandidate>())
    override val eventCandidates: StateFlow<List<EventCandidate>> = _eventCandidates.asStateFlow()

    private val _events: MutableStateFlow<List<Event>>
    override val events: StateFlow<List<Event>>

    private val _eventAdvices: MutableStateFlow<Map<String, EventAdvice>>
    override val eventAdvices: StateFlow<Map<String, EventAdvice>>

    private val _importSession: MutableStateFlow<ImportSession>
    override val importSession: StateFlow<ImportSession>

    private val _userPreferences: MutableStateFlow<UserPreferences>
    override val userPreferences: StateFlow<UserPreferences>

    private val _widgetSnapshot: MutableStateFlow<WidgetSnapshot>
    override val widgetSnapshot: StateFlow<WidgetSnapshot>

    init {
        val now = timeProvider.nowText()
        val initialEvents = eventStore.loadEvents().orEmpty().sortedBy { it.startAt }
        val preferences = eventStore.loadUserPreferences()

        _events = MutableStateFlow(initialEvents)
        events = _events.asStateFlow()
        _eventAdvices = MutableStateFlow(eventStore.loadEventAdvices())
        eventAdvices = _eventAdvices.asStateFlow()
        _userPreferences = MutableStateFlow(preferences)
        userPreferences = _userPreferences.asStateFlow()
        _importSession = MutableStateFlow(
            ImportSession(
                id = "session-${UUID.randomUUID()}",
                createdAt = now,
                updatedAt = now,
                status = ImportSessionStatus.Draft,
            ),
        )
        importSession = _importSession.asStateFlow()

        cachedWeatherHint = weatherService.pendingForCoordinates(
            latitude = preferences.weatherLatitude,
            longitude = preferences.weatherLongitude,
        )
        _widgetSnapshot = MutableStateFlow(createWidgetSnapshot(initialEvents, emptyList()))
        widgetSnapshot = _widgetSnapshot.asStateFlow()

        reminderScheduler.ensureReady()
        scope.launch {
            reminderScheduler.scheduleAll(_events.value)
        }
        refreshWeatherAsync()
    }

    override suspend fun rescheduleReminders() {
        reminderScheduler.scheduleAll(_events.value)
    }

    override suspend fun submitImportText(text: String): ImportSubmissionResult {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            return ImportSubmissionResult(
                isSuccess = false,
                message = "请输入需要导入的日程内容。",
            )
        }

        appendImportMessage(
            role = ChatMessageRole.User,
            content = trimmedText,
        )
        val request = newExtractionRequest(
            type = AiInputType.Text,
            text = trimmedText,
        )
        // 在仓库 scope 中执行：调用方（如页面 ViewModel）中途销毁也不丢结果。
        return scope.async {
            handleExtractionResult(extractionClient().extract(request))
        }.await()
    }

    override suspend fun submitImportImage(attachment: AiInputAttachment): ImportSubmissionResult {
        appendImportMessage(
            role = ChatMessageRole.User,
            content = "图片上传：${attachment.fileName}",
        )
        val request = newExtractionRequest(
            type = AiInputType.Image,
            attachments = listOf(attachment),
        )
        return scope.async {
            handleExtractionResult(extractionClient().extract(request))
        }.await()
    }

    private fun handleExtractionResult(extractionResult: AiExtractionResult): ImportSubmissionResult {
        val assistantText = when (extractionResult) {
            is AiExtractionResult.Success -> extractionResult.summary
            is AiExtractionResult.Failure -> extractionResult.message
        }
        appendImportMessage(
            role = ChatMessageRole.Assistant,
            content = assistantText,
        )
        if (extractionResult is AiExtractionResult.Success) {
            addExtractedCandidates(extractionResult)
        }
        syncSessionAndWidgetSnapshot()
        return ImportSubmissionResult(
            isSuccess = extractionResult is AiExtractionResult.Success,
            message = assistantText,
        )
    }

    override suspend fun transcribeVoice(recording: AiVoiceRecording): VoiceTranscriptionResult =
        aiClientFactory.voiceClient(_userPreferences.value).transcribe(recording)

    override fun addManualCandidate(candidate: EventCandidate) {
        val manualCandidate = candidate.copy(
            id = "candidate-manual-${UUID.randomUUID()}",
            sourceMessageIds = emptyList(),
            sourceEvidence = "用户本地填写",
            reminders = candidate.reminders.ifEmpty {
                remindersFromPreset(_userPreferences.value.defaultReminderMinutes)
            },
            confidence = null,
            isSelectedForImport = true,
            reviewStatus = EventCandidateReviewStatus.Edited,
            missingFields = candidate.findMissingFields(),
        )
        _eventCandidates.update { candidates ->
            candidates + manualCandidate
        }
        appendImportMessage(
            role = ChatMessageRole.System,
            content = "已本地新建候选日程：${manualCandidate.title}",
        )
        _importSession.update { session ->
            session.copy(status = ImportSessionStatus.ReadyForReview)
        }
        syncSessionAndWidgetSnapshot()
    }

    private fun appendImportMessage(
        role: ChatMessageRole,
        content: String,
    ) {
        _importSession.update { session ->
            session.copy(
                updatedAt = timeProvider.nowText(),
                messages = session.messages + ChatMessage(
                    id = "message-${UUID.randomUUID()}",
                    sessionId = session.id,
                    role = role,
                    content = content,
                    createdAt = timeProvider.nowText(),
                ),
            )
        }
    }

    private fun addExtractedCandidates(extractionResult: AiExtractionResult.Success) {
        val candidates = extractionResult.candidates.map { candidate ->
            candidate.copy(
                id = "candidate-ai-${UUID.randomUUID()}",
                reminders = candidate.reminders.ifEmpty {
                    remindersFromPreset(_userPreferences.value.defaultReminderMinutes)
                },
                missingFields = candidate.findMissingFields(),
            )
        }
        _eventCandidates.update { currentCandidates ->
            currentCandidates + candidates
        }
    }

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

    override fun updateCandidate(candidate: EventCandidate) {
        _eventCandidates.update { candidates ->
            candidates.map { currentCandidate ->
                if (currentCandidate.id == candidate.id) {
                    candidate.copy(
                        reviewStatus = EventCandidateReviewStatus.Edited,
                        missingFields = candidate.findMissingFields(),
                    )
                } else {
                    currentCandidate
                }
            }
        }
        syncSessionAndWidgetSnapshot()
    }

    override fun rejectCandidate(candidateId: String) {
        _eventCandidates.update { candidates ->
            candidates.map { candidate ->
                if (candidate.id == candidateId) {
                    candidate.copy(
                        isSelectedForImport = false,
                        reviewStatus = EventCandidateReviewStatus.Rejected,
                    )
                } else {
                    candidate
                }
            }
        }
        syncSessionAndWidgetSnapshot()
    }

    override fun importSelectedCandidates(): ImportResult {
        val selectedCandidates = _eventCandidates.value
            .filter { it.isSelectedForImport }
            .filterNot { it.reviewStatus == EventCandidateReviewStatus.Rejected }
            .filterNot { it.reviewStatus == EventCandidateReviewStatus.Imported }

        return importCandidates(selectedCandidates)
    }

    override fun importCandidate(candidateId: String): ImportResult {
        val candidate = _eventCandidates.value
            .firstOrNull {
                it.id == candidateId &&
                    it.reviewStatus != EventCandidateReviewStatus.Rejected &&
                    it.reviewStatus != EventCandidateReviewStatus.Imported
            }
            ?: return ImportResult(
                importedCount = 0,
                blockedReasons = listOf("未找到可导入的候选日程。"),
            )

        return importCandidates(listOf(candidate))
    }

    override suspend fun submitFloatingImportImage(
        attachment: AiInputAttachment,
        target: ImportTarget,
    ): FloatingImportSubmissionResult {
        val extractionResult = extractionClient().extract(
            newExtractionRequest(
                type = AiInputType.Image,
                attachments = listOf(attachment),
                sessionId = "floating-${target.name.lowercase()}-${UUID.randomUUID()}",
                target = target,
            ),
        )

        return when (extractionResult) {
            is AiExtractionResult.Success -> FloatingImportSubmissionResult(
                isSuccess = true,
                message = extractionResult.summary,
                candidates = extractionResult.candidates.map { candidate ->
                    candidate.copy(
                        id = "candidate-floating-${target.name.lowercase()}-${UUID.randomUUID()}",
                        isSelectedForImport = true,
                        reminders = candidate.reminders.ifEmpty {
                            remindersFromPreset(_userPreferences.value.defaultReminderMinutes)
                        },
                        missingFields = candidate.findMissingFields(),
                    )
                },
            )

            is AiExtractionResult.Failure -> FloatingImportSubmissionResult(
                isSuccess = false,
                message = extractionResult.message,
            )
        }
    }

    override fun importFloatingCandidates(
        candidates: List<EventCandidate>,
        target: ImportTarget,
    ): ImportResult = importCandidates(
        selectedCandidates = candidates,
        target = target,
        updateImportSession = false,
    )

    private fun importCandidates(
        selectedCandidates: List<EventCandidate>,
        target: ImportTarget = ImportTarget.Schedule,
        updateImportSession: Boolean = true,
    ): ImportResult {
        if (selectedCandidates.isEmpty()) {
            return ImportResult(
                importedCount = 0,
                blockedReasons = listOf("请先勾选至少一条候选日程。"),
            )
        }

        val blockedReasons = selectedCandidates.flatMap { candidate ->
            candidate.blockingReasons()
        }
        if (blockedReasons.isNotEmpty()) {
            return ImportResult(
                importedCount = 0,
                blockedReasons = blockedReasons,
            )
        }

        val importedEvents = selectedCandidates.map { candidate ->
            Event(
                id = "event-${candidate.id.removePrefix("candidate-")}",
                title = candidate.title,
                startAt = candidate.startAt,
                endAt = candidate.endAt,
                timezone = candidate.timezone,
                location = candidate.location,
                description = candidate.description,
                reminders = candidate.reminders,
                sourceSessionId = if (updateImportSession) {
                    _importSession.value.id
                } else {
                    "floating-${target.name.lowercase()}"
                },
                isCourse = target == ImportTarget.Timetable,
            )
        }

        _events.update { currentEvents ->
            (currentEvents + importedEvents).distinctBy { it.id }.sortedBy { it.startAt }
        }
        persistEvents()
        importedEvents.forEach { event ->
            reminderScheduler.scheduleEvent(event)
        }
        if (updateImportSession) {
            _eventCandidates.update { candidates ->
                candidates.map { candidate ->
                    if (selectedCandidates.any { it.id == candidate.id }) {
                        candidate.copy(
                            isSelectedForImport = false,
                            reviewStatus = EventCandidateReviewStatus.Imported,
                        )
                    } else {
                        candidate
                    }
                }
            }
            _importSession.update { session ->
                session.copy(status = ImportSessionStatus.Imported)
            }
        }
        syncSessionAndWidgetSnapshot()

        return ImportResult(importedCount = importedEvents.size)
    }

    override fun addEvent(event: Event) {
        val previousEvent = _events.value.firstOrNull { it.id == event.id }
        _events.update { events ->
            (events.filterNot { it.id == event.id } + event).sortedBy { it.startAt }
        }
        if (previousEvent != null) {
            clearEventAdvice(event.id)
        }
        persistEvents()
        reminderScheduler.cancelEvent(
            eventId = event.id,
            extraReminderMinutes = (previousEvent?.reminders.orEmpty() + event.reminders)
                .map { it.minutesBefore },
        )
        reminderScheduler.scheduleEvent(event)
        syncSessionAndWidgetSnapshot()
    }

    override fun updateEvent(event: Event) {
        val previousEvent = _events.value.firstOrNull { it.id == event.id } ?: return
        val reminderMinutesToCancel = (previousEvent.reminders + event.reminders)
            .map { it.minutesBefore }

        _events.update { events ->
            events.map { currentEvent ->
                if (currentEvent.id == event.id) {
                    event
                } else {
                    currentEvent
                }
            }.sortedBy { it.startAt }
        }
        clearEventAdvice(event.id)
        persistEvents()
        reminderScheduler.cancelEvent(
            eventId = event.id,
            extraReminderMinutes = reminderMinutesToCancel,
        )
        reminderScheduler.scheduleEvent(event)
        syncSessionAndWidgetSnapshot()
    }

    override fun deleteEvent(eventId: String) {
        val removedEvent = _events.value.firstOrNull { it.id == eventId }
        reminderScheduler.cancelEvent(
            eventId = eventId,
            extraReminderMinutes = removedEvent?.reminders?.map { it.minutesBefore }.orEmpty(),
        )
        _events.update { events ->
            events.filterNot { it.id == eventId }
        }
        clearEventAdvice(eventId)
        persistEvents()
        syncSessionAndWidgetSnapshot()
    }

    override suspend fun prepareEventAdvice(eventId: String): EventAdvice? {
        val event = _events.value.firstOrNull { it.id == eventId } ?: return null
        val weatherText = listOf(cachedWeatherHint.weatherHint, cachedWeatherHint.prepHint)
            .filter { it.isNotBlank() }
            .joinToString("；")
        val sourceHash = event.adviceSourceHash(weatherText)
        val existing = _eventAdvices.value[eventId]
        if (
            existing?.status == EventAdviceStatus.Ready &&
            existing.sourceHash == sourceHash &&
            existing.adviceText.isNullOrBlank().not()
        ) {
            return existing
        }

        upsertEventAdvice(
            EventAdvice(
                eventId = eventId,
                sourceHash = sourceHash,
                status = EventAdviceStatus.Pending,
            ),
        )

        return scope.async {
            val result = aiClientFactory.adviceClient(_userPreferences.value).generate(
                AiEventAdviceRequest(
                    event = event,
                    weatherHint = weatherText,
                    timezone = timeProvider.zone().id,
                ),
            )
            val advice = when (result) {
                is AiEventAdviceResult.Success -> EventAdvice(
                    eventId = eventId,
                    adviceText = result.adviceText.trim(),
                    generatedAt = timeProvider.nowText(),
                    sourceHash = sourceHash,
                    status = EventAdviceStatus.Ready,
                )

                is AiEventAdviceResult.Failure -> EventAdvice(
                    eventId = eventId,
                    adviceText = null,
                    generatedAt = timeProvider.nowText(),
                    sourceHash = sourceHash,
                    status = EventAdviceStatus.Failed,
                )
            }
            upsertEventAdvice(advice)
            advice
        }.await()
    }

    override fun eventAdviceFor(eventId: String): EventAdvice? =
        _eventAdvices.value[eventId]

    override fun updateDefaultReminderMinutes(minutesBefore: Int?) {
        _userPreferences.update { preferences ->
            preferences.copy(defaultReminderMinutes = minutesBefore)
        }
        persistUserPreferences()
    }

    override fun updateWeatherLocation(latitude: Double, longitude: Double) {
        _userPreferences.update { preferences ->
            preferences.copy(
                weatherLatitude = latitude,
                weatherLongitude = longitude,
                weatherUpdatedAt = timeProvider.nowText(),
            )
        }
        persistUserPreferences()
        refreshWeatherAsync()
    }

    override fun updateAiServiceConfig(
        endpoint: String,
        apiKey: String,
        textModel: String,
        visionModel: String,
        voiceModel: String,
    ) {
        _userPreferences.update { preferences ->
            val trimmedEndpoint = endpoint.trim()
            val trimmedTextModel = textModel.trim()
            preferences.copy(
                aiEndpoint = trimmedEndpoint,
                aiApiKey = apiKey.trim(),
                aiTextModel = trimmedTextModel,
                aiVisionModel = visionModel.trim(),
                aiVoiceModel = voiceModel.trim(),
                aiServiceStatusText = EventStore.aiServiceStatusText(
                    endpoint = trimmedEndpoint,
                    textModel = trimmedTextModel,
                ),
            )
        }
        persistUserPreferences()
    }

    override fun refreshWeather() {
        refreshWeatherAsync()
    }

    @Synchronized
    private fun refreshWeatherAsync() {
        val preferences = _userPreferences.value
        val latitude = preferences.weatherLatitude
        val longitude = preferences.weatherLongitude
        cachedWeatherHint = weatherService.pendingForCoordinates(
            latitude = latitude,
            longitude = longitude,
        )
        syncSessionAndWidgetSnapshot()
        if (latitude == null || longitude == null) {
            return
        }

        weatherRefreshJob?.cancel()
        weatherRefreshJob = scope.launch {
            val weatherHint = weatherService.forCoordinates(latitude, longitude)
            ensureActive()
            cachedWeatherHint = weatherHint
            syncSessionAndWidgetSnapshot()
        }
    }

    private fun syncSessionAndWidgetSnapshot() {
        _importSession.update { session ->
            session.copy(
                updatedAt = timeProvider.nowText(),
                candidates = _eventCandidates.value.filterNot {
                    it.reviewStatus == EventCandidateReviewStatus.Rejected
                },
            )
        }
        _widgetSnapshot.value = createWidgetSnapshot(_events.value, _eventCandidates.value)
    }

    private fun createWidgetSnapshot(
        events: List<Event>,
        candidates: List<EventCandidate>,
    ): WidgetSnapshot {
        val now = timeProvider.now()
        val sortedEvents = events.sortedBy { it.startAt }
        val todayEvents = sortedEvents.filter {
            it.startAt.toOffsetDateTimeOrNull()?.toLocalDate() == now.toLocalDate()
        }
        val remainingEvents = todayEvents.filter { event ->
            val endAt = event.endAt?.toOffsetDateTimeOrNull()
                ?: event.startAt.toOffsetDateTimeOrNull()
            endAt != null && endAt.isAfter(now)
        }
        val weatherTargetEvents = remainingEvents.ifEmpty {
            sortedEvents.filter {
                it.startAt.toOffsetDateTimeOrNull()?.toLocalDate() == now.toLocalDate().plusDays(1)
            }
        }
        val hint = weatherService.withEventPrepHint(
            weatherHint = cachedWeatherHint,
            events = weatherTargetEvents,
        )
        return WidgetSnapshot(
            generatedAt = now.toString(),
            title = "今日 Vela",
            todayEvents = todayEvents,
            upcomingEvents = sortedEvents,
            nextEvent = sortedEvents.firstOrNull {
                val endAt = it.endAt?.toOffsetDateTimeOrNull()
                    ?: it.startAt.toOffsetDateTimeOrNull()
                endAt != null && endAt.isAfter(now)
            } ?: sortedEvents.firstOrNull(),
            remainingEventCount = remainingEvents.size,
            weatherHint = hint.weatherHint,
            prepHint = hint.prepHint,
            weatherStatusText = hint.statusText,
            pendingCandidateCount = candidates.count {
                it.isSelectedForImport &&
                    it.reviewStatus != EventCandidateReviewStatus.Imported &&
                    it.reviewStatus != EventCandidateReviewStatus.Rejected
            },
        )
    }

    private fun newExtractionRequest(
        type: AiInputType,
        text: String? = null,
        attachments: List<AiInputAttachment> = emptyList(),
        sessionId: String = _importSession.value.id,
        target: ImportTarget = ImportTarget.Schedule,
    ): AiExtractionRequest =
        AiExtractionRequest(
            sessionId = sessionId,
            type = type,
            text = text,
            attachments = attachments,
            attachmentIds = attachments.map { it.fileName },
            timezone = timeProvider.zone().id,
            target = target,
        )

    private fun extractionClient() = aiClientFactory.extractionClient(_userPreferences.value)

    private fun String.toOffsetDateTimeOrNull(): OffsetDateTime? =
        runCatching { OffsetDateTime.parse(this) }.getOrNull()

    private fun persistEvents() {
        eventStore.saveEvents(_events.value)
    }

    private fun upsertEventAdvice(advice: EventAdvice) {
        _eventAdvices.update { advices ->
            advices + (advice.eventId to advice)
        }
        eventStore.saveEventAdvices(_eventAdvices.value.values)
    }

    private fun clearEventAdvice(eventId: String) {
        _eventAdvices.update { advices ->
            advices - eventId
        }
        eventStore.saveEventAdvices(_eventAdvices.value.values)
    }

    private fun persistUserPreferences() {
        eventStore.saveUserPreferences(_userPreferences.value)
    }

    private fun EventCandidate.blockingReasons(): List<String> = buildList {
        val validation = validateEventInput(title, startAt, endAt)
        validation.errors.forEach { error ->
            add("${title.ifBlank { "未命名日程" }}：$error")
        }
    }

    private fun EventCandidate.findMissingFields(): List<String> = buildList {
        if (title.isBlank()) {
            add("标题")
        }
        if (startAt.isBlank()) {
            add("开始时间")
        }
        if (endAt.isNullOrBlank()) {
            add("结束时间")
        }
        if (location?.name.isNullOrBlank()) {
            add("地点")
        }
    }

    private fun Event.adviceSourceHash(weatherText: String): String =
        listOf(
            title,
            startAt,
            endAt.orEmpty(),
            timezone.orEmpty(),
            location?.name.orEmpty(),
            location?.address.orEmpty(),
            description.orEmpty(),
            weatherText,
        ).joinToString("|").hashCode().toString()

}
