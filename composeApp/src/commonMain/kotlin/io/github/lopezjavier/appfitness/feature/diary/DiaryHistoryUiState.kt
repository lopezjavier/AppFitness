package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.usecase.DiaryTotals
import io.github.lopezjavier.appfitness.domain.usecase.MonthSummary
import io.github.lopezjavier.appfitness.domain.usecase.PeriodAverage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** What the diary history shows (US-24). */
sealed interface DiaryHistoryUiState {
    data object Loading : DiaryHistoryUiState

    data object Error : DiaryHistoryUiState

    data class Content(val content: DiaryHistoryContent) : DiaryHistoryUiState
}

/** The summary panel the user is looking at. */
enum class DiaryHistoryView { Day, Week, Month }

/**
 * The selected day against the goals. [totals] is null for a day without entries ("Sin registros"); the percents are
 * null without totals or goals. They are not capped (the bar caps them).
 */
data class DiaryHistoryDay(val date: LocalDate, val totals: MealTotals?, val goals: DiaryGoals?) {
    val hasEntries: Boolean get() = totals != null
    val remainingKcal: Int? get() = percentBase { g, t -> DiaryTotals.remainingKcal(g.targetKcal, t.kcal) }
    val proteinPercent: Int? get() = percentBase { g, t -> DiaryTotals.macroPercent(t.proteinG, g.proteinG) }
    val carbsPercent: Int? get() = percentBase { g, t -> DiaryTotals.macroPercent(t.carbsG, g.carbsG) }
    val fatPercent: Int? get() = percentBase { g, t -> DiaryTotals.macroPercent(t.fatG, g.fatG) }

    private fun percentBase(compute: (DiaryGoals, MealTotals) -> Int): Int? =
        goals?.let { g -> totals?.let { t -> compute(g, t) } }
}

/**
 * The history of [month]: [markedDays] have entries, [selectedDay] is null when the viewed month holds none. [week]
 * (last 7 days ending [today]) and [monthSummary] are null for "Sin registros". [canGoNext] is false at the current
 * month. [goals] is null when the user has none.
 */
data class DiaryHistoryContent(
    val month: YearMonth,
    val today: LocalDate,
    val canGoNext: Boolean,
    val markedDays: Set<LocalDate>,
    val view: DiaryHistoryView,
    val selectedDay: DiaryHistoryDay?,
    val week: PeriodAverage?,
    val monthSummary: MonthSummary?,
    val goals: DiaryGoals?,
)

sealed interface DiaryHistoryEvent {
    /** Ignored for a date outside the viewed month or after today. */
    data class SelectDay(val date: LocalDate) : DiaryHistoryEvent

    data object PreviousMonth : DiaryHistoryEvent

    /** Ignored at the current month. */
    data object NextMonth : DiaryHistoryEvent

    data class SelectView(val view: DiaryHistoryView) : DiaryHistoryEvent

    /** Opens the Diary of the selected day. */
    data object OpenDay : DiaryHistoryEvent

    data object Retry : DiaryHistoryEvent

    /** Silent reload (no loading state, selection kept), sent when the screen resumes after edits elsewhere. */
    data object Refresh : DiaryHistoryEvent
}

sealed interface DiaryHistoryEffect {
    data class OpenDiaryDay(val date: LocalDate) : DiaryHistoryEffect
}
