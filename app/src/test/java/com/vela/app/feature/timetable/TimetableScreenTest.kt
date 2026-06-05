package com.vela.app.feature.timetable

import com.vela.app.data.model.Event
import com.vela.app.data.model.Location
import org.junit.Assert.assertEquals
import org.junit.Test

class TimetableScreenTest {
    @Test
    fun timetableGroupsOnlyCourseLikeEventsByStartTime() {
        val groups = buildTimetableGroups(
            listOf(
                Event(
                    id = "java-course",
                    title = "Java 语言程序设计",
                    startAt = "2026-05-18T08:00:00+08:00",
                    endAt = "2026-05-18T09:35:00+08:00",
                    location = Location(name = "教学楼 301"),
                    isCourse = true,
                ),
                Event(
                    id = "keyword-only",
                    title = "数据库课程导入候选",
                    startAt = "2026-05-18T08:00:00+08:00",
                    endAt = "2026-05-18T09:35:00+08:00",
                    location = Location(name = "教学楼 301"),
                ),
                Event(
                    id = "team-meeting",
                    title = "项目会议",
                    startAt = "2026-05-18T08:00:00+08:00",
                    endAt = "2026-05-18T09:00:00+08:00",
                    location = Location(name = "会议室"),
                ),
                Event(
                    id = "math-course",
                    title = "网安数学基础",
                    startAt = "2026-05-19T09:55:00+08:00",
                    endAt = "2026-05-19T11:30:00+08:00",
                    location = Location(name = "教室 205"),
                    isCourse = true,
                ),
            ),
        )

        assertEquals(listOf("08:00", "09:55"), groups.map { it.time })
        assertEquals(listOf("Java 语言程序设计"), groups.first().courses.map { it.event.title })
        assertEquals(listOf("网安数学基础"), groups.last().courses.map { it.event.title })
    }
}
