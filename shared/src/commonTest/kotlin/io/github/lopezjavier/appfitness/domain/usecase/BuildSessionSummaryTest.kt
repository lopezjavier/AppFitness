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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class BuildSessionSummaryTest {
    private val start = Instant.parse("2026-10-19T10:00:00Z")
    private val end = Instant.parse("2026-10-19T10:52:30Z")

    private fun set(index: Int, weight: Double, reps: Int, done: Boolean = true, type: SetType = SetType.Working) =
        WorkoutSet(index, type, SetEntry.WeightReps(weight, reps), if (done) start else null)

    private fun session(vararg exercises: SessionExercise) =
        WorkoutSession("s1", null, "Torso", SessionStatus.InProgress, start, null, exercises.toList())

    private fun history(session: String, oneRepMax: Double?) =
        ExerciseHistoryPoint(session, LocalDate(2026, 10, 1), oneRepMax, 0.0)

    private val bench = SessionExercise(
        "e1",
        0,
        null,
        listOf(set(1, 60.0, 12), set(2, 87.5, 8), set(3, 87.5, 8), set(4, 50.0, 5, false)),
    )

    @Test
    fun durationIsTheTimeBetweenStartAndEnd() {
        val summary = BuildSessionSummary(session(bench), end, emptyMap())

        assertEquals(3150L, summary.durationSeconds)
    }

    @Test
    fun countsOnlyCompletedSetsAndSumsVolumePerExercise() {
        val summary = BuildSessionSummary(session(bench), end, emptyMap())

        assertEquals(3, summary.completedSets)
        assertEquals(2120.0, summary.exercises.single().volume)
    }

    @Test
    fun firstEverSessionOfAnExerciseIsARecord() {
        val summary = BuildSessionSummary(session(bench), end, mapOf("e1" to emptyList()))

        assertTrue(summary.exercises.single().isPersonalRecord)
    }

    @Test
    fun lowerOneRepMaxThanHistoryIsNoRecord() {
        val summary = BuildSessionSummary(session(bench), end, mapOf("e1" to listOf(history("old", 200.0))))

        assertFalse(summary.exercises.single().isPersonalRecord)
    }

    @Test
    fun higherOneRepMaxThanHistoryIsARecordAndTheCurrentSessionIsIgnored() {
        val points = listOf(history("old", 100.0), history("s1", 500.0))

        val summary = BuildSessionSummary(session(bench), end, mapOf("e1" to points))

        assertTrue(summary.exercises.single().isPersonalRecord)
    }

    @Test
    fun unknownHistoryMeansNoRecordAtAll() {
        val summary = BuildSessionSummary(session(bench), end, history = null)

        assertFalse(summary.exercises.single().isPersonalRecord)
    }

    @Test
    fun anExerciseWithoutCompletedSetsHasNoRecordAndNoVolume() {
        val idle = SessionExercise("e2", 1, null, listOf(set(1, 0.0, 0, false)))

        val summary = BuildSessionSummary(session(bench, idle), end, mapOf("e2" to emptyList()))

        assertFalse(summary.exercises[1].isPersonalRecord)
        assertEquals(0.0, summary.exercises[1].volume)
    }

    @Test
    fun warmUpSetsNeverCountAsRecord() {
        val warm = SessionExercise("e3", 0, null, listOf(set(1, 100.0, 5, type = SetType.WarmUp)))

        val summary = BuildSessionSummary(session(warm), end, mapOf("e3" to emptyList()))

        assertFalse(summary.exercises.single().isPersonalRecord)
    }
}
