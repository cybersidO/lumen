package com.sidharth.lumen.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidharth.lumen.core.database.ReviewCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val loading: Boolean = true,
    val queue: List<ReviewCard> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val reviewed: Int = 0,
) {
    val current: ReviewCard? get() = queue.getOrNull(index)
    val total: Int get() = queue.size
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: ReviewRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState

    init {
        viewModelScope.launch {
            // Top the queue up first, then take a *snapshot* rather than observing.
            // A live flow would reshuffle the queue underneath the reader as each answer
            // changes due dates — the session should be the set of cards it started with.
            repository.seed()
            val due = repository.observeDue().first()
            _uiState.value = ReviewUiState(loading = false, queue = due)
        }
    }

    fun reveal() {
        _uiState.value = _uiState.value.copy(revealed = true)
    }

    fun answer(result: ReviewResult) {
        val state = _uiState.value
        val card = state.current ?: return
        _uiState.value = state.copy(
            index = state.index + 1,
            revealed = false,
            reviewed = state.reviewed + 1,
        )
        viewModelScope.launch { repository.record(card, result) }
    }
}
