package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TrainedSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraft
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.port.AlertSound
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.Haptics
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.PersonalRecordCatalogue
import io.github.lopezjavier.appfitness.domain.usecase.SetWrite
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Instant

/** Counts the alerts. */
internal class FakeHaptics : Haptics {
    var alerts = 0

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
    var restFailure: AppError? = null
    var discardFailure: AppError? = null
    var historyFailure: AppError? = null
    var history: Map<String, List<ExerciseHistoryPoint>> = emptyMap()

    /** Per-session record aggregates by exercise id (completed sessions, oldest first). */
    var sessionRecords: Map<String, List<ExerciseSessionRecords>> = emptyMap()
    var recordsFailure: AppError? = null
    var startedId = "s-new"

    /** What [lastCompletedAt] answers, and the routine ids it was asked for. */
    var lastCompleted: Instant? = null
    var lastCompletedFailure: AppError? = null
    val lastCompletedCalls = mutableListOf<String>()
    var previous: Map<String, List<PreviousSet>> = emptyMap()
    var lastPerformanceFailure: AppError? = null
    val lastPerformanceCalls = mutableListOf<String>()

    /** The routine id each [lastPerformance] call was made with, in call order. */
    val lastPerformanceRoutineIds = mutableListOf<String?>()

    /** Session controls: the recorded calls, their failures and the order of set writes versus deletions. */
    val pauses = mutableListOf<Pair<String, Instant>>()
    val resumes = mutableListOf<Pair<String, Instant>>()
    val deletedSets = mutableListOf<Pair<Int, Int>>()

    /** (position, seconds) of every [setExerciseRest] call, in order. */
    val restWrites = mutableListOf<Pair<Int, Int>>()
    var pauseFailure: AppError? = null
    var resumeFailure: AppError? = null
    var deleteSetFailure: AppError? = null

    /** "upsert:<position>:<index>" and "delete:<position>:<index>" in the order the server received them. */
    val serverLog = mutableListOf<String>()

    /** Completed sessions (history); `session` stays the in-progress one. */
    val completed = mutableListOf<WorkoutSession>()
    val listCalls = mutableListOf<Pair<Int, Int>>()
    val deleted = mutableListOf<String>()
    var getSessionCalls = 0
    var listFailure: AppError? = null
    var listFailureFromOffset: Int? = null
    var getSessionFailure: AppError? = null
    var deleteFailure: AppError? = null

    /** (draft, sessionId) of every [saveCompleted] call, in order. */
    val savedDrafts = mutableListOf<Pair<WorkoutDraft, String?>>()
    var saveCompletedFailure: AppError? = null
    var savedId = "s-saved"

    private fun failing(error: AppError?) = error?.let { Result.failure<Nothing>(AppException(it)) }

    override suspend fun startFromRoutine(routineId: String): Result<String> {
        startedRoutines += routineId
        return failing(startFailure) ?: Result.success(startedId)
    }

    override suspend fun lastCompletedAt(routineId: String): Result<Instant?> {
        lastCompletedCalls += routineId
        return failing(lastCompletedFailure) ?: Result.success(lastCompleted)
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
        rpe: Double?,
    ): Result<Unit> {
        failing(upsertFailure)?.let { return it }
        applyToCompleted(sessionId, exercisePosition, setIndex, entry)
        storeSetTypeAndRpe(sessionId, exercisePosition, setIndex, setType, rpe)
        serverLog += "upsert:$exercisePosition:$setIndex"
        upserts +=
            SetWrite(
                sessionId, exerciseId, exercisePosition, supersetGroup, setIndex, entry, completedAt, setType, rpe,
            )
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

    var summariesFailure: AppError? = null
    var summariesCalls = 0

    override suspend fun listCompletedSummaries(): Result<List<TrainedSession>> {
        summariesCalls++
        failing(summariesFailure)?.let { return it }
        return Result.success(
            completed.sortedWith(compareByDescending<WorkoutSession> { it.startedAt }.thenByDescending { it.id })
                .map { TrainedSession(it.id, it.name, it.startedAt) },
        )
    }

    /** Overrides what [exerciseSessions] returns for an offset (e.g. to repeat a session); null uses [completed]. */
    var exerciseSessionsPage: ((offset: Int) -> List<WorkoutSession>)? = null
    val exerciseSessionCalls = mutableListOf<Triple<String, Int, Int>>()
    var exerciseSessionsFailure: AppError? = null

    /** Calls from this offset on fail with a network error (the first pages succeed). */
    var exerciseSessionsFailureFromOffset: Int? = null

    @Suppress("ReturnCount")
    override suspend fun exerciseSessions(exerciseId: String, offset: Int, limit: Int): Result<List<WorkoutSession>> {
        exerciseSessionCalls += Triple(exerciseId, offset, limit)
        failing(exerciseSessionsFailure)?.let { return it }
        if (exerciseSessionsFailureFromOffset?.let { offset >= it } == true) return failing(AppError.Network)!!
        exerciseSessionsPage?.let { return Result.success(it(offset)) }
        val matching = completed
            .filter { session -> session.exercises.any { it.exerciseId == exerciseId } }
            .sortedWith(compareByDescending<WorkoutSession> { it.startedAt }.thenByDescending { it.id })
            .map { session ->
                session.copy(
                    exercises = session.exercises.filter { it.exerciseId == exerciseId }
                        .map { exercise -> exercise.copy(sets = exercise.sets.filter { it.isCompleted }) },
                )
            }
        return Result.success(matching.drop(offset).take(limit))
    }

    override suspend fun getSession(sessionId: String): Result<WorkoutSession?> {
        getSessionCalls++
        return failing(getSessionFailure) ?: Result.success(completed.firstOrNull { it.id == sessionId })
    }

    override suspend fun saveCompleted(draft: WorkoutDraft, sessionId: String?): Result<String> {
        failing(saveCompletedFailure)?.let { return it }
        savedDrafts += draft to sessionId
        val id = sessionId ?: savedId
        val started = Instant.parse("${draft.date}T12:00:00Z")
        val stored = WorkoutSession(
            id = id,
            routineId = draft.routineId,
            name = draft.name,
            status = SessionStatus.Completed,
            startedAt = started,
            endedAt = started,
            exercises = draft.exercises.mapIndexed { position, exercise ->
                SessionExercise(
                    exercise.exerciseId,
                    position,
                    exercise.supersetGroup,
                    exercise.sets.mapIndexed { index, set ->
                        WorkoutSet(index + 1, set.setType, set.entry, started, set.rpe)
                    },
                )
            },
        )
        val index = completed.indexOfFirst { it.id == id }
        if (index >= 0) completed[index] = stored.copy(startedAt = completed[index].startedAt) else completed += stored
        return Result.success(id)
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        failing(deleteFailure)?.let { return it }
        deleted += sessionId
        completed.removeAll { it.id == sessionId }
        return Result.success(Unit)
    }

    /** The server keeps the set type of the in-progress session, so a reload shows it. */
    private fun storeSetTypeAndRpe(sessionId: String, position: Int, setIndex: Int, setType: SetType, rpe: Double?) {
        val stored = session?.takeIf { it.id == sessionId } ?: return
        session = stored.copy(
            exercises = stored.exercises.map { exercise ->
                if (exercise.position != position) {
                    exercise
                } else {
                    exercise.copy(
                        sets = exercise.sets.map {
                            if (it.setIndex == setIndex) it.copy(setType = setType, rpe = rpe) else it
                        },
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

    override suspend fun setExerciseRest(sessionId: String, exercisePosition: Int, seconds: Int): Result<Unit> {
        failing(restFailure)?.let { return it }
        restWrites += exercisePosition to seconds
        session = session?.takeIf { it.id == sessionId }
            ?.let { it.copy(exerciseRests = it.exerciseRests + (exercisePosition to seconds)) } ?: session
        return Result.success(Unit)
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

    override suspend fun pause(sessionId: String, at: Instant): Result<Unit> {
        failing(pauseFailure)?.let { return it }
        pauses += sessionId to at
        return Result.success(Unit)
    }

    override suspend fun resume(sessionId: String, at: Instant): Result<Unit> {
        failing(resumeFailure)?.let { return it }
        resumes += sessionId to at
        return Result.success(Unit)
    }

    override suspend fun deleteSet(sessionId: String, exercisePosition: Int, setIndex: Int): Result<Unit> {
        failing(deleteSetFailure)?.let { return it }
        serverLog += "delete:$exercisePosition:$setIndex"
        deletedSets += exercisePosition to setIndex
        return Result.success(Unit)
    }

    @Suppress("ReturnCount")
    override suspend fun lastPerformance(exerciseId: String, routineId: String?): Result<List<PreviousSet>> {
        lastPerformanceCalls += exerciseId
        lastPerformanceRoutineIds += routineId
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

    override suspend fun personalRecords(exerciseId: String): Result<List<PersonalRecord>> =
        failing(recordsFailure) ?: Result.success(PersonalRecordCatalogue.current(sessionRecords[exerciseId].orEmpty()))

    override suspend fun sessionRecords(exerciseId: String): Result<List<ExerciseSessionRecords>> =
        failing(recordsFailure) ?: Result.success(sessionRecords[exerciseId].orEmpty())
}

internal val SESSION_START: Instant = Instant.parse("2026-10-19T10:00:00Z")

internal fun pendingSet(index: Int) = WorkoutSet(index, SetType.Working, SetEntry.WeightReps(0.0, 0), null)

internal fun doneSet(index: Int, weight: Double, reps: Int) =
    WorkoutSet(index, SetType.Working, SetEntry.WeightReps(weight, reps), SESSION_START)

internal fun sessionOf(vararg exercises: SessionExercise, id: String = "s1") =
    WorkoutSession(id, "r1", "Torso", SessionStatus.InProgress, SESSION_START, null, exercises.toList())
