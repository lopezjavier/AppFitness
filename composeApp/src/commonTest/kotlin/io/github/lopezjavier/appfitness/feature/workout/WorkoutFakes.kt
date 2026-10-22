package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.port.AlertSound
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.Haptics
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.SetWrite
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Instant

/** Counts the ticks. */
internal class FakeHaptics : Haptics {
    var ticks = 0

    var alerts = 0

    override fun tick() {
        ticks++
    }

    override fun alert() {
        alerts++
    }
}

/** Counts the plays. */
internal class FakeAlertSound : AlertSound {
    var plays = 0

    override fun play() {
        plays++
    }
}

/** A [Clock] the test moves forward. */
internal class MutableClock(var current: Instant = Instant.parse("2026-10-19T10:00:00Z")) : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 19)

    override fun now(): Instant = current

    fun advance(by: Duration) {
        current += by
    }
}

/**
 * In-memory [WorkoutSessionRepository]: returns [session] as the in-progress one, records every call and fails
 * the calls whose `*Failure` is set.
 */
@Suppress("TooManyFunctions")
internal class FakeWorkoutSessionRepository(var session: WorkoutSession? = null) : WorkoutSessionRepository {
    val upserts = mutableListOf<SetWrite>()
    val startedRoutines = mutableListOf<String>()
    var finished = mutableListOf<String>()
    var discarded = mutableListOf<String>()
    var getCalls = 0

    var getFailure: AppError? = null
    var startFailure: AppError? = null
    var upsertFailure: AppError? = null
    var finishFailure: AppError? = null
    var discardFailure: AppError? = null
    var historyFailure: AppError? = null
    var history: Map<String, List<ExerciseHistoryPoint>> = emptyMap()
    var startedId = "s-new"
    var previous: Map<String, List<PreviousSet>> = emptyMap()
    var lastPerformanceFailure: AppError? = null
    val lastPerformanceCalls = mutableListOf<String>()

    /** Completed sessions (history); `session` stays the in-progress one. */
    val completed = mutableListOf<WorkoutSession>()
    val listCalls = mutableListOf<Pair<Int, Int>>()
    val deleted = mutableListOf<String>()
    var getSessionCalls = 0
    var listFailure: AppError? = null
    var listFailureFromOffset: Int? = null
    var getSessionFailure: AppError? = null
    var deleteFailure: AppError? = null

    private fun failing(error: AppError?) = error?.let { Result.failure<Nothing>(AppException(it)) }

    override suspend fun startFromRoutine(routineId: String): Result<String> {
        startedRoutines += routineId
        return failing(startFailure) ?: Result.success(startedId)
    }

    override suspend fun getInProgress(): Result<WorkoutSession?> {
        getCalls++
        return failing(getFailure) ?: Result.success(session)
    }

    override suspend fun upsertSet(
        sessionId: String,
        exerciseId: String,
        exercisePosition: Int,
        supersetGroup: Int?,
        setIndex: Int,
        entry: SetEntry,
        completedAt: Instant?,
        setType: SetType,
    ): Result<Unit> {
        failing(upsertFailure)?.let { return it }
        applyToCompleted(sessionId, exercisePosition, setIndex, entry)
        storeSetType(sessionId, exercisePosition, setIndex, setType)
        upserts +=
            SetWrite(sessionId, exerciseId, exercisePosition, supersetGroup, setIndex, entry, completedAt, setType)
        return Result.success(Unit)
    }

    @Suppress("ReturnCount")
    override suspend fun listCompleted(offset: Int, limit: Int): Result<List<WorkoutSession>> {
        listCalls += offset to limit
        failing(listFailure)?.let { return it }
        if (listFailureFromOffset?.let { offset >= it } == true) return failing(AppError.Network)!!
        val ordered = completed.sortedWith(
            compareByDescending<WorkoutSession> {
                it.startedAt
            }.thenByDescending { it.id },
        )
        return Result.success(ordered.drop(offset).take(limit))
    }

    override suspend fun getSession(sessionId: String): Result<WorkoutSession?> {
        getSessionCalls++
        return failing(getSessionFailure) ?: Result.success(completed.firstOrNull { it.id == sessionId })
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        failing(deleteFailure)?.let { return it }
        deleted += sessionId
        completed.removeAll { it.id == sessionId }
        return Result.success(Unit)
    }

    /** The server keeps the set type of the in-progress session, so a reload shows it. */
    private fun storeSetType(sessionId: String, position: Int, setIndex: Int, setType: SetType) {
        val stored = session?.takeIf { it.id == sessionId } ?: return
        session = stored.copy(
            exercises = stored.exercises.map { exercise ->
                if (exercise.position != position) {
                    exercise
                } else {
                    exercise.copy(
                        sets = exercise.sets.map { if (it.setIndex == setIndex) it.copy(setType = setType) else it },
                    )
                }
            },
        )
    }

    private fun applyToCompleted(sessionId: String, position: Int, setIndex: Int, entry: SetEntry) {
        val index = completed.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        completed[index] = completed[index].let { stored ->
            stored.copy(
                exercises = stored.exercises.map { exercise ->
                    if (exercise.position != position) {
                        exercise
                    } else {
                        exercise.copy(
                            sets = exercise.sets.map {
                                if (it.setIndex ==
                                    setIndex
                                ) {
                                    it.copy(entry = entry)
                                } else {
                                    it
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    override suspend fun finish(sessionId: String): Result<Unit> {
        failing(finishFailure)?.let { return it }
        finished += sessionId
        return Result.success(Unit)
    }

    override suspend fun discard(sessionId: String): Result<Unit> {
        failing(discardFailure)?.let { return it }
        discarded += sessionId
        return Result.success(Unit)
    }

    @Suppress("ReturnCount")
    override suspend fun lastPerformance(exerciseId: String): Result<List<PreviousSet>> {
        lastPerformanceCalls += exerciseId
        failing(lastPerformanceFailure)?.let { return it }
        previous[exerciseId]?.let { return Result.success(it) }
        val latest = completed.sortedByDescending { it.startedAt }
            .firstOrNull { session -> session.exercises.any { it.exerciseId == exerciseId } }
        val sets = latest?.exercises?.filter { it.exerciseId == exerciseId }?.flatMap { it.sets }.orEmpty()
        return Result.success(
            sets.filter { it.isCompleted }.mapNotNull { set ->
                (set.entry as? SetEntry.WeightReps)?.let { PreviousSet(set.setIndex, it.weightKg, it.reps) }
            },
        )
    }

    override suspend fun exerciseHistory(exerciseId: String, from: LocalDate?): Result<List<ExerciseHistoryPoint>> =
        failing(historyFailure) ?: Result.success(history[exerciseId].orEmpty())
}

internal val SESSION_START: Instant = Instant.parse("2026-10-19T10:00:00Z")

internal fun pendingSet(index: Int) = WorkoutSet(index, SetType.Working, SetEntry.WeightReps(0.0, 0), null)

internal fun doneSet(index: Int, weight: Double, reps: Int) =
    WorkoutSet(index, SetType.Working, SetEntry.WeightReps(weight, reps), SESSION_START)

internal fun sessionOf(vararg exercises: SessionExercise, id: String = "s1") =
    WorkoutSession(id, "r1", "Torso", SessionStatus.InProgress, SESSION_START, null, exercises.toList())
