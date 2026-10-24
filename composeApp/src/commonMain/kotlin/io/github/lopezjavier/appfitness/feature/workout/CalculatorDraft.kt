package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.designsystem.NumberInputDefaults
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
import io.github.lopezjavier.appfitness.domain.usecase.CalculatorBase
import io.github.lopezjavier.appfitness.domain.usecase.DiscLoader
import io.github.lopezjavier.appfitness.domain.usecase.WarmUpCalculator

/** The two calculators of the exercise menu (US-33): the warm-up calculator and the disc loader (ADR-56). */
enum class CalculatorKind { WarmUp, Discs }

/**
 * The open calculator sheet of the exercise at [position]. Warm-up: the weight as typed ([weightText], prefilled from
 * the exercise). Disc loader: the number of discs of each weight on one side ([discs], starts empty). The bar is
 * never part of the draft: it is the Preferencias bar, or the handle for dumbbells. Mirrored in the saved state
 * (ADR-40).
 */
data class CalculatorDraft(
    val position: Int,
    val kind: CalculatorKind,
    val weightText: String = "",
    /** Warm-up only: how many warm-up sets (1..4, ADR-55); per use, never stored in the preferences. */
    val warmUpSets: Int = WarmUpCalculator.DEFAULT_SETS,
    /** The user typed in the weight field: a late best-past-weight must not overwrite it (US-33). */
    val weightEdited: Boolean = false,
    /** Disc loader only: discs per side by weight in kg; counts 1..[DiscLoader.MAX_DISCS_PER_WEIGHT]. */
    val discs: Map<Double, Int> = emptyMap(),
)

/** Why the calculator shows no result. */
enum class CalculatorHint {
    /** The weight is empty or zero. */
    MissingWeight,

    /** Warm-up only: the working weight is at or below the bar, so there is nothing to warm up with. */
    AtOrBelowBar,
}

/**
 * What a calculator sheet renders: the [draft] and the derived results. [barKg] is the bar or handle actually used,
 * [roundingKg] the derived rounding (twice the smallest enabled plate). [warmUp] is filled for the warm-up calculator
 * (empty while [hint] is set); [totalKg] is the disc loader total (bar plus both sides).
 */
data class CalculatorUiState(
    val draft: CalculatorDraft,
    val weightKg: Double?,
    val barKg: Double,
    val roundingKg: Double,
    val warmUp: List<WarmUpSet> = emptyList(),
    val totalKg: Double? = null,
    val hint: CalculatorHint? = null,
    /** Dumbbell exercise: the bar is the handle. */
    val isDumbbell: Boolean = false,
    /** Disc loader: the plates the sheet offers (enabled in Preferencias), heaviest first. */
    val availableDiscs: List<Double> = emptyList(),
) {
    /** "Añadir series" is enabled only with a warm-up to insert. */
    val canAddWarmUp: Boolean get() = draft.kind == CalculatorKind.WarmUp && warmUp.isNotEmpty()
}

/**
 * Derives the sheet state. The bar is not selectable (ADR-56): always the Preferencias bar, the handle for dumbbells.
 * Discs of a plate that is no longer enabled are dropped and counts are capped, so a restored draft is always valid.
 */
internal fun calculatorUiState(
    savedDraft: CalculatorDraft,
    preferences: TrainingPreferences,
    equipment: Equipment?,
): CalculatorUiState {
    val settings = preferences.calculators
    val bar = CalculatorBase.forEquipment(equipment, settings)
    val available = settings.plateKg.distinct().sortedDescending()
    val dumbbell = equipment == Equipment.Dumbbell
    if (savedDraft.kind == CalculatorKind.Discs) {
        val discs = savedDraft.discs
            .filter { (kg, count) -> kg in available && count > 0 }
            .mapValues { it.value.coerceAtMost(DiscLoader.MAX_DISCS_PER_WEIGHT) }
        return CalculatorUiState(
            draft = savedDraft.copy(discs = discs),
            weightKg = null,
            barKg = bar,
            roundingKg = settings.roundingKg,
            totalKg = DiscLoader.total(bar, discs),
            isDumbbell = dumbbell,
            availableDiscs = available,
        )
    }
    val weight = NumberInputDefaults.parse(savedDraft.weightText)?.takeIf { it > 0.0 }
    val base = CalculatorUiState(savedDraft, weight, bar, settings.roundingKg, isDumbbell = dumbbell)
    return when {
        weight == null -> base.copy(hint = CalculatorHint.MissingWeight)
        weight <= bar -> base.copy(hint = CalculatorHint.AtOrBelowBar)
        else -> base.copy(warmUp = WarmUpCalculator(weight, bar, settings.roundingKg, savedDraft.warmUpSets))
    }
}

/** [discs] with the count of [weightKg] set to [count] (0..[DiscLoader.MAX_DISCS_PER_WEIGHT]); zero removes it. */
internal fun CalculatorDraft.withDiscCount(weightKg: Double, count: Int): CalculatorDraft {
    val capped = count.coerceIn(0, DiscLoader.MAX_DISCS_PER_WEIGHT)
    return copy(discs = if (capped == 0) discs - weightKg else discs + (weightKg to capped))
}

private const val KEY_POSITION = "live_calc_position"
private const val KEY_KIND = "live_calc_kind"
private const val KEY_WEIGHT = "live_calc_weight"
private const val KEY_SETS = "live_calc_sets"
private const val KEY_WEIGHT_EDITED = "live_calc_weight_edited"
private const val KEY_DISCS = "live_calc_discs"
private const val LEGACY_KIND_PLATES = "Plates"

/** "25.0:1,15.0:2": the disc counts as saved; entries that do not parse are skipped. */
internal fun encodeDiscs(discs: Map<Double, Int>): String =
    discs.entries.joinToString(",") { (kg, count) -> "$kg:$count" }

internal fun decodeDiscs(text: String?): Map<Double, Int> = text.orEmpty().split(',').mapNotNull { entry ->
    val kg = entry.substringBefore(':').toDoubleOrNull()
    val count = entry.substringAfter(':', "").toIntOrNull()
    if (kg == null || count == null || count <= 0) null else kg to count.coerceAtMost(DiscLoader.MAX_DISCS_PER_WEIGHT)
}.toMap()

/**
 * The saved calculator draft. A draft saved before ADR-56 (kind "Plates", a chosen bar) restores as an empty disc
 * loader; an unknown kind restores nothing.
 */
internal fun SavedStateHandle.restoredCalculatorDraft(): CalculatorDraft? {
    val position = get<Int>(KEY_POSITION)
    val kindName = get<String>(KEY_KIND)?.let { if (it == LEGACY_KIND_PLATES) CalculatorKind.Discs.name else it }
    val kind = CalculatorKind.entries.firstOrNull { it.name == kindName }
    return if (position == null || kind == null) {
        null
    } else {
        CalculatorDraft(
            position = position,
            kind = kind,
            weightText = get<String>(KEY_WEIGHT).orEmpty(),
            warmUpSets = get<Int>(KEY_SETS) ?: WarmUpCalculator.DEFAULT_SETS,
            weightEdited = get<Boolean>(KEY_WEIGHT_EDITED) ?: false,
            discs = decodeDiscs(get<String>(KEY_DISCS)),
        )
    }
}

internal fun SavedStateHandle.persistCalculatorDraft(draft: CalculatorDraft?) {
    this[KEY_POSITION] = draft?.position
    this[KEY_KIND] = draft?.kind?.name
    this[KEY_WEIGHT] = draft?.weightText
    this[KEY_SETS] = draft?.warmUpSets
    this[KEY_WEIGHT_EDITED] = draft?.weightEdited
    this[KEY_DISCS] = draft?.let { encodeDiscs(it.discs) }
}
