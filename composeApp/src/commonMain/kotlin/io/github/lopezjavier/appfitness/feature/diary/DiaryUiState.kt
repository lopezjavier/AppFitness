package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.DiaryViewMode
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
 * A day of the diary. [dayTotals] keeps the 0.1 precision; the UI rounds for display. [viewMode] is the persisted
 * way to show the entries; [isOffline] disables the writes (edit, delete, undo) with a visible reason.
 */
data class DiaryContent(
    val date: LocalDate,
    val label: DateLabel,
    val canGoNext: Boolean,
    val goals: DiaryGoalsState,
    val meals: List<MealSectionUi>,
    val dayTotals: MealTotals,
    val collapsedSlotIds: Set<String>,
    val viewMode: DiaryViewMode = DiaryViewMode.Detail,
    val isOffline: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val canRepeatYesterday: Map<String, Boolean> = emptyMap(),
    val today: LocalDate = date,
) {
    /** Selection mode is on while at least one entry is selected. */
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
}

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

    /** Open the edit sheet of an entry of the visible day. */
    data class EditEntry(val entryId: String) : DiaryEvent

    /** Delete an entry; [DiaryEffect.ShowUndo] follows so the user can take it back. */
    data class DeleteEntry(val entryId: String) : DiaryEvent

    /** Take back the last delete (re-inserts the entry with the same id and values). */
    data object UndoDelete : DiaryEvent

    data class SetViewMode(val mode: DiaryViewMode) : DiaryEvent

    /** Start selecting with [entryId] selected (long-press). */
    data class EnterSelection(val entryId: String) : DiaryEvent

    /** Select every entry of a meal ("Seleccionar alimentos" in the meal menu). */
    data class SelectMeal(val slotId: String) : DiaryEvent

    data class ToggleSelected(val entryId: String) : DiaryEvent

    data object ClearSelection : DiaryEvent

    /** "Copiar alimentos": opens the copy sheet for the selected entries ([DiaryEffect.OpenCopy]). */
    data object CopySelected : DiaryEvent

    /** "Copiar comida": opens the copy sheet for every entry of the meal; nothing happens when it is empty. */
    data class CopyMeal(val slotId: String) : DiaryEvent

    /** Copy [entryIds] of the visible day to meal [slotId] of [date] in one request, then offer an undo. */
    data class ConfirmCopy(val entryIds: List<String>, val slotId: String, val date: LocalDate) : DiaryEvent

    /** "Repetir comida de ayer": log the previous day's foods of meal [slotId] on the visible day. */
    data class RepeatYesterday(val slotId: String) : DiaryEvent

    /** Take back the last copy or repeat (deletes the entries it created). */
    data object UndoCopy : DiaryEvent
}

/** What an undo snackbar undoes. */
enum class UndoKind { Delete, Copy, Repeat }

/** Where a copy or repeat landed, so the snackbar can name it (it may be a day that is not on screen). */
data class CopyTarget(val slotId: String, val date: LocalDate)

/** One-off effects. */
sealed interface DiaryEffect {
    data class AddToMeal(val slotId: String, val date: LocalDate) : DiaryEffect

    /** Open the food search for [date] with no meal preselected. */
    data class AddFood(val date: LocalDate) : DiaryEffect

    data object OpenPreferences : DiaryEffect

    /** Open the meals editor; [date] is the visible day (a meal with entries on it cannot be disabled). */
    data class OpenMealSettings(val date: LocalDate) : DiaryEffect

    /** Open the edit sheet of [entryId], which belongs to [date]. */
    data class OpenEditEntry(val entryId: String, val date: LocalDate) : DiaryEffect

    /** A write succeeded and can be taken back: show the snackbar with "Deshacer". */
    data class ShowUndo(val kind: UndoKind, val target: CopyTarget? = null) : DiaryEffect

    /** Open the copy sheet for [entryIds] of [date] (the visible day). */
    data class OpenCopy(val entryIds: List<String>, val date: LocalDate) : DiaryEffect

    /** A write (delete, undo) failed; the Diary content is unchanged. */
    data class WriteFailed(val error: AppError?) : DiaryEffect
}
