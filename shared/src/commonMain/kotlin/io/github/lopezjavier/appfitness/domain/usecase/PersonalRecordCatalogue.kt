package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.model.columns
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The personal-record families of an exercise (04 §4.8), mirroring the `exercise_session_records` and
 * `personal_records` RPCs: the same eligible sets (completed; warm-up only when included) and the same aggregates.
 */
object PersonalRecordCatalogue {
    private val WEIGHT_FAMILIES = listOf(
        PersonalRecordType.HeaviestLift,
        PersonalRecordType.BestSet,
        PersonalRecordType.MaxVolume,
        PersonalRecordType.Best1Rm,
        PersonalRecordType.TotalReps,
    )

    /** The families that apply to an exercise of [metricType], in display order. */
    fun families(metricType: ExerciseMetricType): List<PersonalRecordType> = when (metricType) {
        ExerciseMetricType.WeightReps,
        ExerciseMetricType.Weighted,
        ExerciseMetricType.Bodyweight,
        ExerciseMetricType.Assisted,
        -> WEIGHT_FAMILIES

        ExerciseMetricType.RepsOnly -> listOf(PersonalRecordType.TotalReps)

        ExerciseMetricType.Duration -> listOf(PersonalRecordType.BestDuration)

        ExerciseMetricType.WeightDuration ->
            listOf(PersonalRecordType.HeaviestLift, PersonalRecordType.BestDuration)

        ExerciseMetricType.DistanceDuration ->
            listOf(PersonalRecordType.BestDistance, PersonalRecordType.BestDuration)

        ExerciseMetricType.StepsDuration -> listOf(PersonalRecordType.MostSteps, PersonalRecordType.BestDuration)

        ExerciseMetricType.FloorsDuration -> listOf(PersonalRecordType.MostFloors, PersonalRecordType.BestDuration)
    }

    /** Aggregates of one session's [sets] of a single exercise; the local twin of `exercise_session_records`. */
    fun fromSets(
        sessionId: String,
        startedAt: Instant,
        sets: List<WorkoutSet>,
        includeWarmUp: Boolean = false,
        bodyWeightKg: Double? = null,
        doubleDumbbell: Boolean = false,
    ): ExerciseSessionRecords {
        val eligible = sets.filter { it.isCompleted && (includeWarmUp || it.setType != SetType.WarmUp) }
        val volumes = eligible.map { CalculateVolume(listOf(it), includeWarmUp, bodyWeightKg, doubleDumbbell) }
        val columns = eligible.map { it.entry.columns() }
        return ExerciseSessionRecords(
            sessionId = sessionId,
            startedAt = startedAt,
            heaviestLift = columns.mapNotNull { it.weightKg }.filter { it > 0.0 }.maxOrNull(),
            bestSet = volumes.filter { it > 0.0 }.maxOrNull(),
            sessionVolume = volumes.sum(),
            bestOneRm = eligible.mapNotNull { oneRepMax(it.entry) }.maxOrNull(),
            totalReps = columns.sumOf { (it.reps ?: 0).toLong() },
            bestDurationS = columns.mapNotNull { it.durationS }.maxOrNull(),
            bestDistanceM = columns.mapNotNull { it.distanceM }.maxOrNull(),
            totalSteps = columns.mapNotNull { it.steps }.maxOrNull(),
            totalFloors = columns.mapNotNull { it.floors }.maxOrNull(),
        )
    }

    /** The current best per family over [sessions] (earliest on ties) and all-time total reps; empty ones omitted. */
    fun current(sessions: List<ExerciseSessionRecords>): List<PersonalRecord> {
        val ordered = sessions.sortedBy { it.startedAt }
        val bests = PersonalRecordType.entries.filter { it != PersonalRecordType.TotalReps }.mapNotNull { type ->
            var best: ExerciseSessionRecords? = null
            for (session in ordered) {
                val value = valueOf(type, session)
                if (value > 0.0 && (best == null || value > valueOf(type, best))) best = session
            }
            best?.let {
                PersonalRecord(type, valueOf(type, it), it.sessionId, it.startedAt.toLocalDateTime(TimeZone.UTC).date)
            }
        }
        val totalReps = ordered.sumOf { it.totalReps }
        val total = if (totalReps >
            0
        ) {
            PersonalRecord(PersonalRecordType.TotalReps, totalReps.toDouble(), null, null)
        } else {
            null
        }
        return (bests + listOfNotNull(total)).sortedBy { it.type.ordinal }
    }

    /**
     * The families [session] beat: strictly above the best of every [earlierSessions] entry that started before it
     * (the first session of an exercise counts). [PersonalRecordType.TotalReps] is never a beat.
     */
    fun beatenBy(
        session: ExerciseSessionRecords,
        earlierSessions: List<ExerciseSessionRecords>,
    ): Set<PersonalRecordType> {
        val earlier = earlierSessions.filter { it.sessionId != session.sessionId && it.startedAt < session.startedAt }
        return PersonalRecordType.entries.filter { it != PersonalRecordType.TotalReps }.filter { type ->
            val value = valueOf(type, session)
            value > 0.0 && value > (earlier.maxOfOrNull { valueOf(type, it) } ?: 0.0)
        }.toSet()
    }

    /**
     * The families each exercise of [session] beat, by exercise id. [history] maps exercise id to its completed
     * sessions (the session itself may be among them); null (history unavailable) marks nothing.
     */
    fun beatenIn(
        session: WorkoutSession,
        history: Map<String, List<ExerciseSessionRecords>>?,
        includeWarmUp: Boolean = false,
        bodyWeightKg: Double? = null,
        doubleDumbbellExerciseIds: Set<String> = emptySet(),
    ): Map<String, Set<PersonalRecordType>> {
        if (history == null) return emptyMap()
        return session.exercises.groupBy { it.exerciseId }.mapValues { (id, rows) ->
            val current = fromSets(
                session.id,
                session.startedAt,
                rows.flatMap { it.sets },
                includeWarmUp,
                bodyWeightKg,
                id in doubleDumbbellExerciseIds,
            )
            beatenBy(current, history[id].orEmpty())
        }
    }

    private fun oneRepMax(entry: SetEntry): Double? = when (entry) {
        is SetEntry.WeightReps -> EstimateOneRepMax(entry.weightKg, entry.reps)
        is SetEntry.Weighted -> EstimateOneRepMax(entry.addedKg, entry.reps)
        else -> null
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the families.
    private fun valueOf(type: PersonalRecordType, s: ExerciseSessionRecords): Double = when (type) {
        PersonalRecordType.HeaviestLift -> s.heaviestLift ?: 0.0
        PersonalRecordType.BestSet -> s.bestSet ?: 0.0
        PersonalRecordType.MaxVolume -> s.sessionVolume
        PersonalRecordType.Best1Rm -> s.bestOneRm ?: 0.0
        PersonalRecordType.TotalReps -> s.totalReps.toDouble()
        PersonalRecordType.BestDuration -> (s.bestDurationS ?: 0).toDouble()
        PersonalRecordType.BestDistance -> s.bestDistanceM ?: 0.0
        PersonalRecordType.MostSteps -> (s.totalSteps ?: 0).toDouble()
        PersonalRecordType.MostFloors -> (s.totalFloors ?: 0).toDouble()
    }
}
