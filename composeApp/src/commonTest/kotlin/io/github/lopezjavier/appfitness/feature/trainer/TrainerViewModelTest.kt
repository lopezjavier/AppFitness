package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.ActivitySources
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.DerivedActivityLevel
import io.github.lopezjavier.appfitness.domain.model.DerivedExperienceValue
import io.github.lopezjavier.appfitness.domain.model.DerivedIntake
import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import io.github.lopezjavier.appfitness.domain.model.DerivedTotalExpenditure
import io.github.lopezjavier.appfitness.domain.model.DerivedWeightTrend
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TODAY = LocalDate(2026, 10, 19)

internal fun coachValues(
    provisional: Boolean = true,
    reask: Boolean = false,
    level: ActivityLevel = ActivityLevel.Light,
    edited: Boolean = false,
) = CoachDerivedValues(
    asOf = TODAY,
    activityLevel = DerivedActivityLevel(
        value = level,
        estimate = null,
        factor = 1.375,
        source = DerivedSource.Profile,
        provisional = provisional,
        confirmed = !provisional,
        edited = edited,
        reask = reask,
        uncertainty = null,
        sources = ActivitySources(null, 0, null, 0, null),
        missing = emptyList(),
    ),
    totalExpenditure = DerivedTotalExpenditure(
        2400.0, DerivedSource.Formula, false, false, provisional, !provisional, reask, null, emptyList(),
    ),
    weightTrend = DerivedWeightTrend(-0.4, -0.6, -0.2, 6, false, provisional, !provisional, reask, null, emptyList()),
    intake = DerivedIntake(12, 28, 0.43, true),
    experience = DerivedExperienceValue(null, 0, 0, provisional, !provisional, reask, null, emptyList()),
)

internal class FakeCoachRepository(var loaded: Result<CoachDerivedValues> = Result.success(coachValues())) :
    CoachRepository {
    val confirmCalls = mutableListOf<Pair<LocalDate, ActivityLevel?>>()
    var loadCalls = 0
    var confirmResult: (ActivityLevel?) -> Result<CoachDerivedValues> = { level ->
        Result.success(coachValues(provisional = false, level = level ?: ActivityLevel.Light, edited = level != null))
    }
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun derivedValues(today: LocalDate): Result<CoachDerivedValues> {
        loadCalls++
        return loaded
    }

    override suspend fun confirm(today: LocalDate, activityLevel: ActivityLevel?): Result<CoachDerivedValues> {
        confirmCalls += today to activityLevel
        gate?.await()
        return confirmResult(activityLevel)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrainerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCoachRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        TrainerViewModel(repository, MutableClock(), saved)

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    @Test
    fun startsLoadingThenShowsContent() = runTest(dispatcher) {
        val model = viewModel()
        assertEquals(TrainerUiState.Loading, model.uiState.value)

        advanceUntilIdle()

        assertEquals(coachValues(), model.content().values)
        assertFalse(model.content().allConfirmed)
    }

    @Test
    fun loadFailure_isError_andRetryRecovers() = runTest(dispatcher) {
        repository.loaded = Result.failure(AppException(AppError.Unknown))
        val model = viewModel()
        advanceUntilIdle()
        assertEquals(TrainerUiState.Error, model.uiState.value)

        repository.loaded = Result.success(coachValues())
        model.onEvent(TrainerEvent.Retry)
        advanceUntilIdle()

        assertIs<TrainerUiState.Content>(model.uiState.value)
    }

    @Test
    fun networkFailure_isOffline() = runTest(dispatcher) {
        repository.loaded = Result.failure(AppException(AppError.Network))
        val model = viewModel()

        advanceUntilIdle()

        assertEquals(TrainerUiState.Offline, model.uiState.value)
    }

    @Test
    fun confirmWithoutEdit_passesNullAndTodayAndReplacesTheState() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.Confirm)
        advanceUntilIdle()

        assertEquals(listOf<Pair<LocalDate, ActivityLevel?>>(TODAY to null), repository.confirmCalls)
        assertTrue(model.content().allConfirmed)
        assertFalse(model.content().confirming)
    }

    @Test
    fun confirmEmitsConfirmedEffect() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.Confirm)
            advanceUntilIdle()
            assertEquals(TrainerEffect.Confirmed, awaitItem())
        }
    }

    @Test
    fun pickLevel_commitsAtOnce_closesTheSheetAndShowsEdited() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.OpenEdit)
        assertEquals(ActivityLevel.Light, model.content().editSheet)
        model.onEvent(TrainerEvent.PickLevel(ActivityLevel.Moderate))
        advanceUntilIdle()

        assertEquals(listOf<Pair<LocalDate, ActivityLevel?>>(TODAY to ActivityLevel.Moderate), repository.confirmCalls)
        assertNull(model.content().editSheet)
        assertEquals(ActivityLevel.Moderate, model.content().values.activityLevel.value)
        assertTrue(model.content().values.activityLevel.edited)
    }

    @Test
    fun confirmFailure_keepsStateAndEmitsFailure() = runTest(dispatcher) {
        repository.confirmResult = { _ -> Result.failure<CoachDerivedValues>(AppException(AppError.Unknown)) }
        val model = viewModel()
        advanceUntilIdle()
        val before = model.content().values

        model.effects.test {
            model.onEvent(TrainerEvent.Confirm)
            advanceUntilIdle()
            assertEquals(TrainerEffect.ConfirmFailed(fromSheet = false, offline = false), awaitItem())
        }

        assertEquals(before, model.content().values)
        assertFalse(model.content().confirming)
    }

    @Test
    fun offlineConfirmFailure_flagsOffline_andKeepsTheSheetOpen() = runTest(dispatcher) {
        repository.confirmResult = { _ -> Result.failure<CoachDerivedValues>(AppException(AppError.Network)) }
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.effects.test {
            model.onEvent(TrainerEvent.PickLevel(ActivityLevel.High))
            advanceUntilIdle()
            assertEquals(TrainerEffect.ConfirmFailed(fromSheet = true, offline = true), awaitItem())
        }

        assertEquals(ActivityLevel.Light, model.content().editSheet)
        assertFalse(model.content().confirming)
    }

    @Test
    fun pickLevel_emitsConfirmed() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.effects.test {
            model.onEvent(TrainerEvent.PickLevel(ActivityLevel.High))
            advanceUntilIdle()
            assertEquals(TrainerEffect.Confirmed, awaitItem())
        }
    }

    @Test
    fun pickLevel_ofTheAlreadySelectedLevel_stillConfirms() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.onEvent(TrainerEvent.PickLevel(ActivityLevel.Light))
        advanceUntilIdle()

        assertEquals(listOf<Pair<LocalDate, ActivityLevel?>>(TODAY to ActivityLevel.Light), repository.confirmCalls)
    }

    @Test
    fun secondPickWhileConfirming_isIgnored() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.onEvent(TrainerEvent.PickLevel(ActivityLevel.High))
        advanceUntilIdle()
        model.onEvent(TrainerEvent.PickLevel(ActivityLevel.VeryHigh))
        repository.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf<Pair<LocalDate, ActivityLevel?>>(TODAY to ActivityLevel.High), repository.confirmCalls)
    }

    @Test
    fun doubleTapWhileConfirming_isIgnored() = runTest(dispatcher) {
        repository.gate = CompletableDeferred()
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.Confirm)
        advanceUntilIdle()
        assertTrue(model.content().confirming)
        model.onEvent(TrainerEvent.Confirm)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.confirmCalls.size)
    }

    @Test
    fun reaskedValues_stayProvisionalAfterReload() = runTest(dispatcher) {
        repository.loaded = Result.success(coachValues(provisional = true, reask = true))
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.content().values.anyReask)
        assertFalse(model.content().allConfirmed)
    }

    @Test
    fun allConfirmed_isTrueOnlyWhenNothingIsProvisional() = runTest(dispatcher) {
        repository.loaded = Result.success(coachValues(provisional = false))
        val model = viewModel()
        advanceUntilIdle()

        assertTrue(model.content().allConfirmed)
    }

    @Test
    fun closeEdit_closesTheSheetWithoutConfirming() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val model = viewModel(saved)
        advanceUntilIdle()
        model.onEvent(TrainerEvent.OpenEdit)

        model.onEvent(TrainerEvent.CloseEdit)

        assertNull(model.content().editSheet)
        assertTrue(repository.confirmCalls.isEmpty())
        assertNull(viewModel(saved).also { advanceUntilIdle() }.content().editSheet)
    }

    @Test
    fun recreatedFromTheSameSavedState_keepsTheOpenSheet() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenEdit)

        val second = viewModel(saved)
        advanceUntilIdle()

        assertEquals(ActivityLevel.Light, second.content().editSheet)
    }

    @Test
    fun recreatedAfterASuccessfulPick_startsWithTheSheetClosed() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.OpenEdit)
        first.onEvent(TrainerEvent.PickLevel(ActivityLevel.High))
        advanceUntilIdle()

        val second = viewModel(saved)
        advanceUntilIdle()

        assertNull(second.content().editSheet)
    }

    @Test
    fun pickLevelWithoutOpenSheet_isIgnored() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.PickLevel(ActivityLevel.High))
        advanceUntilIdle()

        assertTrue(repository.confirmCalls.isEmpty())
        assertNull(model.content().editSheet)
    }
}
