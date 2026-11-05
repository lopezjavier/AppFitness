package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeProgressionRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.ProgressionAction
import io.github.lopezjavier.appfitness.domain.model.ProgressionReason
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.benchSquatLibrary
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachBlockRepository
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachChangesRepository
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachPlanRepository
import io.github.lopezjavier.appfitness.feature.trainer.coachBlock
import io.github.lopezjavier.appfitness.feature.trainer.coachChange
import io.github.lopezjavier.appfitness.feature.trainer.coachPlan
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** T-228: inside a saved block the T-159 suggestion is not offered, and changed exercises carry a `?` marker. */
class LiveSessionCoachChangeViewModelTest {
    private val repository = FakeWorkoutSessionRepository(
        sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1))),
        ),
    )
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench"), exerciseRow("e2", "squat")))
    private val progression = FakeProgressionRepository()
    private val plans = FakeCoachPlanRepository().apply {
        current = Result.success(coachPlan(status = PlanStatus.Accepted))
    }
    private val blocks = FakeCoachBlockRepository().apply { current = Result.success(blockWithRoutine("r1")) }
    private val changes = FakeCoachChangesRepository()
    private val clock = MutableClock()
    private val weights = FakeWeightRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.history = mapOf("e1" to emptyList(), "e2" to emptyList())
        progression.suggestions = listOf(
            ProgressionSuggestion("e1", ProgressionAction.AddRep, 80.0, 8, ProgressionReason.BelowMax, emptyList()),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun blockWithRoutine(routineId: String, saved: Boolean = true): CoachBlock {
        val base = coachBlock(saved = saved)
        return base.copy(sessions = listOf(base.sessions.first().copy(routineId = routineId)))
    }

    private fun TestScope.loaded(
        savedState: SavedStateHandle = SavedStateHandle(),
        withBlock: Boolean = true,
    ): LiveSessionViewModel {
        val vm = LiveSessionViewModel(
            repository,
            exercises,
            benchSquatLibrary(),
            FakeConnectivityObserver(),
            clock,
            FakeHaptics(),
            FakeRoutineRepository(),
            FakeAlertSound(),
            FakeTrainingPreferencesRepository(),
            weights,
            SaveBodyMeasurement(weights, clock, TimeZone.UTC),
            TimeZone.UTC,
            savedState,
            progressionRepository = progression,
            coachChanges = changes,
            coachPlans = plans.takeIf { withBlock },
            coachBlocks = blocks.takeIf { withBlock },
        )
        advanceUntilIdle()
        return vm
    }

    private fun LiveSessionViewModel.active() = assertIs<LiveSessionUiState.Active>(uiState.value)

    @Test
    fun insideASavedBlock_theSuggestionIsNotOffered() = runTest {
        assertNull(loaded().active().exercises[0].suggestion)
    }

    @Test
    fun outsideABlock_theSuggestionStays() = runTest {
        assertNotNull(loaded(withBlock = false).active().exercises[0].suggestion)
    }

    @Test
    fun aRoutineOutsideTheSavedBlock_keepsTheSuggestion() = runTest {
        blocks.current = Result.success(blockWithRoutine("another-routine"))

        assertNotNull(loaded().active().exercises[0].suggestion)
    }

    @Test
    fun aBlockNotSavedYet_keepsTheSuggestion() = runTest {
        blocks.current = Result.success(blockWithRoutine("r1", saved = false))

        assertNotNull(loaded().active().exercises[0].suggestion)
    }

    @Test
    fun aPlanThatIsOnlyProposed_keepsTheSuggestion() = runTest {
        plans.current = Result.success(coachPlan(status = PlanStatus.Proposed))

        assertNotNull(loaded().active().exercises[0].suggestion)
    }

    @Test
    fun aFailedBlockRead_countsAsOutsideTheBlock() = runTest {
        blocks.current = Result.failure(AppException(AppError.Network))

        assertNotNull(loaded().active().exercises[0].suggestion)
    }

    @Test
    fun aFailedPlanRead_countsAsOutsideTheBlock() = runTest {
        plans.current = Result.failure(AppException(AppError.Network))

        assertNotNull(loaded().active().exercises[0].suggestion)
    }

    @Test
    fun insideABlock_theHelpSheetDoesNotOpen() = runTest {
        val vm = loaded()

        vm.onEvent(LiveSessionEvent.OpenProgressionHelp(0))
        runCurrent()

        assertNull(vm.active().progressionHelpPosition)
    }

    @Test
    fun aRestoredHelpSheet_isClearedWhenTheSuggestionIsGone() = runTest {
        val handle = SavedStateHandle(mapOf("live_progression_help_position" to 0))

        val vm = loaded(handle)

        assertNull(vm.active().progressionHelpPosition)
    }

    @Test
    fun aChangedExercise_carriesItsMarker() = runTest {
        changes.changes = Result.success(listOf(coachChange(exerciseId = "e1", routineId = "r1")))

        val vm = loaded()

        val marker = assertNotNull(vm.coachMarkerFor(0))
        assertEquals(CoachChangeKind.LoadRaise, marker.kind)
        assertEquals(62.5, marker.figures.nextKg)
        assertEquals(marker, vm.active().exercises[0].coachMarker)
        assertNull(vm.coachMarkerFor(1))
        assertNull(vm.active().exercises[1].coachMarker)
    }

    @Test
    fun anUndoneChange_hasNoMarker() = runTest {
        changes.changes = Result.success(
            listOf(coachChange(exerciseId = "e1", routineId = "r1", status = CoachChangeStatus.Undone)),
        )

        assertNull(loaded().coachMarkerFor(0))
    }

    @Test
    fun aFailedChangesRead_leavesTheSessionWithoutMarkers() = runTest {
        changes.changes = Result.failure(AppException(AppError.Network))

        val vm = loaded()

        assertNull(vm.coachMarkerFor(0))
        assertNotNull(vm.active())
    }
}
