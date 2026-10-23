package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Sets that carry no weight x reps must never break the strength use cases (US-31). */
class NonWeightSetsTest {
    private val start = Instant.parse("2026-10-19T10:00:00Z")
    private val end = Instant.parse("2026-10-19T10:30:00Z")

    private fun exercise(position: Int, vararg entries: SetEntry) = SessionExercise(
        exerciseId = "e$position",
        position = position,
        supersetGroup = null,
        sets = entries.mapIndexed { i, entry -> WorkoutSet(i + 1, SetType.Working, entry, start) },
    )

    private fun session(vararg exercises: SessionExercise) =
        WorkoutSession("s1", null, "Mix", SessionStatus.InProgress, start, end, exercises.toList())

    private val mixed = session(
        exercise(0, SetEntry.Duration(90), SetEntry.Duration(60)),
        exercise(1, SetEntry.DistanceDuration(5000.0, 1800)),
        exercise(2, SetEntry.Bodyweight(10.0, 8)),
    )

    @Test
    fun sessionStats_countDurationAndDistanceSetsWithoutVolume() {
        val stats = ComputeSessionStats(session(exercise(0, SetEntry.Duration(90), SetEntry.DistanceDuration(1.0, 2))))

        assertEquals(2, stats.completedSets)
        assertEquals(0.0, stats.volumeKg)
    }

    @Test
    fun sessionStats_useTheBodyWeightForBodyweightSets() {
        val stats = ComputeSessionStats(mixed, bodyWeightKg = 74.0)

        assertEquals(4, stats.completedSets)
        assertEquals(672.0, stats.volumeKg)
    }

    @Test
    fun summary_hasZeroVolumeForDurationAndDistanceExercises() {
        val summary = BuildSessionSummary(mixed, end, emptyMap())

        assertEquals(4, summary.completedSets)
        assertEquals(listOf(0.0, 0.0, 10.0 * 8), summary.exercises.map { it.volume })
    }

    @Test
    fun summary_usesTheBodyWeightForBodyweightVolume() {
        val summary = BuildSessionSummary(mixed, end, emptyMap(), bodyWeightKg = 74.0)

        assertEquals(672.0, summary.exercises[2].volume)
    }

    @Test
    fun bestOneRepMax_ignoresSetsWithoutAWeightAndRepsLoad() {
        val rows = listOf(exercise(0, SetEntry.Duration(90), SetEntry.RepsOnly(20), SetEntry.Bodyweight(10.0, 8)))

        assertNull(bestOneRepMax(rows))
    }

    @Test
    fun bestOneRepMax_countsTheAddedLoadOfWeightedSets() {
        val rows = listOf(exercise(0, SetEntry.Weighted(20.0, 8), SetEntry.Duration(60)))

        assertEquals(EstimateOneRepMax(20.0, 8), bestOneRepMax(rows))
    }

    @Test
    fun pendingQueue_carriesEveryVariant() {
        val queue = PendingSetQueue()
        val entries = listOf(
            SetEntry.Duration(90),
            SetEntry.DistanceDuration(5000.0, 1800),
            SetEntry.Assisted(20.0, 10),
        )

        entries.forEachIndexed { index, entry ->
            queue.enqueue(SetWrite("s1", "e1", 0, null, index + 1, entry, start, SetType.Working))
        }

        assertEquals(entries, queue.snapshot().map { it.entry })
    }
}
