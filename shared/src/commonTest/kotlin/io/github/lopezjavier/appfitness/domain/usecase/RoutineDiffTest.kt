package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseNoteKind
import io.github.lopezjavier.appfitness.domain.model.Note
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private val NOW = Instant.fromEpochSeconds(1_700_000_000)

private fun done(index: Int, weight: Double, reps: Int, type: SetType = SetType.Working) =
    WorkoutSet(index, type, SetEntry.WeightReps(weight, reps), NOW)

private fun pending(index: Int, weight: Double, reps: Int) =
    WorkoutSet(index, SetType.Working, SetEntry.WeightReps(weight, reps), null)

private fun sessionExercise(id: String, position: Int, sets: List<WorkoutSet>, group: Int? = null) =
    SessionExercise(id, position, group, sets)

private fun session(vararg exercises: SessionExercise) = WorkoutSession(
    id = "s1",
    routineId = "r1",
    name = "Torso",
    status = SessionStatus.Completed,
    startedAt = NOW,
    endedAt = NOW,
    exercises = exercises.toList(),
)

private fun planned(id: String, position: Int, sets: List<PlannedSet>, rest: Int = 120, group: Int? = null) =
    RoutineExercise(id, position, group, sets, rest)

private fun routine(vararg exercises: RoutineExercise) = Routine("r1", "Torso", null, false, exercises.toList())

private fun working(weight: Double?, reps: Int) = PlannedSet(weight, reps)

class RoutineDiffTest {
    private val baseRoutine = routine(
        planned("a", 0, listOf(working(80.0, 8), working(80.0, 8))),
        planned("b", 1, listOf(working(50.0, 10))),
    )

    private fun baseSession() = session(
        sessionExercise("a", 0, listOf(done(1, 80.0, 8), done(2, 80.0, 8))),
        sessionExercise("b", 1, listOf(done(1, 50.0, 10))),
    )

    @Test
    fun identicalValues_areTheSame() {
        assertTrue(routineDiff(baseSession(), emptyMap(), baseRoutine).isSame)
    }

    @Test
    fun addedSet_isReported() {
        val s = session(
            sessionExercise("a", 0, listOf(done(1, 80.0, 8), done(2, 80.0, 8), done(3, 80.0, 8))),
            sessionExercise("b", 1, listOf(done(1, 50.0, 10))),
        )

        assertEquals(listOf(RoutineChange.SetAdded("a")), routineDiff(s, emptyMap(), baseRoutine).changes)
    }

    @Test
    fun removedSet_isReported() {
        val s = session(
            sessionExercise("a", 0, listOf(done(1, 80.0, 8))),
            sessionExercise("b", 1, listOf(done(1, 50.0, 10))),
        )

        assertEquals(listOf(RoutineChange.SetRemoved("a")), routineDiff(s, emptyMap(), baseRoutine).changes)
    }

    @Test
    fun uncompletedPlannedSet_countsAsRemoved() {
        val s = session(
            sessionExercise("a", 0, listOf(done(1, 80.0, 8), pending(2, 80.0, 8))),
            sessionExercise("b", 1, listOf(done(1, 50.0, 10))),
        )

        assertEquals(listOf(RoutineChange.SetRemoved("a")), routineDiff(s, emptyMap(), baseRoutine).changes)
    }

    @Test
    fun changedWeight_isReported() {
        val s = session(
            sessionExercise("a", 0, listOf(done(1, 85.0, 8), done(2, 80.0, 8))),
            sessionExercise("b", 1, listOf(done(1, 50.0, 10))),
        )

        assertEquals(listOf(RoutineChange.SetChanged("a")), routineDiff(s, emptyMap(), baseRoutine).changes)
    }

    @Test
    fun changedReps_isReported() {
        val s = session(
            sessionExercise("a", 0, listOf(done(1, 80.0, 8), done(2, 80.0, 6))),
            sessionExercise("b", 1, listOf(done(1, 50.0, 10))),
        )

        assertEquals(listOf(RoutineChange.SetChanged("a")), routineDiff(s, emptyMap(), baseRoutine).changes)
    }

    @Test
    fun changedType_isReported() {
        val s = session(
            sessionExercise("a", 0, listOf(done(1, 80.0, 8, SetType.WarmUp), done(2, 80.0, 8))),
            sessionExercise("b", 1, listOf(done(1, 50.0, 10))),
        )

        assertEquals(listOf(RoutineChange.SetChanged("a")), routineDiff(s, emptyMap(), baseRoutine).changes)
    }

    @Test
    fun addedExercise_isReported() {
        val s = session(
            sessionExercise("a", 0, listOf(done(1, 80.0, 8), done(2, 80.0, 8))),
            sessionExercise("b", 1, listOf(done(1, 50.0, 10))),
            sessionExercise("c", 2, listOf(done(1, 20.0, 12))),
        )

        assertEquals(listOf(RoutineChange.ExerciseAdded("c")), routineDiff(s, emptyMap(), baseRoutine).changes)
    }

    @Test
    fun removedExercise_andExerciseWithNoCompletedSet_areReported() {
        val s = session(
            sessionExercise("a", 0, listOf(done(1, 80.0, 8), done(2, 80.0, 8))),
            sessionExercise("b", 1, listOf(pending(1, 50.0, 10))),
        )

        assertEquals(listOf(RoutineChange.ExerciseRemoved("b")), routineDiff(s, emptyMap(), baseRoutine).changes)
    }

    @Test
    fun changedRest_isLookedUpBySessionPosition() {
        val s = session(
            sessionExercise("a", 3, listOf(done(1, 80.0, 8), done(2, 80.0, 8))),
            sessionExercise("b", 5, listOf(done(1, 50.0, 10))),
        )

        val changes = routineDiff(s, mapOf(5 to 90), baseRoutine).changes

        assertEquals(listOf(RoutineChange.RestChanged("b")), changes)
    }

    @Test
    fun restEqualToTheRoutines_isTheSame() {
        assertTrue(routineDiff(baseSession(), mapOf(0 to 120, 1 to 120), baseRoutine).isSame)
    }

    @Test
    fun changedSuperset_isReported() {
        val s = session(
            sessionExercise("a", 0, listOf(done(1, 80.0, 8), done(2, 80.0, 8)), group = 1),
            sessionExercise("b", 1, listOf(done(1, 50.0, 10)), group = 1),
        )

        assertEquals(
            listOf(RoutineChange.SupersetChanged("a"), RoutineChange.SupersetChanged("b")),
            routineDiff(s, emptyMap(), baseRoutine).changes,
        )
    }

    @Test
    fun changedOrder_isReported() {
        val s = session(
            sessionExercise("b", 0, listOf(done(1, 50.0, 10))),
            sessionExercise("a", 1, listOf(done(1, 80.0, 8), done(2, 80.0, 8))),
        )

        assertEquals(listOf(RoutineChange.OrderChanged), routineDiff(s, emptyMap(), baseRoutine).changes)
    }

    @Test
    fun emptySession_removesEveryExercise() {
        val changes = routineDiff(session(), emptyMap(), baseRoutine).changes

        assertEquals(listOf(RoutineChange.ExerciseRemoved("a"), RoutineChange.ExerciseRemoved("b")), changes)
    }

    @Test
    fun nullPlannedWeight_equalsAZeroWeightSet() {
        val r = routine(planned("a", 0, listOf(working(null, 8))))
        val s = session(sessionExercise("a", 0, listOf(done(1, 0.0, 8))))

        assertTrue(routineDiff(s, emptyMap(), r).isSame)
    }

    @Test
    fun repsOnlySets_compareByRepsWithZeroWeight() {
        val r = routine(planned("a", 0, listOf(working(null, 12))))
        val s = session(
            sessionExercise("a", 0, listOf(WorkoutSet(1, SetType.Working, SetEntry.RepsOnly(12), NOW))),
        )

        assertTrue(routineDiff(s, emptyMap(), r).isSame)
    }

    @Test
    fun editedRoutineNote_isReportedAsNoteChanged() {
        val notes = mapOf(0 to Note(ExerciseNoteKind.WeightUp, null))

        assertEquals(
            listOf(RoutineChange.NoteChanged("a")),
            routineDiff(baseSession(), emptyMap(), baseRoutine, notes).changes,
        )
    }

    @Test
    fun clearedRoutineNote_isReportedWhenTheRoutineHadOne() {
        val withNote = routine(
            planned("a", 0, listOf(working(80.0, 8), working(80.0, 8)))
                .copy(noteKind = ExerciseNoteKind.Setup),
            planned("b", 1, listOf(working(50.0, 10))),
        )

        val changes = routineDiff(baseSession(), emptyMap(), withNote, mapOf(0 to null)).changes

        assertEquals(listOf(RoutineChange.NoteChanged("a")), changes)
    }

    @Test
    fun routineNoteEqualToThePlannedOne_isNotAChange() {
        val withNote = routine(
            planned("a", 0, listOf(working(80.0, 8), working(80.0, 8)))
                .copy(noteKind = ExerciseNoteKind.Other, notes = "tempo lento"),
            planned("b", 1, listOf(working(50.0, 10))),
        )
        val same = mapOf(0 to Note(ExerciseNoteKind.Other, "tempo lento"))

        assertTrue(routineDiff(baseSession(), emptyMap(), withNote, same).isSame)
        assertTrue(routineDiff(baseSession(), emptyMap(), baseRoutine, mapOf(0 to null)).isSame)
    }
}
