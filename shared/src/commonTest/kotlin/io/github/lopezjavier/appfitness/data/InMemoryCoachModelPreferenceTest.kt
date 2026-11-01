package io.github.lopezjavier.appfitness.data

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.model.CoachModelChoice
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryCoachModelPreferenceTest {
    @Test
    fun startsWithOpus() {
        assertEquals(CoachModelChoice.Opus, InMemoryCoachModelPreference().selected.value)
    }

    @Test
    fun select_emitsTheNewChoice() = runTest {
        val preference = InMemoryCoachModelPreference()
        preference.selected.test {
            assertEquals(CoachModelChoice.Opus, awaitItem())
            preference.select(CoachModelChoice.Haiku)
            assertEquals(CoachModelChoice.Haiku, awaitItem())
        }
    }

    @Test
    fun aNewInstance_simulatingProcessDeath_isBackToTheDefault() {
        InMemoryCoachModelPreference().select(CoachModelChoice.Haiku)

        assertEquals(CoachModelChoice.Default, InMemoryCoachModelPreference().selected.value)
    }

    @Test
    fun wireKeys_matchTheServerWhitelistWithoutDuplicates() {
        val keys = CoachModelChoice.entries.map { it.wireKey }
        assertEquals(listOf("opus", "sonnet", "haiku"), keys)
        assertEquals(keys.size, keys.toSet().size)
    }
}
