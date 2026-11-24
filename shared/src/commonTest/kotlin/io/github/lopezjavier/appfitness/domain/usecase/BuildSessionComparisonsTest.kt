package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ComparisonSession
import io.github.lopezjavier.appfitness.domain.model.ComparisonSet
import io.github.lopezjavier.appfitness.domain.model.ExerciseComparisonInput
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class BuildSessionComparisonsTest {
    private val startedAt = Instant.parse("2026-10-20T10:00:00Z")
    private val day = LocalDate(2026, 10, 20)

    private fun done(index: Int, kg: Double, reps: Int, type: SetType = SetType.Working, rpe: Double? = null) =
        WorkoutSet(index, type, SetEntry.WeightReps(kg, reps), startedAt, rpe)

    private fun exercise(id: String, position: Int, vararg sets: WorkoutSet) =
        SessionExercise(id, position, null, sets.toList())

    private fun finished(vararg exercises: SessionExercise) = WorkoutSession(
        id = "s2",
        routineId = null,
        name = "Torso",
        status = SessionStatus.Completed,
        startedAt = startedAt,
        endedAt = startedAt,
        exercises = exercises.toList(),
    )

    private fun input(id: String, current: List<ComparisonSet>, previous: List<ComparisonSet>?, open: Boolean = false) =
        ExerciseComparisonInput(
            id,
            open,
            ComparisonSession("s2", day, false, current),
            previous?.let { ComparisonSession("s1", LocalDate(2026, 10, 13), false, it) },
        )

    @Test
    fun withTheRemoteInputs_eachExerciseIsComparedWithItsPreviousSession() {
        val remote = listOf(
            input("bench", listOf(ComparisonSet(82.5, 6)), listOf(ComparisonSet(80.0, 6))),
            input("row", listOf(ComparisonSet(60.0, 8)), listOf(ComparisonSet(60.0, 8))),
        )

        val result = BuildSessionComparisons(finished(), remote, inCut = true)

        assertEquals(listOf("bench", "row"), result.map { it.exerciseId })
        assertEquals(listOf(ProgressState.Up, ProgressState.Same), result.map { it.state })
        assertEquals(listOf(false, true), result.map { it.hold })
        assertEquals(80.0, result[0].before?.topLoadKg)
        assertEquals(82.5, result[0].now.topLoadKg)
    }

    @Test
    fun anOpenDiscomfortPausesAnExerciseThatDidNotImprove() {
        val remote = listOf(input("row", listOf(ComparisonSet(60.0, 8)), listOf(ComparisonSet(60.0, 8)), open = true))

        assertEquals(ProgressState.Paused, BuildSessionComparisons(finished(), remote, false).single().state)
    }

    @Test
    fun whenTheRpcFailed_currentIsBuiltOnTheDeviceAndReadsUncompared() {
        val session = finished(
            exercise("bench", 0, done(1, 60.0, 10, SetType.WarmUp), done(2, 80.0, 8, rpe = 7.0), done(3, 80.0, 7)),
        )

        val result = BuildSessionComparisons(session, remote = null, inCut = false).single()

        assertEquals(ProgressState.Uncompared, result.state)
        assertNull(result.before)
        assertEquals(80.0, result.now.topLoadKg)
        assertEquals(listOf(8, 7), result.now.repsAtTop)
        assertEquals(7.0, result.now.rpeAtTop)
        assertEquals(day, result.now.date)
    }

    @Test
    fun whenTheRpcFailed_noExerciseIsPaused() {
        val session = finished(exercise("bench", 0, done(1, 80.0, 8)), exercise("row", 1, done(1, 60.0, 8)))

        val result = BuildSessionComparisons(session, remote = null, inCut = true)

        assertEquals(listOf(ProgressState.Uncompared, ProgressState.Uncompared), result.map { it.state })
    }

    @Test
    fun onDevice_onlyExercisesWithACompletedWorkingSetAreListed() {
        val pending = WorkoutSet(1, SetType.Working, SetEntry.WeightReps(80.0, 8), null)
        val session = finished(
            exercise("warmupOnly", 0, done(1, 40.0, 10, SetType.WarmUp)),
            exercise("pending", 1, pending),
            exercise("cardio", 2, WorkoutSet(1, SetType.Working, SetEntry.Duration(600), startedAt)),
            exercise("bench", 3, done(1, 80.0, 8)),
        )

        val result = BuildSessionComparisons(session, remote = null, inCut = false)

        assertEquals(listOf("bench"), result.map { it.exerciseId })
    }

    @Test
    fun onDevice_aRepeatedExerciseIsOneEntryWithItsSetsInPositionOrder() {
        val session = finished(
            exercise("bench", 0, done(1, 80.0, 8)),
            exercise("row", 1, done(1, 60.0, 8)),
            exercise("bench", 2, done(1, 80.0, 6)),
        )

        val result = BuildSessionComparisons(session, remote = null, inCut = false)

        assertEquals(listOf("bench", "row"), result.map { it.exerciseId })
        assertEquals(listOf(8, 6), result[0].now.repsAtTop)
    }

    @Test
    fun whenTheRpcAnswersEmpty_nothingIsListed() {
        assertEquals(
            emptyList(),
            BuildSessionComparisons(finished(exercise("bench", 0, done(1, 80.0, 8))), emptyList(), false),
        )
    }
}
