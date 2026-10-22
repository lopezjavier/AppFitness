package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MacroTargets
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
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
    ) : PreferencesUiState {
        val isDirty: Boolean get() = form != saved

        /** "Guardar" needs a change, a filled name, the network and no save running. */
        val canSave: Boolean get() = isDirty && form.isNameValid && isOnline && !isSaving

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

    data object Save : PreferencesEvent

    data object Retry : PreferencesEvent
}

/** One-off results shown as a snackbar (the screen also fires the haptic on [Saved]). */
sealed interface PreferencesEffect {
    data object Saved : PreferencesEffect

    /** [offline] is true when the failure was the network. */
    data class SaveFailed(val offline: Boolean) : PreferencesEffect
}
