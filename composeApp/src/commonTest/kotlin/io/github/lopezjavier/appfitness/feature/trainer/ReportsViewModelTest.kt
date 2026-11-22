package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ReportKind
import io.github.lopezjavier.appfitness.domain.model.ReportTeaser
import io.github.lopezjavier.appfitness.domain.usecase.LoadReports
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val reports = FakeReportRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ReportsViewModel(LoadReports(reports))

    @Test
    fun load_showsTheItemsInTheOrderTheServerReturned() = runTest(dispatcher) {
        // Deliberately not sorted by date: the order of the server is kept.
        val served = listOf(
            reportItem("older", createdAt = Instant.parse("2026-11-03T08:00:00Z")),
            reportItem("newer", ReportKind.BlockReview, week = null),
            reportItem("same-day"),
        )
        reports.items = Result.success(served)
        val model = viewModel()
        assertEquals(ReportsUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        val groups = assertIs<ReportsUiState.Content>(model.uiState.value).groups
        // One plan: the block review moves first, the weeks keep the served order.
        assertEquals(listOf("newer", "older", "same-day"), groups.single().rows.map { it.id })
    }

    @Test
    fun load_groupsByBlockNewestFirstNumberedFromTheOldestWithTeasersKept() = runTest(dispatcher) {
        val teaser = ReportTeaser(3, null, -0.3)
        reports.items = Result.success(
            listOf(
                reportItem("n2", planId = "new", teaser = teaser),
                reportItem("o2", planId = "old"),
                reportItem("o-review", ReportKind.BlockReview, week = null, planId = "old"),
            ),
        )
        val model = viewModel()

        advanceUntilIdle()

        val groups = assertIs<ReportsUiState.Content>(model.uiState.value).groups
        assertEquals(listOf("new", "old"), groups.map { it.planId })
        assertEquals(listOf(2, 1), groups.map { it.number })
        assertEquals(listOf("o-review", "o2"), groups[1].rows.map { it.id })
        assertEquals(teaser, groups[0].rows.single().teaser)
    }

    @Test
    fun noReports_isEmpty() = runTest(dispatcher) {
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(ReportsUiState.Empty, model.uiState.value)
    }

    @Test
    fun pausedWeek_isFlaggedInTheItems() = runTest(dispatcher) {
        reports.items = Result.success(listOf(reportItem("w1", week = null, paused = true), reportItem("w2")))
        val model = viewModel()

        advanceUntilIdle()

        val items = assertIs<ReportsUiState.Content>(model.uiState.value).groups.single().rows
        assertEquals(listOf(true, false), items.map { it.paused })
    }

    @Test
    fun networkFailure_isOffline() = runTest(dispatcher) {
        reports.items = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(ReportsUiState.Offline, model.uiState.value)
    }

    @Test
    fun otherFailure_isError() = runTest(dispatcher) {
        reports.items = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(ReportsUiState.Error, model.uiState.value)
    }

    @Test
    fun signedOutFailure_isError() = runTest(dispatcher) {
        reports.items = Result.failure(AppException(AppError.Unauthorized))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(ReportsUiState.Error, model.uiState.value)
    }

    @Test
    fun retry_loadsAgainAfterAFailure() = runTest(dispatcher) {
        reports.items = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()
        reports.items = Result.success(listOf(reportItem("w1")))

        model.onEvent(ReportsEvent.Retry)
        assertEquals(ReportsUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        assertTrue(model.uiState.value is ReportsUiState.Content)
        assertEquals(2, reports.listCalls)
    }
}
