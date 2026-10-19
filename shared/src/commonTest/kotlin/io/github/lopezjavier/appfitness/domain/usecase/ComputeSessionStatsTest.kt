package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ComputeSessionStatsTest {
    private val start = Instant.parse("2026-10-19T10:00:00Z")
    private val end = Instant.parse("2026-10-19T10:52:30Z")

    private fun set(index: Int, weight: Double, reps: Int, done: Boolean = true, type: SetType = SetType.Working) =
        WorkoutSet(index, type, SetEntry.WeightReps(weight, reps), if (done) start else null)

    private fun session(vararg exercises: SessionExercise, endedAt: Instant? = end) =
        WorkoutSession("s1", null, "Torso", SessionStatus.Completed, start, endedAt, exercises.toList())

    private val bench = SessionExercise(
        "e1",
        0,
        null,
        listOf(set(1, 60.0, 12), set(2, 87.5, 8), set(3, 87.5, 8)),
    )

    @Test
    fun volumeIsTheSumOfWeightTimesRepsOfCompletedSets() {
        assertEquals(2120.0, ComputeSessionStats(session(bench)).volumeKg)
    }

    @Test
    fun warmUpAndPendingSetsAreExcludedFromVolume() {
        val exercise = SessionExercise(
            "e1",
            0,
            null,
            listOf(set(1, 20.0, 10, type = SetType.WarmUp), set(2, 100.0, 5), set(3, 100.0, 5, done = false)),
        )

        val stats = ComputeSessionStats(session(exercise))

        assertEquals(500.0, stats.volumeKg)
    }

    @Test
    fun completedSetsCountsEveryCompletedSetIncludingWarmUps() {
        val exercise = SessionExercise(
            "e1",
            0,
            null,
            listOf(set(1, 20.0, 10, type = SetType.WarmUp), set(2, 100.0, 5), set(3, 100.0, 5, done = false)),
        )

        assertEquals(2, ComputeSessionStats(session(exercise)).completedSets)
    }

    @Test
    fun durationIsTheTimeBetweenStartAndEnd() {
        assertEquals(3150L, ComputeSessionStats(session(bench)).durationSeconds)
    }

    @Test
    fun durationIsZeroWhenTheSessionHasNoEnd() {
        assertEquals(0L, ComputeSessionStats(session(bench, endedAt = null)).durationSeconds)
    }

    @Test
    fun correctingASetRecalculatesTheVolume() {
        val corrected = SessionExercise("e1", 0, null, listOf(set(1, 60.0, 12), set(2, 87.5, 8), set(3, 87.5, 7)))

        val before = ComputeSessionStats(session(bench)).volumeKg
        val after = ComputeSessionStats(session(corrected)).volumeKg

        assertEquals(87.5, before - after)
    }
}
