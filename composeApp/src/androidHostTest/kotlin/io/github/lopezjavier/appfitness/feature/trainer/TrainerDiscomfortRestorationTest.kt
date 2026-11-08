package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeDiscomfortFollowUpRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the Trainer with the swap picker open comes back with the picker open and the offer intact. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class TrainerDiscomfortRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeDiscomfortFollowUpRepository(
        followUps = listOf(followUp()),
        candidates = swapCandidates(),
    )
    private val saved = SavedStateHandle()

    private fun model() = TrainerViewModel(
        FakeCoachRepository(),
        MutableClock(),
        savedState = saved,
        discomfortFollowUps = repository,
        exerciseNames = discomfortNames(),
    )

    @Test
    fun recreation_keepsThePickerOpen_andTheOffer() {
        val tester = StateRestorationTester(rule)
        var current by mutableStateOf(model())
        tester.setContent {
            AppTheme { TrainerRoute(viewModel = current, connectivity = FakeConnectivityObserver()) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("trainer_discomfort_offer_card_accept").performClick()
        rule.waitForIdle()

        // A new view model over the same saved state, as after process death.
        current = model()
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("trainer_swap_picker_row_$CANDIDATE_FAVORITE_ID").assertExists()
        rule.onNodeWithTag("trainer_discomfort_offer_card").assertExists()
    }
}
