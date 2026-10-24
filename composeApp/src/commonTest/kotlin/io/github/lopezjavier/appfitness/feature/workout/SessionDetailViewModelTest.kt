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
    fun editingASetOpensTheSheetPrefilledWithItsValues() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(SessionDetailEvent.EditSet(0, 2))

        val edit = model.content().edit
        assertEquals(EditSetState(0, 2, "87,5", "8", invalid = false), edit)
    }

    @Test
    fun savingACorrectedSetWritesItKeepingTheOriginalsAndRecalculatesTheVolume() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.EditSet(0, 2))

        model.onEvent(SessionDetailEvent.RepsChanged("7"))
        model.onEvent(SessionDetailEvent.SaveSet)
        advanceUntilIdle()

        val write = repository.upserts.single()
        assertEquals("s1", write.sessionId)
        assertEquals("e1", write.exerciseId)
        assertEquals(0, write.exercisePosition)
        assertEquals(2, write.setIndex)
        assertEquals(SetEntry.WeightReps(87.5, 7), write.entry)
        assertEquals(SESSION_START, write.completedAt)
        assertEquals(SetType.Working, write.setType)
        val content = model.content()
        assertNull(content.edit)
        assertEquals(1400.0 - 87.5, content.stats.volumeKg)
        assertEquals(true, content.exercises.single().sets[1].text.endsWith("× 7"))
    }

    @Test
    fun correctingASet_keepsItsLoggedRpe() = runTest(dispatcher) {
        val base = completedSession("s1")
        val exercise = base.exercises.single()
        repository.completed += base.copy(
            exercises = listOf(
                exercise.copy(sets = exercise.sets.map { if (it.setIndex == 2) it.copy(rpe = 8.5) else it }),
            ),
        )
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.EditSet(0, 2))

        model.onEvent(SessionDetailEvent.RepsChanged("7"))
        model.onEvent(SessionDetailEvent.SaveSet)
        advanceUntilIdle()

        assertEquals(8.5, repository.upserts.single().rpe)
    }

    @Test
    fun correctingASetRecomputesTheRecordMark() = runTest(dispatcher) {
        seed()
        // 87.5 x 8 -> 1RM 110.5 beats 110.0; after correcting both sets to 5 reps (102) it no longer does.
        repository.history = mapOf("e1" to listOf(point("old", 10, 110.0)))
        val model = viewModel()
        advanceUntilIdle()
        assertTrue(model.content().exercises.single().isPersonalRecord)

        for (setIndex in 1..2) {
            model.onEvent(SessionDetailEvent.EditSet(0, setIndex))
            model.onEvent(SessionDetailEvent.RepsChanged("5"))
            model.onEvent(SessionDetailEvent.SaveSet)
            advanceUntilIdle()
        }

        assertEquals(false, model.content().exercises.single().isPersonalRecord)
    }

    @Test
    fun aSaveFailureKeepsTheTypedValuesAndEmitsActionFailed() = runTest(dispatcher) {
        seed()
        repository.upsertFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.EditSet(0, 2))
        model.onEvent(SessionDetailEvent.RepsChanged("7"))

        model.effects.test {
            model.onEvent(SessionDetailEvent.SaveSet)
            advanceUntilIdle()

            assertEquals(SessionDetailEffect.ActionFailed, awaitItem())
        }

        val content = model.content()
        assertEquals("7", content.edit?.reps)
        assertEquals(false, content.busy)
        assertEquals(true, content.exercises.single().sets[1].text.endsWith("× 8"))
        assertEquals(1400.0, content.stats.volumeKg)
    }

    @Test
    fun invalidRepsKeepTheSheetOpenWithoutWriting() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()

        for (reps in listOf("0", "", "1000")) {
            model.onEvent(SessionDetailEvent.EditSet(0, 2))
            model.onEvent(SessionDetailEvent.RepsChanged(reps))
            model.onEvent(SessionDetailEvent.SaveSet)
            advanceUntilIdle()

            assertEquals(true, model.content().edit?.invalid, "reps '$reps'")
        }
        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun editingTheTextClearsTheInvalidMark() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.EditSet(0, 2))
        model.onEvent(SessionDetailEvent.RepsChanged(""))
        model.onEvent(SessionDetailEvent.SaveSet)

        model.onEvent(SessionDetailEvent.RepsChanged("6"))

        assertEquals(false, model.content().edit?.invalid)
    }

    @Test
    fun dismissingTheEditClosesTheSheetWithoutWriting() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.EditSet(0, 2))

        model.onEvent(SessionDetailEvent.DismissEdit)

        assertNull(model.content().edit)
        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun offlineEditingAndSavingDoNothing() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.EditSet(0, 2))
        model.onEvent(SessionDetailEvent.RepsChanged("7"))

        connectivity.state.value = false
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.SaveSet)
        model.onEvent(SessionDetailEvent.DismissEdit)
        model.onEvent(SessionDetailEvent.EditSet(0, 1))
        advanceUntilIdle()

        assertEquals(false, model.content().isOnline)
        assertNull(model.content().edit)
        assertTrue(repository.upserts.isEmpty())
    }

    @Test
    fun savingOrConfirmingDeletionWhenOfflineEmitsActionFailed() = runTest(dispatcher) {
        seed()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.EditSet(0, 2))
        connectivity.state.value = false
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(SessionDetailEvent.SaveSet)
            assertEquals(SessionDetailEffect.ActionFailed, awaitItem())
        }
        assertTrue(repository.upserts.isEmpty())

        connectivity.state.value = true
        advanceUntilIdle()
        model.onEvent(SessionDetailEvent.DismissEdit)
        connectivity.state.value = false
        advanceUntilIdle()
        model.effects.test {
            model.onEvent(SessionDetailEvent.Delete)
            assertEquals(SessionDetailEffect.ActionFailed, awaitItem())
        }
        assertTrue(repository.deleted.isEmpty())
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
    fun aTypedCorrection_survivesARecreatedViewModel_withoutWriting() = runTest(dispatcher) {
        seed()
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle)
        advanceUntilIdle()
        first.onEvent(SessionDetailEvent.EditSet(0, 2))
        first.onEvent(SessionDetailEvent.WeightChanged("92,5"))
        first.onEvent(SessionDetailEvent.RepsChanged("6"))

        val second = viewModel(savedState = handle)
        advanceUntilIdle()

        assertEquals(EditSetState(0, 2, "92,5", "6"), second.content().edit)
        assertEquals(0, repository.upserts.size)
    }

    @Test
    fun aDismissedCorrection_isNotRestored() = runTest(dispatcher) {
        seed()
        val handle = SavedStateHandle()
        val first = viewModel(savedState = handle)
        advanceUntilIdle()
        first.onEvent(SessionDetailEvent.EditSet(0, 2))
        first.onEvent(SessionDetailEvent.DismissEdit)

        val second = viewModel(savedState = handle)
        advanceUntilIdle()

        assertEquals(null, second.content().edit)
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
}
