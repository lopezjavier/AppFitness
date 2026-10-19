package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Exercises of a past session whose best 1RM beat every earlier session (the state it had when it was done). */
object DetectSessionPersonalRecords {
    /**
     * [history] holds every session of each exercise, later ones included. Only points of other sessions dated on or
     * before the session's UTC start date count as earlier; same-day order is approximated because the points carry
     * no timestamp.
     */
    operator fun invoke(session: WorkoutSession, history: Map<String, List<ExerciseHistoryPoint>>): Set<String> {
        val sessionDate = session.startedAt.toLocalDateTime(TimeZone.UTC).date
        val best = session.exercises.groupBy { it.exerciseId }.mapValues { (_, rows) -> bestOneRepMax(rows) }
        val earlier = history.mapValues { (_, points) -> points.filter { it.date <= sessionDate } }
        return DetectPersonalRecords(best, earlier, session.id)
    }
}
