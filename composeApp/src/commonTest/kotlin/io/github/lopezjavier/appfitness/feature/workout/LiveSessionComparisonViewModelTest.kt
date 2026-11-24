package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeDiscomfortRepository
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.ComparisonSession
import io.github.lopezjavier.appfitness.domain.model.ComparisonSet
import io.github.lopezjavier.appfitness.domain.model.ExerciseComparisonInput
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.benchSquatLibrary
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.trainer.FakeCoachPlanRepository
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-147: "Frente a la última vez" on the finished-workout summary. */
class LiveSessionComparisonViewModelTest {
    private val routines = FakeRoutineRepository(listOf(routine()))
    private val repository = FakeWorkoutSessionRepository(freshSession())
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench"), exerciseRow("e2", "squat")))
    private val clock = MutableClock()
    private val trainingPreferences = FakeTrainingPreferencesRepository()
    private val bodyMeasurements = FakeWeightRepository()
    private val coachPlans = FakeCoachPlanRepository()
    private var created: LiveSessionViewModel? = null

    private fun newViewModel(savedState: SavedStateHandle = SavedStateHandle()) = LiveSessionViewModel(
        repository,
        exercises,
        benchSquatLibrary(),
        FakeConnectivityObserver(),
        clock,
        FakeHaptics(),
        routines,
        FakeAlertSound(),
        trainingPreferences,
        bodyMeasurements,
        SaveBodyMeasurement(bodyMeasurements, clock, TimeZone.UTC),
        TimeZone.UTC,
        savedState,
        discomfortRepository = FakeDiscomfortRepository(now = { clock.current }),
        coachPlans = coachPlans,
    ).also { created = it }

    private fun runSessionTest(body: suspend TestScope.() -> Unit) = runTest {
        body()
        created?.onEvent(LiveSessionEvent.RestSkip)
        runCurrent()
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository.history = mapOf("e1" to emptyList(), "e2" to emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.loaded(savedState: SavedStateHandle = SavedStateHandle()): LiveSessionViewModel {
        val vm = newViewModel(savedState)
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.log(vm: LiveSessionViewModel, position: Int, index: Int, weight: String, reps: String) {
        vm.onEvent(LiveSessionEvent.WeightChanged(position, index, weight))
        vm.onEvent(LiveSessionEvent.RepsChanged(position, index, reps))
        vm.onEvent(LiveSessionEvent.SetChecked(position, index, true))
        vm.onEvent(LiveSessionEvent.SetRpeChosen(position, index, 8.0))
        runCurrent()
    }

    private fun TestScope.logBothExercises(vm: LiveSessionViewModel) {
        log(vm, 0, 1, "82.5", "6")
        log(vm, 0, 2, "82.5", "5")
        log(vm, 1, 1, "100", "5")
    }

    private fun TestScope.finish(vm: LiveSessionViewModel) {
        vm.onEvent(LiveSessionEvent.RequestFinish)
        vm.onEvent(LiveSessionEvent.ConfirmFinish)
        advanceUntilIdle()
    }

    private fun LiveSessionViewModel.finished() = assertIs<LiveSessionUiState.Finished>(uiState.value)

    private fun input(id: String, kg: Double, reps: Int, previousKg: Double, previousReps: Int) =
        ExerciseComparisonInput(
            exerciseId = id,
            openDiscomfort = false,
            current = ComparisonSession("s1", LocalDate(2026, 10, 19), false, listOf(ComparisonSet(kg, reps))),
            previous = ComparisonSession(
                "s0",
                LocalDate(2026, 10, 12),
                false,
                listOf(ComparisonSet(previousKg, previousReps)),
            ),
        )

    @Test
    fun finishing_buildsTheComparisonOfEachExerciseWithItsLastSession() = runSessionTest {
        repository.comparisons = listOf(input("e1", 82.5, 6, 80.0, 6), input("e2", 100.0, 5, 100.0, 5))
        val vm = loaded()
        logBothExercises(vm)

        finish(vm)

        val summary = vm.finished().summary
        assertEquals(listOf("s1"), repository.comparisonCalls)
        assertEquals(
            listOf(
                ExerciseComparison(0, ProgressGlyph.Up, TopSet(80.0, 6), TopSet(82.5, 6)),
                ExerciseComparison(1, ProgressGlyph.Same, TopSet(100.0, 5), TopSet(100.0, 5)),
            ),
            summary.comparisons,
        )
        assertFalse(summary.cut)
    }

    @Test
    fun finishing_inACut_marksTheSummaryAsACut() = runSessionTest {
        coachPlans.accepted = Result.success(coachPlan(status = PlanStatus.Accepted))
        repository.comparisons = listOf(input("e2", 100.0, 5, 100.0, 5))
        val vm = loaded()
        logBothExercises(vm)

        finish(vm)

        assertTrue(vm.finished().summary.cut)
    }

    @Test
    fun finishing_withoutAnAcceptedPlan_isNotACut() = runSessionTest {
        coachPlans.accepted = Result.failure(IllegalStateException("offline"))
        val vm = loaded()
        logBothExercises(vm)

        finish(vm)

        assertFalse(vm.finished().summary.cut)
    }

    @Test
    fun anOpenDiscomfortPausesAnExerciseThatDidNotImprove() = runSessionTest {
        repository.comparisons = listOf(input("e2", 100.0, 5, 100.0, 5).copy(openDiscomfort = true))
        val vm = loaded()
        logBothExercises(vm)

        finish(vm)

        assertEquals(ProgressGlyph.Paused, vm.finished().summary.comparisons.single().glyph)
    }

    @Test
    fun whenTheComparisonCannotBeRead_everyExerciseReadsUncomparedWithItsOwnFigures() = runSessionTest {
        repository.comparisonFailure = AppError.Network
        val vm = loaded()
        logBothExercises(vm)

        finish(vm)

        val summary = vm.finished().summary
        assertEquals(
            listOf(
                ExerciseComparison(0, ProgressGlyph.Uncompared, null, TopSet(82.5, 6)),
                ExerciseComparison(1, ProgressGlyph.Uncompared, null, TopSet(100.0, 5)),
            ),
            summary.comparisons,
        )
        assertEquals(2, summary.summary.exercises.size)
    }

    @Test
    fun whenTheComparisonCannotBeRead_theFinishIsNotAnError() = runSessionTest {
        repository.comparisonFailure = AppError.Unknown
        val vm = loaded()
        logBothExercises(vm)

        finish(vm)

        assertIs<LiveSessionUiState.Finished>(vm.uiState.value)
    }

    @Test
    fun anExerciseWithoutACompletedWorkingSet_hasNoComparison() = runSessionTest {
        repository.comparisonFailure = AppError.Network
        val vm = loaded()
        log(vm, 1, 1, "100", "5")

        finish(vm)

        assertEquals(listOf(1), vm.finished().summary.comparisons.map { it.position })
    }

    @Test
    fun theUncomparedExerciseKeepsNoPreviousFigures() = runSessionTest {
        val first = input("e1", 82.5, 6, 80.0, 6)
        repository.comparisons = listOf(
            first.copy(current = first.current.copy(deload = true)),
        )
        val vm = loaded()
        logBothExercises(vm)

        finish(vm)

        val row = vm.finished().summary.comparisons.single()
        assertEquals(ProgressGlyph.Uncompared, row.glyph)
        assertNull(row.previous)
        assertEquals(TopSet(82.5, 6), row.current)
    }

    @Test
    fun theSummaryIsRebuiltWithItsComparisonsAfterRecreationFromTheSameSavedState() = runSessionTest {
        coachPlans.accepted = Result.success(coachPlan(status = PlanStatus.Accepted))
        repository.comparisons = listOf(input("e1", 82.5, 6, 80.0, 6), input("e2", 100.0, 5, 100.0, 5))
        val savedState = SavedStateHandle()
        val vm = loaded(savedState)
        logBothExercises(vm)
        vm.onEvent(LiveSessionEvent.SetExerciseRest(0, 30))
        runCurrent()
        finish(vm)
        val before = vm.finished()
        assertTrue(before.updateRoutineSheet != null)
        repository.completed += repository.session!!.copy(status = SessionStatus.Completed, endedAt = clock.now())
        repository.session = null

        val recreated = loaded(savedState).finished()

        assertEquals(before.summary.comparisons, recreated.summary.comparisons)
        assertTrue(recreated.summary.cut)
        assertEquals(2, repository.comparisonCalls.size)
    }

    private companion object {
        fun freshSession() = sessionOf(
            SessionExercise("e1", 0, null, listOf(pendingSet(1), pendingSet(2))),
            SessionExercise("e2", 1, null, listOf(pendingSet(1))),
        )

        fun routine() = Routine(
            id = "r1",
            name = "Torso",
            notes = null,
            archived = false,
            exercises = listOf(
                RoutineExercise("e1", 0, null, List(2) { PlannedSet(60.0, 10) }, restSeconds = 90),
                RoutineExercise("e2", 1, null, listOf(PlannedSet(100.0, 5)), restSeconds = 120),
            ),
        )
    }
}
