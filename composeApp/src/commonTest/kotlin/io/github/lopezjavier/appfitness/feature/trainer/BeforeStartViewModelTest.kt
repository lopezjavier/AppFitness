package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CurrentPlan
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInput
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val OLD_WEIGH_IN = LocalDate(2026, 10, 1)

@OptIn(ExperimentalCoroutinesApi::class)
class BeforeStartViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val coach = FakeCoachRepository()
    private val intake = FakeCoachIntakeRepository()
    private val routines = FakeRoutineRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun completePlan(vararg extra: PlanInput, current: CurrentPlan? = null) = PlanInputs(
        PlanInputKind.Plan,
        current,
        listOf(
            planInput(PlanInputKey.Intention),
            planInput(PlanInputKey.Weight, source = PlanInputSource.WeighIn),
            planInput(PlanInputKey.ActivityLevel, value = PlanInputValue.Text("light")),
        ) + extra,
    )

    private fun args(mode: String = "plan", planId: String? = null, replacing: String? = null) =
        SavedStateHandle().apply {
            set("mode", mode)
            planId?.let { set("planId", it) }
            replacing?.let { set("replacingPlanId", it) }
        }

    private fun viewModel(saved: SavedStateHandle = args()) =
        BeforeStartViewModel(coach, intake, MutableClock(), routines, saved)

    private fun BeforeStartViewModel.content() = assertIs<BeforeStartUiState.Content>(uiState.value)

    private fun givenPlan(inputs: PlanInputs) {
        coach.inputs[PlanInputKind.Plan] = Result.success(inputs)
    }

    private val blockingGoal =
        planInput(PlanInputKey.Intention, PlanInputStatus.Missing, PlanInputValue.Absent, blocking = true)
    private val provisionalActivity = planInput(PlanInputKey.ActivityLevel, PlanInputStatus.Provisional)

    @Test
    fun startsLoadingThenShowsThePlanInputs_withoutAnyWrite() = runTest(dispatcher) {
        givenPlan(completePlan())
        val model = viewModel()
        assertEquals(BeforeStartUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        assertEquals(BeforeStartMode.Plan, model.content().mode)
        assertTrue(model.content().flags.isEmpty())
        assertTrue(model.content().canCreate)
        assertEquals(listOf(PlanInputKind.Plan), coach.inputsCalls)
        assertTrue(coach.confirmCalls.isEmpty())
    }

    @Test
    fun blockMode_readsTheBlockInputs_andKeepsThePlanId() = runTest(dispatcher) {
        coach.inputs[PlanInputKind.Block] = Result.success(
            PlanInputs(PlanInputKind.Block, null, listOf(planInput(PlanInputKey.Place))),
        )
        val model = viewModel(args("block", planId = "plan-1"))

        advanceUntilIdle()

        assertEquals(BeforeStartMode.Block, model.content().mode)
        assertEquals("plan-1", model.content().planId)
        assertEquals(listOf(PlanInputKind.Block), coach.inputsCalls)
    }

    @Test
    fun blockingMissingInput_isAFlag_andDisablesCreate() = runTest(dispatcher) {
        givenPlan(completePlan(blockingGoal))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(PlanInputKey.Intention), model.content().flags.map { it.key })
        assertTrue(model.content().hasBlocking)
        assertFalse(model.content().canCreate)
    }

    @Test
    fun createWhileBlocked_isIgnored() = runTest(dispatcher) {
        givenPlan(completePlan(blockingGoal))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BeforeStartEvent.Create)
            advanceUntilIdle()
            expectNoEvents()
        }
        assertTrue(coach.confirmCalls.isEmpty())
    }

    @Test
    fun staleWeight_showsTheCautionButKeepsCreateEnabled() = runTest(dispatcher) {
        val stale = planInput(
            PlanInputKey.Weight,
            PlanInputStatus.Stale,
            PlanInputValue.Number(80.0),
            PlanInputSource.WeighIn,
            fixTarget = PlanFixTarget.Weight,
            date = OLD_WEIGH_IN,
        )
        givenPlan(PlanInputs(PlanInputKind.Plan, null, listOf(stale)))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(OLD_WEIGH_IN, model.content().staleWeight?.date)
        assertTrue(model.content().flags.isEmpty())
        assertTrue(model.content().canCreate)
    }

    @Test
    fun missingNotBlocking_isANote_notAFlag() = runTest(dispatcher) {
        givenPlan(completePlan(planInput(PlanInputKey.Sex, PlanInputStatus.Missing, PlanInputValue.Absent)))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(PlanInputKey.Sex), model.content().missingNotes.map { it.key })
        assertTrue(model.content().flags.isEmpty())
        assertTrue(model.content().canCreate)
    }

    @Test
    fun unsetOptional_isNeitherFlagNorNote() = runTest(dispatcher) {
        givenPlan(
            completePlan(planInput(PlanInputKey.Experience, PlanInputStatus.UnsetOptional, PlanInputValue.Absent)),
        )
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().flags.isEmpty())
        assertTrue(model.content().missingNotes.isEmpty())
    }

    @Test
    fun replaceNote_followsTheCurrentPlan_inPlanModeOnly() = runTest(dispatcher) {
        givenPlan(completePlan(current = CurrentPlan.Accepted))
        coach.inputs[PlanInputKind.Block] =
            Result.success(PlanInputs(PlanInputKind.Block, CurrentPlan.Accepted, emptyList()))

        val plan = viewModel()
        val block = viewModel(args("block", planId = "p"))
        advanceUntilIdle()

        assertEquals(CurrentPlan.Accepted, plan.content().replaces)
        assertNull(block.content().replaces)
    }

    @Test
    fun createWithProvisional_confirmsOnceThenEmitsCreated() = runTest(dispatcher) {
        givenPlan(completePlan(provisionalActivity))
        val model = viewModel(args(replacing = "old"))
        advanceUntilIdle()
        assertTrue(model.content().hasProvisional)

        model.effects.test {
            model.onEvent(BeforeStartEvent.Create)
            advanceUntilIdle()
            assertEquals(BeforeStartEffect.Created(BeforeStartMode.Plan, null, "old"), awaitItem())
        }

        assertEquals(1, coach.confirmCalls.size)
        assertNull(coach.confirmCalls.single().second)
        assertFalse(model.content().creating)
    }

    @Test
    fun createWithoutProvisional_skipsConfirm() = runTest(dispatcher) {
        givenPlan(completePlan())
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BeforeStartEvent.Create)
            advanceUntilIdle()
            assertEquals(BeforeStartEffect.Created(BeforeStartMode.Plan, null, null), awaitItem())
        }

        assertTrue(coach.confirmCalls.isEmpty())
    }

    private fun givenBlock(provisional: Boolean = true) {
        val rows = if (provisional) listOf(planInput(PlanInputKey.Place, PlanInputStatus.Provisional)) else emptyList()
        coach.inputs[PlanInputKind.Block] = Result.success(PlanInputs(PlanInputKind.Block, null, rows))
    }

    private suspend fun BeforeStartViewModel.createBlock() {
        onEvent(BeforeStartEvent.Create)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun blockCreate_confirmsThenOpensTheStartChoiceInsteadOfNavigating() = runTest(dispatcher) {
        givenBlock()
        val model = viewModel(args("block", planId = "plan-1"))
        advanceUntilIdle()

        model.effects.test {
            model.createBlock()
            expectNoEvents()
        }

        assertEquals(1, coach.confirmCalls.size)
        assertEquals(StartChoiceStep.Choice, model.content().startChoice)
    }

    @Test
    fun planCreate_neverOpensTheStartChoice() = runTest(dispatcher) {
        givenPlan(completePlan())
        val model = viewModel()
        advanceUntilIdle()

        model.createBlock()

        assertEquals(StartChoiceStep.None, model.content().startChoice)
    }

    @Test
    fun pickScratch_emitsCreatedForTheSplitsAndClosesTheChoice() = runTest(dispatcher) {
        givenBlock(provisional = false)
        val model = viewModel(args("block", planId = "plan-1"))
        advanceUntilIdle()
        model.createBlock()

        model.effects.test {
            model.onEvent(BeforeStartEvent.PickStart(StartOption.Scratch))
            advanceUntilIdle()
            assertEquals(BeforeStartEffect.Created(BeforeStartMode.Block, "plan-1", null), awaitItem())
        }

        assertEquals(StartChoiceStep.None, model.content().startChoice)
    }

    @Test
    fun pickFolder_listsTheFolders() = runTest(dispatcher) {
        routines.folders += RoutineFolder("f1", "Torso")
        givenBlock(provisional = false)
        val model = viewModel(args("block", planId = "plan-1"))
        advanceUntilIdle()
        model.createBlock()

        model.onEvent(BeforeStartEvent.PickStart(StartOption.Folder))
        advanceUntilIdle()

        assertEquals(StartChoiceStep.Folders, model.content().startChoice)
        assertEquals(
            FolderPickerState.Loaded(listOf(RoutineFolder("f1", "Torso"))),
            model.content().folderPicker,
        )
    }

    @Test
    fun pickFolder_withNoFoldersIsAnEmptyList() = runTest(dispatcher) {
        givenBlock(provisional = false)
        val model = viewModel(args("block", planId = "plan-1"))
        advanceUntilIdle()
        model.createBlock()

        model.onEvent(BeforeStartEvent.PickStart(StartOption.Folder))
        advanceUntilIdle()

        assertEquals(FolderPickerState.Loaded(emptyList()), model.content().folderPicker)
    }

    @Test
    fun folderListFailure_isAnErrorAndRetryReloads() = runTest(dispatcher) {
        routines.folders += RoutineFolder("f1", "Torso")
        routines.failure = AppError.Network
        givenBlock(provisional = false)
        val model = viewModel(args("block", planId = "plan-1"))
        advanceUntilIdle()
        model.createBlock()

        model.onEvent(BeforeStartEvent.PickStart(StartOption.Folder))
        advanceUntilIdle()
        assertEquals(FolderPickerState.Failed(offline = true), model.content().folderPicker)
        routines.failure = null
        model.onEvent(BeforeStartEvent.RetryFolders)
        advanceUntilIdle()

        assertEquals(FolderPickerState.Loaded(listOf(RoutineFolder("f1", "Torso"))), model.content().folderPicker)
    }

    @Test
    fun folderTap_emitsOpenFolderBlockWithThePlan() = runTest(dispatcher) {
        routines.folders += RoutineFolder("f1", "Torso")
        givenBlock(provisional = false)
        val model = viewModel(args("block", planId = "plan-1"))
        advanceUntilIdle()
        model.createBlock()
        model.onEvent(BeforeStartEvent.PickStart(StartOption.Folder))
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BeforeStartEvent.PickFolder("f1"))
            advanceUntilIdle()
            assertEquals(BeforeStartEffect.OpenFolderBlock("plan-1", "f1"), awaitItem())
        }
    }

    @Test
    fun closeChoice_clearsTheSheet() = runTest(dispatcher) {
        givenBlock(provisional = false)
        val model = viewModel(args("block", planId = "plan-1"))
        advanceUntilIdle()
        model.createBlock()

        model.onEvent(BeforeStartEvent.CloseChoice)

        assertEquals(StartChoiceStep.None, model.content().startChoice)
    }

    @Test
    fun startChoice_survivesRecreationAndReloadsTheFolders() = runTest(dispatcher) {
        routines.folders += RoutineFolder("f1", "Torso")
        givenBlock(provisional = false)
        val saved = args("block", planId = "plan-1")
        val first = viewModel(saved)
        advanceUntilIdle()
        first.createBlock()
        first.onEvent(BeforeStartEvent.PickStart(StartOption.Folder))
        advanceUntilIdle()

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(StartChoiceStep.Folders, second.content().startChoice)
        assertEquals(FolderPickerState.Loaded(listOf(RoutineFolder("f1", "Torso"))), second.content().folderPicker)
    }

    @Test
    fun blockConfirmFailure_keepsCreateFailedAndNoChoice() = runTest(dispatcher) {
        givenBlock()
        coach.confirmResult = { Result.failure<CoachDerivedValues>(AppException(AppError.Unknown)) }
        val model = viewModel(args("block", planId = "plan-1"))
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BeforeStartEvent.Create)
            advanceUntilIdle()
            assertEquals(BeforeStartEffect.CreateFailed(offline = false), awaitItem())
        }

        assertEquals(StartChoiceStep.None, model.content().startChoice)
    }

    @Test
    fun confirmFailure_emitsNoNavigation_andKeepsTheScreen() = runTest(dispatcher) {
        givenPlan(completePlan(provisionalActivity))
        coach.confirmResult = { Result.failure<CoachDerivedValues>(AppException(AppError.Unknown)) }
        val model = viewModel()
        advanceUntilIdle()
        val before = model.content()

        model.effects.test {
            model.onEvent(BeforeStartEvent.Create)
            advanceUntilIdle()
            assertEquals(BeforeStartEffect.CreateFailed(offline = false), awaitItem())
            expectNoEvents()
        }

        assertEquals(before, model.content())
    }

    @Test
    fun offlineConfirmFailure_flagsOffline() = runTest(dispatcher) {
        givenPlan(completePlan(provisionalActivity))
        coach.confirmResult = { Result.failure<CoachDerivedValues>(AppException(AppError.Network)) }
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(BeforeStartEvent.Create)
            advanceUntilIdle()
            assertEquals(BeforeStartEffect.CreateFailed(offline = true), awaitItem())
        }
    }

    @Test
    fun doubleTapOnCreate_confirmsOnce() = runTest(dispatcher) {
        givenPlan(completePlan(provisionalActivity))
        coach.gate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BeforeStartEvent.Create)
        advanceUntilIdle()
        assertTrue(model.content().creating)
        assertFalse(model.content().canCreate)
        model.onEvent(BeforeStartEvent.Create)
        coach.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, coach.confirmCalls.size)
    }

    @Test
    fun leavingWithoutCreating_writesNothing() = runTest(dispatcher) {
        givenPlan(completePlan(provisionalActivity))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BeforeStartEvent.Refresh)
        advanceUntilIdle()

        assertTrue(coach.confirmCalls.isEmpty())
    }

    @Test
    fun refresh_picksUpTheFixedInput_withoutShowingLoading() = runTest(dispatcher) {
        givenPlan(completePlan(blockingGoal))
        val model = viewModel()
        advanceUntilIdle()
        assertFalse(model.content().canCreate)

        givenPlan(completePlan())
        model.uiState.test {
            assertIs<BeforeStartUiState.Content>(awaitItem())
            model.onEvent(BeforeStartEvent.Refresh)
            advanceUntilIdle()
            assertTrue(assertIs<BeforeStartUiState.Content>(awaitItem()).canCreate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun failedRefresh_keepsThePreviousContent() = runTest(dispatcher) {
        givenPlan(completePlan())
        val model = viewModel()
        advanceUntilIdle()
        val before = model.content()
        coach.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Network))

        model.onEvent(BeforeStartEvent.Refresh)
        advanceUntilIdle()

        assertEquals(before, model.content())
    }

    @Test
    fun networkFailure_isOffline() = runTest(dispatcher) {
        coach.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(BeforeStartUiState.Offline, model.uiState.value)
    }

    @Test
    fun otherFailure_isError_andRetryRecovers() = runTest(dispatcher) {
        coach.inputs[PlanInputKind.Plan] = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(BeforeStartUiState.Error, model.uiState.value)

        givenPlan(completePlan())
        model.onEvent(BeforeStartEvent.Retry)
        advanceUntilIdle()

        assertIs<BeforeStartUiState.Content>(model.uiState.value)
    }

    @Test
    fun openSheet_startsAtTheCurrentLevel() = runTest(dispatcher) {
        givenPlan(completePlan())
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BeforeStartEvent.OpenSheet)

        assertEquals(ActivityLevel.Light, model.content().sheet)
    }

    @Test
    fun pickLevel_confirmsWithThatLevel_thenReloadsAndClosesTheSheet() = runTest(dispatcher) {
        givenPlan(completePlan(provisionalActivity))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BeforeStartEvent.OpenSheet)
        val loadsBefore = coach.inputsCalls.size

        model.onEvent(BeforeStartEvent.PickLevel(ActivityLevel.High))
        advanceUntilIdle()

        assertEquals(ActivityLevel.High, coach.confirmCalls.single().second)
        assertEquals(loadsBefore + 1, coach.inputsCalls.size)
        assertNull(model.content().sheet)
        assertFalse(model.content().hasProvisional)
        assertFalse(model.content().savingLevel)
    }

    @Test
    fun pickLevelFailure_keepsTheSheetOpen_andEmitsFailure() = runTest(dispatcher) {
        givenPlan(completePlan(provisionalActivity))
        coach.confirmResult = { Result.failure<CoachDerivedValues>(AppException(AppError.Network)) }
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BeforeStartEvent.OpenSheet)

        model.effects.test {
            model.onEvent(BeforeStartEvent.PickLevel(ActivityLevel.High))
            advanceUntilIdle()
            assertEquals(BeforeStartEffect.CreateFailed(offline = true), awaitItem())
        }

        assertEquals(ActivityLevel.Light, model.content().sheet)
        assertFalse(model.content().savingLevel)
    }

    @Test
    fun pickLevelWithoutOpenSheet_isIgnored() = runTest(dispatcher) {
        givenPlan(completePlan())
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(BeforeStartEvent.PickLevel(ActivityLevel.High))
        advanceUntilIdle()

        assertTrue(coach.confirmCalls.isEmpty())
    }

    @Test
    fun closeSheet_closesWithoutConfirming() = runTest(dispatcher) {
        givenPlan(completePlan())
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(BeforeStartEvent.OpenSheet)

        model.onEvent(BeforeStartEvent.CloseSheet)

        assertNull(model.content().sheet)
        assertTrue(coach.confirmCalls.isEmpty())
    }

    @Test
    fun recreatedFromTheSameSavedState_keepsModeIdsAndSheet_andNeverReConfirms() = runTest(dispatcher) {
        givenPlan(completePlan(provisionalActivity))
        val saved = args(planId = "p", replacing = "old")
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(BeforeStartEvent.OpenSheet)

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(BeforeStartMode.Plan, second.content().mode)
        assertEquals("p", second.content().planId)
        assertEquals("old", second.content().replacingPlanId)
        assertEquals(ActivityLevel.Light, second.content().sheet)
        assertTrue(coach.confirmCalls.isEmpty())
    }

    @Test
    fun unknownModeArgument_fallsBackToThePlan() = runTest(dispatcher) {
        val model = viewModel(args("weird"))

        advanceUntilIdle()

        assertEquals(BeforeStartMode.Plan, model.content().mode)
    }

    private val savedIntakeEntry =
        planInput(PlanInputKey.TrainingYears, PlanInputStatus.Ok, PlanInputValue.Number(4.0), PlanInputSource.Intake)

    @Test
    fun planMode_exposesThreeIntakeRows_withTheSavedValues() = runTest(dispatcher) {
        givenPlan(completePlan(savedIntakeEntry))
        intake.stored = CoachIntake(trainingYears = 4.0, knownMaintenanceKcal = 2400, freeText = "Rodilla operada")
        val model = viewModel()

        advanceUntilIdle()

        val content = model.content()
        assertEquals(
            listOf(IntakeRowKind.TrainingYears, IntakeRowKind.Maintenance, IntakeRowKind.FreeText),
            content.intakeRows,
        )
        assertEquals(IntakeSummary(4.0, 2400, "Rodilla operada"), content.intake)
    }

    @Test
    fun planMode_withoutAnIntake_hasEmptyRowsAndNoFlag() = runTest(dispatcher) {
        givenPlan(completePlan())
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(3, model.content().intakeRows.size)
        assertTrue(model.content().intake.isEmpty)
        assertTrue(model.content().flags.isEmpty())
        assertTrue(model.content().canCreate)
    }

    @Test
    fun intakeEntries_areNeverARowOrAMissingNote() = runTest(dispatcher) {
        givenPlan(
            completePlan(
                planInput(PlanInputKey.TrainingYears, PlanInputStatus.UnsetOptional, PlanInputValue.Absent),
                planInput(PlanInputKey.KnownMaintenance, PlanInputStatus.UnsetOptional, PlanInputValue.Absent),
                planInput(PlanInputKey.FreeText, PlanInputStatus.UnsetOptional, PlanInputValue.Absent),
            ),
        )
        val model = viewModel()

        advanceUntilIdle()

        val intakeKeys = setOf(PlanInputKey.TrainingYears, PlanInputKey.KnownMaintenance, PlanInputKey.FreeText)
        assertTrue(model.content().rows.none { it.key in intakeKeys })
        assertTrue(model.content().missingNotes.isEmpty())
    }

    @Test
    fun blockMode_hasOnlyYearsAndFreeTextRows_withTheStoredValues() = runTest(dispatcher) {
        coach.inputs[PlanInputKind.Block] = Result.success(PlanInputs(PlanInputKind.Block, null, emptyList()))
        intake.stored = CoachIntake(trainingYears = 1.5, knownMaintenanceKcal = 2400)
        val model = viewModel(args("block"))

        advanceUntilIdle()

        assertEquals(listOf(IntakeRowKind.TrainingYears, IntakeRowKind.FreeText), model.content().intakeRows)
        assertEquals(1.5, model.content().intake.trainingYears)
    }

    @Test
    fun loading_neverOpensAnythingOnItsOwn() = runTest(dispatcher) {
        givenPlan(completePlan())
        val model = viewModel()

        model.effects.test {
            advanceUntilIdle()
            expectNoEvents()
        }
        assertNull(model.editor.value.sheet)
    }

    @Test
    fun failedIntakeRead_stillShowsThePlanInputs() = runTest(dispatcher) {
        givenPlan(completePlan(savedIntakeEntry))
        intake.getFailure = AppException(AppError.Network)
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().intake.isEmpty)
        assertEquals(3, model.content().intakeRows.size)
    }

    @Test
    fun savingTheMaintenanceSheet_updatesOnlyThatRow_andKeepsTheOthers() = runTest(dispatcher) {
        givenPlan(completePlan())
        intake.stored = CoachIntake(trainingYears = 3.0, freeText = "hola")
        val model = viewModel()
        advanceUntilIdle()

        model.onEditorEvent(IntakeRowEvent.OpenMaintenance(null))
        model.onEditorEvent(IntakeRowEvent.EditMaintenance("2400"))
        model.onEditorEvent(IntakeRowEvent.Save)
        advanceUntilIdle()

        assertEquals(listOf<IntakeUpdate>(IntakeUpdate.KnownMaintenance(2400)), intake.saveCalls)
        assertEquals(IntakeSummary(3.0, 2400, "hola"), model.content().intake)
        assertNull(model.editor.value.sheet)
    }

    @Test
    fun anOpenYearsSheetWithADraft_survivesRecreationFromTheSameSavedState() = runTest(dispatcher) {
        givenPlan(completePlan())
        val saved = SavedStateHandle()
        val first = viewModel(saved = saved)
        advanceUntilIdle()
        first.onEditorEvent(IntakeRowEvent.OpenYears(2.0))
        first.onEditorEvent(IntakeRowEvent.YearsUp)

        val second = viewModel(saved = saved)
        advanceUntilIdle()

        assertEquals(IntakeRowSheet.Years(2.5), second.editor.value.sheet)
    }
}
