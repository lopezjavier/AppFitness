package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardioPlannedSetsTest {
    private fun timed(seconds: Int, reps: Int = 0, weight: Double? = null) =
        PlannedSet(weight, reps, SetType.Working, seconds)

    @Test
    fun durationBetween60And14400SecondsIsValid() {
        assertNull(validatePlannedSets(listOf(timed(60))))
        assertNull(validatePlannedSets(listOf(timed(14_400))))
    }

    @Test
    fun durationOutsideTheBoundsIsRejected() {
        assertEquals(PlannedSetsError.Duration, validatePlannedSets(listOf(timed(59))))
        assertEquals(PlannedSetsError.Duration, validatePlannedSets(listOf(timed(14_401))))
    }

    @Test
    fun durationThatIsNotWholeMinutesIsRejected() {
        assertEquals(PlannedSetsError.Duration, validatePlannedSets(listOf(timed(90))))
    }

    @Test
    fun durationTogetherWithRepsOrWeightIsRejected() {
        assertEquals(PlannedSetsError.Duration, validatePlannedSets(listOf(timed(3000, reps = 8))))
        assertEquals(PlannedSetsError.Duration, validatePlannedSets(listOf(timed(3000, weight = 20.0))))
    }

    @Test
    fun liftingSetsKeepTheirRules() {
        assertEquals(PlannedSetsError.Reps, validatePlannedSets(listOf(PlannedSet(null, 0))))
        assertNull(validatePlannedSets(listOf(PlannedSet(null, 8))))
    }

    @Test
    fun formatHoursMinutes_followsRule47() {
        assertEquals("50 min", DurationFormat.formatHoursMinutes(50))
        assertEquals("1 h 50 min", DurationFormat.formatHoursMinutes(110))
        assertEquals("1 h", DurationFormat.formatHoursMinutes(60))
        assertEquals("0 min", DurationFormat.formatHoursMinutes(-5))
    }

    @Test
    fun draftFromRoutine_buildsADurationEntryFromAPlannedDuration() {
        val routine = Routine(
            id = "r1",
            name = "Dia 1",
            notes = null,
            archived = false,
            exercises = listOf(
                RoutineExercise("c1", 0, null, listOf(PlannedSet.ofMinutes(50)), restSeconds = 0),
                RoutineExercise("e1", 1, null, listOf(PlannedSet(40.0, 8)), restSeconds = 90),
            ),
        )

        val draft = WorkoutDraftFactory.fromRoutine(
            routine,
            mapOf("c1" to ExerciseMetricType.Duration),
            LocalDate(2026, 11, 2),
        )

        assertEquals(SetEntry.Duration(3000), draft.exercises[0].sets.single().entry)
        assertEquals(SetEntry.WeightReps(40.0, 8), draft.exercises[1].sets.single().entry)
    }
}
