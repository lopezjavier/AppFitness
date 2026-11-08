package io.github.lopezjavier.appfitness.feature.trainer

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.ReminderClock
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.usecase.LoadBlockReviewUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadWelcomeBack
import io.github.lopezjavier.appfitness.feature.workout.FakeWorkoutSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** T-243 (US-107): the "Bienvenido de nuevo" card of the Trainer tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerWelcomeBackViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plans = FakeCoachPlanRepository()
    private val blocks = FakeCoachBlockRepository()
    private val sessions = FakeWorkoutSessionRepository()
    private val reviews = FakeCoachReviewRepository()
    private val today = LocalDate(2026, 11, 20)
    private val blockStart = LocalDate(2026, 10, 19)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): TrainerViewModel {
        val clock = ReminderClock(today)
        return TrainerViewModel(
            FakeCoachRepository(),
            clock,
            plans,
            blocks,
            loadBlockReview = LoadBlockReviewUseCase(reviews),
            loadWelcomeBack = LoadWelcomeBack(sessions, clock, TimeZone.UTC),
        )
    }

    private fun TrainerViewModel.content() = assertIs<TrainerUiState.Content>(uiState.value)

    private fun session(day: LocalDate, id: String = "s-${day.toEpochDays()}") = WorkoutSession(
        id = id,
        routineId = null,
        name = "Push",
        status = SessionStatus.Completed,
        startedAt = day.atStartOfDayIn(TimeZone.UTC),
        endedAt = day.atStartOfDayIn(TimeZone.UTC),
        exercises = emptyList(),
    )

    private fun daysAgo(n: Int) = LocalDate.fromEpochDays(today.toEpochDays() - n)

    private fun openSavedBlock(acceptedOn: LocalDate? = null) {
        val plan = coachPlan(status = PlanStatus.Accepted)
        plans.current = Result.success(
            plan.copy(
                blockStart = blockStart,
                acceptedAt = acceptedOn?.atStartOfDayIn(TimeZone.UTC),
            ),
        )
        blocks.current = Result.success(coachBlock(saved = true))
    }

    @Test
    fun fourteenDaysAfterTheLastSession_theCardShows() = runTest(dispatcher) {
        openSavedBlock()
        sessions.completed += session(daysAgo(14))
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().welcomeBack)
    }

    @Test
    fun sixDaysAfterTheLastSession_noCard() = runTest(dispatcher) {
        openSavedBlock()
        sessions.completed += session(daysAgo(6))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().welcomeBack)
    }

    @Test
    fun withoutCompletedSessions_noCard() = runTest(dispatcher) {
        openSavedBlock()
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().welcomeBack)
    }

    @Test
    fun aProposedPlanOnly_noCard() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Proposed))
        blocks.current = Result.success(coachBlock(saved = true))
        sessions.completed += session(daysAgo(20))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().welcomeBack)
    }

    @Test
    fun withoutAPlan_noCard() = runTest(dispatcher) {
        sessions.completed += session(daysAgo(20))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().welcomeBack)
    }

    @Test
    fun anAcceptedPlanWithoutASavedBlock_noCard() = runTest(dispatcher) {
        plans.current = Result.success(coachPlan(status = PlanStatus.Accepted))
        blocks.current = Result.success(coachBlock(saved = false))
        sessions.completed += session(daysAgo(20))
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().welcomeBack)
    }

    @Test
    fun aFinishedSession_removesTheCard() = runTest(dispatcher) {
        openSavedBlock()
        sessions.completed += session(daysAgo(20))
        val model = viewModel()
        advanceUntilIdle()
        assertTrue(model.content().welcomeBack)

        sessions.completed += session(today)
        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertFalse(model.content().welcomeBack)
    }

    @Test
    fun aBlockSavedAfterTheLastSession_removesTheCard() = runTest(dispatcher) {
        openSavedBlock(acceptedOn = daysAgo(20))
        sessions.completed += session(daysAgo(20))
        val model = viewModel()
        advanceUntilIdle()
        assertTrue(model.content().welcomeBack)

        openSavedBlock(acceptedOn = daysAgo(1))
        model.onEvent(TrainerEvent.Refresh)
        advanceUntilIdle()

        assertFalse(model.content().welcomeBack)
    }

    @Test
    fun aFailedSessionRead_hidesTheCardWithoutBreakingTheTab() = runTest(dispatcher) {
        openSavedBlock()
        sessions.completed += session(daysAgo(20))
        sessions.listFailure = AppError.Network
        val model = viewModel()

        advanceUntilIdle()

        assertFalse(model.content().welcomeBack)
        assertTrue(model.content().canStartNewBlock)
    }

    @Test
    fun withoutTheUseCase_noCard() = runTest(dispatcher) {
        openSavedBlock()
        sessions.completed += session(daysAgo(20))
        val model = TrainerViewModel(FakeCoachRepository(), ReminderClock(today), plans, blocks)

        advanceUntilIdle()

        assertFalse(model.content().welcomeBack)
    }

    @Test
    fun whileTheReviewIsDue_theCardStaysBecauseNewBlockIsAlreadyOffered() = runTest(dispatcher) {
        openSavedBlock()
        sessions.completed += session(daysAgo(20))
        reviews.review = Result.success(blockReview())
        val model = viewModel()

        advanceUntilIdle()

        assertTrue(model.content().blockReviewDue)
        assertTrue(model.content().welcomeBack)
        assertTrue(model.content().canStartNewBlock)
    }

    @Test
    fun openNewBlockFromTheCard_asksToOpenTheNewBlockFlow() = runTest(dispatcher) {
        openSavedBlock()
        sessions.completed += session(daysAgo(20))
        val model = viewModel()
        advanceUntilIdle()

        model.effects.test {
            model.onEvent(TrainerEvent.OpenNewBlock)
            advanceUntilIdle()

            assertEquals(TrainerEffect.OpenNewBlock("plan-1"), awaitItem())
        }
    }
}
