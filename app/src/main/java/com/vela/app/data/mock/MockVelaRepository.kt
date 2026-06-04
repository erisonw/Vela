package com.vela.app.data.mock

import android.content.Context
import com.vela.app.data.ai.AiEventAdviceRequest
import com.vela.app.data.ai.AiEventAdviceResult
import com.vela.app.data.ai.AiInputAttachment
import com.vela.app.data.ai.AiVoiceRecording
import com.vela.app.data.ai.AiExtractionRequest
import com.vela.app.data.ai.AiExtractionResult
import com.vela.app.data.ai.AiInputType
import com.vela.app.data.ai.HttpAiEventAdviceClient
import com.vela.app.data.ai.HttpAiExtractionClient
import com.vela.app.data.ai.HttpVoiceTranscriptionClient
import com.vela.app.data.ai.UnavailableAiEventAdviceClient
import com.vela.app.data.ai.UnavailableAiExtractionClient
import com.vela.app.data.ai.UnavailableVoiceTranscriptionClient
import com.vela.app.data.ai.VoiceTranscriptionResult
import com.vela.app.data.model.ChatMessage
import com.vela.app.data.model.ChatMessageRole
import com.vela.app.data.model.DefaultReminderMinutes
import com.vela.app.data.model.Event
import com.vela.app.data.model.EventAdvice
import com.vela.app.data.model.EventAdviceStatus
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.EventCandidateReviewStatus
import com.vela.app.data.model.ImportSession
import com.vela.app.data.model.ImportSessionStatus
import com.vela.app.data.model.Location
import com.vela.app.data.model.Reminder
import com.vela.app.data.model.UserPreferences
import com.vela.app.data.model.WidgetSnapshot
import com.vela.app.data.model.remindersFromPreset
import com.vela.app.data.model.validateEventInput
import com.vela.app.data.repository.ImportSubmissionResult
import com.vela.app.data.repository.ImportResult
import com.vela.app.data.repository.VelaRepository
import com.vela.app.data.weather.WeatherHint
import com.vela.app.data.weather.WeatherHintProvider
import com.vela.app.notification.EventNotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.concurrent.thread

object MockVelaRepository : VelaRepository {
    private const val MockNow = "2026-05-16T09:00:00+08:00"
    private const val PreferencesName = "vela_local_store"
    private const val EventsKey = "events_json"
    private const val EventAdvicesKey = "event_advices_json"
    private const val DefaultReminderKey = "default_reminder_minutes"
    private const val WeatherLatitudeKey = "weather_latitude"
    private const val WeatherLongitudeKey = "weather_longitude"
    private const val WeatherUpdatedAtKey = "weather_updated_at"
    private const val AiEndpointKey = "ai_endpoint"
    private const val AiApiKeyKey = "ai_api_key"
    private const val AiTextModelKey = "ai_text_model"
    private const val AiVisionModelKey = "ai_vision_model"
    private const val AiVoiceModelKey = "ai_voice_model"
    private const val NoReminderValue = -1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var hasInitialized = false

    @Volatile
    private var cachedWeatherHint: WeatherHint = WeatherHintProvider.pendingForCoordinates(null, null)

    @Volatile
    private var weatherRefreshGeneration = 0

    private val mockReminder = Reminder(
        id = "reminder-10-min",
        minutesBefore = 10,
        label = "提前 10 分钟",
    )

    private val initialCandidates = emptyList<EventCandidate>()

    private val initialEvents = listOf(
        Event(
            id = "event-english",
            title = "大学英语",
            startAt = "2026-05-16T09:55:00+08:00",
            endAt = "2026-05-16T12:20:00+08:00",
            timezone = "Asia/Shanghai",
            location = Location(name = "教学楼 402"),
            description = "小组件排版使用的模拟课程。",
            reminders = listOf(mockReminder),
        ),
        Event(
            id = "event-weekly",
            title = "中心例会",
            startAt = "2026-05-16T12:40:00+08:00",
            endAt = "2026-05-16T13:00:00+08:00",
            timezone = "Asia/Shanghai",
            location = Location(name = "A 会议室"),
            description = "小组件排版使用的模拟会议。",
            reminders = listOf(mockReminder),
        ),
        Event(
            id = "event-sync",
            title = "Vela MVP 同步会",
            startAt = "2026-05-16T16:00:00+08:00",
            endAt = "2026-05-16T16:45:00+08:00",
            timezone = "Asia/Shanghai",
            location = Location(name = "Vela 工作区", address = "线上会议"),
            description = "用于 App 框架联调的模拟已导入日程。",
            reminders = listOf(mockReminder),
            sourceSessionId = "session-mock-import",
        ),
        Event(
            id = "event-policy",
            title = "形式与政策",
            startAt = "2026-05-16T19:30:00+08:00",
            endAt = "2026-05-16T21:05:00+08:00",
            timezone = "Asia/Shanghai",
            location = Location(name = "教学楼 201"),
            description = "小组件排版使用的模拟课程。",
            reminders = listOf(mockReminder),
        ),
        Event(
            id = "event-tomorrow-design",
            title = "设计评审",
            startAt = "2026-05-17T10:00:00+08:00",
            endAt = "2026-05-17T11:00:00+08:00",
            timezone = "Asia/Shanghai",
            location = Location(name = "A 会议室"),
            description = "明日模拟日程。",
            reminders = listOf(mockReminder),
        ),
        Event(
            id = "event-tomorrow-run",
            title = "户外跑步",
            startAt = "2026-05-17T18:30:00+08:00",
            endAt = "2026-05-17T19:20:00+08:00",
            timezone = "Asia/Shanghai",
            location = Location(name = "滨河公园"),
            description = "明日模拟日程。",
            reminders = listOf(mockReminder),
        ),
    )

    private val _eventCandidates = MutableStateFlow(initialCandidates)
    override val eventCandidates: StateFlow<List<EventCandidate>> = _eventCandidates.asStateFlow()

    private val _events = MutableStateFlow(initialEvents)
    override val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _eventAdvices = MutableStateFlow<Map<String, EventAdvice>>(emptyMap())
    override val eventAdvices: StateFlow<Map<String, EventAdvice>> = _eventAdvices.asStateFlow()

    private val _importSession = MutableStateFlow(
        ImportSession(
            id = "session-mock-import",
            createdAt = MockNow,
            updatedAt = MockNow,
            status = ImportSessionStatus.Draft,
            messages = emptyList(),
            candidates = initialCandidates,
        ),
    )
    override val importSession: StateFlow<ImportSession> = _importSession.asStateFlow()

    private val _userPreferences = MutableStateFlow(UserPreferences())
    override val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()

    private val _widgetSnapshot = MutableStateFlow(createWidgetSnapshot(initialEvents, initialCandidates))
    override val widgetSnapshot: StateFlow<WidgetSnapshot> = _widgetSnapshot.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        if (!hasInitialized) {
            loadPersistedEvents(applicationContext)?.let { savedEvents ->
                _events.value = savedEvents.sortedBy { it.startAt }
            }
            _eventAdvices.value = loadEventAdvices(applicationContext)
            val preferences = loadUserPreferences(applicationContext)
            _userPreferences.value = preferences
            cachedWeatherHint = WeatherHintProvider.pendingForCoordinates(
                latitude = preferences.weatherLatitude,
                longitude = preferences.weatherLongitude,
            )
            EventNotificationScheduler.ensureChannel(applicationContext)
            hasInitialized = true
            scheduleAllRemindersAsync(applicationContext, _events.value)
            refreshWeatherAsync()
        }
        syncSessionAndWidgetSnapshot()
    }

    fun rescheduleReminders(context: Context) {
        val applicationContext = context.applicationContext
        initialize(applicationContext)
        scheduleAllRemindersAsync(applicationContext, _events.value)
    }

    override fun submitImportText(text: String): ImportSubmissionResult {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            return ImportSubmissionResult(
                isSuccess = false,
                message = "请输入需要导入的日程内容。",
            )
        }

        val session = _importSession.value
        val messageIndex = session.messages.size + 1
        val userMessageId = "message-$messageIndex"
        val assistantMessageId = "message-${messageIndex + 1}"

        val extractionResult = aiExtractionClient().extract(
            AiExtractionRequest(
                sessionId = session.id,
                type = AiInputType.Text,
                text = trimmedText,
            ),
        )
        val assistantText = when (extractionResult) {
            is AiExtractionResult.Success -> extractionResult.summary
            is AiExtractionResult.Failure -> extractionResult.message
        }

        _importSession.update { currentSession ->
            currentSession.copy(
                updatedAt = MockNow,
                messages = currentSession.messages + listOf(
                    ChatMessage(
                        id = userMessageId,
                        sessionId = currentSession.id,
                        role = ChatMessageRole.User,
                        content = trimmedText,
                        createdAt = MockNow,
                    ),
                    ChatMessage(
                        id = assistantMessageId,
                        sessionId = currentSession.id,
                        role = ChatMessageRole.Assistant,
                        content = assistantText,
                        createdAt = MockNow,
                    ),
                ),
                candidates = _eventCandidates.value,
            )
        }
        if (extractionResult is AiExtractionResult.Success) {
            addExtractedCandidates(extractionResult)
        }
        syncSessionAndWidgetSnapshot()
        return ImportSubmissionResult(
            isSuccess = extractionResult is AiExtractionResult.Success,
            message = assistantText,
        )
    }

    override fun submitImportImage(attachment: AiInputAttachment): ImportSubmissionResult {
        val session = _importSession.value
        val extractionResult = aiExtractionClient().extract(
            AiExtractionRequest(
                sessionId = session.id,
                type = AiInputType.Image,
                attachments = listOf(attachment),
                attachmentIds = listOf(attachment.fileName),
            ),
        )
        val assistantText = when (extractionResult) {
            is AiExtractionResult.Success -> extractionResult.summary
            is AiExtractionResult.Failure -> extractionResult.message
        }
        _importSession.update { currentSession ->
            currentSession.copy(
                updatedAt = MockNow,
                messages = currentSession.messages + listOf(
                    ChatMessage(
                        id = "message-${session.messages.size + 1}",
                        sessionId = currentSession.id,
                        role = ChatMessageRole.User,
                        content = "图片上传：${attachment.fileName}",
                        createdAt = MockNow,
                    ),
                    ChatMessage(
                        id = "message-${session.messages.size + 2}",
                        sessionId = currentSession.id,
                        role = ChatMessageRole.Assistant,
                        content = assistantText,
                        createdAt = MockNow,
                    ),
                ),
            )
        }
        if (extractionResult is AiExtractionResult.Success) {
            addExtractedCandidates(extractionResult)
        }
        syncSessionAndWidgetSnapshot()
        return ImportSubmissionResult(
            isSuccess = extractionResult is AiExtractionResult.Success,
            message = assistantText,
        )
    }

    override fun transcribeVoice(recording: AiVoiceRecording): VoiceTranscriptionResult =
        voiceTranscriptionClient().transcribe(recording)

    override fun submitNaturalLanguageEdit(instruction: String): ImportSubmissionResult {
        val trimmedInstruction = instruction.trim()
        if (trimmedInstruction.isBlank()) {
            return ImportSubmissionResult(
                isSuccess = false,
                message = "请输入需要修改的日程指令。",
            )
        }
        val session = _importSession.value
        val assistantText = "自然语言修改服务暂不可用。当前不会直接修改日历，请到「日程」里手动编辑。"
        _importSession.update { currentSession ->
            currentSession.copy(
                updatedAt = MockNow,
                messages = currentSession.messages + listOf(
                    ChatMessage(
                        id = "message-${session.messages.size + 1}",
                        sessionId = currentSession.id,
                        role = ChatMessageRole.User,
                        content = trimmedInstruction,
                        createdAt = MockNow,
                    ),
                    ChatMessage(
                        id = "message-${session.messages.size + 2}",
                        sessionId = currentSession.id,
                        role = ChatMessageRole.Assistant,
                        content = assistantText,
                        createdAt = MockNow,
                    ),
                ),
            )
        }
        syncSessionAndWidgetSnapshot()
        return ImportSubmissionResult(
            isSuccess = false,
            message = assistantText,
        )
    }

    override fun addManualCandidate(candidate: EventCandidate) {
        val manualCandidate = candidate.copy(
            id = "candidate-manual-${System.currentTimeMillis()}",
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
        _importSession.update { session ->
            session.copy(
                updatedAt = MockNow,
                status = ImportSessionStatus.ReadyForReview,
                messages = session.messages + ChatMessage(
                    id = "message-${session.messages.size + 1}",
                    sessionId = session.id,
                    role = ChatMessageRole.System,
                    content = "已本地新建候选日程：${manualCandidate.title}",
                    createdAt = MockNow,
                ),
                candidates = _eventCandidates.value,
            )
        }
        syncSessionAndWidgetSnapshot()
    }

    private fun addExtractedCandidates(extractionResult: AiExtractionResult.Success) {
        val timestamp = System.currentTimeMillis()
        val candidates = extractionResult.candidates.mapIndexed { index, candidate ->
            candidate.copy(
                id = "candidate-ai-$timestamp-$index",
                isSelectedForImport = candidate.isSelectedForImport,
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

    private fun importCandidates(selectedCandidates: List<EventCandidate>): ImportResult {
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

        val importedEvents = selectedCandidates
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

        _events.update { currentEvents ->
            (currentEvents + importedEvents).distinctBy { it.id }.sortedBy { it.startAt }
        }
        persistEvents()
        appContext?.let { context ->
            importedEvents.forEach { event ->
                EventNotificationScheduler.scheduleEvent(context, event)
            }
        }
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
            session.copy(
                updatedAt = MockNow,
                status = ImportSessionStatus.Imported,
                candidates = _eventCandidates.value,
            )
        }
        syncSessionAndWidgetSnapshot()

        return ImportResult(importedCount = importedEvents.size)
    }

    override fun addEvent(event: Event) {
        _events.update { events ->
            (events + event).distinctBy { it.id }.sortedBy { it.startAt }
        }
        persistEvents()
        appContext?.let { context ->
            EventNotificationScheduler.scheduleEvent(context, event)
        }
        syncSessionAndWidgetSnapshot()
    }

    override fun updateEvent(event: Event) {
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
        appContext?.let { context ->
            EventNotificationScheduler.scheduleEvent(context, event)
        }
        syncSessionAndWidgetSnapshot()
    }

    override fun deleteEvent(eventId: String) {
        appContext?.let { context ->
            EventNotificationScheduler.cancelEvent(context, eventId)
        }
        _events.update { events ->
            events.filterNot { it.id == eventId }
        }
        clearEventAdvice(eventId)
        persistEvents()
        syncSessionAndWidgetSnapshot()
    }

    override fun prepareEventAdvice(eventId: String): EventAdvice? {
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

        val result = eventAdviceClient().generate(
            AiEventAdviceRequest(
                event = event,
                weatherHint = weatherText,
            ),
        )
        val advice = when (result) {
            is AiEventAdviceResult.Success -> EventAdvice(
                eventId = eventId,
                adviceText = result.adviceText.trim(),
                generatedAt = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString(),
                sourceHash = sourceHash,
                status = EventAdviceStatus.Ready,
            )

            is AiEventAdviceResult.Failure -> EventAdvice(
                eventId = eventId,
                adviceText = null,
                generatedAt = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString(),
                sourceHash = sourceHash,
                status = EventAdviceStatus.Failed,
            )
        }
        upsertEventAdvice(advice)
        return advice
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
                weatherUpdatedAt = OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString(),
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
                aiServiceStatusText = if (trimmedEndpoint.isBlank() || trimmedTextModel.isBlank()) {
                    "未接入真实 AI 服务"
                } else {
                    "已配置 AI 服务，文本模型：$trimmedTextModel"
                },
            )
        }
        persistUserPreferences()
    }

    override fun refreshWeather() {
        refreshWeatherAsync()
    }

    private fun scheduleAllRemindersAsync(context: Context, events: List<Event>) {
        val applicationContext = context.applicationContext
        thread(name = "vela-reminder-restore") {
            EventNotificationScheduler.scheduleAll(applicationContext, events)
        }
    }

    private fun refreshWeatherAsync() {
        val preferences = _userPreferences.value
        val latitude = preferences.weatherLatitude
        val longitude = preferences.weatherLongitude
        cachedWeatherHint = WeatherHintProvider.pendingForCoordinates(
            latitude = latitude,
            longitude = longitude,
        )
        syncSessionAndWidgetSnapshot()
        if (latitude == null || longitude == null) {
            return
        }

        val generation = weatherRefreshGeneration + 1
        weatherRefreshGeneration = generation
        thread(name = "vela-weather-refresh") {
            val weatherHint = WeatherHintProvider.forCoordinates(latitude, longitude)
            if (weatherRefreshGeneration == generation) {
                cachedWeatherHint = weatherHint
                syncSessionAndWidgetSnapshot()
            }
        }
    }

    private fun syncSessionAndWidgetSnapshot() {
        _importSession.update { session ->
            session.copy(
                updatedAt = MockNow,
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
        val now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"))
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
        val hint = WeatherHintProvider.withEventPrepHint(
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

    private fun String.toOffsetDateTimeOrNull(): OffsetDateTime? =
        runCatching { OffsetDateTime.parse(this) }.getOrNull()

    private fun loadPersistedEvents(context: Context): List<Event>? {
        val storedEvents = context
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(EventsKey, null)
            ?: return null

        return runCatching {
            json.decodeFromString<List<Event>>(storedEvents)
        }.getOrNull()
    }

    private fun persistEvents() {
        val context = appContext ?: return
        val storedEvents = json.encodeToString(_events.value)
        context
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(EventsKey, storedEvents)
            .apply()
    }

    private fun loadEventAdvices(context: Context): Map<String, EventAdvice> {
        val storedAdvices = context
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(EventAdvicesKey, null)
            ?: return emptyMap()

        return runCatching {
            json.decodeFromString<List<EventAdvice>>(storedAdvices)
                .associateBy { it.eventId }
        }.getOrDefault(emptyMap())
    }

    private fun persistEventAdvices() {
        val context = appContext ?: return
        val storedAdvices = json.encodeToString(_eventAdvices.value.values.toList())
        context
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(EventAdvicesKey, storedAdvices)
            .apply()
    }

    private fun upsertEventAdvice(advice: EventAdvice) {
        _eventAdvices.update { advices ->
            advices + (advice.eventId to advice)
        }
        persistEventAdvices()
    }

    private fun clearEventAdvice(eventId: String) {
        _eventAdvices.update { advices ->
            advices - eventId
        }
        persistEventAdvices()
    }

    private fun loadUserPreferences(context: Context): UserPreferences {
        val sharedPreferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val storedReminder = sharedPreferences.getInt(DefaultReminderKey, DefaultReminderMinutes)
        val aiEndpoint = sharedPreferences.getString(AiEndpointKey, "").orEmpty()
        val aiTextModel = sharedPreferences.getString(AiTextModelKey, "").orEmpty()
        return UserPreferences(
            defaultReminderMinutes = storedReminder.takeIf { it != NoReminderValue },
            aiServiceStatusText = if (aiEndpoint.isBlank() || aiTextModel.isBlank()) {
                "未接入真实 AI 服务"
            } else {
                "已配置 AI 服务，文本模型：$aiTextModel"
            },
            aiEndpoint = aiEndpoint,
            aiApiKey = sharedPreferences.getString(AiApiKeyKey, "").orEmpty(),
            aiTextModel = aiTextModel,
            aiVisionModel = sharedPreferences.getString(AiVisionModelKey, "").orEmpty(),
            aiVoiceModel = sharedPreferences.getString(AiVoiceModelKey, "").orEmpty(),
            weatherLatitude = sharedPreferences.getDoubleOrNull(WeatherLatitudeKey),
            weatherLongitude = sharedPreferences.getDoubleOrNull(WeatherLongitudeKey),
            weatherUpdatedAt = sharedPreferences.getString(WeatherUpdatedAtKey, null),
        )
    }

    private fun persistUserPreferences() {
        val context = appContext ?: return
        val preferences = _userPreferences.value
        context
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putInt(DefaultReminderKey, preferences.defaultReminderMinutes ?: NoReminderValue)
            .putString(AiEndpointKey, preferences.aiEndpoint)
            .putString(AiApiKeyKey, preferences.aiApiKey)
            .putString(AiTextModelKey, preferences.aiTextModel)
            .putString(AiVisionModelKey, preferences.aiVisionModel)
            .putString(AiVoiceModelKey, preferences.aiVoiceModel)
            .putNullableDouble(WeatherLatitudeKey, preferences.weatherLatitude)
            .putNullableDouble(WeatherLongitudeKey, preferences.weatherLongitude)
            .putString(WeatherUpdatedAtKey, preferences.weatherUpdatedAt)
            .apply()
    }

    private fun aiExtractionClient() =
        _userPreferences.value.takeIf {
            it.aiEndpoint.isNotBlank() && it.aiTextModel.isNotBlank()
        }?.let { preferences ->
            HttpAiExtractionClient(
                endpoint = preferences.aiEndpoint,
                apiKey = preferences.aiApiKey,
                textModel = preferences.aiTextModel,
                visionModel = preferences.aiVisionModel,
            )
        } ?: UnavailableAiExtractionClient

    private fun eventAdviceClient() =
        _userPreferences.value.takeIf {
            it.aiEndpoint.isNotBlank() && it.aiTextModel.isNotBlank()
        }?.let { preferences ->
            HttpAiEventAdviceClient(
                endpoint = preferences.aiEndpoint,
                apiKey = preferences.aiApiKey,
                model = preferences.aiTextModel,
            )
        } ?: UnavailableAiEventAdviceClient

    private fun voiceTranscriptionClient() =
        _userPreferences.value.takeIf {
            it.aiEndpoint.isNotBlank() && it.aiVoiceModel.isNotBlank()
        }?.let { preferences ->
            HttpVoiceTranscriptionClient(
                endpoint = preferences.aiEndpoint,
                apiKey = preferences.aiApiKey,
                model = preferences.aiVoiceModel,
            )
        } ?: UnavailableVoiceTranscriptionClient

    private fun android.content.SharedPreferences.getDoubleOrNull(key: String): Double? =
        if (contains(key)) {
            Double.fromBits(getLong(key, 0L))
        } else {
            null
        }

    private fun android.content.SharedPreferences.Editor.putNullableDouble(
        key: String,
        value: Double?,
    ): android.content.SharedPreferences.Editor =
        if (value == null) {
            remove(key)
        } else {
            putLong(key, value.toBits())
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
