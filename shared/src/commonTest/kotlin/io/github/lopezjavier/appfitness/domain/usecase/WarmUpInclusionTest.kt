package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The "Incluir calentamiento" setting (US-29): warm-up sets count in aggregates only when the flag is on. */
class WarmUpInclusionTest {
    private val start = Instant.parse("2026-10-19T10:00:00Z")
    private val end = Instant.parse("2026-10-19T10:50:00Z")

    private fun set(index: Int, weight: Double, reps: Int, type: SetType, done: Boolean = true) =
        WorkoutSet(index, type, SetEntry.WeightReps(weight, reps), if (done) start else null)

    // Warm-up 40 x 10 + working 80 x 8 (04 section 4.6 worked example).
    private val example = listOf(set(1, 40.0, 10, SetType.WarmUp), set(2, 80.0, 8, SetType.Working))

    // Heavy warm-up 100 x 3 (Epley 110.0) beats the working set 80 x 8 (101.5 rounded) only when counted.
    private val heavyWarmUp = listOf(set(1, 100.0, 3, SetType.WarmUp), set(2, 80.0, 8, SetType.Working))

    private fun session(sets: List<WorkoutSet>) = WorkoutSession(
        "s1",
        null,
        "Torso",
        SessionStatus.Completed,
        start,
        end,
        listOf(SessionExercise("e1", 0, null, sets)),
    )

    @Test
    fun volume_excludesWarmUpByDefault() = assertEquals(640.0, CalculateVolume(example))

    @Test
    fun volume_includesWarmUpWhenOn() = assertEquals(1040.0, CalculateVolume(example, includeWarmUp = true))

    @Test
    fun volume_countsFailureAndDropInBothStates() {
        val sets = listOf(set(1, 80.0, 8, SetType.Failure), set(2, 60.0, 10, SetType.Drop))

        assertEquals(1240.0, CalculateVolume(sets))
        assertEquals(1240.0, CalculateVolume(sets, includeWarmUp = true))
    }

    @Test
    fun volume_ignoresIncompleteWarmUpEvenWhenOn() {
        val sets = listOf(set(1, 40.0, 10, SetType.WarmUp, done = false), set(2, 80.0, 8, SetType.Working))

        assertEquals(640.0, CalculateVolume(sets, includeWarmUp = true))
    }

    @Test
    fun oneRepMax_ignoresWarmUpByDefaultAndUsesItWhenOn() {
        val rows = session(heavyWarmUp).exercises

        assertEquals(EstimateOneRepMax(80.0, 8), bestOneRepMax(rows))
        assertEquals(EstimateOneRepMax(100.0, 3), bestOneRepMax(rows, includeWarmUp = true))
    }

    @Test
    fun stats_followTheFlag() {
        val off = ComputeSessionStats(session(example))
        val on = ComputeSessionStats(session(example), includeWarmUp = true)

        assertEquals(1, off.completedSets)
        assertEquals(640.0, off.volumeKg)
        assertEquals(2, on.completedSets)
        assertEquals(1040.0, on.volumeKg)
    }

    @Test
    fun summary_totalsAndRecordFollowTheFlag() {
        val s = session(heavyWarmUp)
        val history = mapOf("e1" to listOf(ExerciseHistoryPoint("old", LocalDate(2026, 10, 1), 105.0, 0.0)))

        val off = BuildSessionSummary(s, end, history)
        val on = BuildSessionSummary(s, end, history, includeWarmUp = true)

        assertEquals(1, off.completedSets)
        assertEquals(640.0, off.exercises.single().volume)
        assertTrue(!off.exercises.single().isPersonalRecord)
        assertEquals(2, on.completedSets)
        assertEquals(940.0, on.exercises.single().volume)
        assertTrue(on.exercises.single().isPersonalRecord)
    }

    @Test
    fun sessionRecords_followTheFlag() {
        val s = session(heavyWarmUp)
        val history = mapOf("e1" to listOf(ExerciseHistoryPoint("old", LocalDate(2026, 10, 1), 105.0, 0.0)))

        assertEquals(emptySet(), DetectSessionPersonalRecords(s, history))
        assertEquals(setOf("e1"), DetectSessionPersonalRecords(s, history, includeWarmUp = true))
    }
}
