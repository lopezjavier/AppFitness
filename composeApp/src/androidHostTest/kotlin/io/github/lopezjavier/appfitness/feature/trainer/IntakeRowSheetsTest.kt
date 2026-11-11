package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-81: each intake row opens only its own small editor, in "Antes de empezar" and in "Tus datos". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class IntakeRowSheetsTest {
    @get:Rule
    val rule = createComposeRule()

    private val coach = FakeCoachRepository().apply { inputs[PlanInputKind.Plan] = Result.success(planKeys()) }
    private val intake = FakeCoachIntakeRepository()
    private val fixed = mutableListOf<PlanFixTarget>()

    private fun showBeforeStart(online: Boolean = true) {
        val model = BeforeStartViewModel(
            coach,
            intake,
            MutableClock(),
            FakeRoutineRepository(),
            SavedStateHandle(mapOf("mode" to "plan")),
        )
        rule.setContent {
            AppTheme {
                BeforeStartRoot(
                    onBack = {},
                    onCreate = {},
                    onOpenYourData = {},
                    onFix = { fixed += it },
                    viewModel = model,
                    connectivity = FakeConnectivityObserver(online),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun showYourData() {
        val model = YourDataViewModel(coach, intake, MutableClock(), SavedStateHandle())
        rule.setContent {
            AppTheme {
                YourDataRoot(
                    onBack = {},
                    onFix = { fixed += it },
                    viewModel = model,
                    connectivity = FakeConnectivityObserver(),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun typeKcal(text: String) {
        rule.onNodeWithTag("intake_maintenance_sheet_field").performTextInput(text)
        rule.waitForIdle()
    }

    @Test
    fun nothingOpensOnItsOwn_andTheRowsReadSinIndicar() {
        showBeforeStart()

        rule.onNodeWithTag("intake_years_sheet").assertDoesNotExist()
        rule.onNodeWithTag("intake_maintenance_sheet").assertDoesNotExist()
        rule.onNodeWithTag("before_start_row_training_years").assertTextEquals("Tiempo entrenando", "Sin indicar")
        rule.onNodeWithTag("before_start_row_maintenance")
            .assertTextEquals("Calorías de mantenimiento", "Sin indicar")
        rule.onNodeWithTag("before_start_row_free_text").assertDoesNotExist()
        assertTrue(fixed.isEmpty())
    }

    @Test
    fun theYearsRow_opensOnlyTheYearsSheet() {
        showBeforeStart()

        tap("before_start_row_training_years")

        rule.onNodeWithTag("intake_years_sheet").assertExists()
        rule.onNodeWithTag("intake_maintenance_sheet").assertDoesNotExist()
        assertTrue(fixed.isEmpty())
    }

    @Test
    fun theMaintenanceRow_opensOnlyTheMaintenanceSheet() {
        showBeforeStart()

        tap("before_start_row_maintenance")

        rule.onNodeWithTag("intake_maintenance_sheet").assertExists()
        rule.onNodeWithTag("intake_years_sheet").assertDoesNotExist()
        assertTrue(fixed.isEmpty())
    }

    @Test
    fun theYearsStepper_startsAtSinIndicar_andSavingStoresOnlyTheYears() {
        intake.stored = CoachIntake(knownMaintenanceKcal = 2400)
        showBeforeStart()
        tap("before_start_row_training_years")
        rule.onNodeWithTag("intake_years_sheet_stepper").assertExists()
        rule.onNodeWithTag("intake_years_sheet_stepper_decrement").assertIsNotEnabled()

        tap("intake_years_sheet_stepper_increment")
        tap("intake_years_sheet_stepper_increment")
        tap("intake_years_sheet_commit")

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.TrainingYears(0.5)), intake.saveCalls)
        rule.onNodeWithTag("intake_years_sheet").assertDoesNotExist()
        rule.onNodeWithTag("before_start_row_training_years").assertTextEquals("Tiempo entrenando", "0,5 años")
        rule.onNodeWithTag("before_start_row_maintenance")
            .assertTextEquals("Calorías de mantenimiento", "2.400 kcal")
    }

    @Test
    fun stepRightDownToTheLowestStep_readsSinIndicarAgain() {
        intake.stored = CoachIntake(trainingYears = 0.0)
        showBeforeStart()
        tap("before_start_row_training_years")

        tap("intake_years_sheet_stepper_decrement")
        tap("intake_years_sheet_commit")

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.TrainingYears(null)), intake.saveCalls)
        rule.onNodeWithTag("before_start_row_training_years").assertTextEquals("Tiempo entrenando", "Sin indicar")
    }

    @Test
    fun aMaintenanceValue_isSaved_andShownInTheRowWithoutTouchingTheOthers() {
        intake.stored = CoachIntake(trainingYears = 4.0)
        showBeforeStart()
        tap("before_start_row_maintenance")

        typeKcal("2400")
        tap("intake_maintenance_sheet_commit")

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.KnownMaintenance(2400)), intake.saveCalls)
        rule.onNodeWithTag("intake_maintenance_sheet").assertDoesNotExist()
        rule.onNodeWithTag("before_start_row_maintenance")
            .assertTextEquals("Calorías de mantenimiento", "2.400 kcal")
        rule.onNodeWithTag("before_start_row_training_years").assertTextEquals("Tiempo entrenando", "4 años")
    }

    @Test
    fun aMaintenanceOutOfRange_showsTheInlineError_andGuardarStaysDisabled() {
        showBeforeStart()
        tap("before_start_row_maintenance")

        typeKcal("1199")

        rule.onNodeWithTag("intake_maintenance_error", useUnmergedTree = true)
            .assertTextEquals("Entre 1200 y 6000 kcal")
        rule.onNodeWithTag("intake_maintenance_sheet_commit").assertIsNotEnabled()
        assertTrue(intake.saveCalls.isEmpty())
    }

    @Test
    fun theBoundsOfTheMaintenance_areValid() {
        showBeforeStart()
        tap("before_start_row_maintenance")

        typeKcal("1200")
        rule.onNodeWithTag("intake_maintenance_sheet_commit").assertIsEnabled()
        rule.onNodeWithTag("intake_maintenance_error", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun anEmptyMaintenance_clearsTheValue() {
        intake.stored = CoachIntake(knownMaintenanceKcal = 2400)
        showBeforeStart()
        tap("before_start_row_maintenance")

        rule.onNodeWithTag("intake_maintenance_sheet_field").performTextClearance()
        rule.waitForIdle()
        tap("intake_maintenance_sheet_commit")

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.KnownMaintenance(null)), intake.saveCalls)
        rule.onNodeWithTag("before_start_row_maintenance")
            .assertTextEquals("Calorías de mantenimiento", "Sin indicar")
    }

    @Test
    fun closingASheet_dropsTheDraftSilently() {
        showBeforeStart()
        tap("before_start_row_maintenance")
        typeKcal("2400")

        tap("intake_maintenance_sheet_close")

        rule.onNodeWithTag("intake_maintenance_sheet").assertDoesNotExist()
        rule.onNodeWithTag("before_start_row_maintenance")
            .assertTextEquals("Calorías de mantenimiento", "Sin indicar")
        assertTrue(intake.saveCalls.isEmpty())
    }

    @Test
    fun aSaveThatFails_keepsTheSheetAndTheDraft_withTheErrorUnderGuardar() {
        intake.saveFailure = AppException(AppError.Unknown)
        showBeforeStart()
        tap("before_start_row_maintenance")
        typeKcal("2400")

        tap("intake_maintenance_sheet_commit")

        rule.onNodeWithTag("intake_maintenance_sheet").assertExists()
        rule.onNodeWithTag("intake_maintenance_sheet_commit_hint")
            .assertTextContains("No se pudo guardar. Inténtalo de nuevo.")
        rule.onNodeWithTag("intake_maintenance_sheet_commit").assertIsEnabled()
    }

    @Test
    fun withoutConnection_guardarIsDisabled_withTheOfflineHint() {
        showBeforeStart(online = false)
        tap("before_start_row_maintenance")
        typeKcal("2400")

        rule.onNodeWithTag("intake_maintenance_sheet_commit").assertIsNotEnabled()
        rule.onNodeWithTag("intake_maintenance_sheet_commit_hint").assertExists()
    }

    @Test
    fun aStatedYearsAndMaintenance_carryTheProvisionalChip() {
        intake.stored = CoachIntake(trainingYears = 4.0, knownMaintenanceKcal = 2400)

        showBeforeStart()

        rule.onNode(
            hasText("Provisional") and hasAnyAncestor(hasTestTag("before_start_row_training_years_chip_provisional")),
            useUnmergedTree = true,
        ).assertExists()
        rule.onNodeWithTag("before_start_row_maintenance_chip_provisional", useUnmergedTree = true).assertExists()
    }

    @Test
    fun withNothingSaid_noRowCarriesTheChip() {
        showBeforeStart()

        rule.onNodeWithTag("before_start_row_training_years_chip_provisional", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun inTusDatos_changingTheTimeTraining_isSavedAndShown() {
        intake.stored = CoachIntake(trainingYears = 1.0)
        showYourData()
        tap("your_data_row_training_years")

        tap("intake_years_sheet_stepper_increment")
        tap("intake_years_sheet_commit")

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.TrainingYears(1.5)), intake.saveCalls)
        rule.onNodeWithTag("your_data_row_training_years").assertTextEquals("Tiempo entrenando", "1,5 años")
    }

    @Test
    fun inTusDatos_eachRowOpensOnlyItsEditor() {
        showYourData()

        tap("your_data_row_maintenance")
        rule.onNodeWithTag("intake_maintenance_sheet").assertExists()
        rule.onNodeWithTag("intake_years_sheet").assertDoesNotExist()
        tap("intake_maintenance_sheet_close")
        tap("your_data_row_training_years")
        rule.onNodeWithTag("intake_years_sheet").assertExists()
        rule.onNodeWithTag("intake_maintenance_sheet").assertDoesNotExist()
        assertTrue(fixed.isEmpty())
    }

    @Test
    fun theYearsAndMaintenanceRows_haveTheirAdviceBehindOneQuestionMarkEach() {
        showBeforeStart()
        val years = "Años entrenando con regularidad. Si tu historial en la app es mayor, el entrenador usa el mayor."
        val maintenance = "Si ya lo conoces, el entrenador lo usa hasta tener datos tuyos suficientes."

        rule.onNodeWithText(years).assertDoesNotExist()
        rule.onNodeWithText(maintenance).assertDoesNotExist()
        rule.onAllNodesWithTag("before_start_row_training_years_help").assertCountEquals(1)
        rule.onAllNodesWithTag("before_start_row_maintenance_help").assertCountEquals(1)
        rule.onNodeWithContentDescription("Ayuda: tiempo entrenando").assertExists()
        rule.onNodeWithContentDescription("Ayuda: calorías de mantenimiento").assertExists()
        tap("before_start_row_training_years_help")
        rule.onNodeWithTag(
            "before_start_row_training_years_help_sheet_body",
        ).assertTextContains(years, substring = true)
        tap("before_start_row_training_years_help_sheet_close")
        tap("before_start_row_maintenance_help")
        rule.onNodeWithTag("before_start_row_maintenance_help_sheet_body")
            .assertTextContains(maintenance, substring = true)
        rule.onNodeWithTag("intake_years_sheet").assertDoesNotExist()
    }

    @Test
    fun anOpenIntakeHelp_survivesRecreation() {
        val model = BeforeStartViewModel(
            coach,
            intake,
            MutableClock(),
            FakeRoutineRepository(),
            SavedStateHandle(mapOf("mode" to "plan")),
        )
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                BeforeStartRoot(
                    onBack = {},
                    onCreate = {},
                    onOpenYourData = {},
                    onFix = {},
                    viewModel = model,
                    connectivity = FakeConnectivityObserver(),
                )
            }
        }
        rule.waitForIdle()
        tap("before_start_row_training_years_help")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("before_start_row_training_years_help_sheet_body").assertExists()
    }
}
