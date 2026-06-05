package com.vela.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ImportTarget {
    Schedule,
    Timetable,
}
