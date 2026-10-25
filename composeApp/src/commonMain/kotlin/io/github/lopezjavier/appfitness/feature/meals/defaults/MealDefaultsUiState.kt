package io.github.lopezjavier.appfitness.feature.meals.defaults

/** A saved comida assigned to some weekdays (ISO 1..7) of a meal. [kcal] includes the cooking oil. */
data class AssignmentUi(
    val templateId: String,
    val name: String,
    val foodCount: Int,
    val kcal: Double,
    val days: Set<Int>,
)

/** One meal of the editor with its assignments. [canAdd] is false once every weekday has a comida. */
data class MealDefaultsCardUi(
    val slotId: String,
    val label: String,
    val assignments: List<AssignmentUi>,
    val canAdd: Boolean,
)

/** A saved comida offered by the picker sheet, in the same terms as a template row. */
data class DefaultComidaOptionUi(val templateId: String, val name: String, val foodCount: Int, val kcal: Double)

/**
 * The sheet that assigns [templateId] to weekdays of the meal [slotId]. [conflicts] maps each weekday that belongs to
 * another comida of the same meal to that comida's name (those days cannot be selected). [canSave] needs at least one
 * selected day.
 */
data class AssignSheetUi(
    val slotId: String,
    val mealLabel: String,
    val templateId: String,
    val templateName: String,
    val selectedDays: Set<Int>,
    val conflicts: Map<Int, String>,
    val canSave: Boolean,
)

/** Why the last save failed. The draft is always kept. */
enum class MealDefaultsSaveError {
    Offline,
    Failed,
}

/** State of the default-comidas editor (US-51). */
sealed interface MealDefaultsUiState {
    data object Loading : MealDefaultsUiState

    /** Reading the meals, comidas or defaults failed; [MealDefaultsEvent.Retry] tries again. */
    data object LoadError : MealDefaultsUiState

    /**
     * [meals] are the enabled meals in saved order; [comidas] are the options of the picker, empty when
     * [hasSavedComidas] is false. [picker] is the meal whose picker is open and [sheet] the open assignment sheet.
     */
    data class Content(
        val meals: List<MealDefaultsCardUi>,
        val comidas: List<DefaultComidaOptionUi>,
        val hasSavedComidas: Boolean,
        val isOnline: Boolean,
        val isSaving: Boolean,
        val isDirty: Boolean,
        val canSave: Boolean,
        val showDiscardConfirm: Boolean,
        val saveError: MealDefaultsSaveError?,
        val picker: String?,
        val sheet: AssignSheetUi?,
    ) : MealDefaultsUiState
}

sealed interface MealDefaultsEvent {
    data object Retry : MealDefaultsEvent

    /** Open the comida picker of meal [slotId]. */
    data class OpenPicker(val slotId: String) : MealDefaultsEvent

    data object DismissPicker : MealDefaultsEvent

    /** A comida was chosen in the picker (or an assignment tapped): open its assignment sheet. */
    data class OpenAssignment(val slotId: String, val templateId: String) : MealDefaultsEvent

    data class ToggleDay(val day: Int) : MealDefaultsEvent

    /** Apply the sheet to the screen draft (nothing is persisted yet). */
    data object ConfirmSheet : MealDefaultsEvent

    /** Close the sheet discarding its selection. */
    data object DismissSheet : MealDefaultsEvent

    data class Remove(val slotId: String, val templateId: String) : MealDefaultsEvent

    data object Save : MealDefaultsEvent

    data object Back : MealDefaultsEvent

    data object ConfirmDiscard : MealDefaultsEvent

    data object DismissDiscard : MealDefaultsEvent
}

sealed interface MealDefaultsEffect {
    data object Close : MealDefaultsEffect
}
