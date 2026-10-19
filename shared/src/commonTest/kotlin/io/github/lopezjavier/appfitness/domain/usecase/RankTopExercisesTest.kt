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

class RankTopExercisesTest {
    private val since = Instant.parse("2026-09-07T00:00:00Z")

    private fun session(id: String, start: String, vararg exerciseIds: String, done: Boolean = true) = WorkoutSession(
        id,
        null,
        id,
        SessionStatus.Completed,
        Instant.parse(start),
        null,
        exerciseIds.mapIndexed { index, exerciseId ->
            val completedAt = if (done) Instant.parse(start) else null
            val set = WorkoutSet(1, SetType.Working, SetEntry.WeightReps(50.0, 5), completedAt)
            SessionExercise(exerciseId, index, null, listOf(set))
        },
    )

    @Test
    fun ranksBySessionCountAndKeepsTheTopThree() {
        val sessions = listOf(
            session("a", "2026-10-01T10:00:00Z", "bench", "squat", "row"),
            session("b", "2026-10-05T10:00:00Z", "bench", "squat", "row"),
            session("c", "2026-10-09T10:00:00Z", "bench", "squat"),
            session("d", "2026-10-12T10:00:00Z", "bench", "curl"),
        )

        assertEquals(listOf("bench", "squat", "row"), RankTopExercises(sessions, since))
    }

    @Test
    fun aTieGoesToTheMostRecentlyTrainedExercise() {
        val sessions = listOf(
            session("a", "2026-10-01T10:00:00Z", "bench"),
            session("b", "2026-10-09T10:00:00Z", "squat"),
        )

        assertEquals(listOf("squat", "bench"), RankTopExercises(sessions, since))
    }

    @Test
    fun sessionsBeforeTheWindowAreIgnored() {
        val sessions = listOf(
            session("old1", "2026-08-01T10:00:00Z", "squat"),
            session("old2", "2026-08-05T10:00:00Z", "squat"),
            session("new", "2026-10-09T10:00:00Z", "bench"),
        )

        assertEquals(listOf("bench"), RankTopExercises(sessions, since))
    }

    @Test
    fun exercisesWithoutCompletedSetsDoNotCount() {
        val sessions = listOf(session("a", "2026-10-01T10:00:00Z", "bench", done = false))

        assertEquals(emptyList(), RankTopExercises(sessions, since))
    }

    @Test
    fun noSessionsMeansNoExercises() {
        assertEquals(emptyList(), RankTopExercises(emptyList(), since))
    }
}
