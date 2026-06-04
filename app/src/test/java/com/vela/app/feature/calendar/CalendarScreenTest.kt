package com.vela.app.feature.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarScreenTest {
    @Test
    fun holidayInfoMarksRestAndAdjustedWorkdays() {
        val days = chineseSpecialDayMap(2026)

        assertEquals(
            CalendarDayKind.Rest,
            days[LocalDate.of(2026, 5, 1)]?.kind,
        )
        assertEquals(
            "劳动节",
            days[LocalDate.of(2026, 5, 1)]?.label,
        )
        assertEquals(
            CalendarDayKind.Work,
            days[LocalDate.of(2026, 5, 9)]?.kind,
        )
    }

    @Test
    fun eventColorSlotIsStableForSameEventId() {
        assertEquals(
            calendarEventColorSlot("event-final-exam"),
            calendarEventColorSlot("event-final-exam"),
        )
    }
}
