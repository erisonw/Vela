package com.vela.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class EventNotificationSchedulerTest {
    @Test
    fun reminderUsesExactWhileIdleBeforeAndroid12() {
        assertEquals(
            AlarmScheduleMode.ExactAllowWhileIdle,
            EventNotificationScheduler.reminderAlarmMode(
                sdkInt = 30,
                canScheduleExactAlarms = false,
            ),
        )
    }

    @Test
    fun reminderUsesExactWhileIdleWhenAndroid12PermissionIsGranted() {
        assertEquals(
            AlarmScheduleMode.ExactAllowWhileIdle,
            EventNotificationScheduler.reminderAlarmMode(
                sdkInt = 31,
                canScheduleExactAlarms = true,
            ),
        )
    }

    @Test
    fun reminderFallsBackToInexactWhileIdleWhenAndroid12PermissionIsMissing() {
        assertEquals(
            AlarmScheduleMode.InexactAllowWhileIdle,
            EventNotificationScheduler.reminderAlarmMode(
                sdkInt = 31,
                canScheduleExactAlarms = false,
            ),
        )
    }
}
