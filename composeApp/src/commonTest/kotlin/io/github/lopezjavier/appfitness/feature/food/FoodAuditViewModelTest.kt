package io.github.lopezjavier.appfitness.feature.food

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.FoodAuditRow
import io.github.lopezjavier.appfitness.domain.usecase.EnergyCheckLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

private fun auditRow(id: String, level: EnergyCheckLevel = EnergyCheckLevel.Mismatch, gap: Double = 40.0) =
    FoodAuditRow(id, "Food $id", null, kcal = 100.0, computedKcal = 100.0 + gap, gap = gap, level = level)

class FoodAuditViewModelTest {
    private val foods = FakeFoodRepository()
    private val connectivity = FakeConnectivityObserver()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.audit(): FoodAuditViewModel {
        val vm = FoodAuditViewModel(foods, connectivity)
        advanceUntilIdle()
        return vm
    }

    private fun assertNothingWritten() {
        assertTrue(foods.updates.isEmpty(), "updateFood was called")
        assertTrue(foods.created.isEmpty(), "createFood was called")
    }

    @Test
    fun loadsTheRowsInTheRepositoryOrderWithoutResorting() = runTest {
        val rows = listOf(
            auditRow("b", EnergyCheckLevel.ClearlyWrong, gap = -300.0),
            auditRow("a", EnergyCheckLevel.Mismatch, gap = 500.0),
            auditRow("c", EnergyCheckLevel.Mismatch, gap = 20.0),
        )
        foods.auditResult = Result.success(rows)

        val vm = audit()

        assertEquals(FoodAuditUiState.Content(rows), vm.uiState.value)
        assertNothingWritten()
    }

    @Test
    fun startsWithTheSkeletonUntilTheFirstLoadFinishes() = runTest {
        foods.auditGate = CompletableDeferred()
        val vm = FoodAuditViewModel(foods, connectivity)
        advanceUntilIdle()

        assertEquals(FoodAuditUiState.Loading, vm.uiState.value)
    }

    @Test
    fun noFoodsToReview_isEmpty() = runTest {
        assertEquals(FoodAuditUiState.Empty, audit().uiState.value)
    }

    @Test
    fun networkFailure_isOffline() = runTest {
        foods.auditResult = Result.failure(AppException(AppError.Network))

        assertEquals(FoodAuditUiState.Offline, audit().uiState.value)
    }

    @Test
    fun otherFailure_isError_andRetryShowsTheSkeletonThenTheRows() = runTest {
        foods.auditResult = Result.failure(AppException(AppError.Unknown))
        val vm = audit()
        assertEquals(FoodAuditUiState.Error, vm.uiState.value)
        foods.auditResult = Result.success(listOf(auditRow("a")))

        vm.uiState.test {
            assertEquals(FoodAuditUiState.Error, awaitItem())
            vm.onEvent(FoodAuditEvent.Retry)
            assertEquals(FoodAuditUiState.Loading, awaitItem())
            assertEquals(FoodAuditUiState.Content(listOf(auditRow("a"))), awaitItem())
        }
        assertNothingWritten()
    }

    @Test
    fun refreshAfterAnEdit_dropsTheFixedFoodWithoutEverShowingTheSkeleton() = runTest {
        foods.auditResult = Result.success(listOf(auditRow("a"), auditRow("b")))
        val vm = audit()
        foods.auditResult = Result.success(listOf(auditRow("b")))

        vm.uiState.test {
            assertEquals(FoodAuditUiState.Content(listOf(auditRow("a"), auditRow("b"))), awaitItem())
            vm.onEvent(FoodAuditEvent.Refresh)
            assertEquals(FoodAuditUiState.Content(listOf(auditRow("b"))), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refreshThatEmptiesTheList_showsTheEmptyState() = runTest {
        foods.auditResult = Result.success(listOf(auditRow("a")))
        val vm = audit()
        foods.auditResult = Result.success(emptyList())

        vm.onEvent(FoodAuditEvent.Refresh)
        advanceUntilIdle()

        assertEquals(FoodAuditUiState.Empty, vm.uiState.value)
    }

    @Test
    fun refreshThatFails_keepsThePreviousRows() = runTest {
        val rows = listOf(auditRow("a"))
        foods.auditResult = Result.success(rows)
        val vm = audit()
        foods.auditResult = Result.failure(AppException(AppError.Network))

        vm.onEvent(FoodAuditEvent.Refresh)
        advanceUntilIdle()

        assertEquals(FoodAuditUiState.Content(rows), vm.uiState.value)
    }

    @Test
    fun aLoadInFlight_absorbsRetryAndRefresh() = runTest {
        foods.auditGate = CompletableDeferred()
        val vm = FoodAuditViewModel(foods, connectivity)
        advanceUntilIdle()

        vm.onEvent(FoodAuditEvent.Refresh)
        vm.onEvent(FoodAuditEvent.Retry)
        foods.auditGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, foods.auditCalls)
    }

    @Test
    fun openFood_emitsOpenEditorOnce() = runTest {
        foods.auditResult = Result.success(listOf(auditRow("a")))
        val vm = audit()

        vm.effects.test {
            vm.onEvent(FoodAuditEvent.OpenFood("a"))
            assertEquals(FoodAuditEffect.OpenEditor("a"), awaitItem())
            expectNoEvents()
        }
        assertNothingWritten()
        assertEquals(1, foods.auditCalls)
    }

    @Test
    fun reconnecting_whileOffline_reloadsTheList() = runTest {
        foods.auditResult = Result.failure(AppException(AppError.Network))
        connectivity.state.value = false
        val vm = audit()
        assertEquals(FoodAuditUiState.Offline, vm.uiState.value)
        foods.auditResult = Result.success(listOf(auditRow("a")))

        connectivity.state.value = true
        advanceUntilIdle()

        assertIs<FoodAuditUiState.Content>(vm.uiState.value)
    }

    @Test
    fun aRecreatedViewModel_readsTheSameRowsAgain() = runTest {
        val rows = listOf(auditRow("a", EnergyCheckLevel.ClearlyWrong), auditRow("b"))
        foods.auditResult = Result.success(rows)
        audit()

        val recreated = audit()

        assertEquals(FoodAuditUiState.Content(rows), recreated.uiState.value)
        assertEquals(2, foods.auditCalls)
        assertNothingWritten()
    }
}
