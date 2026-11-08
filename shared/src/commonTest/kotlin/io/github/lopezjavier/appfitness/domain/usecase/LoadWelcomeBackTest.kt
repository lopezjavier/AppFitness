package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.data.repository.UnconfiguredWorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PlanNutrition
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class LoadWelcomeBackTest {
    private val today = LocalDate(2026, 11, 20)

    private class FixedClock(private val day: LocalDate) : Clock {
        override fun today() = day

        override fun now(): Instant = day.atStartOfDayIn(TimeZone.UTC)
    }

    private fun session(startedAt: Instant) = WorkoutSession(
        id = "s",
        routineId = null,
        name = "Push",
        status = SessionStatus.Completed,
        startedAt = startedAt,
        endedAt = startedAt,
        exercises = emptyList(),
    )

    private fun repo(result: Result<List<WorkoutSession>>, calls: MutableList<Pair<Int, Int>> = mutableListOf()) =
        object : WorkoutSessionRepository by UnconfiguredWorkoutSessionRepository() {
            override suspend fun listCompleted(offset: Int, limit: Int): Result<List<WorkoutSession>> {
                calls += offset to limit
                return result
            }
        }

    private fun plan(acceptedAt: Instant? = null, blockStart: LocalDate = LocalDate(2026, 9, 1)) = CoachPlan(
        id = "p1",
        blockStart = blockStart,
        blockWeeks = 4,
        intention = "muscle",
        status = PlanStatus.Accepted,
        nutrition = PlanNutrition(
            targetKcal = 2500, floorKcal = null, limitedBy = null, expenditureKcal = null, ratePctWeek = 0.0,
            rateKgWeek = 0.0, strategy = GoalStrategy.Maintenance, proteinG = 150, carbsG = 300, fatG = 70,
            proteinPct = 25, carbsPct = 50, fatPct = 25, distribution = null, editedByUser = false,
            phase = Phase.Maintenance,
        ),
        outcomes = emptyList(),
        acceptedAt = acceptedAt,
    )

    private fun load(zone: TimeZone, result: Result<List<WorkoutSession>>) =
        LoadWelcomeBack(repo(result), FixedClock(today), zone)

    @Test
    fun readsOnlyTheNewestCompletedSession() = runTest {
        val calls = mutableListOf<Pair<Int, Int>>()
        LoadWelcomeBack(repo(Result.success(emptyList()), calls), FixedClock(today), TimeZone.UTC)(plan())

        assertEquals(listOf(0 to 1), calls)
    }

    @Test
    fun sessionAtLateUtc_countsOnTheLocalDay() = runTest {
        // 23:30 UTC on 6 Nov is 7 Nov at +02:00: 13 days before 20 Nov (no card), while UTC says 14 days.
        val at = LocalDate(2026, 11, 6).atTime(23, 30).toInstant(TimeZone.UTC)

        assertTrue(load(TimeZone.UTC, Result.success(listOf(session(at))))(plan()).getOrThrow())
        assertFalse(load(TimeZone.of("+02:00"), Result.success(listOf(session(at))))(plan()).getOrThrow())
    }

    @Test
    fun noSessions_isFalse() = runTest {
        assertFalse(load(TimeZone.UTC, Result.success(emptyList()))(plan()).getOrThrow())
    }

    @Test
    fun blockAcceptedAfterTheSession_isFalse() = runTest {
        val old = LocalDate(2026, 10, 1).atStartOfDayIn(TimeZone.UTC)
        val accepted = LocalDate(2026, 10, 2).atStartOfDayIn(TimeZone.UTC)

        assertFalse(load(TimeZone.UTC, Result.success(listOf(session(old))))(plan(acceptedAt = accepted)).getOrThrow())
    }

    @Test
    fun withoutAcceptedAt_theBlockStartIsUsed() = runTest {
        val old = LocalDate(2026, 10, 1).atStartOfDayIn(TimeZone.UTC)

        val after = plan(blockStart = LocalDate(2026, 10, 2))
        assertFalse(load(TimeZone.UTC, Result.success(listOf(session(old))))(after).getOrThrow())
        assertTrue(load(TimeZone.UTC, Result.success(listOf(session(old))))(plan()).getOrThrow())
    }

    @Test
    fun aFailedRead_isAFailure() = runTest {
        val result = load(TimeZone.UTC, Result.failure(AppException(AppError.Network)))(plan())

        assertTrue(result.isFailure)
    }
}
