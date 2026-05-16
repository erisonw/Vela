package com.vela.app.data.repository

import com.vela.app.data.model.Event
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.ImportSession
import com.vela.app.data.model.WidgetSnapshot
import kotlinx.coroutines.flow.StateFlow

interface VelaRepository {
    val importSession: StateFlow<ImportSession>
    val eventCandidates: StateFlow<List<EventCandidate>>
    val events: StateFlow<List<Event>>
    val widgetSnapshot: StateFlow<WidgetSnapshot>

    fun toggleCandidateSelection(candidateId: String)
    fun importSelectedCandidates()
}
