package io.github.lopezjavier.appfitness.feature.workout

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

enum class WorkoutCalendarView { Month, Year, MultiYear }

sealed interface WorkoutCalendarUiState {
    data object Loading : WorkoutCalendarUiState

    /** The workouts could not be loaded; [WorkoutCalendarEvent.Retry] tries again. */
    data object Error : WorkoutCalendarUiState

    /**
     * The training calendar. [markedDays] are the days of the shown [month] with a workout ([dayCounts] says how many);
     * [yearCounts] feeds the multi-year list and [monthCounts] (month number 1-12 of [year]) the year view.
     * [dayCards] are the summary cards of the [selectedDay]'s workouts.
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
        val dayCards: DayCardsState = DayCardsState.None,
        /** "Añadir entreno" is offered: a day is selected and it is not after [today]. */
        val canAddToSelectedDay: Boolean = false,
    ) : WorkoutCalendarUiState
}

/** The summary cards below the grid for the selected day (ordered by start time). */
sealed interface DayCardsState {
    /** No day selected. */
    data object None : DayCardsState

    data object Loading : DayCardsState

    /** Loading the day's sessions failed; [WorkoutCalendarEvent.RetryDayCards] tries again. */
    data object Error : DayCardsState

    data class Loaded(val rows: List<HistoryRow>) : DayCardsState
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

    /** Tap on a day up to today (with or without workouts): selects it and shows its cards (no navigation). */
    data class SelectDay(val date: LocalDate) : WorkoutCalendarEvent

    /** Tap on a day card: opens that workout's detail. Ignored when it is not one of the selected day's. */
    data class OpenSession(val sessionId: String) : WorkoutCalendarEvent

    /** "Añadir entreno" of the selected day: asks the screen to open the source chooser for that day. */
    data object AddWorkout : WorkoutCalendarEvent

    /** Retries loading the selected day's cards. */
    data object RetryDayCards : WorkoutCalendarEvent
}

sealed interface WorkoutCalendarEffect {
    data class OpenSession(val sessionId: String) : WorkoutCalendarEffect

    /** Open the source chooser to log a past workout on [date]. */
    data class AddWorkout(val date: LocalDate) : WorkoutCalendarEffect
}
