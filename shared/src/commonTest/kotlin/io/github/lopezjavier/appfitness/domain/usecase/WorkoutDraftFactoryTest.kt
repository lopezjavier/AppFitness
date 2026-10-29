package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
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

class WorkoutDraftFactoryTest {
    private val date = LocalDate(2026, 10, 20)
    private val start = Instant.parse("2026-10-10T10:00:00Z")

    private fun routine() = Routine(
        id = "r1",
        name = "Torso",
        notes = null,
        archived = false,
        exercises = listOf(
            RoutineExercise("bench", 0, 3, listOf(PlannedSet(80.0, 5), PlannedSet(null, 8, SetType.WarmUp)), 90),
            RoutineExercise("row", 1, 3, listOf(PlannedSet(60.0, 10), PlannedSet(60.0, 10)), 150),
            RoutineExercise("plank", 2, null, listOf(PlannedSet(null, 1)), 60),
        ),
    )

    private fun done(index: Int, weight: Double, reps: Int, type: SetType = SetType.Working, rpe: Double? = null) =
        WorkoutSet(index, type, SetEntry.WeightReps(weight, reps), start, rpe)

    private fun session(vararg exercises: SessionExercise, rests: Map<Int, Int> = emptyMap()) = WorkoutSession(
        id = "s1",
        routineId = "r1",
        name = "Torso",
        status = SessionStatus.Completed,
        startedAt = start,
        endedAt = Instant.parse("2026-10-10T11:10:20Z"),
        exercises = exercises.toList(),
        exerciseRests = rests,
    )

    @Test
    fun fromRoutine_prefillsPlannedWeightAndRepsWithZeroWhenThePlanHasNoWeight() {
        val draft = WorkoutDraftFactory.fromRoutine(routine(), mapOf("plank" to ExerciseMetricType.Duration), date)

        val bench = draft.exercises[0].sets
        assertEquals(SetEntry.WeightReps(80.0, 5), bench[0].entry)
        assertEquals(SetEntry.WeightReps(0.0, 8), bench[1].entry)
        assertEquals(SetType.WarmUp, bench[1].setType)
        assertEquals(SetEntry.Duration(0), draft.exercises[2].sets.single().entry)
    }

    @Test
    fun fromRoutine_copiesRestsSupersetsAndTheRoutineLink() {
        val draft = WorkoutDraftFactory.fromRoutine(routine(), emptyMap(), date)

        assertEquals(listOf(90, 150, 60), draft.exercises.map { it.restSeconds })
        assertEquals(listOf(3, 3, null), draft.exercises.map { it.supersetGroup })
        assertEquals("r1", draft.routineId)
        assertEquals(date, draft.date)
        assertEquals(0, draft.durationMinutes)
    }

    @Test
    fun fromSession_usesCompletedSetsOnlyAndRenumbersExercises() {
        val pending = WorkoutSet(2, SetType.Working, SetEntry.WeightReps(0.0, 0), null)
        val source = session(
            SessionExercise("a", 0, null, listOf(done(1, 50.0, 10), pending)),
            SessionExercise("b", 1, null, listOf(pending)),
            SessionExercise("c", 2, null, listOf(done(1, 20.0, 12))),
        )

        val draft = WorkoutDraftFactory.fromSession(source, date)

        assertEquals(listOf("a", "c"), draft.exercises.map { it.exerciseId })
        assertEquals(1, draft.exercises[0].sets.size)
    }

    @Test
    fun fromSession_keepsRoutineAndRoundsDurationToMinutesWhenEditing() {
        val draft = WorkoutDraftFactory.fromSession(
            session(SessionExercise("a", 0, null, listOf(done(1, 50.0, 10)))),
            date,
        )

        assertEquals("r1", draft.routineId)
        assertEquals(70, draft.durationMinutes)
    }

    @Test
    fun fromSession_keepsTheSetNotesWhenEditing_butNotAsATemplate() {
        val noted = session(
            SessionExercise("a", 0, null, listOf(done(1, 50.0, 10).copy(notes = "pausa larga"))),
        )

        val edit = WorkoutDraftFactory.fromSession(noted, date)
        val template = WorkoutDraftFactory.fromSession(noted, date, asTemplate = true)

        assertEquals("pausa larga", edit.exercises.single().sets.single().notes)
        assertNull(template.exercises.single().sets.single().notes)
    }

    @Test
    fun fromSession_asTemplateIsANewWorkoutWithoutRoutineLinkOrDuration() {
        val draft = WorkoutDraftFactory.fromSession(
            session(SessionExercise("a", 0, null, listOf(done(1, 50.0, 10)))),
            date,
            asTemplate = true,
        )

        assertNull(draft.routineId)
        assertEquals(0, draft.durationMinutes)
        assertEquals(date, draft.date)
    }

    @Test
    fun fromSession_usesRecordedRestsElseTheDefaultAndNormalisesRpe() {
        val draft = WorkoutDraftFactory.fromSession(
            session(
                SessionExercise("a", 0, null, listOf(done(1, 50.0, 10, rpe = 7.5))),
                SessionExercise("b", 1, null, listOf(done(1, 20.0, 12))),
                rests = mapOf(0 to 45),
            ),
            date,
        )

        assertEquals(listOf(45, 120), draft.exercises.map { it.restSeconds })
        assertEquals(8.0, draft.exercises[0].sets[0].rpe)
        assertNull(draft.exercises[1].sets[0].rpe)
    }

    @Test
    fun fromSession_ungroupsASupersetWhoseMembersNoLongerHaveTheSameSetCount() {
        val pending = WorkoutSet(2, SetType.Working, SetEntry.WeightReps(0.0, 0), null)
        val source = session(
            SessionExercise("a", 0, 1, listOf(done(1, 50.0, 10), done(2, 50.0, 10))),
            SessionExercise("b", 1, 1, listOf(done(1, 20.0, 12), pending)),
        )

        val draft = WorkoutDraftFactory.fromSession(source, date)

        assertEquals(listOf<Int?>(null, null), draft.exercises.map { it.supersetGroup })
    }

    @Test
    fun fromSession_doesNotModifyTheSource() {
        val source = session(SessionExercise("a", 0, null, listOf(done(1, 50.0, 10, rpe = 7.5))))
        val copy = source.copy()

        WorkoutDraftFactory.fromSession(source, date, asTemplate = true)

        assertEquals(copy, source)
    }
}
