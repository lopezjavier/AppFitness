package io.github.lopezjavier.appfitness.feature.onboarding

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.Sex
import kotlinx.datetime.LocalDate

/** Steps of the onboarding flow. [Goals] is a stub until the goals task replaces it. */
enum class OnboardingStep {
    Sex,
    BirthDate,
    Height,
    Weight,
    Activity,
    Goals,
    ;

    /** 1-based number shown as "N / 5". The goals stub has none. */
    val number: Int get() = ordinal + 1

    fun previous(): OnboardingStep? = entries.getOrNull(ordinal - 1)

    fun next(): OnboardingStep? = entries.getOrNull(ordinal + 1)

    companion object {
        const val BIOMETRIC_STEPS = 5
    }
}

/** What the screen is doing besides showing the current step. */
enum class OnboardingPhase { Loading, Content, LoadError, Saving }

/** Why the last save failed: no connection, or anything else. */
enum class OnboardingSaveError { Network, Other }

/**
 * State of the onboarding flow. The user values always hold a valid value (the defaults of [BiometricBounds]
 * until the user changes them), except [sex] and [activity], which start unselected.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Sex,
    val sex: Sex? = null,
    val birthDate: LocalDate,
    val heightCm: Int = BiometricBounds.DEFAULT_HEIGHT_CM,
    val weightTenths: Int = BiometricBounds.DEFAULT_WEIGHT_TENTHS,
    val activity: ActivityLevel? = null,
    val phase: OnboardingPhase = OnboardingPhase.Loading,
    val saveError: OnboardingSaveError? = null,
    val isOffline: Boolean = false,
    val today: LocalDate,
) {
    /** Whether "Continuar" is enabled. Only the save step needs the network. */
    val canContinue: Boolean
        get() = phase == OnboardingPhase.Content &&
            when (step) {
                OnboardingStep.Sex -> sex != null
                OnboardingStep.Activity -> activity != null && !isOffline
                OnboardingStep.Goals -> false
                else -> true
            }

    /** Whether the offline hint explaining the disabled save is shown. */
    val showOfflineHint: Boolean
        get() = step == OnboardingStep.Activity && isOffline && phase == OnboardingPhase.Content

    val canGoBack: Boolean get() = step != OnboardingStep.Sex && phase != OnboardingPhase.Saving
}

/** Events of the onboarding screen. */
sealed interface OnboardingEvent {
    data class SelectSex(val sex: Sex) : OnboardingEvent

    data class SetBirthDate(val date: LocalDate) : OnboardingEvent

    data class SetHeight(val heightCm: Int) : OnboardingEvent

    data class SetWeight(val tenths: Int) : OnboardingEvent

    data class SelectActivity(val level: ActivityLevel) : OnboardingEvent

    data object Continue : OnboardingEvent

    data object Back : OnboardingEvent

    data object RetryLoad : OnboardingEvent

    data object RetrySave : OnboardingEvent
}
