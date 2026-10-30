package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import io.github.lopezjavier.appfitness.domain.model.DevOptions
import io.github.lopezjavier.appfitness.domain.repository.CoachPingRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachUsageRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
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

private class FakeCoachUsageRepository(var result: Result<CoachUsage>) : CoachUsageRepository {
    val calls = mutableListOf<LocalDate>()

    override suspend fun currentMonth(today: LocalDate): Result<CoachUsage> {
        calls += today
        return result
    }
}

private class FakeCoachPingRepository(var result: Result<Unit> = Result.success(Unit)) : CoachPingRepository {
    var calls = 0

    override suspend fun ping(): Result<Unit> {
        calls++
        return result
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CoachUsageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val usage = CoachUsage(LocalDate(2026, 11, 1), 1200, 300, 0.42)
    private val repository = FakeCoachUsageRepository(Result.success(usage))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val pingRepository = FakeCoachPingRepository()

    private fun viewModel(devOptions: DevOptions = DevOptions(showCoachPing = true)) =
        CoachUsageViewModel(repository, MutableClock(), pingRepository, devOptions)

    @Test
    fun startsLoadingThenShowsTheUsage() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(CoachUsageUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        assertEquals(CoachUsageUiState.Content(usage), model.uiState.value)
    }

    @Test
    fun zeroUsage_isContentNotEmptyState() = runTest(dispatcher) {
        val empty = CoachUsage.empty(LocalDate(2026, 11, 1))
        repository.result = Result.success(empty)
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(CoachUsageUiState.Content(empty), model.uiState.value)
    }

    @Test
    fun loadFailure_isError_andRetryRecovers() = runTest(dispatcher) {
        repository.result = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(CoachUsageUiState.Error, model.uiState.value)

        repository.result = Result.success(usage)
        model.onEvent(CoachUsageEvent.Retry)
        assertEquals(CoachUsageUiState.Loading, model.uiState.value)
        advanceUntilIdle()

        assertIs<CoachUsageUiState.Content>(model.uiState.value)
    }

    @Test
    fun networkFailure_isOffline() = runTest(dispatcher) {
        repository.result = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(CoachUsageUiState.Offline, model.uiState.value)
    }

    @Test
    fun loadsOnceOnCreation() = runTest(dispatcher) {
        viewModel()

        advanceUntilIdle()

        assertEquals(1, repository.calls.size)
    }

    @Test
    fun pingStartsIdle_andTheFlagComesFromDevOptions() = runTest(dispatcher) {
        assertEquals(CoachPingUiState.Idle, viewModel().pingState.value)
        assertEquals(true, viewModel().showPing)
        assertEquals(false, viewModel(DevOptions(showCoachPing = false)).showPing)
    }

    @Test
    fun ping_runsThenSucceeds_andReloadsTheUsageWithoutLoadingFlash() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        val updated = CoachUsage(LocalDate(2026, 11, 1), 1300, 320, 0.43)
        repository.result = Result.success(updated)

        model.onEvent(CoachUsageEvent.Ping)
        assertEquals(CoachPingUiState.Running, model.pingState.value)
        assertEquals(CoachUsageUiState.Content(usage), model.uiState.value)
        advanceUntilIdle()

        assertEquals(CoachPingUiState.Success, model.pingState.value)
        assertEquals(1, pingRepository.calls)
        assertEquals(CoachUsageUiState.Content(updated), model.uiState.value)
    }

    @Test
    fun ping_functionRefusal_isFailedWithTheReason_andRetryCanSucceed() = runTest(dispatcher) {
        val limit = CoachFailure.LimitReached(CoachFailure.Scope.User)
        pingRepository.result = Result.failure(CoachException(limit))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(CoachUsageEvent.Ping)
        advanceUntilIdle()
        assertEquals(CoachPingUiState.Failed(limit), model.pingState.value)

        pingRepository.result = Result.success(Unit)
        model.onEvent(CoachUsageEvent.Ping)
        advanceUntilIdle()
        assertEquals(CoachPingUiState.Success, model.pingState.value)
    }

    @Test
    fun ping_offline_isOffline() = runTest(dispatcher) {
        pingRepository.result = Result.failure(AppException(AppError.Network))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(CoachUsageEvent.Ping)
        advanceUntilIdle()

        assertEquals(CoachPingUiState.Offline, model.pingState.value)
    }

    @Test
    fun ping_otherError_isFailedOther() = runTest(dispatcher) {
        pingRepository.result = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(CoachUsageEvent.Ping)
        advanceUntilIdle()

        assertEquals(CoachPingUiState.Failed(CoachFailure.Other), model.pingState.value)
    }

    @Test
    fun ping_whileRunning_orWhenDisabled_doesNotCallAgain() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(CoachUsageEvent.Ping)
        model.onEvent(CoachUsageEvent.Ping)
        advanceUntilIdle()
        assertEquals(1, pingRepository.calls)

        val release = viewModel(DevOptions(showCoachPing = false))
        advanceUntilIdle()
        release.onEvent(CoachUsageEvent.Ping)
        advanceUntilIdle()
        assertEquals(1, pingRepository.calls)
        assertEquals(CoachPingUiState.Idle, release.pingState.value)
    }
}
