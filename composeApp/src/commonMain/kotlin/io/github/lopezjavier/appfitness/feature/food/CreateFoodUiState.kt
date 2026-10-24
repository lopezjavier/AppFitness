package io.github.lopezjavier.appfitness.feature.food

import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.NewFoodErrors

/** A portion row of the form; both values are the text the user typed. */
data class PortionDraft(val label: String = "", val amount: String = "")

/**
 * State of the create-food form. All numbers are the typed text (a comma or a point is accepted). [errors] only
 * holds the problems of fields the user has already edited; [canSave] is false while anything is invalid, while
 * saving and offline. [baseUnit] is "g" or "ml"; in edit mode [unitLocked] while an entry or comida references the
 * food or after the server refused a change ([unitRefused] explains why); [barcode] is read-only. [nutrients] holds
 * the text of every label nutrient in [labelRows] order. In edit mode ([isEdit]) the form is [isLoading] until the
 * food is read and shows [loadFailed] (with a retry) when that fails.
 */
data class CreateFoodUiState(
    val name: String = "",
    val brand: String = "",
    val baseUnit: String = "g",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val nutrients: Map<MicronutrientKind, String> = emptyMap(),
    val barcode: String? = null,
    val portions: List<PortionDraft> = emptyList(),
    val errors: NewFoodErrors = NewFoodErrors(),
    val canSave: Boolean = false,
    val canAddPortion: Boolean = true,
    val isSaving: Boolean = false,
    val isOffline: Boolean = false,
    val saveFailed: Boolean = false,
    val isEdit: Boolean = false,
    val unitLocked: Boolean = false,
    val unitRefused: Boolean = false,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val labelRows: List<MicronutrientKind> = MicronutrientKind.editableKinds,
    val expandedSections: Set<CreateFoodSection> = emptySet(),
)

/**
 * The optional collapsible groups of label nutrients (collapsed by default, also when editing); [kinds] are the
 * nutrients each one holds, in label order. The usual rows ([USUAL_KINDS]) are always visible.
 */
enum class CreateFoodSection(val id: String, val kinds: List<MicronutrientKind>) {
    Fats(
        "fats",
        listOf(
            MicronutrientKind.MonounsaturatedFat,
            MicronutrientKind.PolyunsaturatedFat,
            MicronutrientKind.Polyols,
            MicronutrientKind.Starch,
        ),
    ),
    Vitamins(
        "vitamins",
        MicronutrientKind.editableKinds.subList(
            MicronutrientKind.editableKinds.indexOf(MicronutrientKind.VitaminA),
            MicronutrientKind.editableKinds.indexOf(MicronutrientKind.PantothenicAcid) + 1,
        ),
    ),
    Minerals(
        "minerals",
        MicronutrientKind.editableKinds.subList(
            MicronutrientKind.editableKinds.indexOf(MicronutrientKind.Potassium),
            MicronutrientKind.editableKinds.size,
        ),
    ),
    ;

    companion object {
        /** The label rows that are always visible. */
        val USUAL_KINDS = listOf(
            MicronutrientKind.SaturatedFat,
            MicronutrientKind.Sugars,
            MicronutrientKind.Fiber,
            MicronutrientKind.Salt,
        )
    }
}

/** The identity and macro fields; the label nutrients are keyed by [MicronutrientKind] (blank means not typed). */
enum class CreateFoodField { Name, Brand, Kcal, Protein, Carbs, Fat }

sealed interface CreateFoodEvent {
    data class FieldChanged(val field: CreateFoodField, val text: String) : CreateFoodEvent

    /** A label nutrient, typed in the [MicronutrientKind.labelUnit] of [kind]. */
    data class NutrientChanged(val kind: MicronutrientKind, val text: String) : CreateFoodEvent

    /** [unit] is "g" or "ml". */
    data class UnitSelected(val unit: String) : CreateFoodEvent

    data object AddPortion : CreateFoodEvent

    data class RemovePortion(val index: Int) : CreateFoodEvent

    data class PortionLabelChanged(val index: Int, val text: String) : CreateFoodEvent

    data class PortionAmountChanged(val index: Int, val text: String) : CreateFoodEvent

    data object Save : CreateFoodEvent

    /** Expand or collapse a group of label nutrients. */
    data class ToggleSection(val section: CreateFoodSection) : CreateFoodEvent

    /** Edit mode: load the food again after a failure. */
    data object Retry : CreateFoodEvent
}

sealed interface CreateFoodEffect {
    /** The food was created or edited: open (or go back to) its add-food sheet. */
    data class Saved(val foodId: String) : CreateFoodEffect

    data object SaveFailed : CreateFoodEffect
}
