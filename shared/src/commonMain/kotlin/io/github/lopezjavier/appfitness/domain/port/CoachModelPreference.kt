package io.github.lopezjavier.appfitness.domain.port

import io.github.lopezjavier.appfitness.domain.model.CoachModelChoice
import kotlinx.coroutines.flow.StateFlow

/**
 * The AI model used by coach requests (a test aid in "Uso de IA").
 *
 * Process-scoped on purpose (ADR-40 exception): it is NOT restored after process death and resets to
 * [CoachModelChoice.Default]. It survives leaving the screen, rotation and ViewModel recreation because the state
 * lives in a singleton, so the screen always shows what the requests send.
 */
interface CoachModelPreference {
    val selected: StateFlow<CoachModelChoice>

    fun select(choice: CoachModelChoice)
}
