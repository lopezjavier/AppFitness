package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.data.InMemoryCoachModelPreference
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachModelChoice
import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import io.github.lopezjavier.appfitness.domain.repository.CoachUsageRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private object FakeUsageRepository : CoachUsageRepository {
    override suspend fun currentMonth(today: LocalDate): Result<CoachUsage> =
        Result.success(CoachUsage(today, 0, 0, 0.1))
}

/** ADR-40 exception: the AI model choice lives in a process-scoped singleton, so it outlives recreation only. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class CoachUsageRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val usage = CoachUsageUiState.Content(CoachUsage(LocalDate(2026, 11, 1), 0, 0, 0.1))

    private fun StateRestorationTester.setScreen(preference: InMemoryCoachModelPreference) {
        setContent {
            val model by preference.selected.collectAsState()
            AppTheme {
                CoachUsageScreen(
                    usage,
                    flowOf(true),
                    { if (it is CoachUsageEvent.SelectModel) preference.select(it.choice) },
                    {},
                    model = model,
                )
            }
        }
    }

    @Test
    fun recreation_keepsTheChosenModelSelected() {
        val preference = InMemoryCoachModelPreference()
        val tester = StateRestorationTester(rule)
        tester.setScreen(preference)
        rule.onNodeWithTag("coach_usage_model_haiku").performScrollTo().performClick()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("coach_usage_model_haiku").assertIsSelected()
    }

    @Test
    fun viewModelRecreatedOverTheSamePreference_showsTheChosenModel() {
        val preference = InMemoryCoachModelPreference()
        CoachUsageViewModel(FakeUsageRepository, MutableClock(), preference)
            .onEvent(CoachUsageEvent.SelectModel(CoachModelChoice.Haiku))

        val recreated = CoachUsageViewModel(FakeUsageRepository, MutableClock(), preference)

        assertEquals(CoachModelChoice.Haiku, recreated.model.value)
    }

    @Test
    fun newProcess_startsFromTheDefaultModel() {
        InMemoryCoachModelPreference().select(CoachModelChoice.Haiku)

        val afterProcessDeath = InMemoryCoachModelPreference()

        assertEquals(CoachModelChoice.Default, afterProcessDeath.selected.value)
    }
}
