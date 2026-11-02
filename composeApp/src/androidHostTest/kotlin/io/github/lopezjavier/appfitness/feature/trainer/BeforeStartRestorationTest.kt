package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: "Antes de empezar" and "Tus datos" keep their arguments and the open sheet when the model is recreated. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class BeforeStartRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachRepository()
    private val created = mutableListOf<BeforeStartEffect.Created>()

    private fun missingActivity() = PlanInputs(
        PlanInputKind.Plan,
        null,
        listOf(
            planInput(
                PlanInputKey.ActivityLevel,
                PlanInputStatus.Missing,
                PlanInputValue.Absent,
                blocking = true,
                fixTarget = PlanFixTarget.ActivitySheet,
            ),
        ),
    )

    @Test
    fun recreatedBeforeStart_reopensTheSheet_withoutConfirmingOrCreating() {
        repository.inputs[PlanInputKind.Plan] = Result.success(missingActivity())
        val saved = SavedStateHandle(mapOf("mode" to "plan", "replacingPlanId" to "plan-1"))
        var current by mutableStateOf(
            BeforeStartViewModel(
                repository,
                FakeCoachIntakeRepository(),
                FakeIntakePromptPreference(asked = true),
                MutableClock(),
                saved,
            ),
        )
        rule.setContent {
            AppTheme {
                BeforeStartRoot(
                    onBack = {},
                    onCreate = { created += it },
                    onOpenYourData = {},
                    onFix = {},
                    onOpenIntake = {},
                    viewModel = current,
                    connectivity = FakeConnectivityObserver(),
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("before_start_flag_activity_level").performClick()
        rule.waitForIdle()

        // A new view model over the same saved state, as after process death: the inputs are fetched again.
        current =
            BeforeStartViewModel(
                repository,
                FakeCoachIntakeRepository(),
                FakeIntakePromptPreference(asked = true),
                MutableClock(),
                saved,
            )
        rule.waitForIdle()

        rule.onNodeWithTag("before_start_activity_sheet_activity_moderate").assertIsSelected()
        assertTrue(repository.confirmCalls.isEmpty())
        assertTrue(created.isEmpty())
    }

    @Test
    fun recreatedBeforeStart_inBlockMode_keepsTheBlockLinesAndTheirArguments() {
        repository.inputs[PlanInputKind.Block] = Result.success(
            PlanInputs(
                PlanInputKind.Block,
                null,
                listOf(planInput(PlanInputKey.Place, value = PlanInputValue.Text("home"))),
            ),
        )
        val saved = SavedStateHandle(mapOf("mode" to "block", "planId" to "plan-2"))
        var current by mutableStateOf(
            BeforeStartViewModel(
                repository,
                FakeCoachIntakeRepository(),
                FakeIntakePromptPreference(asked = true),
                MutableClock(),
                saved,
            ),
        )
        rule.setContent {
            AppTheme {
                BeforeStartRoot(
                    onBack = {},
                    onCreate = { created += it },
                    onOpenYourData = {},
                    onFix = {},
                    onOpenIntake = {},
                    viewModel = current,
                    connectivity = FakeConnectivityObserver(),
                )
            }
        }
        rule.waitForIdle()

        current =
            BeforeStartViewModel(
                repository,
                FakeCoachIntakeRepository(),
                FakeIntakePromptPreference(asked = true),
                MutableClock(),
                saved,
            )
        rule.waitForIdle()

        rule.onNodeWithTag("before_start_row_place", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("before_start_create").performClick()
        rule.waitForIdle()
        assertEquals(listOf(BeforeStartEffect.Created(BeforeStartMode.Block, "plan-2", null)), created)
    }

    @Test
    fun recreatedYourData_reopensTheSheet() {
        repository.inputs[PlanInputKind.Plan] = Result.success(planKeys())
        val saved = SavedStateHandle()
        var current by mutableStateOf(YourDataViewModel(repository, FakeCoachIntakeRepository(), MutableClock(), saved))
        rule.setContent {
            AppTheme {
                YourDataRoot(onBack = {}, onFix = {}, viewModel = current, connectivity = FakeConnectivityObserver())
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("your_data_row_activity_level").performClick()
        rule.waitForIdle()

        current = YourDataViewModel(repository, FakeCoachIntakeRepository(), MutableClock(), saved)
        rule.waitForIdle()

        rule.onNodeWithTag("your_data_activity_sheet_activity_light").assertIsSelected()
        assertTrue(repository.confirmCalls.isEmpty())
    }
}
