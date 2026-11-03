package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.IntakeHost
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val STORY = "Entrené 4 años y lo dejé por una lesión de hombro"

/** ADR-40: the intake editors survive recreation and process death with their draft and open sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class IntakeRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachIntakeRepository()
    private val coach = FakeCoachRepository().apply { inputs[PlanInputKind.Plan] = Result.success(planKeys()) }
    private val connectivity = FakeConnectivityObserver()
    private val host = IntakeHost(onDone = {}, onClose = {})

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun beforeStartModel(handle: SavedStateHandle) =
        BeforeStartViewModel(coach, repository, MutableClock(), FakeRoutineRepository(), handle)

    private fun yourDataModel(handle: SavedStateHandle) = YourDataViewModel(coach, repository, MutableClock(), handle)

    @Test
    fun theFreeTextDraft_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        val handle = SavedStateHandle()
        var current: IntakeViewModel? = null
        tester.setContent {
            AppTheme {
                val model = remember { IntakeViewModel(repository, handle).also { current = it } }
                IntakeRoot(host, viewModel = model, connectivity = connectivity)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("intake_free_text").performTextInput(STORY)
        val before = current

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertNotSame(before, current)
        rule.onNodeWithTag("intake_free_text_counter").assertTextEquals("${STORY.length}/500")
        rule.onNodeWithTag("intake_save").assertExists()
    }

    @Test
    fun aViewModelRecreatedFromTheSameState_showsTheDraft_overTheStoredText() {
        repository.stored = CoachIntake(freeText = "Antes")
        val handle = SavedStateHandle()
        var current by mutableStateOf(IntakeViewModel(repository, handle))
        rule.setContent {
            AppTheme { IntakeRoot(host, viewModel = current, connectivity = connectivity) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("intake_free_text").performTextInput(" y ahora")

        current = IntakeViewModel(repository, handle)
        rule.waitForIdle()

        rule.onNodeWithTag("intake_free_text_counter").assertTextEquals("${"Antes y ahora".length}/500")
    }

    @Test
    fun theDiscardDialog_staysOpen_afterRecreation() {
        val tester = StateRestorationTester(rule)
        val handle = SavedStateHandle()
        tester.setContent {
            AppTheme {
                val model = remember { IntakeViewModel(repository, handle) }
                IntakeRoot(host, viewModel = model, connectivity = connectivity)
            }
        }
        rule.waitForIdle()
        rule.rootText(STORY)
        tap("intake_back")
        rule.onNodeWithTag("intake_discard_dialog").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("intake_discard_dialog").assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.rootText(text: String) {
        onNodeWithTag("intake_free_text").performTextInput(text)
        waitForIdle()
    }

    @Test
    fun theYearsSheetOfAntesDeEmpezar_staysOpenWithItsValue_afterTheModelIsRecreated() {
        repository.stored = CoachIntake(trainingYears = 2.0)
        val handle = SavedStateHandle(mapOf("mode" to "plan"))
        var current by mutableStateOf(beforeStartModel(handle))
        rule.setContent {
            AppTheme {
                BeforeStartRoot(
                    onBack = {},
                    onCreate = {},
                    onOpenYourData = {},
                    onFix = {},
                    viewModel = current,
                    connectivity = connectivity,
                )
            }
        }
        rule.waitForIdle()
        tap("before_start_row_training_years")
        tap("intake_years_sheet_stepper_increment")

        current = beforeStartModel(handle)
        rule.waitForIdle()

        rule.onNodeWithTag("intake_years_sheet").assertExists()
        rule.onNodeWithTag("intake_years_sheet_stepper", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("intake_years_sheet_commit").assertExists()
    }

    @Test
    fun theMaintenanceSheetOfAntesDeEmpezar_keepsWhatWasTyped_afterTheModelIsRecreated() {
        val handle = SavedStateHandle(mapOf("mode" to "plan"))
        var current by mutableStateOf(beforeStartModel(handle))
        rule.setContent {
            AppTheme {
                BeforeStartRoot(
                    onBack = {},
                    onCreate = {},
                    onOpenYourData = {},
                    onFix = {},
                    viewModel = current,
                    connectivity = connectivity,
                )
            }
        }
        rule.waitForIdle()
        tap("before_start_row_maintenance")
        rule.onNodeWithTag("intake_maintenance_sheet_field").performTextInput("2400")
        rule.waitForIdle()

        current = beforeStartModel(handle)
        rule.waitForIdle()

        rule.onNodeWithTag("intake_maintenance_sheet").assertExists()
        rule.onNodeWithTag("intake_maintenance_sheet_field").assertTextEquals("kcal al día", "2400")
    }

    @Test
    fun theMaintenanceSheetOfTusDatos_keepsWhatWasTyped_afterTheModelIsRecreated() {
        val handle = SavedStateHandle()
        var current by mutableStateOf(yourDataModel(handle))
        rule.setContent {
            AppTheme {
                YourDataRoot(onBack = {}, onFix = {}, viewModel = current, connectivity = connectivity)
            }
        }
        rule.waitForIdle()
        tap("your_data_row_maintenance")
        rule.onNodeWithTag("intake_maintenance_sheet_field").performTextInput("2400")
        rule.waitForIdle()

        current = yourDataModel(handle)
        rule.waitForIdle()

        rule.onNodeWithTag("intake_maintenance_sheet").assertExists()
        rule.onNodeWithTag("intake_maintenance_sheet_field").assertTextEquals("kcal al día", "2400")
    }
}
