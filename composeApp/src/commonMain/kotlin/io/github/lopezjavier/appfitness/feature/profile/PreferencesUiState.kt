package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MacroTargets
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.domain.usecase.CalculateMacroTargets
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyEvent
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyUiState
import io.github.lopezjavier.appfitness.feature.goals.energyStrategyUiState
import kotlinx.datetime.LocalDate

/**
 * Everything Preferencias edits: the name, the biometrics, the current weight, the energy strategy and the macro
 * split. [weightKg] has one decimal (a multiple of 0.1).
 */
data class PreferencesForm(
    val name: String,
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Int,
    val weightKg: Double,
    val activity: ActivityLevel,
    val strategy: GoalStrategy,
    val adjustment: Int,
    val split: MacroSplit,
    /** "Incluir calentamiento": warm-up sets count in volume, 1RM, records and set totals. */
    val warmUpIncluded: Boolean = false,
    /** "Valores anteriores": where the live session takes the previous values from. */
    val previousValuesMode: PreviousValuesMode = PreviousValuesMode.Overall,
    /** "Calculadoras": default bar, available plates and the plate/dumbbell rounding. */
    val calculators: CalculatorSettings = CalculatorSettings(),
) {
    fun toBiometrics(): Biometrics = Biometrics(sex, birthDate, heightCm, activity)

    /** The name without surrounding blanks is what gets stored; an empty one is not valid. */
    val isNameValid: Boolean get() = name.isNotBlank()
}

/** State of the Preferencias screen. */
sealed interface PreferencesUiState {
    data object Loading : PreferencesUiState

    /** Any read failed, or the profile is incomplete (no weight, missing biometrics). */
    data object LoadFailed : PreferencesUiState

    /**
     * [form] is what the user is editing, [saved] what the server holds. The energy card and the macro targets follow
     * [form] live, including its weight.
     */
    data class Loaded(
        val form: PreferencesForm,
        val saved: PreferencesForm,
        val today: LocalDate,
        val isSaving: Boolean = false,
        val isOnline: Boolean = true,
        /** The latest weight comes from Health Connect: the weight is read-only here (ADR-47). */
        val weightFromHealthConnect: Boolean = false,
        /** The user tried to switch off the last plate; cleared by the next accepted plate change. */
        val platesError: Boolean = false,
    ) : PreferencesUiState {
        val isDirty: Boolean get() = form != saved

        /** "Guardar" needs a change, a filled name, the network and no save running. */
        val canSave: Boolean
            get() = isDirty && form.isNameValid && form.calculators.plateKg.isNotEmpty() && isOnline && !isSaving

        val energy: EnergyStrategyUiState
            get() = energyStrategyUiState(
                sex = form.sex,
                birthDate = form.birthDate,
                heightCm = form.heightCm,
                weightKg = form.weightKg,
                activityLevel = form.activity,
                today = today,
                strategy = form.strategy,
                adjustment = form.adjustment,
                enabled = !isSaving,
            )

        val macroTargets: MacroTargets get() = CalculateMacroTargets(energy.targetKcal, form.split)
    }
}

/** Events of the Preferencias screen. */
sealed interface PreferencesEvent {
    data class SetName(val name: String) : PreferencesEvent

    data class SelectSex(val sex: Sex) : PreferencesEvent

    data class SetBirthDate(val date: LocalDate) : PreferencesEvent

    data class SetHeight(val heightCm: Int) : PreferencesEvent

    /** The weight picker applied [tenths] tenths of a kilogram. */
    data class SetWeight(val tenths: Int) : PreferencesEvent

    data class SelectActivity(val level: ActivityLevel) : PreferencesEvent

    data class Energy(val event: EnergyStrategyEvent) : PreferencesEvent

    /** A macro slider moved to [percent]; the others rebalance. */
    data class MoveMacro(val macro: Macronutrient, val percent: Int) : PreferencesEvent

    /** The "Incluir calentamiento" switch; only a draft until [Save]. */
    data class SetWarmUpIncluded(val included: Boolean) : PreferencesEvent

    /** The "Valores anteriores" selector; only a draft until [Save]. */
    data class SetPreviousValuesMode(val mode: PreviousValuesMode) : PreferencesEvent

    /** Default bar of the calculators (20, 15, 10 or 7,5 kg); only a draft until [Save]. */
    data class SetBarWeight(val kg: Double) : PreferencesEvent

    /** Dumbbell handle weight in kg (clamped to 0,5..10, steps of 0,5); only a draft until [Save]. */
    data class SetDumbbellHandle(val kg: Double) : PreferencesEvent

    /** Switches one plate of the catalogue on or off; the last plate cannot be switched off (sets `platesError`). */
    data class SetPlateEnabled(val kg: Double, val enabled: Boolean) : PreferencesEvent

    data object Save : PreferencesEvent

    data object Retry : PreferencesEvent

    /** Reloads only the weight and its Health Connect flag (e.g. on resume); never saves, keeps other fields. */
    data object Refresh : PreferencesEvent
}

/** One-off results shown as a snackbar (the screen also fires the haptic on [Saved]). */
sealed interface PreferencesEffect {
    data object Saved : PreferencesEffect

    /** [offline] is true when the failure was the network. */
    data class SaveFailed(val offline: Boolean) : PreferencesEffect
}
