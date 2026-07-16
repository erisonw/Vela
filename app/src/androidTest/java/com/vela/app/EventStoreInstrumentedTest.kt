package com.vela.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vela.app.data.local.EventStore
import com.vela.app.data.model.Event
import com.vela.app.data.model.Reminder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearStore() {
        context.deleteSharedPreferences("vela_local_store")
    }

    @After
    fun cleanUpStore() {
        context.deleteSharedPreferences("vela_local_store")
    }

    @Test
    fun eventPersistsAcrossStoreInstances() {
        val event = Event(
            id = "event-instrumented",
            title = "真机持久化测试",
            startAt = "2026-07-17T10:00:00+08:00",
            reminders = listOf(Reminder(id = "r-10", minutesBefore = 10)),
        )

        EventStore(context).saveEvents(listOf(event))
        val restored = EventStore(context).loadEvents()

        assertEquals(listOf(event), restored)
    }
}
