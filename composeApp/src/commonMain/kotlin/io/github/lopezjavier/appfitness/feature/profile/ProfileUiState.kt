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

/** Everything the Profile screen edits: the biometrics, the energy strategy and the macro split. */
data class ProfileForm(
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Int,
    val activity: ActivityLevel,
    val strategy: GoalStrategy,
    val adjustment: Int,
    val split: MacroSplit,
) {
    fun toBiometrics(): Biometrics = Biometrics(sex, birthDate, heightCm, activity)
}

/** State of the Profile screen. */
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    /** Any read failed, or the profile is incomplete (no weight, missing biometrics). */
    data object LoadFailed : ProfileUiState

    /**
     * [form] is what the user is editing, [saved] what the server holds. [weightKg] is the latest weight (read-only
     * here). The energy card and the macro targets follow [form] live.
     */
    data class Loaded(
        val form: ProfileForm,
        val saved: ProfileForm,
        val weightKg: Double,
        val today: LocalDate,
        val isSaving: Boolean = false,
        val isOnline: Boolean = true,
    ) : ProfileUiState {
        val isDirty: Boolean get() = form != saved

        /** "Guardar" is enabled when something changed, the network is up and no save is running. */
        val canSave: Boolean get() = isDirty && isOnline && !isSaving

        val energy: EnergyStrategyUiState
            get() = energyStrategyUiState(
                sex = form.sex,
                birthDate = form.birthDate,
                heightCm = form.heightCm,
                weightKg = weightKg,
                activityLevel = form.activity,
                today = today,
                strategy = form.strategy,
                adjustment = form.adjustment,
                enabled = !isSaving,
            )

        val macroTargets: MacroTargets get() = CalculateMacroTargets(energy.targetKcal, form.split)
    }
}

/** Events of the Profile screen. */
sealed interface ProfileEvent {
    data class SelectSex(val sex: Sex) : ProfileEvent

    data class SetBirthDate(val date: LocalDate) : ProfileEvent

    data class SetHeight(val heightCm: Int) : ProfileEvent

    data class SelectActivity(val level: ActivityLevel) : ProfileEvent

    data class Energy(val event: EnergyStrategyEvent) : ProfileEvent

    /** A macro slider moved to [percent]; the others rebalance. */
    data class MoveMacro(val macro: Macronutrient, val percent: Int) : ProfileEvent

    data object Save : ProfileEvent

    data object Retry : ProfileEvent

    /** The user confirmed the sign-out dialog. */
    data object SignOutConfirmed : ProfileEvent
}

/** One-off results shown as a snackbar (the screen also fires the haptic on [Saved]). */
sealed interface ProfileEffect {
    data object Saved : ProfileEffect

    /** [offline] is true when the failure was the network. */
    data class SaveFailed(val offline: Boolean) : ProfileEffect
}
