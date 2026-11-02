package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.PreBreakBest
import io.github.lopezjavier.appfitness.domain.model.Situation
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

private val BENCH = PreBreakBest("bench_press", 90.0, 5)
private val SQUAT = PreBreakBest("squat", 120.0, 3)

@OptIn(ExperimentalCoroutinesApi::class)
class IntakeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCoachIntakeRepository()
    private val asked = FakeIntakePromptPreference()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = IntakeViewModel(repository, asked, saved)

    private fun IntakeViewModel.content() = assertIs<IntakeUiState.Content>(uiState.value)

    private val us81Draft = CoachIntakeDraft(
        trainingYears = 4.0,
        situation = Situation.ReturningInjury,
        injuredArea = InjuredArea.Shoulder,
        preBreakBests = listOf(BENCH),
    )

    private suspend fun kotlinx.coroutines.test.TestScope.loaded(
        saved: SavedStateHandle = SavedStateHandle(),
    ): IntakeViewModel {
        val model = viewModel(saved)
        advanceUntilIdle()
        return model
    }

    @Test
    fun withoutStoredIntake_theFormIsEmptyAndNotDirty() = runTest(dispatcher) {
        val model = loaded()

        val content = model.content()
        assertEquals(IntakeForm(), content.form)
        assertFalse(content.dirty)
        assertTrue(content.extracted.isEmpty())
    }

    @Test
    fun withStoredIntake_theFormShowsIt() = runTest(dispatcher) {
        repository.stored = CoachIntake(
            trainingYears = 2.5,
            situation = Situation.Continuous,
            preBreakBests = listOf(SQUAT),
            knownMaintenanceKcal = 2600,
            freeText = "hola",
        )

        val form = loaded().content().form

        assertEquals(2.5, form.years)
        assertEquals(Situation.Continuous, form.situation)
        assertEquals(listOf(SQUAT), form.bests)
        assertEquals(2600, form.maintenanceKcal)
        assertEquals("hola", form.freeText)
    }

    @Test
    fun loadFailure_isErrorOrOffline_andRetryRecovers() = runTest(dispatcher) {
        repository.getFailure = AppException(AppError.Network)
        val model = loaded()
        assertEquals(IntakeUiState.Offline, model.uiState.value)

        repository.getFailure = AppException(AppError.Unknown)
        model.onEvent(IntakeEvent.Retry)
        advanceUntilIdle()
        assertEquals(IntakeUiState.Error, model.uiState.value)

        repository.getFailure = null
        model.onEvent(IntakeEvent.Retry)
        advanceUntilIdle()
        assertIs<IntakeUiState.Content>(model.uiState.value)
    }

    @Test
    fun extract_fillsTheUs81Example_marksFieldsAndLeavesCaloriesEmpty() = runTest(dispatcher) {
        repository.extractResult = us81Draft
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("Llevo 4 años, vuelvo de una lesión de hombro, hacía 90x5 en banca"))

        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        val content = model.content()
        assertEquals(4.0, content.form.years)
        assertEquals(Situation.ReturningInjury, content.form.situation)
        assertEquals(InjuredArea.Shoulder, content.form.injuredArea)
        assertEquals(listOf(BENCH), content.form.bests)
        assertNull(content.form.maintenanceKcal)
        assertNull(content.form.cutKcal)
        assertEquals(
            setOf(IntakeField.Years, IntakeField.Situation, IntakeField.InjuredArea, IntakeField.Bests),
            content.extracted,
        )
        assertEquals(IntakePhase.Editing, content.phase)
    }

    @Test
    fun extract_saveNothingUntilGuardar() = runTest(dispatcher) {
        repository.extractResult = us81Draft
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("texto"))

        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        assertTrue(repository.saveCalls.isEmpty())
        assertTrue(model.content().dirty)
    }

    @Test
    fun extract_neverOverwritesAUserTypedField_andSendsThemAsFields() = runTest(dispatcher) {
        repository.extractResult = CoachIntakeDraft(trainingYears = null, situation = Situation.ReturningBreak)
        val model = loaded()
        model.onEvent(IntakeEvent.EditYears(7.0))
        model.onEvent(IntakeEvent.EditMaintenance(2500))
        model.onEvent(IntakeEvent.EditFreeText("texto"))

        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        val (text, fields) = repository.extractCalls.single()
        assertEquals("texto", text)
        assertEquals(7.0, fields?.trainingYears)
        assertEquals(2500, fields?.knownMaintenanceKcal)
        assertNull(fields?.situation)
        val content = model.content()
        assertEquals(7.0, content.form.years)
        assertEquals(Situation.ReturningBreak, content.form.situation)
        assertEquals(setOf(IntakeField.Situation), content.extracted)
    }

    @Test
    fun extractedFieldLosesItsMarkerOnFirstTouch() = runTest(dispatcher) {
        repository.extractResult = us81Draft
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("texto"))
        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        model.onEvent(IntakeEvent.EditYears(5.0))

        assertEquals(
            setOf(IntakeField.Situation, IntakeField.InjuredArea, IntakeField.Bests),
            model.content().extracted,
        )
    }

    @Test
    fun aCorrectedFieldSurvivesASecondExtract() = runTest(dispatcher) {
        repository.extractResult = us81Draft
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("texto"))
        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()
        model.onEvent(IntakeEvent.EditYears(6.0))

        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        assertEquals(6.0, model.content().form.years)
        assertFalse(IntakeField.Years in model.content().extracted)
        assertEquals(6.0, repository.extractCalls.last().second?.trainingYears)
    }

    @Test
    fun extractWithBlankText_isANoOp() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("   "))

        assertFalse(model.content().canExtract)
        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        assertTrue(repository.extractCalls.isEmpty())
    }

    @Test
    fun extract_ignoresInvalidValuesFromTheModel() = runTest(dispatcher) {
        repository.extractResult = CoachIntakeDraft(
            trainingYears = 81.0,
            preBreakBests = listOf(PreBreakBest("a", 50.0, 0), BENCH),
        )
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("texto"))

        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        val content = model.content()
        assertNull(content.form.years)
        assertEquals(listOf(BENCH), content.form.bests)
    }

    @Test
    fun extract_limitReached_showsTheLimitNotice_andFieldsStayEditable() = runTest(dispatcher) {
        val failure = CoachFailure.LimitReached(CoachFailure.Scope.User)
        repository.extractFailure = CoachException(failure)
        val model = loaded()
        model.onEvent(IntakeEvent.EditYears(3.0))
        model.onEvent(IntakeEvent.EditFreeText("texto"))

        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        val content = model.content()
        assertEquals(IntakeNotice.Limit(failure), content.notice)
        assertEquals(IntakePhase.Editing, content.phase)
        assertEquals(3.0, content.form.years)
        model.onEvent(IntakeEvent.EditYears(4.0))
        assertEquals(4.0, model.content().form.years)
    }

    @Test
    fun extract_offline_showsTheOfflineHint() = runTest(dispatcher) {
        repository.extractFailure = AppException(AppError.Network)
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("texto"))

        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        assertEquals(IntakeNotice.ExtractOffline, model.content().notice)
    }

    @Test
    fun extract_invalidAnswer_isAGenericFailure_leavingTheFormUntouched() = runTest(dispatcher) {
        repository.extractFailure = CoachException(CoachFailure.Invalid)
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("texto"))
        val before = model.content().form

        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        assertEquals(IntakeNotice.ExtractFailed, model.content().notice)
        assertEquals(before, model.content().form)
        assertTrue(model.content().extracted.isEmpty())
    }

    @Test
    fun editing_clearsTheNotice() = runTest(dispatcher) {
        repository.extractFailure = AppException(AppError.Network)
        val model = loaded()
        model.onEvent(IntakeEvent.EditFreeText("texto"))
        model.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()

        model.onEvent(IntakeEvent.EditFreeText("texto 2"))

        assertNull(model.content().notice)
    }

    @Test
    fun save_storesBlankTextAsNull_emitsSaved_andMarksAsked() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(IntakeEvent.EditYears(4.0))
        model.onEvent(IntakeEvent.EditFreeText("   "))

        model.effects.test {
            model.onEvent(IntakeEvent.Save)
            advanceUntilIdle()
            assertEquals(IntakeEffect.Saved, awaitItem())
        }

        val saved = repository.saveCalls.single()
        assertEquals(4.0, saved.trainingYears)
        assertNull(saved.freeText)
        assertTrue(asked.asked)
        assertFalse(model.content().dirty)
    }

    @Test
    fun save_keepsTheHandoverFlagOfTheStoredIntake() = runTest(dispatcher) {
        repository.stored = CoachIntake(trainingYears = 1.0, maintenanceHandoverPending = true)
        val model = loaded()
        model.onEvent(IntakeEvent.EditYears(2.0))

        model.onEvent(IntakeEvent.Save)
        advanceUntilIdle()

        assertEquals(true, repository.saveCalls.single().maintenanceHandoverPending)
    }

    @Test
    fun save_failure_keepsTheForm_andShowsAGenericNotice() = runTest(dispatcher) {
        repository.saveFailure = AppException(AppError.Validation)
        val model = loaded()
        model.onEvent(IntakeEvent.EditYears(4.0))

        model.effects.test {
            model.onEvent(IntakeEvent.Save)
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals(IntakeNotice.SaveFailed, model.content().notice)
        assertEquals(4.0, model.content().form.years)
        assertEquals(IntakePhase.Editing, model.content().phase)
        assertFalse(asked.asked)
    }

    @Test
    fun save_offline_showsTheOfflineNotice() = runTest(dispatcher) {
        repository.saveFailure = AppException(AppError.Network)
        val model = loaded()

        model.onEvent(IntakeEvent.Save)
        advanceUntilIdle()

        assertEquals(IntakeNotice.SaveOffline, model.content().notice)
    }

    @Test
    fun skip_persistsTheAskedFlag_emitsSkipped_andSavesNothing() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(IntakeEvent.EditYears(4.0))

        model.effects.test {
            model.onEvent(IntakeEvent.Skip)
            advanceUntilIdle()
            assertEquals(IntakeEffect.Skipped, awaitItem())
        }

        assertTrue(asked.asked)
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun backWithChanges_asksToDiscard_confirmCloses() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(IntakeEvent.EditYears(4.0))

        model.effects.test {
            model.onEvent(IntakeEvent.Back)
            advanceUntilIdle()
            expectNoEvents()
            assertTrue(model.content().discardDialog)

            model.onEvent(IntakeEvent.ConfirmDiscard)
            advanceUntilIdle()
            assertEquals(IntakeEffect.Close, awaitItem())
        }
        assertTrue(repository.saveCalls.isEmpty())
    }

    @Test
    fun backWithChanges_keepEditingClosesTheDialogOnly() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(IntakeEvent.EditYears(4.0))
        model.onEvent(IntakeEvent.Back)

        model.onEvent(IntakeEvent.DismissDiscard)

        assertFalse(model.content().discardDialog)
        assertEquals(4.0, model.content().form.years)
    }

    @Test
    fun backWithoutChanges_closesRightAway() = runTest(dispatcher) {
        val model = loaded()

        model.effects.test {
            model.onEvent(IntakeEvent.Back)
            advanceUntilIdle()
            assertEquals(IntakeEffect.Close, awaitItem())
        }
    }

    @Test
    fun freeText_isCutAt500Characters() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(IntakeEvent.EditFreeText("a".repeat(500)))
        assertEquals(500, model.content().form.freeText.length)
        assertEquals(0, model.content().remainingChars)

        model.onEvent(IntakeEvent.EditFreeText("a".repeat(501)))
        assertEquals(500, model.content().form.freeText.length)
    }

    @Test
    fun years_acceptZeroAndEighty_rejectOutOfRangeAndOddSteps() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(IntakeEvent.EditYears(0.0))
        assertTrue(model.content().canSave)
        model.onEvent(IntakeEvent.EditYears(80.0))
        assertTrue(model.content().canSave)
        model.onEvent(IntakeEvent.EditYears(80.5))
        assertEquals(setOf(IntakeField.Years), model.content().form.errors)
        assertFalse(model.content().canSave)
        model.onEvent(IntakeEvent.EditYears(2.3))
        assertFalse(model.content().canSave)
        model.onEvent(IntakeEvent.EditYears(null))
        assertTrue(model.content().canSave)
    }

    @Test
    fun kcal_mustBeBetween800And6000() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(IntakeEvent.EditMaintenance(799))
        model.onEvent(IntakeEvent.EditCutKcal(6001))
        assertEquals(setOf(IntakeField.Maintenance, IntakeField.CutKcal), model.content().form.errors)

        model.onEvent(IntakeEvent.EditMaintenance(800))
        model.onEvent(IntakeEvent.EditCutKcal(6000))
        assertTrue(model.content().canSave)
    }

    @Test
    fun bests_rejectZeroRepsNonPositiveWeightAndDuplicates() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(IntakeEvent.AddBest(PreBreakBest("a", 50.0, 0)))
        model.onEvent(IntakeEvent.AddBest(PreBreakBest("a", 50.0, 31)))
        model.onEvent(IntakeEvent.AddBest(PreBreakBest("a", 0.0, 5)))
        assertTrue(model.content().form.bests.isEmpty())

        model.onEvent(IntakeEvent.AddBest(BENCH))
        model.onEvent(IntakeEvent.AddBest(BENCH.copy(weightKg = 100.0)))
        assertEquals(listOf(BENCH), model.content().form.bests)
    }

    @Test
    fun bests_theEleventhIsRejected() = runTest(dispatcher) {
        val model = loaded()

        repeat(11) { model.onEvent(IntakeEvent.AddBest(PreBreakBest("ex$it", 20.0, 8))) }

        assertEquals(10, model.content().form.bests.size)
        assertFalse(model.content().canAddBest)
    }

    @Test
    fun bests_canBeEditedAndRemoved() = runTest(dispatcher) {
        val model = loaded()
        model.onEvent(IntakeEvent.AddBest(BENCH))
        model.onEvent(IntakeEvent.AddBest(SQUAT))

        model.onEvent(IntakeEvent.EditBest(BENCH.copy(weightKg = 95.0)))
        model.onEvent(IntakeEvent.EditBest(BENCH.copy(reps = 0)))
        model.onEvent(IntakeEvent.RemoveBest("squat"))

        assertEquals(listOf(BENCH.copy(weightKg = 95.0)), model.content().form.bests)
    }

    @Test
    fun picker_opensAndClosesAndSelectionAppliesAtOnce() = runTest(dispatcher) {
        val model = loaded()

        model.onEvent(IntakeEvent.OpenPicker(IntakePicker.Situation))
        assertEquals(IntakePicker.Situation, model.content().picker)
        model.onEvent(IntakeEvent.SelectSituation(Situation.FirstTime))
        model.onEvent(IntakeEvent.ClosePicker)

        assertEquals(Situation.FirstTime, model.content().form.situation)
        assertNull(model.content().picker)
        model.onEvent(IntakeEvent.SelectSituation(null))
        assertNull(model.content().form.situation)
    }

    @Test
    fun recreatedViewModel_restoresTheDraftOverTheStoredIntake() = runTest(dispatcher) {
        repository.stored = CoachIntake(trainingYears = 1.0, situation = Situation.Continuous)
        val saved = SavedStateHandle()
        repository.extractResult = us81Draft
        val first = loaded(saved)
        first.onEvent(IntakeEvent.EditFreeText("a medias"))
        first.onEvent(IntakeEvent.EditMaintenance(2500))
        first.onEvent(IntakeEvent.Extract)
        advanceUntilIdle()
        first.onEvent(IntakeEvent.OpenPicker(IntakePicker.InjuredArea))
        first.onEvent(IntakeEvent.Back)

        val second = loaded(saved)

        val content = second.content()
        assertEquals("a medias", content.form.freeText)
        assertEquals(2500, content.form.maintenanceKcal)
        assertEquals(listOf(BENCH), content.form.bests)
        assertEquals(Situation.Continuous, content.form.situation)
        assertEquals(1.0, content.baseline.years)
        assertEquals(first.content().extracted, content.extracted)
        assertEquals(IntakePicker.InjuredArea, content.picker)
        assertTrue(content.discardDialog)
        assertTrue(content.dirty)
    }

    @Test
    fun recreatedViewModel_withoutADraft_showsTheStoredIntake() = runTest(dispatcher) {
        repository.stored = CoachIntake(trainingYears = 3.0)

        val content = loaded(SavedStateHandle()).content()

        assertEquals(3.0, content.form.years)
        assertFalse(content.dirty)
    }
}
