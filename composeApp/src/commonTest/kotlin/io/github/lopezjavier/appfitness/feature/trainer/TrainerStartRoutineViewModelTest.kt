package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.PlanWeekSession
import io.github.lopezjavier.appfitness.domain.model.WeekPhase
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import io.github.lopezjavier.appfitness.feature.workout.MutableClock
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** T-300 (US-136): the play button of the first pending routine of the current week starts a live workout. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerStartRoutineViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository().apply {
        current = Result.success(coachPlan(status = PlanStatus.Accepted))
    }
    private val blocks = FakeCoachBlockRepository().apply { current = Result.success(coachBlock(saved = true)) }
    private val weeks = FakeCoachWeeksRepository()
    private val sessions = FakeWorkoutSessionRepository()

    private fun session(index: Int, name: String, routineId: String?, done: Boolean) =
        PlanWeekSession(sessionIndex = index, name = name, routineId = routineId, done = done)

    // Week 2 is past with a pending session; week 3 is the current one: Torso A done, Pierna A pending (the next).
    private val pastWeek = planWeek(2, WeekPhase.Past).copy(sessions = listOf(session(0, "Torso A", "r-past", false)))
    private val currentWeek = planWeek(3).copy(
        sessions = listOf(
            session(0, "Torso A", "r-torso-a", done = true),
            session(1, "Pierna A", "r-pierna-a", done = false),
            session(2, "Torso B", "r-torso-b", done = false),
        ),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        weeks.weeksResult = Result.success(listOf<PlanWeek>(pastWeek, currentWeek))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        saved: SavedStateHandle = SavedStateHandle(),
        sessionsRepository: FakeWorkoutSessionRepository? = sessions,
    ) = TrainerViewModel(
        FakeCoachRepository(),
        MutableClock(),
        plans = plans,
        blocks = blocks,
        coachWeeks = weeks,
        savedState = saved,
        sessions = sessionsRepository,
    )

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    @Test
    fun startingTheNextPendingRoutine_startsItAndOpensTheLiveSession() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.StartRoutine("r-pierna-a"))
            advanceUntilIdle()

            assertEquals(TrainerEffect.OpenLiveSession, awaitItem())
            expectNoEvents()
        }
        assertEquals(listOf("r-pierna-a"), sessions.startedRoutines)
    }

    @Test
    fun aWorkoutAlreadyInProgress_isReportedAndNothingElseIsEmitted() = runTest(dispatcher) {
        sessions.startFailure = AppError.SessionAlreadyInProgress
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.StartRoutine("r-pierna-a"))
            advanceUntilIdle()

            assertEquals(TrainerEffect.SessionInProgress, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun anUnknownFailure_isReportedAsNotOffline() = runTest(dispatcher) {
        sessions.startFailure = AppError.Unknown
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.StartRoutine("r-pierna-a"))
            advanceUntilIdle()

            assertEquals(TrainerEffect.StartFailed(offline = false), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun aNetworkFailure_isReportedAsOffline() = runTest(dispatcher) {
        sessions.startFailure = AppError.Network
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.StartRoutine("r-pierna-a"))
            advanceUntilIdle()

            assertEquals(TrainerEffect.StartFailed(offline = true), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun aSecondTapWhileTheFirstIsInFlight_startsOnlyOnceAndTheFlagFollowsTheCall() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>().also { sessions.startGate = it }
        val model = viewModel()
        advanceUntilIdle()

        model.onEvent(TrainerEvent.StartRoutine("r-pierna-a"))
        advanceUntilIdle()
        assertTrue(model.content().startingRoutine)
        model.onEvent(TrainerEvent.StartRoutine("r-pierna-a"))
        advanceUntilIdle()
        assertEquals(listOf("r-pierna-a"), sessions.startedRoutines)

        model.effects.test {
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(TrainerEffect.OpenLiveSession, awaitItem())
        }
        assertFalse(model.content().startingRoutine)
        assertEquals(1, sessions.startedRoutines.size)
    }

    @Test
    fun aRoutineThatIsNotTheNextPendingOneOfTheCurrentWeek_isIgnored() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.StartRoutine("r-torso-a")) // Done.
            model.onEvent(TrainerEvent.StartRoutine("r-torso-b")) // Pending but not the next.
            model.onEvent(TrainerEvent.StartRoutine("r-past")) // Another week.
            model.onEvent(TrainerEvent.StartRoutine("r-unknown"))
            advanceUntilIdle()

            expectNoEvents()
        }
        assertEquals(emptyList(), sessions.startedRoutines)
        assertFalse(model.content().startingRoutine)
    }

    @Test
    fun withoutTheSessionRepository_theTapIsIgnored() = runTest(dispatcher) {
        val model = viewModel(sessionsRepository = null)
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.StartRoutine("r-pierna-a"))
            advanceUntilIdle()

            expectNoEvents()
        }
        assertFalse(model.content().startingRoutine)
    }

    @Test
    fun withoutContent_theTapIsIgnored() = runTest(dispatcher) {
        val model = viewModel() // Still loading: the dispatcher has not run the read yet.
        assertEquals(TrainerUiState.Loading, model.uiState.value)

        model.onEvent(TrainerEvent.StartRoutine("r-pierna-a"))
        advanceUntilIdle()

        assertEquals(emptyList(), sessions.startedRoutines)
    }

    @Test
    fun aViewModelRecreatedFromTheSameSavedState_isNotStartingAndShowsTheSameWeeks() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        sessions.startGate = CompletableDeferred()
        val first = viewModel(saved)
        advanceUntilIdle()
        first.onEvent(TrainerEvent.StartRoutine("r-pierna-a"))
        advanceUntilIdle()
        assertTrue(first.content().startingRoutine)

        val second = viewModel(saved)
        advanceUntilIdle()

        assertFalse(second.content().startingRoutine)
        assertEquals(first.content().weeks, second.content().weeks)
    }
}
