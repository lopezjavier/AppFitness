package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.usecase.LoadReports
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * "Informes" (US-129): the stored weekly summaries and block reviews, newest first as the server orders them (the
 * order is kept, never re-sorted). Opening a row is plain navigation in the UI; nothing is stored here.
 */
class ReportsViewModel(private val loadReports: LoadReports) : ViewModel() {
    private val _uiState = MutableStateFlow<ReportsUiState>(ReportsUiState.Loading)
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: ReportsEvent) {
        when (event) {
            ReportsEvent.Retry -> load()
        }
    }

    private fun load() {
        _uiState.value = ReportsUiState.Loading
        viewModelScope.launch {
            val result = loadReports()
            val items = result.getOrNull()
            _uiState.value = when {
                result.isFailure -> if (result.appErrorOrNull() == AppError.Network) {
                    ReportsUiState.Offline
                } else {
                    ReportsUiState.Error
                }

                items.isNullOrEmpty() -> ReportsUiState.Empty

                else -> ReportsUiState.Content(items)
            }
        }
    }
}
