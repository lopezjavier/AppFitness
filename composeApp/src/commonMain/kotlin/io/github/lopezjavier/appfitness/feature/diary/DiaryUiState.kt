package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.AppError
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

/**
 * A day of the diary. [dayTotals] keeps the 0.1 precision; the UI rounds for display. [isOffline] disables the
 * writes (edit, delete) with a visible reason.
 */
data class DiaryContent(
    val date: LocalDate,
    /** Today's date, the upper bound of the day picker. */
    val today: LocalDate,
    val label: DateLabel,
    val canGoNext: Boolean,
    val goals: DiaryGoalsState,
    val meals: List<MealSectionUi>,
    val dayTotals: MealTotals,
    val collapsedSlotIds: Set<String>,
    val isOffline: Boolean = false,
    /** Applying today's default comidas failed; the Diary still shows, with a retry ([DiaryEvent.RetryDefaults]). */
    val defaultsFailed: Boolean = false,
)

sealed interface DiaryEvent {
    data object PreviousDay : DiaryEvent

    data object NextDay : DiaryEvent

    data class ToggleSection(val slotId: String) : DiaryEvent

    data object Retry : DiaryEvent

    /** Try again to add today's default comidas, then reload the day. */
    data object RetryDefaults : DiaryEvent

    /** Silent reload (no loading state), sent when the tab resumes so goals edited elsewhere show up. */
    data object Refresh : DiaryEvent

    data class AddToMeal(val slotId: String) : DiaryEvent

    /** The Diary-level add action: no meal is preselected. */
    data object AddFood : DiaryEvent

    data object OpenGoals : DiaryEvent

    /** Jump to [date] (day-picker sheet); ignored when [date] is after today. */
    data class GoToDay(val date: LocalDate) : DiaryEvent

    /** Save the entries of meal [slotId] of the visible day as a comida (US-21). */
    data class SaveSection(val slotId: String) : DiaryEvent

    /** Open the edit sheet of an entry of the visible day. */
    data class EditEntry(val entryId: String) : DiaryEvent

    /** Delete an entry at once (no confirmation, no undo); [DiaryEffect.WriteFailed] follows if it fails. */
    data class DeleteEntry(val entryId: String) : DiaryEvent
}

/** One-off effects. */
sealed interface DiaryEffect {
    data class AddToMeal(val slotId: String, val date: LocalDate) : DiaryEffect

    /** Open the food search for [date] with no meal preselected. */
    data class AddFood(val date: LocalDate) : DiaryEffect

    /** Open the save-as-comida sheet for the section [slotId] of [date]. */
    data class OpenSaveTemplate(val slotId: String, val date: LocalDate) : DiaryEffect

    data object OpenPreferences : DiaryEffect

    /** Open the edit sheet of [entryId], which belongs to [date]. */
    data class OpenEditEntry(val entryId: String, val date: LocalDate) : DiaryEffect

    /** A write (delete) failed; the Diary content is unchanged. */
    data class WriteFailed(val error: AppError?) : DiaryEffect
}
