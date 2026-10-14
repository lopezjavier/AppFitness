package io.github.lopezjavier.appfitness.feature.onboarding

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.Sex
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyEvent
import io.github.lopezjavier.appfitness.feature.goals.EnergyStrategyUiState
import io.github.lopezjavier.appfitness.feature.goals.energyStrategyUiState
import kotlinx.datetime.LocalDate

/** Steps of the onboarding flow: three biometric steps and the [Goals] step (energy strategy). */
enum class OnboardingStep {
    AboutYou,
    Measures,
    Activity,
    Goals,
    ;

    /** 1-based number shown as "N / 4". */
    val number: Int get() = ordinal + 1

    fun previous(): OnboardingStep? = entries.getOrNull(ordinal - 1)

    fun next(): OnboardingStep? = entries.getOrNull(ordinal + 1)

    companion object {
        const val TOTAL_STEPS = 4

        /** Step for a saved name, mapping the names of the former five-step flow; unknown names start over. */
        fun fromSavedName(name: String?): OnboardingStep = when (name) {
            null -> AboutYou
            "Sex", "BirthDate" -> AboutYou
            "Height", "Weight" -> Measures
            else -> entries.firstOrNull { it.name == name } ?: AboutYou
        }
    }
}

/**
 * What the screen is doing besides showing the current step. [Completed]: the goals are saved and the confirmation
 * shows before the root leaves onboarding.
 */
enum class OnboardingPhase { Loading, Content, LoadError, Saving, Completed }

/** Why the last save failed: no connection, or anything else. */
enum class OnboardingSaveError { Network, Other }

/**
 * State of the onboarding flow. The user values always hold a valid value (the defaults of [BiometricBounds]
 * until the user changes them), except [sex] and [activity], which start unselected.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.AboutYou,
    val sex: Sex? = null,
    val birthDate: LocalDate,
    val heightCm: Int = BiometricBounds.DEFAULT_HEIGHT_CM,
    val weightTenths: Int = BiometricBounds.DEFAULT_WEIGHT_TENTHS,
    val activity: ActivityLevel? = null,
    val strategy: GoalStrategy = GoalStrategy.Maintenance,
    val adjustment: Int = 0,
    val phase: OnboardingPhase = OnboardingPhase.Loading,
    val saveError: OnboardingSaveError? = null,
    val isOffline: Boolean = false,
    val today: LocalDate,
) {
    /** Whether the primary button ("Continuar" or "Guardar objetivos") is enabled. The save steps need the network. */
    val canContinue: Boolean
        get() = phase == OnboardingPhase.Content &&
            when (step) {
                OnboardingStep.AboutYou -> sex != null
                OnboardingStep.Activity -> activity != null && !isOffline
                OnboardingStep.Goals -> !isOffline
                else -> true
            }

    /** Whether the offline hint explaining the disabled save is shown. */
    val showOfflineHint: Boolean
        get() = (step == OnboardingStep.Activity || step == OnboardingStep.Goals) &&
            isOffline &&
            phase == OnboardingPhase.Content

    val canGoBack: Boolean
        get() = step != OnboardingStep.AboutYou && phase != OnboardingPhase.Saving && phase != OnboardingPhase.Completed

    /** The energy strategy section of the Goals step, once sex and activity are known; its controls follow [phase]. */
    val energy: EnergyStrategyUiState?
        get() {
            val sex = sex ?: return null
            val activity = activity ?: return null
            if (step != OnboardingStep.Goals) return null
            return energyStrategyUiState(
                sex = sex,
                birthDate = birthDate,
                heightCm = heightCm,
                weightKg = weightTenths / TENTHS_PER_KG,
                activityLevel = activity,
                today = today,
                strategy = strategy,
                adjustment = adjustment,
                enabled = phase == OnboardingPhase.Content,
            )
        }
}

private const val TENTHS_PER_KG = 10.0

/** Events of the onboarding screen. */
sealed interface OnboardingEvent {
    data class SelectSex(val sex: Sex) : OnboardingEvent

    data class SetBirthDate(val date: LocalDate) : OnboardingEvent

    data class SetHeight(val heightCm: Int) : OnboardingEvent

    data class SetWeight(val tenths: Int) : OnboardingEvent

    data class SelectActivity(val level: ActivityLevel) : OnboardingEvent

    /** An event of the energy strategy section. */
    data class Energy(val event: EnergyStrategyEvent) : OnboardingEvent

    data object SaveGoals : OnboardingEvent

    data object Continue : OnboardingEvent

    data object Back : OnboardingEvent

    data object RetryLoad : OnboardingEvent
}
