package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachSummaryRepository
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.MarkWeekSummarySeen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val KEY_MARKED = "week_summary_marked"

/**
 * "Resumen semanal" (US-98): the latest finished plan week, read-only and built from recorded numbers on the server.
 * Once the content is shown, the summary is marked as seen (once per open, kept in [savedState] so a recreated model
 * does not repeat it) and the reminders are refreshed so its notice clears. A failed mark-seen is silent: the notice
 * simply stays. Nothing here calls the coach.
 */
class WeekSummaryViewModel(
    private val summaries: CoachSummaryRepository,
    private val markSeen: MarkWeekSummarySeen,
    private val reminders: ReminderCoordinator,
    private val names: ExerciseNameResolver,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<WeekSummaryUiState>(WeekSummaryUiState.Loading)
    val uiState: StateFlow<WeekSummaryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: WeekSummaryEvent) {
        when (event) {
            WeekSummaryEvent.Retry -> load()
        }
    }

    // The week boundaries are UTC dates, like the rest of the coach SQL.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    private fun load() {
        _uiState.value = WeekSummaryUiState.Loading
        viewModelScope.launch {
            val state = fetch()
            _uiState.value = state
            if (state is WeekSummaryUiState.Content) markAsSeen(state.summary)
        }
    }

    private suspend fun fetch(): WeekSummaryUiState {
        val result = summaries.getLatest(today())
        val summary = result.getOrNull()
        return when {
            result.isFailure -> if (result.appErrorOrNull() == AppError.Network) {
                WeekSummaryUiState.Offline
            } else {
                WeekSummaryUiState.Error
            }

            summary == null -> WeekSummaryUiState.Empty

            // A failed name read leaves the change rows out (R-54) instead of failing the whole summary.
            else -> WeekSummaryUiState.Content(summary, names.resolve().getOrNull()?.byId.orEmpty())
        }
    }

    private suspend fun markAsSeen(summary: WeekSummary) {
        val key = "${summary.planId}:${summary.week}"
        if (summary.seen || savedState.get<String>(KEY_MARKED) == key) return
        if (markSeen(summary.planId, summary.week).isSuccess) {
            savedState[KEY_MARKED] = key
            reminders.refresh()
        }
    }
}
