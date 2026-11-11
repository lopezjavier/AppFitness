package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import io.github.lopezjavier.appfitness.domain.model.ExperienceSource
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.model.PlanInput
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class YourDataViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val coach = FakeCoachRepository()
    private val intake = FakeCoachIntakeRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        YourDataViewModel(coach, intake, MutableClock(), saved)

    private fun YourDataViewModel.content() = assertIs<YourDataUiState.Content>(uiState.value)

    private fun plan(vararg inputs: PlanInput) {
        coach.inputs[PlanInputKind.Plan] = Result.success(PlanInputs(PlanInputKind.Plan, null, inputs.toList()))
    }

    private fun block(vararg inputs: PlanInput) {
        coach.inputs[PlanInputKind.Block] = Result.success(PlanInputs(PlanInputKind.Block, null, inputs.toList()))
    }

    @Test
    fun groupsRowsByOrigin_inScreenOrder_skippingEmptySections() = runTest(dispatcher) {
        plan(
            planInput(PlanInputKey.Weight, source = PlanInputSource.WeighIn),
            planInput(PlanInputKey.Sex, source = PlanInputSource.Profile),
            planInput(PlanInputKey.TotalExpenditure, source = PlanInputSource.CoachEstimate),
        )
        block(planInput(PlanInputKey.Place, source = PlanInputSource.MyTraining))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(
            listOf(
                PlanInputOrigin.Profile,
                PlanInputOrigin.MyTraining,
                PlanInputOrigin.Weighing,
                PlanInputOrigin.Estimate,
            ),
            model.content().sections.map { it.origin },
        )
    }

    @Test
    fun emptyOrigin_hasNoSection() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Sex, source = PlanInputSource.Profile))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(PlanInputOrigin.Profile), model.content().sections.map { it.origin })
    }

    @Test
    fun mergesPlanAndBlock_dedupingSharedKeys() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Intention), planInput(PlanInputKey.Sex))
        block(planInput(PlanInputKey.Intention), planInput(PlanInputKey.Place, source = PlanInputSource.MyTraining))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(
            listOf(PlanInputKey.Intention, PlanInputKey.Sex, PlanInputKey.Place),
            model.content().rows.map { it.key },
        )
    }

    @Test
    fun countsTheProvisionalRows() = runTest(dispatcher) {
        plan(
            planInput(PlanInputKey.ActivityLevel, PlanInputStatus.Provisional),
            planInput(PlanInputKey.TotalExpenditure, PlanInputStatus.Provisional),
            planInput(PlanInputKey.Sex),
        )
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(2, model.content().provisionalCount)
    }

    @Test
    fun failedBlockList_keepsThePlanRows() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Sex))
        coach.inputs[PlanInputKind.Block] = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(PlanInputKey.Sex), model.content().rows.map { it.key })
    }

    @Test
    fun bothListsOffline_isOffline() = runTest(dispatcher) {
        coach.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Network))
        coach.inputs[PlanInputKind.Block] = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(YourDataUiState.Offline, model.uiState.value)
    }

    @Test
    fun bothListsFailing_isError_andRetryRecovers() = runTest(dispatcher) {
        coach.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Unknown))
        coach.inputs[PlanInputKind.Block] = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(YourDataUiState.Error, model.uiState.value)

        coach.inputs.clear()
        plan(planInput(PlanInputKey.Sex))
        model.onEvent(YourDataEvent.Retry)
        advanceUntilIdle()

        assertIs<YourDataUiState.Content>(model.uiState.value)
    }

    @Test
    fun refresh_reloadsSilently_andKeepsTheSheet() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.ActivityLevel, value = PlanInputValue.Text("high")))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(YourDataEvent.OpenSheet)
        plan(planInput(PlanInputKey.ActivityLevel, value = PlanInputValue.Text("high")), planInput(PlanInputKey.Sex))

        model.uiState.test {
            assertIs<YourDataUiState.Content>(awaitItem())
            model.onEvent(YourDataEvent.Refresh)
            advanceUntilIdle()
            val next = assertIs<YourDataUiState.Content>(awaitItem())
            assertEquals(2, next.rows.size)
            assertEquals(ActivityLevel.High, next.sheet)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pickLevel_confirmsAndReloadsTheRows() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.ActivityLevel, PlanInputStatus.Provisional))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(YourDataEvent.OpenSheet)

        model.onEvent(YourDataEvent.PickLevel(ActivityLevel.VeryHigh))
        advanceUntilIdle()

        assertEquals(ActivityLevel.VeryHigh, coach.confirmCalls.single().second)
        assertNull(model.content().sheet)
        assertEquals(0, model.content().provisionalCount)
    }

    @Test
    fun pickLevelFailure_keepsTheSheet_andEmitsFailure() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.ActivityLevel, PlanInputStatus.Provisional))
        coach.confirmResult = { Result.failure<CoachDerivedValues>(AppException(AppError.Network)) }
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(YourDataEvent.OpenSheet)

        model.effects.test {
            model.onEvent(YourDataEvent.PickLevel(ActivityLevel.High))
            advanceUntilIdle()
            assertEquals(YourDataEffect.SaveFailed(offline = true), awaitItem())
        }

        assertEquals(ActivityLevel.Moderate, model.content().sheet)
        assertFalse(model.content().savingLevel)
    }

    @Test
    fun sheetSurvivesRecreation() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.ActivityLevel))
        val saved = SavedStateHandle()
        viewModel(saved).also {
            advanceUntilIdle()
            it.onEvent(YourDataEvent.OpenSheet)
        }

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(ActivityLevel.Moderate, second.content().sheet)
        assertEquals(0, coach.confirmCalls.size)
    }

    private fun derived(
        expenditureSource: DerivedSource = DerivedSource.Formula,
        handover: Boolean = false,
        experienceSource: ExperienceSource = ExperienceSource.History,
    ): CoachDerivedValues {
        val base = coachValues(provisional = false)
        return base.copy(
            totalExpenditure = base.totalExpenditure.copy(source = expenditureSource, handover = handover),
            experience = base.experience.copy(source = experienceSource),
        )
    }

    @Test
    fun intakeSourcedValues_areMarkedProvisionalByKey() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Experience), planInput(PlanInputKey.TotalExpenditure))
        coach.loaded = Result.success(
            derived(expenditureSource = DerivedSource.Intake, experienceSource = ExperienceSource.Intake),
        )
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(
            setOf(PlanInputKey.Experience, PlanInputKey.TotalExpenditure),
            model.content().intakeSourcedKeys,
        )
    }

    @Test
    fun noIntake_hasNoProvisionalTextNorRows() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Sex))
        val model = viewModel()

        advanceUntilIdle()

        val content = model.content()
        assertTrue(content.intake.isEmpty)
        assertTrue(content.intakeSourcedKeys.isEmpty())
        assertFalse(content.showHandover)
    }

    @Test
    fun savedIntake_isSummarisedInTheTwoRows() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Sex))
        intake.stored = CoachIntake(trainingYears = 4.0, knownMaintenanceKcal = 2600)
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(IntakeSummary(4.0, 2600), model.content().intake)
    }

    @Test
    fun savingTheYearsSheet_updatesOnlyThatRow() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Sex))
        intake.stored = CoachIntake(knownMaintenanceKcal = 2600)
        val model = viewModel()
        advanceUntilIdle()

        model.onEditorEvent(IntakeRowEvent.OpenYears(null))
        model.onEditorEvent(IntakeRowEvent.YearsUp)
        model.onEditorEvent(IntakeRowEvent.YearsUp)
        model.onEditorEvent(IntakeRowEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.TrainingYears(0.5)), intake.saveCalls)
        assertEquals(IntakeSummary(0.5, 2600), model.content().intake)
        assertNull(model.editor.value.sheet)
    }

    @Test
    fun anOpenMaintenanceSheetWithATypedValue_survivesRecreationFromTheSameSavedState() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Sex))
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEditorEvent(IntakeRowEvent.OpenMaintenance(null))
        first.onEditorEvent(IntakeRowEvent.EditMaintenance("24"))

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(IntakeRowSheet.Maintenance("24"), second.editor.value.sheet)
    }

    @Test
    fun handover_showsTheMeasuredKcal_andIsAcknowledgedOnce() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.TotalExpenditure))
        coach.loaded = Result.success(derived(expenditureSource = DerivedSource.Adaptive, handover = true))
        val model = viewModel()
        advanceUntilIdle()
        assertTrue(model.content().showHandover)
        assertEquals(2400.0, model.content().handoverKcal)

        model.onEvent(YourDataEvent.HandoverShown)
        model.onEvent(YourDataEvent.HandoverShown)
        advanceUntilIdle()

        assertEquals(1, intake.ackCalls)
    }

    @Test
    fun afterTheAcknowledgement_theNextLoadHasNoHandover() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.TotalExpenditure))
        coach.loaded = Result.success(derived(handover = true))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(YourDataEvent.HandoverShown)
        advanceUntilIdle()
        // The server now reports handover = false.
        coach.loaded = Result.success(derived(handover = false))

        model.onEvent(YourDataEvent.Refresh)
        advanceUntilIdle()

        assertFalse(model.content().showHandover)
        assertEquals(1, intake.ackCalls)
    }

    @Test
    fun handoverShownWithoutAHandover_doesNotCallTheServer() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.TotalExpenditure))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(YourDataEvent.HandoverShown)
        advanceUntilIdle()

        assertEquals(0, intake.ackCalls)
    }

    @Test
    fun failedIntakeRead_stillShowsTheRows() = runTest(dispatcher) {
        plan(planInput(PlanInputKey.Sex))
        intake.getFailure = AppException(AppError.Network)
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(PlanInputKey.Sex), model.content().rows.map { it.key })
        assertTrue(model.content().intake.isEmpty)
    }
}
