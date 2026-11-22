package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeCoachSummaryRepository
import io.github.lopezjavier.appfitness.app.FakeDiscomfortFollowUpRepository
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.app.ReminderEnv
import io.github.lopezjavier.appfitness.designsystem.ProgressGlyph
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.DiscomfortNoteReason
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.model.StrengthGroup
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.LoadDiscomfortFollowUpsUseCase
import io.github.lopezjavier.appfitness.domain.usecase.MarkWeekSummarySeen
import io.github.lopezjavier.appfitness.domain.usecase.RecordPausedWeekReport
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseLibrary
import io.github.lopezjavier.appfitness.feature.exercises.FakeExerciseRepository
import io.github.lopezjavier.appfitness.feature.exercises.exerciseInfo
import io.github.lopezjavier.appfitness.feature.exercises.exerciseRow
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WeekSummaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plusTwo = TimeZone.of("+02:00")
    private val env = ReminderEnv(LocalDate(2026, 11, 10))
    private val summaries: FakeCoachSummaryRepository get() = env.summaries
    private val reports = FakeReportRepository()
    private val discomfort = FakeDiscomfortFollowUpRepository()
    private val exercises =
        FakeExerciseRepository(listOf(exerciseRow("id-bench", "bench"), exerciseRow("id-fly", "fly")))
    private val library = FakeExerciseLibrary(
        mapOf("bench" to exerciseInfo("bench", "Press banca"), "fly" to exerciseInfo("fly", "Aperturas")),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        saved: SavedStateHandle = SavedStateHandle(),
        coordinator: ReminderCoordinator = ReminderCoordinator(env.load, backgroundScope),
        timeZone: TimeZone = TimeZone.UTC,
        clock: Clock = env.clock,
    ) = WeekSummaryViewModel(
        summaries = summaries,
        markSeen = MarkWeekSummarySeen(summaries),
        recordPausedWeek = RecordPausedWeekReport(reports),
        reminders = coordinator,
        names = ExerciseNameResolver(exercises, library),
        discomfortFollowUps = LoadDiscomfortFollowUpsUseCase(discomfort),
        clock = clock,
        savedState = saved,
        timeZone = timeZone,
    )

    private fun pausedSummary() =
        WeekSummary.pausedWeek("plan-1", LocalDate(2026, 11, 3), LocalDate(2026, 11, 9)).copy(seen = false)

    @Test
    fun pausedSummary_isContentAndNeverMarkedSeen() = runTest(dispatcher) {
        summaries.latest = Result.success(pausedSummary())
        val model = viewModel()

        advanceUntilIdle()

        val content = assertIs<WeekSummaryUiState.Content>(model.uiState.value)
        assertTrue(content.summary.paused)
        assertTrue(summaries.seen.isEmpty())
    }

    @Test
    fun pausedSummary_survivesARecreatedModelOverTheSameHandle() = runTest(dispatcher) {
        summaries.latest = Result.success(pausedSummary())
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()

        val recreated = viewModel(saved)
        advanceUntilIdle()

        assertTrue(assertIs<WeekSummaryUiState.Content>(recreated.uiState.value).summary.paused)
        assertTrue(summaries.seen.isEmpty())
        assertNull(saved.get<String>("week_summary_marked"))
    }

    @Test
    fun pausedSummary_isRecordedOnceWithTheLocalToday() = runTest(dispatcher) {
        summaries.latest = Result.success(pausedSummary())
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()

        viewModel(saved)
        advanceUntilIdle()

        assertEquals(listOf(LocalDate(2026, 11, 10)), reports.recorded)
    }

    @Test
    fun pausedSummary_failedRecordIsSilentAndRetriedOnTheNextOpen() = runTest(dispatcher) {
        summaries.latest = Result.success(pausedSummary())
        reports.recordResult = Result.failure(AppException(AppError.Network))
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()
        assertTrue(model.uiState.value is WeekSummaryUiState.Content)

        reports.recordResult = Result.success(Unit)
        viewModel(saved)
        advanceUntilIdle()

        assertEquals(2, reports.recorded.size)
    }

    @Test
    fun normalWeek_isNeverRecordedAsPaused() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary(seen = false))
        viewModel()

        advanceUntilIdle()

        assertTrue(reports.recorded.isEmpty())
    }

    @Test
    fun summaryWithNutrition_flowsToContentUnchanged() = runTest(dispatcher) {
        val summary = weekSummary(nutrition = weekNutrition(decisions = listOf(weekDecision())))
        summaries.latest = Result.success(summary)
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(summary.nutrition, assertIs<WeekSummaryUiState.Content>(model.uiState.value).summary.nutrition)
    }

    private fun pausedBenchSummary() = v2Summary(
        weekProgress(
            groups = listOf(
                StrengthGroup(
                    MuscleGroup.Chest,
                    listOf(
                        exerciseProgress("id-bench", ProgressState.Paused),
                        exerciseProgress("id-fly", ProgressState.Up),
                    ),
                ),
            ),
        ),
    )

    private fun due(id: String) = DiscomfortFollowUp(
        id,
        "d-$id",
        Instant.parse("2026-11-01T10:00:00Z"),
        offerSwap = false,
        showNote = true,
        noteReason = DiscomfortNoteReason.Sessions3,
    )

    @Test
    fun load_fillsTheLiveReportFromTheProgress() = runTest(dispatcher) {
        summaries.latest = Result.success(v2Summary())
        val model = viewModel()

        advanceUntilIdle()

        val report = assertNotNull(assertIs<WeekSummaryUiState.Content>(model.uiState.value).report)
        assertTrue(report.live)
        assertNotNull(report.progress)
    }

    @Test
    fun healthNote_showsOnlyForAPausedExerciseWithADueNote() = runTest(dispatcher) {
        summaries.latest = Result.success(pausedBenchSummary())
        discomfort.followUps = listOf(due("id-bench"), due("id-fly"))
        val model = viewModel()

        advanceUntilIdle()

        val rows = assertNotNull(assertIs<WeekSummaryUiState.Content>(model.uiState.value).report)
            .progress!!.strength.groups.single().exercises
        assertEquals(HealthNoteKind.ThreeSessions, rows[0].healthNote)
        assertNull(rows[1].healthNote)
    }

    @Test
    fun failedFollowUpRead_hidesOnlyTheNoteAndTheReportStillShows() = runTest(dispatcher) {
        summaries.latest = Result.success(pausedBenchSummary())
        discomfort.followUps = listOf(due("id-bench"))
        discomfort.failing = true
        val model = viewModel()

        advanceUntilIdle()

        val rows = assertNotNull(assertIs<WeekSummaryUiState.Content>(model.uiState.value).report)
            .progress!!.strength.groups.single().exercises
        assertNull(rows.first().healthNote)
        assertEquals(ProgressGlyph.Paused, rows.first().state)
    }

    @Test
    fun pausedWeek_hasNoReport() = runTest(dispatcher) {
        summaries.latest = Result.success(pausedSummary())
        val model = viewModel()

        advanceUntilIdle()

        assertNull(assertIs<WeekSummaryUiState.Content>(model.uiState.value).report)
    }

    // 22:30 UTC on 9 Nov is already 10 Nov at +02:00.
    private val boundary = MutableClock(Instant.parse("2026-11-09T22:30:00Z"))

    @Test
    fun today_isTheLocalDayNotTheUtcDay() = runTest(dispatcher) {
        viewModel(timeZone = plusTwo, clock = boundary)
        advanceUntilIdle()

        assertEquals(listOf(LocalDate(2026, 11, 10)), summaries.reads)
    }

    @Test
    fun today_followsTheInjectedZone() = runTest(dispatcher) {
        viewModel(timeZone = TimeZone.UTC, clock = boundary)
        advanceUntilIdle()

        assertEquals(listOf(LocalDate(2026, 11, 9)), summaries.reads)
    }

    @Test
    fun load_showsTheLatestSummaryWithTheResolvedNames() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary(changes = listOf(coachChange(exerciseId = "id-bench"))))
        val model = viewModel()
        assertEquals(WeekSummaryUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val content = assertIs<WeekSummaryUiState.Content>(model.uiState.value)
        assertEquals(3, content.summary.sessionsDone)
        assertEquals("Press banca", content.exerciseNames["id-bench"])
    }

    @Test
    fun load_passesTheStallsThroughWithTheirNames() = runTest(dispatcher) {
        val stall = StallDiagnosis("id-bench", StallCause.Sleep(6.2))
        summaries.latest = Result.success(weekSummary(stalls = listOf(stall)))
        val model = viewModel()

        advanceUntilIdle()

        val content = assertIs<WeekSummaryUiState.Content>(model.uiState.value)
        assertEquals(listOf(stall), content.summary.stalls)
        assertEquals("Press banca", content.exerciseNames["id-bench"])
    }

    @Test
    fun noSummary_isEmptyAndMarksNothing() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(WeekSummaryUiState.Empty, model.uiState.value)
        assertTrue(summaries.seen.isEmpty())
    }

    @Test
    fun failedRead_isErrorAndMarksNothing() = runTest(dispatcher) {
        summaries.latest = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(WeekSummaryUiState.Error, model.uiState.value)
        assertTrue(summaries.seen.isEmpty())
    }

    @Test
    fun networkFailure_isOffline() = runTest(dispatcher) {
        summaries.latest = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(WeekSummaryUiState.Offline, model.uiState.value)
    }

    @Test
    fun retry_loadsAgainAfterAFailure() = runTest(dispatcher) {
        summaries.latest = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()
        summaries.latest = Result.success(weekSummary())

        model.onEvent(WeekSummaryEvent.Retry)
        advanceUntilIdle()

        assertIs<WeekSummaryUiState.Content>(model.uiState.value)
    }

    @Test
    fun unseenSummary_isMarkedSeenOnceAndItsNoticeClears() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary(seen = false))
        val coordinator = ReminderCoordinator(env.load, backgroundScope)
        coordinator.refresh()
        assertEquals(listOf(ReminderKind.WeekSummary), coordinator.reminders.value.map { it.kind })

        viewModel(coordinator = coordinator)
        advanceUntilIdle()

        assertEquals(listOf("plan-1" to 2), summaries.seen)
        assertTrue(coordinator.reminders.value.none { it.kind == ReminderKind.WeekSummary })
    }

    @Test
    fun recreatedModelOverTheSameHandle_doesNotMarkAgain() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary(seen = false))
        val saved = SavedStateHandle()
        viewModel(saved)
        advanceUntilIdle()
        summaries.latest = Result.success(weekSummary(seen = false))

        viewModel(saved)
        advanceUntilIdle()

        assertEquals(1, summaries.seen.size)
    }

    @Test
    fun alreadySeenSummary_isNotMarkedAgain() = runTest(dispatcher) {
        summaries.latest = Result.success(weekSummary(seen = true))
        viewModel()

        advanceUntilIdle()

        assertTrue(summaries.seen.isEmpty())
    }
}
