package com.vela.app.data.repository

import com.vela.app.data.ai.AiClientProvider
import com.vela.app.data.ai.AiEventAdviceClient
import com.vela.app.data.ai.AiExtractionClient
import com.vela.app.data.ai.AiExtractionRequest
import com.vela.app.data.ai.AiExtractionResult
import com.vela.app.data.ai.AiVoiceRecording
import com.vela.app.data.ai.UnavailableAiEventAdviceClient
import com.vela.app.data.ai.UnavailableVoiceTranscriptionClient
import com.vela.app.data.ai.VoiceTranscriptionClient
import com.vela.app.data.local.VelaDataStore
import com.vela.app.data.model.Event
import com.vela.app.data.model.EventAdvice
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.Reminder
import com.vela.app.data.model.UserPreferences
import com.vela.app.data.time.TimeProvider
import com.vela.app.data.weather.WeatherHint
import com.vela.app.data.weather.WeatherService
import com.vela.app.notification.ReminderScheduler
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultVelaRepositoryTest {
    @Test
    fun freshRepositoryStartsEmptyAndUsesRealSessionId() {
        val fixture = repositoryFixture()

        assertTrue(fixture.repository.events.value.isEmpty())
        assertTrue(fixture.repository.importSession.value.id.startsWith("session-"))
        assertNotEquals("session-mock-import", fixture.repository.importSession.value.id)
        assertEquals("定位后获取天气", fixture.repository.widgetSnapshot.value.weatherHint)
    }

    @Test
    fun successfulExtractionImportsSelectedCandidateAndPersistsIt() = runBlocking {
        val candidate = EventCandidate(
            id = "candidate-upstream",
            title = "产品评审",
            startAt = "2026-07-17T10:00:00+08:00",
            endAt = "2026-07-17T11:00:00+08:00",
            reminders = listOf(Reminder(id = "r-10", minutesBefore = 10)),
            isSelectedForImport = true,
        )
        val fixture = repositoryFixture(
            extractionResult = AiExtractionResult.Success(
                summary = "识别到 1 条日程",
                candidates = listOf(candidate),
            ),
        )

        val submission = fixture.repository.submitImportText("明天十点产品评审")
        val imported = fixture.repository.importSelectedCandidates()

        assertTrue(submission.isSuccess)
        assertTrue(imported.isSuccess)
        assertEquals(1, fixture.repository.events.value.size)
        assertEquals("产品评审", fixture.store.savedEvents.single().title)
        assertEquals(1, fixture.reminders.scheduledEvents.size)
        assertEquals(0, fixture.repository.widgetSnapshot.value.pendingCandidateCount)
    }

    @Test
    fun failedExtractionDoesNotCreateCandidatesOrEvents() = runBlocking {
        val fixture = repositoryFixture(
            extractionResult = AiExtractionResult.Failure(
                code = "NETWORK_TIMEOUT",
                message = "AI 解析连接超时，请重试。",
                retryable = true,
            ),
        )

        val submission = fixture.repository.submitImportText("明天开会")

        assertFalse(submission.isSuccess)
        assertTrue(fixture.repository.eventCandidates.value.isEmpty())
        assertTrue(fixture.repository.events.value.isEmpty())
        assertTrue(
            fixture.repository.importSession.value.messages
                .last()
                .content
                .contains("连接超时"),
        )
    }

    @Test
    fun addEventUpsertsAndCancelsOldAndNewReminderOffsets() {
        val fixture = repositoryFixture()
        val original = testEvent(
            title = "原日程",
            reminderMinutes = 10,
        )
        val updated = testEvent(
            title = "更新后的日程",
            reminderMinutes = 30,
        )

        fixture.repository.addEvent(original)
        fixture.repository.addEvent(updated)

        assertEquals(1, fixture.repository.events.value.size)
        assertEquals("更新后的日程", fixture.repository.events.value.single().title)
        assertEquals(setOf(10, 30), fixture.reminders.cancelCalls.last().minutes.toSet())
    }

    @Test
    fun rescheduleUsesAllPersistedEvents() = runBlocking {
        val storedEvent = testEvent(title = "已保存日程", reminderMinutes = 10)
        val fixture = repositoryFixture(initialEvents = listOf(storedEvent))

        fixture.repository.rescheduleReminders()

        assertEquals(listOf(storedEvent), fixture.reminders.scheduleAllCalls.last())
    }

    private fun repositoryFixture(
        initialEvents: List<Event>? = null,
        extractionResult: AiExtractionResult = AiExtractionResult.Failure(
            code = "SERVICE_UNAVAILABLE",
            message = "服务未配置",
            retryable = false,
        ),
    ): RepositoryFixture {
        val store = FakeDataStore(initialEvents = initialEvents)
        val reminders = FakeReminderScheduler()
        val provider = FakeAiClientProvider(extractionResult)
        val repository = DefaultVelaRepository(
            eventStore = store,
            aiClientFactory = provider,
            timeProvider = FixedTimeProvider,
            scope = CoroutineScope(Dispatchers.Unconfined),
            reminderScheduler = reminders,
            weatherService = FakeWeatherService,
        )
        return RepositoryFixture(repository, store, reminders)
    }

    private fun testEvent(
        title: String,
        reminderMinutes: Int,
    ): Event =
        Event(
            id = "event-1",
            title = title,
            startAt = "2026-07-17T10:00:00+08:00",
            endAt = "2026-07-17T11:00:00+08:00",
            reminders = listOf(
                Reminder(
                    id = "reminder-$reminderMinutes",
                    minutesBefore = reminderMinutes,
                ),
            ),
        )
}

private data class RepositoryFixture(
    val repository: DefaultVelaRepository,
    val store: FakeDataStore,
    val reminders: FakeReminderScheduler,
)

private class FakeDataStore(
    initialEvents: List<Event>?,
) : VelaDataStore {
    private var events: List<Event>? = initialEvents
    var savedEvents: List<Event> = emptyList()
        private set
    private var advices: Map<String, EventAdvice> = emptyMap()
    private var preferences = UserPreferences()

    override fun loadEvents(): List<Event>? = events

    override fun saveEvents(events: List<Event>) {
        this.events = events
        savedEvents = events
    }

    override fun loadEventAdvices(): Map<String, EventAdvice> = advices

    override fun saveEventAdvices(advices: Collection<EventAdvice>) {
        this.advices = advices.associateBy { it.eventId }
    }

    override fun loadUserPreferences(): UserPreferences = preferences

    override fun saveUserPreferences(preferences: UserPreferences) {
        this.preferences = preferences
    }
}

private class FakeAiClientProvider(
    private val extractionResult: AiExtractionResult,
) : AiClientProvider {
    override fun extractionClient(preferences: UserPreferences): AiExtractionClient =
        object : AiExtractionClient {
            override suspend fun extract(request: AiExtractionRequest): AiExtractionResult =
                extractionResult
        }

    override fun adviceClient(preferences: UserPreferences): AiEventAdviceClient =
        UnavailableAiEventAdviceClient

    override fun voiceClient(preferences: UserPreferences): VoiceTranscriptionClient =
        UnavailableVoiceTranscriptionClient
}

private class FakeReminderScheduler : ReminderScheduler {
    data class CancelCall(
        val eventId: String,
        val minutes: Collection<Int>,
    )

    val scheduledEvents = mutableListOf<Event>()
    val cancelCalls = mutableListOf<CancelCall>()
    val scheduleAllCalls = mutableListOf<List<Event>>()

    override fun ensureReady() = Unit

    override fun scheduleAll(events: List<Event>) {
        scheduleAllCalls += events
    }

    override fun scheduleEvent(event: Event) {
        scheduledEvents += event
    }

    override fun cancelEvent(
        eventId: String,
        extraReminderMinutes: Collection<Int>,
    ) {
        cancelCalls += CancelCall(eventId, extraReminderMinutes)
    }
}

private object FakeWeatherService : WeatherService {
    override fun pendingForCoordinates(
        latitude: Double?,
        longitude: Double?,
    ): WeatherHint =
        WeatherHint(
            weatherHint = "定位后获取天气",
            prepHint = "天气不影响日程",
            statusText = "测试降级状态",
        )

    override fun forCoordinates(
        latitude: Double?,
        longitude: Double?,
    ): WeatherHint =
        pendingForCoordinates(latitude, longitude)

    override fun withEventPrepHint(
        weatherHint: WeatherHint,
        events: List<Event>,
    ): WeatherHint = weatherHint
}

private object FixedTimeProvider : TimeProvider {
    override fun zone(): ZoneId = ZoneId.of("Asia/Shanghai")

    override fun now(): OffsetDateTime =
        OffsetDateTime.parse("2026-07-16T12:00:00+08:00")
}
