package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.PastReport
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.LoadPastReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A stored weekly summary or block review opened read-only (US-129). The [reportId] is the route argument (kept by
 * the back stack entry, ADR-40), so a recreated model reloads the same report. The UI model comes from the same
 * builders as the live screens; nothing is marked, decided or recorded here.
 */
class PastReportViewModel(
    private val reportId: String,
    private val loadReport: LoadPastReport,
    private val names: ExerciseNameResolver,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PastReportUiState>(PastReportUiState.Loading)
    val uiState: StateFlow<PastReportUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: PastReportEvent) {
        when (event) {
            PastReportEvent.Retry -> load()
        }
    }

    private fun load() {
        _uiState.value = PastReportUiState.Loading
        viewModelScope.launch {
            val result = loadReport(reportId)
            _uiState.value = when {
                result.isFailure -> if (result.appErrorOrNull() == AppError.Network) {
                    PastReportUiState.Offline
                } else {
                    PastReportUiState.Error
                }

                else -> result.getOrNull()?.let { build(it) } ?: PastReportUiState.NotFound
            }
        }
    }

    private suspend fun build(report: PastReport): PastReportUiState = when (report) {
        is PastReport.Week -> PastReportUiState.Week(report.item, names.weekSummaryContent(report.summary))
        is PastReport.Block -> PastReportUiState.Block(report.item, names.blockReviewContent(report.review))
    }
}
