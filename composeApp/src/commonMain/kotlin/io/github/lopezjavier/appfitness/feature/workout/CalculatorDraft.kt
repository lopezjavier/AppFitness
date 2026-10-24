package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.designsystem.NumberInputDefaults
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.PlateResult
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
import io.github.lopezjavier.appfitness.domain.usecase.CalculatorBase
import io.github.lopezjavier.appfitness.domain.usecase.PlateCalculator
import io.github.lopezjavier.appfitness.domain.usecase.WarmUpCalculator

/** The two calculators of the exercise menu (US-33). */
enum class CalculatorKind { WarmUp, Plates }

/**
 * The open calculator sheet of the exercise at [position]: the weight as typed ([weightText], prefilled from the
 * exercise) and the bar (chosen for this use for plates; always the Preferencias bar or handle for warm-ups).
 * Mirrored in the saved state (ADR-40).
 */
data class CalculatorDraft(
    val position: Int,
    val kind: CalculatorKind,
    val weightText: String,
    val barKg: Double,
    /** Warm-up only: how many warm-up sets (1..4, ADR-55); per use, never stored in the preferences. */
    val warmUpSets: Int = WarmUpCalculator.DEFAULT_SETS,
    /** The user typed in the weight field: a late best-past-weight must not overwrite it (US-33). */
    val weightEdited: Boolean = false,
)

/** Why the calculator shows no result. */
enum class CalculatorHint {
    /** The weight is empty or zero. */
    MissingWeight,

    /** Warm-up only: the working weight is at or below the bar, so there is nothing to warm up with. */
    AtOrBelowBar,
}

/**
 * What a calculator sheet renders: the [draft] and the derived results. [warmUp] is filled for the warm-up
 * calculator and [plates] for the plate calculator; both are empty/null while [hint] is set. [roundingKg] is the
 * dumbbell step for dumbbell exercises and the plate step for the rest.
 */
data class CalculatorUiState(
    val draft: CalculatorDraft,
    val weightKg: Double?,
    val roundingKg: Double,
    val warmUp: List<WarmUpSet> = emptyList(),
    val plates: PlateResult? = null,
    val hint: CalculatorHint? = null,
    /** Dumbbell exercise: the bar is the handle, so the sheet shows no bar selector. */
    val isDumbbell: Boolean = false,
) {
    /** "Añadir series" is enabled only with a warm-up to insert. */
    val canAddWarmUp: Boolean get() = draft.kind == CalculatorKind.WarmUp && warmUp.isNotEmpty()
}

/**
 * Derives the sheet state. The warm-up bar is not selectable (ADR-56): always the Preferencias bar, the handle for
 * dumbbells; the plate calculator keeps its chosen bar except for dumbbells (ADR-55). A draft saved with another bar
 * is re-based here; [CalculatorUiState.draft] carries the value actually used.
 */
internal fun calculatorUiState(
    savedDraft: CalculatorDraft,
    preferences: TrainingPreferences,
    equipment: Equipment?,
): CalculatorUiState {
    val dumbbell = equipment == Equipment.Dumbbell
    val bar = if (savedDraft.kind == CalculatorKind.WarmUp) {
        CalculatorBase.forEquipment(equipment, preferences.calculators)
    } else {
        barFor(savedDraft.barKg, equipment, preferences)
    }
    val draft = savedDraft.copy(barKg = bar)
    val weight = NumberInputDefaults.parse(draft.weightText)?.takeIf { it > 0.0 }
    val rounding = if (dumbbell) preferences.dumbbellRoundingKg else preferences.plateRoundingKg
    return calculatorResult(draft, weight, rounding, preferences).copy(isDumbbell = dumbbell)
}

private fun calculatorResult(
    draft: CalculatorDraft,
    weight: Double?,
    rounding: Double,
    preferences: TrainingPreferences,
): CalculatorUiState = when {
    weight == null -> CalculatorUiState(draft, null, rounding, hint = CalculatorHint.MissingWeight)

    draft.kind == CalculatorKind.Plates -> CalculatorUiState(
        draft,
        weight,
        rounding,
        plates = PlateCalculator(weight, draft.barKg, preferences.plateKg),
    )

    weight <= draft.barKg -> CalculatorUiState(draft, weight, rounding, hint = CalculatorHint.AtOrBelowBar)

    else -> CalculatorUiState(
        draft,
        weight,
        rounding,
        warmUp = WarmUpCalculator(weight, draft.barKg, rounding, draft.warmUpSets),
    )
}

/** The bar to use: the handle for dumbbells (not selectable), the [chosenKg] bar for the rest. */
internal fun barFor(chosenKg: Double, equipment: Equipment?, preferences: TrainingPreferences): Double =
    if (equipment == Equipment.Dumbbell) CalculatorBase.forEquipment(equipment, preferences.calculators) else chosenKg

private const val KEY_POSITION = "live_calc_position"
private const val KEY_KIND = "live_calc_kind"
private const val KEY_WEIGHT = "live_calc_weight"
private const val KEY_BAR = "live_calc_bar"
private const val KEY_SETS = "live_calc_sets"
private const val KEY_WEIGHT_EDITED = "live_calc_weight_edited"

internal fun SavedStateHandle.restoredCalculatorDraft(): CalculatorDraft? {
    val position = get<Int>(KEY_POSITION)
    val kind = CalculatorKind.entries.firstOrNull { it.name == get<String>(KEY_KIND) }
    val bar = get<Double>(KEY_BAR)
    return if (position == null || kind == null || bar == null) {
        null
    } else {
        CalculatorDraft(
            position,
            kind,
            get<String>(KEY_WEIGHT).orEmpty(),
            bar,
            get<Int>(KEY_SETS) ?: WarmUpCalculator.DEFAULT_SETS,
            get<Boolean>(KEY_WEIGHT_EDITED) ?: false,
        )
    }
}

internal fun SavedStateHandle.persistCalculatorDraft(draft: CalculatorDraft?) {
    this[KEY_POSITION] = draft?.position
    this[KEY_KIND] = draft?.kind?.name
    this[KEY_WEIGHT] = draft?.weightText
    this[KEY_BAR] = draft?.barKg
    this[KEY_SETS] = draft?.warmUpSets
    this[KEY_WEIGHT_EDITED] = draft?.weightEdited
}
