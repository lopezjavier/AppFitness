package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ReportKind
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

        val items = assertIs<ReportsUiState.Content>(model.uiState.value).items
        assertEquals(listOf("older", "newer", "same-day"), items.map { it.id })
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

        val items = assertIs<ReportsUiState.Content>(model.uiState.value).items
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
