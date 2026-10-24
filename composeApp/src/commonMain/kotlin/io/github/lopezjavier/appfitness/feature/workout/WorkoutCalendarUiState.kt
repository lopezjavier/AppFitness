package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.model.TrainedSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

enum class WorkoutCalendarView { Month, Year, MultiYear }

sealed interface WorkoutCalendarUiState {
    data object Loading : WorkoutCalendarUiState

    /** The workouts could not be loaded; [WorkoutCalendarEvent.Retry] tries again. */
    data object Error : WorkoutCalendarUiState

    /** The user has no completed workout yet. */
    data object Empty : WorkoutCalendarUiState

    /**
     * The training calendar. [markedDays] are the days of the shown [month] with a workout ([dayCounts] says how many);
     * [yearCounts] feeds the multi-year list and [monthCounts] (month number 1-12 of [year]) the year view.
     * [dayChoices] is non-empty while the picker of a day with 2+ workouts is open.
     */
    data class Content(
        val view: WorkoutCalendarView,
        val month: YearMonth,
        val year: Int,
        val markedDays: Set<LocalDate>,
        val dayCounts: Map<LocalDate, Int>,
        val selectedDay: LocalDate?,
        val yearCounts: Map<Int, Int>,
        val monthCounts: Map<Int, Int>,
        val canGoNext: Boolean,
        /** The current day: later days of the grid are inert. */
        val today: LocalDate,
        val dayChoices: List<TrainedSession> = emptyList(),
    ) : WorkoutCalendarUiState
}

sealed interface WorkoutCalendarEvent {
    data object Retry : WorkoutCalendarEvent

    /**
     * Reloads the workouts without a loading state (on resume: a workout saved, edited or deleted elsewhere appears or
     * disappears). A failed refresh keeps what is shown.
     */
    data object Refresh : WorkoutCalendarEvent

    data class SelectView(val view: WorkoutCalendarView) : WorkoutCalendarEvent

    data object PreviousMonth : WorkoutCalendarEvent

    data object NextMonth : WorkoutCalendarEvent

    /** From the year view: shows that month. */
    data class SelectMonth(val month: YearMonth) : WorkoutCalendarEvent

    /** From the multi-year list: shows that year's months. */
    data class SelectYear(val year: Int) : WorkoutCalendarEvent

    /** Tap on a day: opens its workout, or the picker when there are two or more. */
    data class SelectDay(val date: LocalDate) : WorkoutCalendarEvent

    data object DismissChoices : WorkoutCalendarEvent

    data class PickSession(val sessionId: String) : WorkoutCalendarEvent
}

sealed interface WorkoutCalendarEffect {
    data class OpenSession(val sessionId: String) : WorkoutCalendarEffect
}
