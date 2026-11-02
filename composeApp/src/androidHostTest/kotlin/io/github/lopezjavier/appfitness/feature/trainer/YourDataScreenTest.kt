package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.DerivedSituation
import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.domain.model.Situation
import io.github.lopezjavier.appfitness.domain.model.SituationSource
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-78: "Tus datos" lists every input by origin, with its source, and opens the place where it is fixed. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class YourDataScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeCoachRepository()
    private val fixed = mutableListOf<PlanFixTarget>()
    private val intake = FakeCoachIntakeRepository()

    private fun show(online: Boolean = true) {
        val model = YourDataViewModel(repository, intake, MutableClock(), SavedStateHandle())
        rule.setContent {
            AppTheme {
                YourDataRoot(
                    onBack = {},
                    onFix = { fixed += it },
                    viewModel = model,
                    connectivity = FakeConnectivityObserver(online),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun withProvisionalEstimates() {
        repository.inputs[PlanInputKind.Plan] = Result.success(
            planKeys(
                planInput(
                    PlanInputKey.TotalExpenditure,
                    PlanInputStatus.Provisional,
                    PlanInputValue.Number(2400.0),
                    PlanInputSource.CoachEstimate,
                ),
            ),
        )
    }

    @Test
    fun rows_showTheirValueAndSource_groupedByOrigin() {
        withProvisionalEstimates()

        show()

        rule.onNodeWithTag("your_data_screen").assertExists()
        rule.onNodeWithTag("your_data_section_profile").assertExists()
        rule.onNodeWithTag("your_data_section_my_training").assertExists()
        rule.onNodeWithTag("your_data_section_weighing").assertExists()
        rule.onNodeWithTag("your_data_section_estimate").assertExists()
        rule.onNodeWithText("Perder grasa · Mi entrenamiento").assertExists()
        rule.onNodeWithText("80,0 kg · Pesaje").assertExists()
    }

    @Test
    fun aProvisionalValue_carriesTheChip() {
        withProvisionalEstimates()

        show()

        rule.onNodeWithTag("your_data_chip_provisional_total_expenditure").assertTextEquals("Provisional")
        rule.onNodeWithTag("your_data_chip_provisional_intention").assertDoesNotExist()
    }

    private fun withDerived(
        source: DerivedSource = DerivedSource.Formula,
        handover: Boolean = false,
        situation: DerivedSituation? = null,
    ) {
        withProvisionalEstimates()
        val base = coachValues(provisional = false)
        repository.loaded = Result.success(
            base.copy(
                totalExpenditure = base.totalExpenditure.copy(source = source, handover = handover),
                situation = situation,
            ),
        )
    }

    @Test
    fun aValueFromTheIntake_isProvisional_withItsExplanation() {
        withDerived(source = DerivedSource.Intake)

        show()

        rule.onNodeWithTag("your_data_chip_provisional_total_expenditure").assertTextEquals("Provisional")
        rule.onNodeWithTag("your_data_intake_provisional_total_expenditure").assertTextEquals(
            "Según lo que nos contaste (provisional). Lo cambiaremos por tus datos reales cuando haya suficientes.",
        )
    }

    @Test
    fun withoutIntakeValues_thereIsNoProvisionalIntakeText() {
        withDerived()

        show()

        rule.onNodeWithTag("your_data_intake_provisional_total_expenditure").assertDoesNotExist()
    }

    @Test
    fun theSituationRow_showsWhatTheCoachWorksWith() {
        withDerived(situation = DerivedSituation(Situation.ReturningInjury, SituationSource.Intake, null))

        show()

        rule.onNodeWithTag("your_data_situation_row").assertExists()
        rule.onNodeWithText("Vuelvo tras una lesión").assertExists()
    }

    @Test
    fun withoutASituation_thereIsNoSituationRow() {
        withDerived()

        show()

        rule.onNodeWithTag("your_data_situation_row").assertDoesNotExist()
    }

    @Test
    fun theParaConocerteRow_opensTheIntake() {
        withDerived()
        show()

        tap("your_data_intake_row")

        assertEquals(listOf(PlanFixTarget.Intake), fixed)
    }

    @Test
    fun theHandoverNote_showsTheMeasuredFigure_andIsAcknowledgedOnce() {
        withDerived(source = DerivedSource.Adaptive, handover = true)

        show()

        rule.onNodeWithTag("your_data_handover_note")
            .assertTextEquals("Ahora usamos tus datos reales: tu gasto medido es de 2.400 kcal.")
        assertEquals(1, intake.ackCalls)
    }

    @Test
    fun withoutAHandover_noNoteAndNothingIsAcknowledged() {
        withDerived()

        show()

        rule.onNodeWithTag("your_data_handover_note").assertDoesNotExist()
        assertEquals(0, intake.ackCalls)
    }

    @Test
    fun aRow_opensTheEditorOfItsInput() {
        withProvisionalEstimates()
        show()

        tap("your_data_row_intention")
        tap("your_data_row_weight")
        tap("your_data_row_sex")

        assertEquals(listOf(PlanFixTarget.MyTraining, PlanFixTarget.Weight, PlanFixTarget.PersonalData), fixed)
    }

    @Test
    fun aDerivedValue_hasNoEditor() {
        withProvisionalEstimates()
        show()

        tap("your_data_row_total_expenditure")

        assertTrue(fixed.isEmpty())
    }

    @Test
    fun theActivityRow_opensTheSheet_andTappingACardConfirmsWithThatLevel() {
        withProvisionalEstimates()
        show()

        tap("your_data_row_activity_level")
        rule.onNodeWithTag("your_data_activity_sheet_activity_high").assertExists()
        tap("your_data_activity_sheet_activity_high")

        assertEquals(listOf<ActivityLevel?>(ActivityLevel.High), repository.confirmCalls.map { it.second })
        rule.onNodeWithTag("your_data_activity_sheet_activity_high").assertDoesNotExist()
    }

    @Test
    fun theSameKeyInPlanAndBlock_isListedOnce() {
        withProvisionalEstimates()
        repository.inputs[PlanInputKind.Block] = Result.success(
            PlanInputs(PlanInputKind.Block, null, planKeys().inputs.take(1)),
        )

        show()

        rule.onNodeWithTag("your_data_row_intention").assertExists()
    }

    @Test
    fun aLoadFailure_offersReintentar_thatRecovers() {
        repository.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Unknown))
        repository.inputs[PlanInputKind.Block] = Result.failure(AppException(AppError.Unknown))
        show()
        rule.onNodeWithText("No se pudieron cargar tus datos.").assertExists()
        withProvisionalEstimates()

        tap("your_data_error_retry")

        rule.onNodeWithTag("your_data_row_intention").assertExists()
    }

    @Test
    fun withoutConnection_showsTheOfflineState() {
        repository.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Network))
        repository.inputs[PlanInputKind.Block] = Result.failure(AppException(AppError.Network))

        show()

        rule.onNodeWithText("Sin conexión").assertExists()
    }
}
