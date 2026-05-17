package com.vela.app.data.model

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class EventInputValidation(
    val errors: List<String>,
) {
    val isValid: Boolean = errors.isEmpty()
    val message: String? = errors.firstOrNull()
}

fun validateEventInput(
    title: String,
    startAt: String,
    endAt: String?,
): EventInputValidation {
    val trimmedTitle = title.trim()
    val trimmedStartAt = startAt.trim()
    val trimmedEndAt = endAt.orEmpty().trim()
    var parsedStartAt: OffsetDateTime? = null

    val errors = buildList {
        if (trimmedTitle.isBlank()) {
            add("标题不能为空。")
        }
        if (trimmedStartAt.isBlank()) {
            add("开始时间不能为空。")
        } else {
            parsedStartAt = trimmedStartAt.toOffsetDateTimeOrNull()
            if (parsedStartAt == null) {
                add("开始时间格式不正确，请使用 2026-05-18T10:00:00+08:00。")
            }
        }
        if (trimmedEndAt.isNotBlank()) {
            val parsedEndAt = trimmedEndAt.toOffsetDateTimeOrNull()
            if (parsedEndAt == null) {
                add("结束时间格式不正确，请使用 2026-05-18T11:00:00+08:00。")
            } else if (parsedStartAt != null && parsedEndAt.isBefore(parsedStartAt)) {
                add("结束时间不能早于开始时间。")
            }
        }
    }

    return EventInputValidation(errors = errors)
}

private fun String.toOffsetDateTimeOrNull(): OffsetDateTime? = try {
    OffsetDateTime.parse(this)
} catch (_: DateTimeParseException) {
    null
}
