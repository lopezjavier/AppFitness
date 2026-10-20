package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.FoodLogEntry
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.feature.workout.DateLabel
import kotlinx.datetime.LocalDate

/** What the diary tab shows. */
sealed interface DiaryUiState {
    data object Loading : DiaryUiState

    data object Error : DiaryUiState

    data class Content(val content: DiaryContent) : DiaryUiState
}

/** Whether the user has daily goals; without them the dashboard is replaced by a "set your goal" prompt. */
sealed interface DiaryGoalsState {
    /** [remainingKcal] is whole kcal left for the day, negative when exceeded. */
    data class Set(val goals: DiaryGoals, val remainingKcal: Int) : DiaryGoalsState

    data object NotSet : DiaryGoalsState
}

/** One meal section: [showRemainingHint] is true only for an empty section with goals set and kcal left. */
data class MealSectionUi(
    val slot: MealSlot,
    val entries: List<FoodLogEntry>,
    val totals: MealTotals,
    val isEmpty: Boolean,
    val showRemainingHint: Boolean,
    val remainingKcal: Int?,
)

/** A day of the diary. [dayTotals] keeps the 0.1 precision; the UI rounds for display. */
data class DiaryContent(
    val date: LocalDate,
    val label: DateLabel,
    val canGoNext: Boolean,
    val goals: DiaryGoalsState,
    val meals: List<MealSectionUi>,
    val dayTotals: MealTotals,
    val collapsedSlotIds: Set<String>,
)

sealed interface DiaryEvent {
    data object PreviousDay : DiaryEvent

    data object NextDay : DiaryEvent

    data class ToggleSection(val slotId: String) : DiaryEvent

    data object Retry : DiaryEvent

    /** Silent reload (no loading state), sent when the tab resumes so goals edited elsewhere show up. */
    data object Refresh : DiaryEvent

    data class AddToMeal(val slotId: String) : DiaryEvent

    /** The Diary-level add action: no meal is preselected. */
    data object AddFood : DiaryEvent

    data object OpenGoals : DiaryEvent

    /** Open the meals editor for the visible day. */
    data object OpenMealSettings : DiaryEvent
}

/** One-off effects. */
sealed interface DiaryEffect {
    data class AddToMeal(val slotId: String, val date: LocalDate) : DiaryEffect

    /** Open the food search for [date] with no meal preselected. */
    data class AddFood(val date: LocalDate) : DiaryEffect

    data object OpenPreferences : DiaryEffect

    /** Open the meals editor; [date] is the visible day (a meal with entries on it cannot be disabled). */
    data class OpenMealSettings(val date: LocalDate) : DiaryEffect
}
