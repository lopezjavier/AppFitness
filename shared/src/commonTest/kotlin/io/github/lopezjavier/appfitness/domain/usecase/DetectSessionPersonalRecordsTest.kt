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
import kotlin.time.Instant

class DetectSessionPersonalRecordsTest {
    private val start = Instant.parse("2026-10-19T10:00:00Z")

    private fun set(index: Int, weight: Double, reps: Int, type: SetType = SetType.Working) =
        WorkoutSet(index, type, SetEntry.WeightReps(weight, reps), start)

    private fun session(vararg sets: WorkoutSet) = WorkoutSession(
        "s1",
        null,
        "Torso",
        SessionStatus.Completed,
        start,
        null,
        listOf(SessionExercise("e1", 0, null, sets.toList())),
    )

    private fun point(id: String, day: Int, best: Double?) =
        ExerciseHistoryPoint(id, LocalDate(2026, 10, day), best, 0.0)

    private fun records(session: WorkoutSession, vararg points: ExerciseHistoryPoint) =
        DetectSessionPersonalRecords(session, mapOf("e1" to points.toList()))

    @Test
    fun theFirstSessionOfAnExerciseIsARecord() {
        assertEquals(setOf("e1"), records(session(set(1, 100.0, 5))))
    }

    @Test
    fun beatingTheEarlierBestIsARecord() {
        val result = records(session(set(1, 100.0, 5)), point("old", 10, 107.5))

        assertEquals(setOf("e1"), result)
    }

    @Test
    fun anEqualOneRepMaxIsNotARecord() {
        val result = records(session(set(1, 100.0, 5)), point("old", 10, 116.5))

        assertEquals(emptySet(), result)
    }

    @Test
    fun aLaterSessionWithAHigherOneRepMaxDoesNotRemoveTheRecord() {
        val result = records(session(set(1, 100.0, 5)), point("old", 10, 107.5), point("later", 25, 200.0))

        assertEquals(setOf("e1"), result)
    }

    @Test
    fun theSessionsOwnHistoryPointIsIgnored() {
        val result = records(session(set(1, 100.0, 5)), point("s1", 19, 116.5))

        assertEquals(setOf("e1"), result)
    }

    @Test
    fun warmUpSetsAreIgnored() {
        val result = records(session(set(1, 100.0, 5, SetType.WarmUp)))

        assertEquals(emptySet(), result)
    }

    @Test
    fun setsWithoutAnEstimateAreNotEligible() {
        val result = records(session(set(1, 0.0, 5), set(2, 50.0, 13)))

        assertEquals(emptySet(), result)
    }

    @Test
    fun anEmptyHistoryMapMakesEveryEligibleExerciseARecord() {
        assertEquals(setOf("e1"), DetectSessionPersonalRecords(session(set(1, 100.0, 5)), emptyMap()))
    }
}
