package com.vela.app.data.local

import com.vela.app.data.model.Event
import com.vela.app.data.model.EventAdvice
import com.vela.app.data.model.EventAdviceStatus
import com.vela.app.data.model.Location
import com.vela.app.data.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventStoreCodecTest {
    @Test
    fun eventRoundTripPreservesPersistedFields() {
        val event = Event(
            id = "event-1",
            title = "内部测试",
            startAt = "2026-07-17T09:00:00+08:00",
            endAt = "2026-07-17T10:00:00+08:00",
            timezone = "Asia/Shanghai",
            location = Location(name = "会议室"),
            description = "验证本地持久化",
            reminders = listOf(Reminder(id = "r-10", minutesBefore = 10)),
            sourceSessionId = "session-1",
            isCourse = true,
        )

        val decoded = EventStoreCodec.decodeEvents(
            EventStoreCodec.encodeEvents(listOf(event)),
        )

        assertEquals(listOf(event), decoded)
    }

    @Test
    fun malformedEventsReturnNullWithoutCrashing() {
        assertNull(EventStoreCodec.decodeEvents("{not-json"))
    }

    @Test
    fun adviceRoundTripIndexesByEventId() {
        val advice = EventAdvice(
            eventId = "event-1",
            adviceText = "提前准备资料",
            generatedAt = "2026-07-16T10:00:00+08:00",
            sourceHash = "hash",
            status = EventAdviceStatus.Ready,
        )

        val decoded = EventStoreCodec.decodeEventAdvices(
            EventStoreCodec.encodeEventAdvices(listOf(advice)),
        )

        assertEquals(advice, decoded["event-1"])
    }
}
