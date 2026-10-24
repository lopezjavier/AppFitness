package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutFormTest {
    private val date = LocalDate(2026, 10, 20)

    private fun form(): WorkoutForm = WorkoutForm(date = date, name = "Torso")
        .withNewExercise("a", ExerciseMetricType.WeightReps)
        .withNewExercise("b", ExerciseMetricType.Duration)

    @Test
    fun anAddedExerciseStartsWithOneEmptySetAndTheDefaultRest() {
        val added = form().exercises[0]

        assertEquals(1, added.sets.size)
        assertEquals(120, added.restSeconds)
        assertEquals("", added.sets[0].reps)
    }

    @Test
    fun toDraft_readsOnlyTheColumnsOfTheMetricType() {
        val typed = form()
            .withField(0, 0, SetField.Weight, "82,5")
            .withField(0, 0, SetField.Reps, "6")
            .withField(1, 0, SetField.Duration, "45")
            .withField(1, 0, SetField.Reps, "99")

        val draft = typed.toDraft()

        assertEquals(SetEntry.WeightReps(82.5, 6), draft.exercises[0].sets[0].entry)
        assertEquals(SetEntry.Duration(45), draft.exercises[1].sets[0].entry)
    }

    @Test
    fun toDraft_turnsTextThatIsNotANumberIntoAnOutOfRangeValue() {
        val draft = form().withField(0, 0, SetField.Reps, "abc").toDraft()

        assertEquals(SetEntry.WeightReps(0.0, -1), draft.exercises[0].sets[0].entry)
    }

    @Test
    fun toDraft_blankDescriptionAndGymBecomeNullAndDurationIsParsed() {
        val draft = form().copy(description = "  ", gym = " Box ", duration = "45").toDraft()

        assertNull(draft.description)
        assertEquals("Box", draft.gym)
        assertEquals(45, draft.durationMinutes)
    }

    @Test
    fun draftToFormAndBackKeepsTheValues() {
        val original = form().withField(0, 0, SetField.Weight, "80").withField(0, 0, SetField.Reps, "5")
            .withRpe(0, 0, 8.0).withSetType(0, 0, SetType.Drop).withField(1, 0, SetField.Duration, "30").toDraft()

        assertEquals(original, original.toForm().toDraft())
    }

    @Test
    fun addingASetCopiesTheLastOneAndRemovingKeepsTheLastRemaining() {
        val added = form().withField(0, 0, SetField.Reps, "5").withAddedSet(0)

        assertEquals(listOf("5", "5"), added.exercises[0].sets.map { it.reps })
        assertEquals(1, added.withoutSet(0, 0).exercises[0].sets.size)
        assertEquals(1, added.withoutSet(0, 0).withoutSet(0, 0).exercises[0].sets.size)
    }

    @Test
    fun aSupersetAddsAndRemovesSetsOnEveryMember() {
        val grouped = form().let { f -> f.copy(exercises = f.exercises.map { it.copy(group = 1) }) }

        val added = grouped.withAddedSet(0)
        val removed = added.withoutSet(1, 0)

        assertEquals(listOf(2, 2), added.exercises.map { it.sets.size })
        assertEquals(listOf(1, 1), removed.exercises.map { it.sets.size })
    }

    @Test
    fun removingAMemberUngroupsTheSupersetItLeftWithOneExercise() {
        val grouped = form().let { f -> f.copy(exercises = f.exercises.map { it.copy(group = 1) }) }

        assertEquals(listOf<Int?>(null), grouped.withoutExercise(1).exercises.map { it.group })
    }

    @Test
    fun restIsClampedToTheServerRange() {
        assertEquals(600, form().withRest(0, 9999).exercises[0].restSeconds)
        assertEquals(0, form().withRest(0, -5).exercises[0].restSeconds)
    }

    @Test
    fun movingAnExerciseReordersTheForm() {
        assertEquals(listOf("b", "a"), form().movedExercise(0, 1).exercises.map { it.exerciseId })
    }
}
