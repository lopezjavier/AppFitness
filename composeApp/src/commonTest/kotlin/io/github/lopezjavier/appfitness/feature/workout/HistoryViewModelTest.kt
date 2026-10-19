package io.github.lopezjavier.appfitness.feature.workout

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** A completed session started [daysAgo] days before the fake clock's day, with a 52 min duration. */
internal fun completedSession(
    id: String,
    daysAgo: Int = 0,
    name: String = "Torso",
    hourOffset: Int = 0,
): WorkoutSession {
    val start = SESSION_START - (daysAgo * 24).hours + hourOffset.hours
    return WorkoutSession(
        id = id,
        routineId = "r1",
        name = name,
        status = SessionStatus.Completed,
        startedAt = start,
        endedAt = start + 52.minutes,
        exercises = listOf(SessionExercise("e1", 0, null, listOf(doneSet(1, 87.5, 8), doneSet(2, 87.5, 8)))),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeWorkoutSessionRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HistoryViewModel(repository, MutableClock())

    private fun HistoryViewModel.content() = assertIs<HistoryUiState.Content>(uiState.value)

    private fun seed(count: Int) {
        // Session n started n hours after the base instant, so a higher number is newer.
        repeat(count) { repository.completed += completedSession("s%02d".format(it), hourOffset = it) }
    }

    @Test
    fun startsLoadingThenShowsTheCompletedSessionsNewestFirst() = runTest(dispatcher) {
        repository.completed += completedSession("old", daysAgo = 3)
        repository.completed += completedSession("recent", daysAgo = 0)
        val model = viewModel()
        assertEquals(HistoryUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        assertEquals(listOf("recent", "old"), model.content().rows.map { it.id })
    }

    @Test
    fun aRowCarriesTheDateLabelNameDurationVolumeAndSets() = runTest(dispatcher) {
        repository.completed += completedSession("recent", daysAgo = 1, name = "Pierna")
        val model = viewModel()

        advanceUntilIdle()

        val row = model.content().rows.single()
        assertEquals(DateLabel.Yesterday, row.label)
        assertEquals("Pierna", row.name)
        assertEquals(52 * 60L, row.durationSeconds)
        assertEquals(1400.0, row.volumeKg)
        assertEquals(2, row.completedSets)
    }

    @Test
    fun withoutSessionsTheStateIsEmpty() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(HistoryUiState.Empty, model.uiState.value)
    }

    @Test
    fun theFirstPageHasTwentyRowsAndLoadMoreAppendsTheRestWithoutDuplicates() = runTest(dispatcher) {
        seed(25)
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(20, model.content().rows.size)
        assertEquals(false, model.content().endReached)

        model.onEvent(HistoryEvent.LoadMore)
        advanceUntilIdle()

        val rows = model.content().rows
        assertEquals(25, rows.size)
        assertEquals(25, rows.map { it.id }.toSet().size)
        assertTrue(model.content().endReached)
        assertEquals(listOf(0 to 20, 20 to 20), repository.listCalls)
    }

    @Test
    fun aFullLastPageStillLoadsAnEmptyNextPageThenEnds() = runTest(dispatcher) {
        seed(20)
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(false, model.content().endReached)

        model.onEvent(HistoryEvent.LoadMore)
        advanceUntilIdle()

        assertTrue(model.content().endReached)
        assertEquals(20, model.content().rows.size)
    }

    @Test
    fun loadMoreIsIgnoredOnceTheEndIsReached() = runTest(dispatcher) {
        seed(3)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(HistoryEvent.LoadMore)
        advanceUntilIdle()

        assertEquals(1, repository.listCalls.size)
    }

    @Test
    fun aSessionFinishedMeanwhileDoesNotDuplicateRowsOnTheNextPage() = runTest(dispatcher) {
        seed(25)
        val model = viewModel()
        advanceUntilIdle()
        // A newer session shifts the offset window by one: the last row of page one comes back on page two.
        repository.completed += completedSession("fresh", hourOffset = 100)

        model.onEvent(HistoryEvent.LoadMore)
        advanceUntilIdle()

        val ids = model.content().rows.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(25, ids.size)
    }

    @Test
    fun aFailedSecondPageKeepsTheRowsSetsThePageErrorAndRetryRecovers() = runTest(dispatcher) {
        seed(25)
        repository.listFailureFromOffset = 20
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(HistoryEvent.LoadMore)
        advanceUntilIdle()

        assertEquals(20, model.content().rows.size)
        assertTrue(model.content().pageError)
        assertEquals(false, model.content().loadingMore)

        repository.listFailureFromOffset = null
        model.onEvent(HistoryEvent.RetryLoadMore)
        advanceUntilIdle()

        assertEquals(25, model.content().rows.size)
        assertEquals(false, model.content().pageError)
    }

    @Test
    fun loadMoreIsIgnoredWhileThePageErrorIsShown() = runTest(dispatcher) {
        seed(25)
        repository.listFailureFromOffset = 20
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(HistoryEvent.LoadMore)
        advanceUntilIdle()
        val calls = repository.listCalls.size

        model.onEvent(HistoryEvent.LoadMore)
        advanceUntilIdle()

        assertEquals(calls, repository.listCalls.size)
    }

    @Test
    fun loadingMoreIsShownWhileThePageIsInFlight() = runTest(dispatcher) {
        seed(25)
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(HistoryEvent.LoadMore)

        assertTrue(model.content().loadingMore)
        advanceUntilIdle()
        assertEquals(false, model.content().loadingMore)
    }

    @Test
    fun aFailedFirstPageShowsTheErrorAndRetryLoadsAgain() = runTest(dispatcher) {
        seed(2)
        repository.listFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(HistoryUiState.Error, model.uiState.value)

        repository.listFailure = null
        model.onEvent(HistoryEvent.Retry)
        advanceUntilIdle()

        assertEquals(2, model.content().rows.size)
    }

    @Test
    fun refreshAfterADeletionDropsTheRowAndTheLastPerformanceFallsBack() = runTest(dispatcher) {
        repository.completed += completedSession("older", daysAgo = 3)
        repository.completed += completedSession("newer", daysAgo = 0)
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("newer", "older"), model.content().rows.map { it.id })

        repository.deleteSession("newer")
        model.onEvent(HistoryEvent.Refresh)
        advanceUntilIdle()

        assertEquals(listOf("older"), model.content().rows.map { it.id })
        assertEquals(
            listOf(PreviousSet(1, 87.5, 8), PreviousSet(2, 87.5, 8)),
            repository.lastPerformance("e1").getOrThrow(),
        )
    }

    @Test
    fun refreshOfTheLastRowLeavesTheEmptyState() = runTest(dispatcher) {
        repository.completed += completedSession("only")
        val model = viewModel()
        advanceUntilIdle()

        repository.deleteSession("only")
        model.onEvent(HistoryEvent.Refresh)
        advanceUntilIdle()

        assertEquals(HistoryUiState.Empty, model.uiState.value)
    }

    @Test
    fun aFailedRefreshKeepsTheRowsThatWereShown() = runTest(dispatcher) {
        seed(2)
        val model = viewModel()
        advanceUntilIdle()

        repository.listFailure = AppError.Network
        model.onEvent(HistoryEvent.Refresh)
        advanceUntilIdle()

        assertEquals(2, model.content().rows.size)
    }

    @Test
    fun aFailedRefreshThatCancelledLoadMoreLeavesLoadMoreWorkingAgain() = runTest(dispatcher) {
        seed(25)
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(HistoryEvent.LoadMore)
        assertTrue(model.content().loadingMore)

        repository.listFailure = AppError.Network
        model.onEvent(HistoryEvent.Refresh)
        advanceUntilIdle()
        assertEquals(false, model.content().loadingMore)
        assertEquals(20, model.content().rows.size)

        repository.listFailure = null
        model.onEvent(HistoryEvent.LoadMore)
        advanceUntilIdle()

        assertEquals(25, model.content().rows.size)
    }

    @Test
    fun openingASessionEmitsTheOpenDetailEffect() = runTest(dispatcher) {
        seed(1)
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(HistoryEvent.OpenSession("s00"))
            assertEquals(HistoryEffect.OpenDetail("s00"), awaitItem())
        }
    }
}
