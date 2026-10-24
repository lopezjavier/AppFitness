package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeTrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.routines.FakeRoutineRepository
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeWorkoutSessionRepository()
    private val trainingPreferences = FakeTrainingPreferencesRepository()
    private val bodyMeasurements = FakeWeightRepository()
    private val connectivity = FakeConnectivityObserver()
    private val routines = FakeRoutineRepository()
    private val exercises = FakeExerciseRepository(listOf(exerciseRow("e1", "bench")))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String = "s1", savedState: SavedStateHandle = SavedStateHandle()) =
        SessionDetailViewModel(
            sessionId = id,
            repository = repository,
            exerciseRepository = exercises,
            exerciseLibrary = FakeExerciseLibrary(mapOf("bench" to exerciseInfo("bench", "Press banca"))),
            connectivity = connectivity,
            clock = MutableClock(),
            preferences = trainingPreferences,
            bodyMeasurements = bodyMeasurements,
            routineRepository = routines,
            savedState = savedState,
        )

    private fun SessionDetailViewModel.content() = assertIs<SessionDetailUiState.Content>(uiState.value)

    private fun point(id: String, day: Int, best: Double?) =
        ExerciseHistoryPoint(id, LocalDate(2026, 10, day), best, 0.0)

    /** 87.5 x 8 twice (1RM 110.5) at 10:00 UTC on 2026-10-19. */
    private fun seed(id: String = "s1") {
        repository.completed += completedSession(id)
    }

    @Test
    fun startsLoadingThenShowsTheStatsExercisesAndSets() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        assertEquals(SessionDetailUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = model.content()
        assertEquals("Torso", content.name)
        assertEquals(DateLabel.Today, content.label)
        assertEquals(1400.0, content.stats.volumeKg)
        assertEquals(2, content.stats.completedSets)
        val exercise = content.exercises.single()
        assertEquals("Press banca", exercise.name)
        assertEquals(listOf(1, 2), exercise.sets.map { it.setIndex })
        assertEquals("87,5 kg × 8", exercise.sets[0].text)
    }

    @Test
    fun detailVolumeFollowsThePerHandFlag() = runTest(dispatcher) {
        seed()
        exercises.rows = listOf(exerciseRow("e1", "bench").copy(perHand = true))
        val on = viewModel()
        advanceUntilIdle()
        assertEquals(2800.0, on.content().stats.volumeKg)

        exercises.rows = listOf(exerciseRow("e1", "bench"))
        val off = viewModel()
        advanceUntilIdle()
        assertEquals(1400.0, off.content().stats.volumeKg)
    }

    @Test
    fun bodyweightVolumeUsesTheLatestBodyWeight() = runTest(dispatcher) {
        repository.completed += completedSession("s1").copy(
            exercises = listOf(
                SessionExercise(
                    "e1",
                    0,
                    null,
                    listOf(WorkoutSet(1, SetType.Working, SetEntry.Bodyweight(10.0, 8), SESSION_START)),
                ),
            ),
        )
        bodyMeasurements.rows += BodyMeasurement("w1", SESSION_START, 74.0, MeasurementSource.Manual)
        val model = viewModel("s1")

        advanceUntilIdle()

        assertEquals(672.0, model.content().stats.volumeKg)
    }

    @Test
    fun anExerciseThatBeatsEveryEarlierSessionIsMarkedAsARecordEvenWithLaterSessions() = runTest(dispatcher) {
        seed()
        repository.history = mapOf("e1" to listOf(point("old", 10, 100.0), point("later", 25, 200.0)))
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().exercises.single().isPersonalRecord)
    }

    @Test
    fun anExerciseThatDoesNotBeatAnEarlierSessionIsNotMarked() = runTest(dispatcher) {
        seed()
        repository.history = mapOf("e1" to listOf(point("old", 10, 150.0)))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(false, model.content().exercises.single().isPersonalRecord)
    }

    /** Warm-up 100 x 3 (1RM 110) then working 80 x 8 (1RM 101.5). */
    private fun seedHeavyWarmUp() {
        repository.completed += completedSession("s1").copy(
            exercises = listOf(
                SessionExercise(
                    "e1",
                    0,
                    null,
                    listOf(
                        WorkoutSet(1, SetType.WarmUp, SetEntry.WeightReps(100.0, 3), SESSION_START),
                        doneSet(2, 80.0, 8),
                    ),
                ),
            ),
        )
        repository.history = mapOf("e1" to listOf(point("old", 10, 105.0)))
    }

    @Test
    fun warmUpSetsAreExcludedFromTheTotalsAndRecordsByDefault() = runTest(dispatcher) {
        seedHeavyWarmUp()
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(1, model.content().stats.completedSets)
        assertEquals(640.0, model.content().stats.volumeKg)
        assertEquals(false, model.content().exercises.single().isPersonalRecord)
    }

    @Test
    fun warmUpSetsCountInTheTotalsAndRecordsWhenThePreferenceIsOn() = runTest(dispatcher) {
        seedHeavyWarmUp()
        trainingPreferences.result = Result.success(TrainingPreferences(warmUpIncluded = true))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(2, model.content().stats.completedSets)
        assertEquals(940.0, model.content().stats.volumeKg)
        assertTrue(model.content().exercises.single().isPersonalRecord)
    }

    @Test
    fun aFailedPreferenceReadStillShowsTheSessionExcludingWarmUps() = runTest(dispatcher) {
        seedHeavyWarmUp()
        trainingPreferences.result = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(1, model.content().stats.completedSets)
    }

    @Test
    fun aFailedHistoryReadShowsNoRecordsButStillShowsTheSession() = runTest(dispatcher) {
        seed()
        repository.historyFailure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(false, model.content().exercises.single().isPersonalRecord)
    }

    private fun earlierRecords(id: String = "old") = ExerciseSessionRecords(
        id,
        Instant.parse("2026-10-01T10:00:00Z"),
        heaviestLift = 80.0,
        bestSet = 10_000.0,
        sessionVolume = 100_000.0,
        bestOneRm = 100.0,
    )

    @Test
    fun beatingTheBestOneRepMaxAndTheHeaviestLiftMarksBothRecords() = runTest(dispatcher) {
        seed()
        repository.sessionRecords = mapOf("e1" to listOf(earlierRecords()))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(
            setOf(PersonalRecordType.HeaviestLift, PersonalRecordType.Best1Rm),
            model.content().exercises.single().records,
        )
    }

    @Test
    fun aWarmUpSetThatWouldBeatTheRecordsMarksNothingWhileExcluded() = runTest(dispatcher) {
        seedHeavyWarmUp()
        repository.sessionRecords = mapOf("e1" to listOf(earlierRecords().copy(heaviestLift = 90.0, bestOneRm = 105.0)))
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().exercises.single().records.isEmpty())
    }

    @Test
    fun aWarmUpSetCountsForTheRecordsWhenThePreferenceIsOn() = runTest(dispatcher) {
        seedHeavyWarmUp()
        repository.sessionRecords = mapOf("e1" to listOf(earlierRecords().copy(heaviestLift = 90.0, bestOneRm = 105.0)))
        trainingPreferences.result = Result.success(TrainingPreferences(warmUpIncluded = true))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(
            setOf(PersonalRecordType.HeaviestLift, PersonalRecordType.Best1Rm),
            model.content().exercises.single().records,
        )
    }

    @Test
    fun aFailedRecordsLoadLeavesNoMarksAndStillShowsTheSession() = runTest(dispatcher) {
        seed()
        repository.sessionRecords = mapOf("e1" to listOf(earlierRecords()))
        repository.recordsFailure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().exercises.single().records.isEmpty())
    }

    @Test
    fun aMissingSessionShowsTheErrorState() = runTest(dispatcher) {
        val model = viewModel("nope")

        advanceUntilIdle()

        assertEquals(SessionDetailUiState.Error, model.uiState.value)
    }

    @Test
    fun aFailedLoadShowsTheErrorAndRetryLoadsAgain() = runTest(dispatcher) {
        seed()
        repository.getSessionFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(SessionDetailUiState.Error, model.uiState.value)

        repository.getSessionFailure = null
        model.onEvent(SessionDetailEvent.Retry)
        advanceUntilIdle()

        assertEquals("Torso", model.content().name)
    }

    @Test
    fun offlineDeletionCannotBeRequested() = runTest(dispatcher) {
        seed()
        connectivity.state.value = false
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(SessionDetailEvent.Delete)
        advanceUntilIdle()

        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun deletingWhenOfflineEmitsActionFailedWithoutDeleting() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        connectivity.state.value = false
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(SessionDetailEvent.Delete)
            assertEquals(SessionDetailEffect.ActionFailed, awaitItem())
        }
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun deletingDeletesTheSessionAtOnceAndEmitsDeleted() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(SessionDetailEvent.Delete)
            advanceUntilIdle()

            assertEquals(SessionDetailEffect.Deleted, awaitItem())
        }
        assertEquals(listOf("s1"), repository.deleted)
    }

    @Test
    fun aDeletionFailureEmitsActionFailedAndKeepsTheSession() = runTest(dispatcher) {
        seed()
        repository.deleteFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(SessionDetailEvent.Delete)
            advanceUntilIdle()

            assertEquals(SessionDetailEffect.ActionFailed, awaitItem())
        }
        assertEquals(false, model.content().busy)
    }

    @Test
    fun showsTheMuscleSplitOfCompletedSetsByPrimaryGroup() = runTest(dispatcher) {
        seed()
        val model = viewModel()

        advanceUntilIdle()

        val split = model.content().split
        assertEquals(listOf(MuscleGroup.Chest), split.map { it.group })
        assertEquals(2, split.single().sets)
        assertEquals(1.0, split.single().fraction)
    }

    @Test
    fun theSplitIsEmptyWhenTheExerciseGroupsCannotBeRead() = runTest(dispatcher) {
        seed()
        exercises.failure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().split.isEmpty())
    }

    @Test
    fun setsExposeTheirRpeOnlyWhenTheyHaveOne() = runTest(dispatcher) {
        repository.completed += completedSession("s1").copy(
            exercises = listOf(
                SessionExercise(
                    "e1",
                    0,
                    null,
                    listOf(
                        WorkoutSet(1, SetType.Working, SetEntry.WeightReps(80.0, 8), SESSION_START, rpe = 8.5),
                        WorkoutSet(2, SetType.Working, SetEntry.WeightReps(80.0, 8), SESSION_START),
                    ),
                ),
            ),
        )
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(listOf(8.5, null), model.content().exercises.single().sets.map { it.rpe })
    }

    @Test
    fun openingSaveAsRoutinePrefillsTheWorkoutName() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(SessionDetailEvent.OpenSaveAsRoutine)

        assertEquals(SaveAsRoutineState("Torso", tooManySets = false), model.content().saveAsRoutine)
    }

    @Test
    fun confirmingCreatesTheRoutineAndEmitsItsIdClosingTheSheet() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.OpenSaveAsRoutine)
        model.onEvent(SessionDetailEvent.SaveAsRoutineNameChanged("Mi torso"))

        model.effects.test {
            model.onEvent(SessionDetailEvent.ConfirmSaveAsRoutine)
            advanceUntilIdle()

            assertEquals(SessionDetailEffect.SavedAsRoutine("r-from-session"), awaitItem())
        }
        assertEquals(listOf("s1" to "Mi torso"), routines.createdFromSession)
        assertNull(model.content().saveAsRoutine)
        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun aBlankNameDoesNotCallTheServer() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.OpenSaveAsRoutine)
        model.onEvent(SessionDetailEvent.SaveAsRoutineNameChanged("   "))

        model.onEvent(SessionDetailEvent.ConfirmSaveAsRoutine)
        advanceUntilIdle()

        assertEquals(false, model.content().saveAsRoutine?.canConfirm)
        assertTrue(routines.createdFromSession.isEmpty())
    }

    @Test
    fun anExerciseWithMoreThanTenSetsBlocksSavingAsRoutineUpFront() = runTest(dispatcher) {
        val sets = (1..11).map { doneSet(it, 50.0, 10) }
        repository.completed += completedSession("s1").copy(exercises = listOf(SessionExercise("e1", 0, null, sets)))
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.OpenSaveAsRoutine)

        model.onEvent(SessionDetailEvent.ConfirmSaveAsRoutine)
        advanceUntilIdle()

        val sheet = model.content().saveAsRoutine
        assertEquals(true, sheet?.tooManySets)
        assertEquals(false, sheet?.canConfirm)
        assertTrue(routines.createdFromSession.isEmpty())
    }

    @Test
    fun aFailureKeepsTheSheetOpenWithTheNameAndFlagsIt() = runTest(dispatcher) {
        seed()
        routines.createFromSessionFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.OpenSaveAsRoutine)
        model.onEvent(SessionDetailEvent.SaveAsRoutineNameChanged("Mi torso"))

        model.onEvent(SessionDetailEvent.ConfirmSaveAsRoutine)
        advanceUntilIdle()

        assertEquals(SaveAsRoutineState("Mi torso", tooManySets = false, failed = true), model.content().saveAsRoutine)
    }

    @Test
    fun offlineConfirmingFlagsTheSheetWithoutCallingTheServer() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.OpenSaveAsRoutine)
        connectivity.state.value = false
        advanceUntilIdle()

        model.onEvent(SessionDetailEvent.ConfirmSaveAsRoutine)
        advanceUntilIdle()

        assertEquals(true, model.content().saveAsRoutine?.failed)
        assertTrue(routines.createdFromSession.isEmpty())
    }

    @Test
    fun dismissingTheSheetDiscardsTheName() = runTest(dispatcher) {
        seed()
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle)
        advanceUntilIdle()
        first.onEvent(SessionDetailEvent.OpenSaveAsRoutine)
        first.onEvent(SessionDetailEvent.SaveAsRoutineNameChanged("Mi torso"))
        first.onEvent(SessionDetailEvent.DismissSaveAsRoutine)

        val second = viewModel(savedState = handle)
        advanceUntilIdle()

        assertNull(second.content().saveAsRoutine)
    }

    @Test
    fun theOpenSheetAndItsTypedNameSurviveARecreatedViewModel() = runTest(dispatcher) {
        seed()
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle)
        advanceUntilIdle()
        first.onEvent(SessionDetailEvent.OpenSaveAsRoutine)
        first.onEvent(SessionDetailEvent.SaveAsRoutineNameChanged("Mi torso"))

        val second = viewModel(savedState = handle)
        advanceUntilIdle()

        assertEquals("Mi torso", second.content().saveAsRoutine?.name)
        assertTrue(routines.createdFromSession.isEmpty())
    }
}
