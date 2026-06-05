package com.vela.app.feature.floating

import com.vela.app.data.model.Event
import com.vela.app.data.model.EventCandidate
import com.vela.app.data.model.ImportTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FloatingImportUiState(
    val isOverlayVisible: Boolean = true,
    val isExpanded: Boolean = false,
    val isLoading: Boolean = false,
    val target: ImportTarget = ImportTarget.Schedule,
    val message: String? = null,
    val candidates: List<EventCandidate> = emptyList(),
    val selectedCandidateIds: Set<String> = emptySet(),
    val todayEvents: List<Event> = emptyList(),
)

object FloatingImportStore {
    private val _uiState = MutableStateFlow(FloatingImportUiState())
    val uiState: StateFlow<FloatingImportUiState> = _uiState.asStateFlow()

    fun expand() {
        _uiState.value = _uiState.value.copy(
            isOverlayVisible = true,
            isExpanded = true,
        )
    }

    fun collapse() {
        _uiState.value = _uiState.value.copy(
            isOverlayVisible = true,
            isExpanded = false,
        )
    }

    fun startCapture(target: ImportTarget) {
        _uiState.value = FloatingImportUiState(
            isOverlayVisible = false,
            isExpanded = true,
            isLoading = true,
            target = target,
            message = "正在识别截图...",
        )
    }

    fun setResult(
        target: ImportTarget,
        message: String,
        candidates: List<EventCandidate>,
    ) {
        _uiState.value = FloatingImportUiState(
            isOverlayVisible = true,
            isExpanded = true,
            target = target,
            message = message,
            candidates = candidates,
            selectedCandidateIds = candidates.map { it.id }.toSet(),
        )
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(
            isOverlayVisible = true,
            isExpanded = true,
            isLoading = false,
            message = message,
            candidates = emptyList(),
            selectedCandidateIds = emptySet(),
        )
    }

    fun toggleCandidate(candidateId: String) {
        val state = _uiState.value
        val selectedIds = if (candidateId in state.selectedCandidateIds) {
            state.selectedCandidateIds - candidateId
        } else {
            state.selectedCandidateIds + candidateId
        }
        _uiState.value = state.copy(selectedCandidateIds = selectedIds)
    }

    fun removeCandidate(candidateId: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            candidates = state.candidates.filterNot { it.id == candidateId },
            selectedCandidateIds = state.selectedCandidateIds - candidateId,
        )
    }

    fun showTodayEvents(events: List<Event>) {
        _uiState.value = FloatingImportUiState(
            isOverlayVisible = true,
            isExpanded = true,
            message = if (events.isEmpty()) "今天暂无日程。" else "今日日程",
            todayEvents = events,
        )
    }

    fun markImported(count: Int) {
        _uiState.value = FloatingImportUiState(
            isOverlayVisible = true,
            isExpanded = true,
            message = if (count > 0) "已导入 $count 条。" else "没有导入日程。",
        )
    }
}
