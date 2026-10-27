package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.ExerciseSetCount
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.feature.analytics.stats.FakeTrainingStatsRepository.Window
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingStatsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stats = FakeTrainingStatsRepository()
    private val exercises = FakeExerciseRepository(
        listOf(exerciseRow("bench", "bench"), exerciseRow("fly", "fly"), exerciseRow("dip", "dip")) +
            (1..6).map { exerciseRow("e$it", "e$it") },
    )
    private val library = FakeExerciseLibrary(
        mapOf(
            "bench" to exerciseInfo("bench", "Press de banca"),
            "fly" to exerciseInfo("fly", "Aperturas"),
            "dip" to exerciseInfo("dip", "Fondos"),
        ) + (1..6).associate { "e$it" to exerciseInfo("e$it", "Ejercicio $it") },
    )
    private val today = LocalDate(2026, 10, 19)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        TrainingStatsViewModel(stats, exercises, library, MutableClock(), savedState)

    private inline fun <reified T> SectionState<*>.data(): T = assertIs<SectionState.Content<*>>(this).data as T

    private val sevenDays = Window(LocalDate(2026, 10, 13), today)

    @Test
    fun startsLoadingThenSendsTheOneMonthWindowAndItsPreviousOne() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(SectionState.Loading, model.uiState.value.summary)

        advanceUntilIdle()

        assertEquals(ProgressRange.OneMonth, model.uiState.value.range)
        assertEquals(listOf(Window(LocalDate(2026, 9, 19), today)), stats.summaryCalls)
        assertEquals(listOf(Window(LocalDate(2026, 9, 19), today)), stats.weeklyCalls)
        // Current and previous muscle windows, plus the 7-day map and the (7-day) detail only when opened.
        assertEquals(
            setOf(
                Window(LocalDate(2026, 9, 19), today),
                Window(LocalDate(2026, 8, 19), LocalDate(2026, 9, 18)),
                sevenDays,
            ),
            stats.muscleCalls.toSet(),
        )
    }

    @Test
    fun everyFrameSendsItsOwnWindowAndTheMapStaysOnTheLastSevenDays() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        val expectedFrom = mapOf(
            ProgressRange.ThreeMonths to LocalDate(2026, 7, 19),
            ProgressRange.SixMonths to LocalDate(2026, 4, 19),
            ProgressRange.OneYear to LocalDate(2025, 10, 19),
            ProgressRange.AllTime to null,
        )
        for ((range, from) in expectedFrom) {
            stats.summaryCalls.clear()
            stats.muscleCalls.clear()
            model.onEvent(TrainingStatsEvent.SelectRange(range))
            advanceUntilIdle()
            assertEquals(listOf(Window(from, today)), stats.summaryCalls, "summary $range")
            assertEquals(false, sevenDays in stats.muscleCalls, "map is not reloaded for $range")
        }
    }

    @Test
    fun allTimeSendsNoLowerBoundAndTheRadarHasNoPreviousSeries() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        stats.muscleCalls.clear()

        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.AllTime))
        advanceUntilIdle()

        assertEquals(listOf(Window(null, today)), stats.muscleCalls)
        assertNull(model.uiState.value.radar.data<MuscleRadar>().previous)
    }

    @Test
    fun radarHasCurrentAndPreviousOverEveryMuscleGroupInCatalogOrder() = runTest(dispatcher) {
        stats.muscleFor = { w ->
            if (w.from == LocalDate(2026, 8, 19)) {
                listOf(MuscleGroupSets(MuscleGroup.Back, 2.0))
            } else {
                listOf(MuscleGroupSets(MuscleGroup.Chest, 3.0))
            }
        }
        val model = viewModel()
        advanceUntilIdle()

        val radar = model.uiState.value.radar.data<MuscleRadar>()
        assertEquals(MuscleGroup.entries, radar.current.map { it.muscleGroup })
        assertEquals(3.0, radar.current.first { it.muscleGroup == MuscleGroup.Chest }.sets)
        assertEquals(0.0, radar.current.first { it.muscleGroup == MuscleGroup.Back }.sets)
        assertEquals(2.0, radar.previous!!.first { it.muscleGroup == MuscleGroup.Back }.sets)
    }

    @Test
    fun muscleRowsKeepBenchWeightsAndListEveryGroupIncludingFullBody() = runTest(dispatcher) {
        stats.muscleFor = {
            listOf(
                MuscleGroupSets(MuscleGroup.Chest, 3.0),
                MuscleGroupSets(MuscleGroup.Triceps, 1.5),
                MuscleGroupSets(MuscleGroup.Shoulders, 1.5),
                MuscleGroupSets(MuscleGroup.FullBody, 2.0),
            )
        }
        val model = viewModel()
        advanceUntilIdle()

        val rows = model.uiState.value.muscleSets.data<List<MuscleGroupSets>>()
        assertEquals(MuscleGroup.entries, rows.map { it.muscleGroup })
        assertEquals(1.5, rows.first { it.muscleGroup == MuscleGroup.Triceps }.sets)
        assertEquals(2.0, rows.first { it.muscleGroup == MuscleGroup.FullBody }.sets)
        assertEquals(0.0, rows.first { it.muscleGroup == MuscleGroup.Calves }.sets)
    }

    @Test
    fun bodyMapExcludesFullBodyAndCoversLastSevenDaysRegardlessOfFrame() = runTest(dispatcher) {
        stats.muscleFor = { w ->
            if (w == sevenDays) {
                listOf(MuscleGroupSets(MuscleGroup.Chest, 3.0), MuscleGroupSets(MuscleGroup.FullBody, 1.0))
            } else {
                emptyList()
            }
        }
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.OneYear))
        advanceUntilIdle()

        val map = model.uiState.value.bodyMap.data<List<MuscleGroupSets>>()
        assertEquals(MuscleGroup.entries - MuscleGroup.FullBody, map.map { it.muscleGroup })
        assertEquals(3.0, map.first { it.muscleGroup == MuscleGroup.Chest }.sets)
    }

    @Test
    fun weeklyBucketsForOneMonthAndMonthlyBucketsForOneYear() = runTest(dispatcher) {
        stats.weeklyFor = { listOf(SetCountPoint(LocalDate(2026, 10, 12), 10)) }
        val model = viewModel()
        advanceUntilIdle()
        val weekly = model.uiState.value.setCounts.data<List<SetCountPoint>>()
        assertEquals(LocalDate(2026, 9, 14), weekly.first().weekStart)
        assertEquals(10, weekly.last { it.weekStart == LocalDate(2026, 10, 12) }.sets)

        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.OneYear))
        advanceUntilIdle()
        val monthly = model.uiState.value.setCounts.data<List<SetCountPoint>>()
        assertEquals(LocalDate(2025, 10, 1), monthly.first().weekStart)
        assertEquals(SetCountPoint(LocalDate(2026, 10, 1), 10), monthly.last())
    }

    @Test
    fun topExercisesAreFiveOrderedBySetsWithNames() = runTest(dispatcher) {
        stats.exercisesFor = { _, group ->
            if (group != null) {
                emptyList()
            } else {
                listOf(
                    ExerciseSetCount("e1", 1.0, 1),
                    ExerciseSetCount("bench", 9.0, 3),
                    ExerciseSetCount("e2", 2.0, 1),
                    ExerciseSetCount("fly", 6.0, 2),
                    ExerciseSetCount("e3", 3.0, 1),
                    ExerciseSetCount("dip", 4.5, 2),
                    ExerciseSetCount("e4", 5.0, 1),
                )
            }
        }
        val model = viewModel()
        advanceUntilIdle()

        val top = model.uiState.value.topExercises.data<List<StatsExercise>>()
        assertEquals(listOf("bench", "fly", "e4", "dip", "e3"), top.map { it.exerciseId })
        assertEquals("Press de banca", top.first().name)
        assertEquals(3, top.first().sessions)
    }

    @Test
    fun frameWithoutWorkoutsIsEmptyButTheFrameStaysSelectable() = runTest(dispatcher) {
        stats.summaryFor = { TrainingSummary(0, 0, 0.0, 0) }
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(true, model.uiState.value.isEmpty)
        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.SixMonths))
        advanceUntilIdle()
        assertEquals(ProgressRange.SixMonths, model.uiState.value.range)
    }

    @Test
    fun allZeroBucketsAndNoExercisesAreEmptySections() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(SectionState.Empty, model.uiState.value.setCounts)
        assertEquals(SectionState.Empty, model.uiState.value.topExercises)
    }

    @Test
    fun aFailingSectionDoesNotBlankTheOthersAndRetryReloadsIt() = runTest(dispatcher) {
        stats.weeklyFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(SectionState.Error, model.uiState.value.setCounts)
        assertIs<SectionState.Content<*>>(model.uiState.value.summary)
        assertIs<SectionState.Content<*>>(model.uiState.value.muscleSets)

        stats.weeklyFailure = null
        stats.weeklyFor = { listOf(SetCountPoint(LocalDate(2026, 10, 12), 4)) }
        model.onEvent(TrainingStatsEvent.Retry(StatsSection.Chart))
        advanceUntilIdle()
        assertIs<SectionState.Content<*>>(model.uiState.value.setCounts)
    }

    @Test
    fun offlineEverythingFailsToErrorAndRetryAllRecovers() = runTest(dispatcher) {
        stats.summaryFailure = AppError.Network
        stats.weeklyFailure = AppError.Network
        stats.muscleFailure = AppError.Network
        stats.exercisesFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        val s = model.uiState.value
        assertEquals(
            listOf(true, true, true, true, true, true),
            listOf(
                s.summary == SectionState.Error,
                s.setCounts == SectionState.Error,
                s.muscleSets == SectionState.Error,
                s.topExercises == SectionState.Error,
                s.radar == SectionState.Error,
                s.bodyMap == SectionState.Error,
            ),
        )

        stats.summaryFailure = null
        stats.weeklyFailure = null
        stats.muscleFailure = null
        stats.exercisesFailure = null
        model.onEvent(TrainingStatsEvent.Retry())
        advanceUntilIdle()
        assertIs<SectionState.Content<*>>(model.uiState.value.summary)
        assertIs<SectionState.Content<*>>(model.uiState.value.bodyMap)
    }

    @Test
    fun aStaleFrameResultDoesNotOverwriteTheLatestFrame() = runTest(dispatcher) {
        stats.summaryDelay = { if (it.from == LocalDate(2026, 7, 19)) 1_000L else 0L }
        stats.summaryFor = { TrainingSummary(if (it.from == LocalDate(2026, 7, 19)) 99 else 5, 1, 1.0, 1) }
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.ThreeMonths))
        advanceTimeBy(10)
        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.SixMonths))
        advanceUntilIdle()

        assertEquals(ProgressRange.SixMonths, model.uiState.value.range)
        assertEquals(5, model.uiState.value.summary.data<TrainingSummary>().workouts)
    }

    @Test
    fun openingAMuscleLoadsItsSetsAndExercisesForTheLastSevenDays() = runTest(dispatcher) {
        stats.muscleFor = { listOf(MuscleGroupSets(MuscleGroup.Triceps, 1.5)) }
        stats.exercisesFor = { _, group ->
            if (group == MuscleGroup.Triceps) listOf(ExerciseSetCount("bench", 1.5, 1)) else emptyList()
        }
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainingStatsEvent.OpenMuscle(MuscleGroup.Triceps))
        advanceUntilIdle()

        assertEquals(MuscleGroup.Triceps, model.uiState.value.openMuscle)
        assertEquals(sevenDays to MuscleGroup.Triceps, stats.exerciseCalls.last())
        val detail = model.uiState.value.muscleDetail.data<MuscleDetail>()
        assertEquals(1.5, detail.sets)
        assertEquals(listOf("Press de banca"), detail.exercises.map { it.name })
    }

    @Test
    fun anUntrainedMuscleHasZeroSetsAndNoExercises() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainingStatsEvent.OpenMuscle(MuscleGroup.Calves))
        advanceUntilIdle()

        val detail = model.uiState.value.muscleDetail.data<MuscleDetail>()
        assertEquals(0.0, detail.sets)
        assertEquals(emptyList(), detail.exercises)
    }

    @Test
    fun closingTheMuscleClearsItAndAFailedDetailCanBeRetried() = runTest(dispatcher) {
        stats.detailFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainingStatsEvent.OpenMuscle(MuscleGroup.Chest))
        advanceUntilIdle()
        assertEquals(SectionState.Error, model.uiState.value.muscleDetail)

        stats.detailFailure = null
        model.onEvent(TrainingStatsEvent.Retry(StatsSection.Sheet))
        advanceUntilIdle()
        assertIs<SectionState.Content<*>>(model.uiState.value.muscleDetail)

        model.onEvent(TrainingStatsEvent.CloseMuscle)
        assertNull(model.uiState.value.openMuscle)
    }

    @Test
    fun previousWindowFailureKeepsTheCurrentRadarAndFlagsIt() = runTest(dispatcher) {
        stats.muscleFor = { listOf(MuscleGroupSets(MuscleGroup.Chest, 3.0)) }
        stats.muscleFailureFor = { if (it.from == LocalDate(2026, 8, 19)) AppError.Network else null }
        val model = viewModel()
        advanceUntilIdle()

        val radar = model.uiState.value.radar.data<MuscleRadar>()
        assertNull(radar.previous)
        assertEquals(true, radar.previousFailed)
        assertEquals(3.0, radar.current.first { it.muscleGroup == MuscleGroup.Chest }.sets)
    }

    @Test
    fun currentWindowFailureIsARadarError() = runTest(dispatcher) {
        stats.muscleFailureFor = { if (it.from == LocalDate(2026, 9, 19)) AppError.Network else null }
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(SectionState.Error, model.uiState.value.radar)
    }

    @Test
    fun allTimeRadarHasNoPreviousAndNoFailureFlag() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.AllTime))
        advanceUntilIdle()

        val radar = model.uiState.value.radar.data<MuscleRadar>()
        assertNull(radar.previous)
        assertEquals(false, radar.previousFailed)
    }

    @Test
    fun exerciseLookupIsLoadedOnceAcrossRangeChangesAndMuscleOpens() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.SixMonths))
        advanceUntilIdle()
        model.onEvent(TrainingStatsEvent.OpenMuscle(MuscleGroup.Chest))
        advanceUntilIdle()
        model.onEvent(TrainingStatsEvent.CloseMuscle)
        model.onEvent(TrainingStatsEvent.OpenMuscle(MuscleGroup.Back))
        advanceUntilIdle()

        assertEquals(1, exercises.getAllCalls)
    }

    @Test
    fun aFailedExerciseLookupIsRetried() = runTest(dispatcher) {
        exercises.failure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(SectionState.Error, model.uiState.value.topExercises)

        exercises.failure = null
        stats.exercisesFor = { _, _ -> listOf(ExerciseSetCount("bench", 2.0, 1)) }
        model.onEvent(TrainingStatsEvent.Retry())
        advanceUntilIdle()

        assertIs<SectionState.Content<*>>(model.uiState.value.topExercises)
        assertEquals(2, exercises.getAllCalls)
    }

    @Test
    fun recreatedFromTheSameSavedStateKeepsTheFrameAndTheOpenMuscle() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.ThreeMonths))
        first.onEvent(TrainingStatsEvent.OpenMuscle(MuscleGroup.Triceps))
        advanceUntilIdle()

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(ProgressRange.ThreeMonths, second.uiState.value.range)
        assertEquals(MuscleGroup.Triceps, second.uiState.value.openMuscle)
        assertIs<SectionState.Content<*>>(second.uiState.value.muscleDetail)
        assertEquals(Window(LocalDate(2026, 7, 19), today), stats.summaryCalls.last())
    }
}
