package io.github.lopezjavier.appfitness.data

import io.github.lopezjavier.appfitness.domain.model.CoachModelChoice
import io.github.lopezjavier.appfitness.domain.port.CoachModelPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the choice in memory only: a new process starts with [CoachModelChoice.Default]. */
class InMemoryCoachModelPreference : CoachModelPreference {
    private val state = MutableStateFlow(CoachModelChoice.Default)
    override val selected: StateFlow<CoachModelChoice> = state.asStateFlow()

    override fun select(choice: CoachModelChoice) {
        state.value = choice
    }
}
