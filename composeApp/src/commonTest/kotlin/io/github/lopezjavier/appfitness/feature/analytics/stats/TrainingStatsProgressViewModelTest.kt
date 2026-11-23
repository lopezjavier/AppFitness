package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.ExerciseSetCount
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.RangeExerciseProgress
import io.github.lopezjavier.appfitness.domain.model.RangeProgress
import io.github.lopezjavier.appfitness.domain.model.RangeProgressResult
import io.github.lopezjavier.appfitness.domain.model.RangeProgressState
import io.github.lopezjavier.appfitness.domain.model.RangeRecord
import io.github.lopezjavier.appfitness.domain.model.RangeRecordKind
import io.github.lopezjavier.appfitness.domain.model.RangeRecords
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Progress, records and principal-exercise change of the statistics screen (US-145, planning/07 section 3). */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainingStatsProgressViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stats = FakeTrainingStatsRepository()
    private val exercises = FakeExerciseRepository(
        listOf(
            exerciseRow("bench", "bench"),
            exerciseRow("fly", "fly"),
            exerciseRow("dip", "dip"),
            exerciseRow("e1", "e1"),
        ),
    )
    private val library = FakeExerciseLibrary(
        mapOf(
            "bench" to exerciseInfo("bench", "Press de banca"),
            "fly" to exerciseInfo("fly", "Aperturas"),
            "dip" to exerciseInfo("dip", "Fondos"),
            "e1" to exerciseInfo("e1", "Ejercicio 1"),
        ),
    )
    private val today = LocalDate(2026, 10, 19)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        TrainingStatsViewModel(stats, exercises, library, MutableClock(), savedState)

    private inline fun <reified T> SectionState<*>.data(): T = assertIs<SectionState.Content<*>>(this).data as T

    private fun progress(id: String, state: RangeProgressState, changeKg: Double? = 6.0, mainLift: Boolean = true) =
        RangeExerciseProgress(
            exerciseId = id,
            mainLift = mainLift,
            sessions = 3,
            firstKg = 100.0,
            lastKg = if (state == RangeProgressState.Uncompared) null else 100.0 + (changeKg ?: 0.0),
            changeKg = if (state == RangeProgressState.Uncompared) null else changeKg,
            changePct = if (state == RangeProgressState.Uncompared) null else changeKg,
            state = state,
        )

    private fun record(id: String, kind: RangeRecordKind = RangeRecordKind.E1rm) =
        RangeRecord(id, kind, 106.0, 100.0, mainLift = true)

    private fun result(
        vararg items: RangeExerciseProgress,
        records: List<RangeRecord> = emptyList(),
        total: Int = records.size,
    ) = RangeProgressResult(RangeProgress(items.toList()), RangeRecords(records, total))

    @Test
    fun progressAndRecordsAreFilledInServerOrderFromOneCall() = runTest(dispatcher) {
        stats.rangeFor = {
            result(
                progress("bench", RangeProgressState.Up),
                progress("fly", RangeProgressState.Same, changeKg = 1.0, mainLift = false),
                progress("dip", RangeProgressState.Down, changeKg = -8.0, mainLift = false),
                progress("e1", RangeProgressState.Uncompared, mainLift = false),
                records = listOf(record("bench"), record("fly", RangeRecordKind.Heaviest)),
                total = 5,
            )
        }
        val model = viewModel()
        assertEquals(SectionState.Loading, model.uiState.value.progress)
        assertEquals(SectionState.Loading, model.uiState.value.records)

        advanceUntilIdle()

        val progress = model.uiState.value.progress.data<PeriodProgressUi>()
        assertEquals(listOf("bench", "fly", "dip", "e1"), progress.exercises.map { it.exerciseId })
        assertEquals(
            listOf(ProgressGlyph.Up, ProgressGlyph.Same, ProgressGlyph.Down, ProgressGlyph.Uncompared),
            progress.exercises.map { it.state },
        )
        assertEquals("Press de banca", progress.exercises.first().name)
        assertEquals("bench", progress.exercises.first().imageKey)
        assertEquals(1, progress.ups)
        assertEquals(3, progress.compared)
        val records = model.uiState.value.records.data<PeriodRecordsUi>()
        assertEquals(listOf(true, false), records.items.map { it.e1rm })
        assertEquals(listOf("Press de banca", "Aperturas"), records.items.map { it.name })
        assertEquals(5, records.total)
        assertEquals(1, stats.rangeCalls.size)
    }

    @Test
    fun aRangeChangeReloadsProgressWithTheNewWindowAndCancelsTheOldCall() = runTest(dispatcher) {
        stats.rangeDelay = { if (it.from == LocalDate(2026, 7, 19)) 1_000L else 0L }
        stats.rangeFor = {
            result(progress(if (it.from == LocalDate(2026, 7, 19)) "bench" else "fly", RangeProgressState.Up))
        }
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.ThreeMonths))
        advanceTimeBy(10)
        assertEquals(SectionState.Loading, model.uiState.value.progress)
        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.SixMonths))
        advanceUntilIdle()

        assertEquals(Window(LocalDate(2026, 4, 19), today), stats.rangeCalls.last())
        assertEquals(
            listOf("fly"),
            model.uiState.value.progress.data<PeriodProgressUi>().exercises.map { it.exerciseId },
        )
    }

    @Test
    fun allTimeSendsANullFromToProgress() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.AllTime))
        advanceUntilIdle()

        assertEquals(Window(null, today), stats.rangeCalls.last())
    }

    @Test
    fun aFailedProgressCallOnlyFailsItsOwnCards() = runTest(dispatcher) {
        stats.rangeFailure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(SectionState.Error, state.progress)
        assertEquals(SectionState.Error, state.records)
        assertIs<SectionState.Content<*>>(state.summary)
        assertIs<SectionState.Content<*>>(state.muscleSets)
        assertIs<SectionState.Content<*>>(state.radar)
    }

    @Test
    fun progressAndRecordsBothInErrorShowOneErrorAndNoRecordsCard() = runTest(dispatcher) {
        stats.rangeFailure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.uiState.value.progressErrorVisible)
        assertFalse(model.uiState.value.recordsCardVisible)
    }

    @Test
    fun recordsErrorAloneHidesTheRecordsCardWithoutAnErrorCard() {
        val state = TrainingStatsUiState(
            progress = SectionState.Content(PeriodProgressUi(emptyList())),
            records = SectionState.Error,
        )

        assertFalse(state.progressErrorVisible)
        assertFalse(state.recordsCardVisible)
    }

    @Test
    fun noExercisesIsAnEmptyProgressAndNoRecordsIsAnEmptyRecordsSection() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(SectionState.Empty, model.uiState.value.progress)
        assertEquals(SectionState.Empty, model.uiState.value.records)
        assertFalse(model.uiState.value.recordsCardVisible)
    }

    @Test
    fun recordsTotalZeroIsEmptyEvenWithProgress() = runTest(dispatcher) {
        stats.rangeFor = { result(progress("bench", RangeProgressState.Up)) }
        val model = viewModel()

        advanceUntilIdle()

        assertIs<SectionState.Content<*>>(model.uiState.value.progress)
        assertEquals(SectionState.Empty, model.uiState.value.records)
    }

    @Test
    fun anExerciseWithoutAResolvableNameFailsProgressAndRecords() = runTest(dispatcher) {
        stats.rangeFor = { result(progress("ghost", RangeProgressState.Up), records = listOf(record("bench"))) }
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(SectionState.Error, model.uiState.value.progress)
        assertIs<SectionState.Content<*>>(model.uiState.value.records)
    }

    @Test
    fun aRecordWithoutAResolvableNameFailsOnlyTheRecordsCard() = runTest(dispatcher) {
        stats.rangeFor = { result(progress("bench", RangeProgressState.Up), records = listOf(record("ghost"))) }
        val model = viewModel()

        advanceUntilIdle()

        assertIs<SectionState.Content<*>>(model.uiState.value.progress)
        assertEquals(SectionState.Error, model.uiState.value.records)
    }

    @Test
    fun principalRowsGetTheirChangeAndComparedFlagByExerciseId() = runTest(dispatcher) {
        stats.exercisesFor = { _, group ->
            if (group != null) {
                emptyList()
            } else {
                listOf(
                    ExerciseSetCount("bench", 9.0, 3),
                    ExerciseSetCount("fly", 6.0, 2),
                    ExerciseSetCount("dip", 3.0, 1),
                )
            }
        }
        stats.rangeFor = {
            result(
                progress("bench", RangeProgressState.Up, changeKg = 5.0),
                progress("fly", RangeProgressState.Uncompared),
            )
        }
        val model = viewModel()

        advanceUntilIdle()

        val top = model.uiState.value.topExercises.data<List<StatsExercise>>()
        assertEquals(listOf(5.0, null, null), top.map { it.changeKg })
        assertEquals(listOf(true, false, false), top.map { it.compared })
    }

    @Test
    fun aFailedProgressCallKeepsThePrincipalRowsUncompared() = runTest(dispatcher) {
        stats.exercisesFor = { _, group ->
            if (group != null) emptyList() else listOf(ExerciseSetCount("bench", 9.0, 3))
        }
        stats.rangeFailure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        val top = model.uiState.value.topExercises.data<List<StatsExercise>>()
        assertEquals(listOf(false), top.map { it.compared })
        assertNull(top.single().changeKg)
    }

    @Test
    fun theMuscleSheetRowsStayWithoutChange() = runTest(dispatcher) {
        stats.exercisesFor = { _, group ->
            if (group == MuscleGroup.Chest) listOf(ExerciseSetCount("bench", 3.0, 1)) else emptyList()
        }
        stats.rangeFor = { result(progress("bench", RangeProgressState.Up)) }
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainingStatsEvent.OpenMuscle(MuscleGroup.Chest))
        advanceUntilIdle()

        val row = model.uiState.value.muscleDetail.data<MuscleDetail>().exercises.single()
        assertFalse(row.compared)
        assertNull(row.changeKg)
    }

    @Test
    fun retryOnProgressReloadsTheFrameSectionsTogether() = runTest(dispatcher) {
        stats.rangeFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()

        stats.rangeFailure = null
        stats.rangeFor = { result(progress("bench", RangeProgressState.Up), records = listOf(record("bench"))) }
        model.onEvent(TrainingStatsEvent.Retry(StatsSection.Progress))
        advanceUntilIdle()

        assertIs<SectionState.Content<*>>(model.uiState.value.progress)
        assertIs<SectionState.Content<*>>(model.uiState.value.records)
    }

    @Test
    fun theRangeIsRestoredFromTheSameSavedStateAndLoadsItsWindow() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainingStatsEvent.SelectRange(ProgressRange.SixMonths))
        advanceUntilIdle()
        stats.rangeCalls.clear()

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(ProgressRange.SixMonths, second.uiState.value.range)
        assertEquals(listOf(Window(LocalDate(2026, 4, 19), today)), stats.rangeCalls)
    }
}
