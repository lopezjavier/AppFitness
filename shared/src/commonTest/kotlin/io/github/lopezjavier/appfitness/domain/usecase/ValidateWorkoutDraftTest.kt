package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DraftExercise
import io.github.lopezjavier.appfitness.domain.model.DraftWorkoutSet
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraft
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ValidateWorkoutDraftTest {
    // FixedTestClock.today() is 2026-10-22.
    private val validate = ValidateWorkoutDraft(FixedTestClock(Instant.parse("2026-10-22T10:00:00Z")))

    private fun set(entry: SetEntry = SetEntry.WeightReps(80.0, 5), rpe: Double? = null) =
        DraftWorkoutSet(SetType.Working, rpe, entry)

    private fun exercise(vararg sets: DraftWorkoutSet, group: Int? = null) =
        DraftExercise("e1", group, 90, sets.toList())

    private fun draft(
        name: String = "Torso",
        date: LocalDate = LocalDate(2026, 10, 20),
        description: String? = null,
        gym: String? = null,
        minutes: Int = 0,
        exercises: List<DraftExercise> = listOf(exercise(set())),
    ) = WorkoutDraft(name, description, gym, date, null, minutes, exercises)

    @Test
    fun aCompleteDraftHasNoIssues() {
        assertTrue(validate(draft()).isEmpty())
    }

    @Test
    fun blankNameIsFlagged() {
        assertEquals(listOf(WorkoutDraftIssue.Name), validate(draft(name = "   ")))
    }

    @Test
    fun nameBoundaryIs100Characters() {
        assertTrue(validate(draft(name = "a".repeat(100))).isEmpty())
        assertEquals(listOf(WorkoutDraftIssue.Name), validate(draft(name = "a".repeat(101))))
    }

    @Test
    fun descriptionAndGymLengthsAreBounded() {
        assertTrue(validate(draft(description = "d".repeat(1000), gym = "g".repeat(100))).isEmpty())
        assertEquals(
            listOf(WorkoutDraftIssue.Description, WorkoutDraftIssue.Gym),
            validate(draft(description = "d".repeat(1001), gym = "g".repeat(101))),
        )
    }

    @Test
    fun durationMustBeBetweenZeroAnd24Hours() {
        assertTrue(validate(draft(minutes = 1440)).isEmpty())
        assertEquals(listOf(WorkoutDraftIssue.Duration), validate(draft(minutes = 1441)))
        assertEquals(listOf(WorkoutDraftIssue.Duration), validate(draft(minutes = -1)))
    }

    @Test
    fun aWorkoutWithoutExercisesIsFlagged() {
        assertEquals(listOf(WorkoutDraftIssue.Exercises), validate(draft(exercises = emptyList())))
    }

    @Test
    fun anExerciseWithoutSetsIsFlaggedByPosition() {
        val issues = validate(draft(exercises = listOf(exercise(set()), exercise())))

        assertEquals(listOf(WorkoutDraftIssue.ExerciseSets(1)), issues)
    }

    @Test
    fun repsMustBeBetweenOneAnd999() {
        assertEquals(
            listOf(WorkoutDraftIssue.SetValue(0, 0)),
            validate(draft(exercises = listOf(exercise(set(SetEntry.WeightReps(50.0, 0)))))),
        )
        assertEquals(
            listOf(WorkoutDraftIssue.SetValue(0, 0)),
            validate(draft(exercises = listOf(exercise(set(SetEntry.WeightReps(50.0, 1000)))))),
        )
        assertTrue(validate(draft(exercises = listOf(exercise(set(SetEntry.WeightReps(0.0, 999)))))).isEmpty())
    }

    @Test
    fun negativeWeightIsFlaggedOnTheSetThatHasIt() {
        val issues = validate(
            draft(exercises = listOf(exercise(set(), set(SetEntry.WeightReps(-1.0, 5))))),
        )

        assertEquals(listOf(WorkoutDraftIssue.SetValue(0, 1)), issues)
    }

    @Test
    fun timeMetricsNeedAPositiveDurationAndIgnoreReps() {
        assertEquals(
            listOf(WorkoutDraftIssue.SetValue(0, 0)),
            validate(draft(exercises = listOf(exercise(set(SetEntry.Duration(0)))))),
        )
        assertTrue(validate(draft(exercises = listOf(exercise(set(SetEntry.Duration(45)))))).isEmpty())
    }

    @Test
    fun rpeAcceptsWholeNumbersFromSixToTen() {
        listOf(6.0, 8.0, 10.0).forEach { rpe ->
            assertTrue(validate(draft(exercises = listOf(exercise(set(rpe = rpe))))).isEmpty(), "rpe $rpe")
        }
        listOf(5.0, 7.5, 10.5).forEach { rpe ->
            assertEquals(
                listOf(WorkoutDraftIssue.SetRpe(0, 0)),
                validate(draft(exercises = listOf(exercise(set(rpe = rpe))))),
                "rpe $rpe",
            )
        }
    }

    @Test
    fun aFutureDateIsRejectedButTodayAndThePastAreAccepted() {
        assertEquals(listOf(WorkoutDraftIssue.Date), validate(draft(date = LocalDate(2026, 10, 23))))
        assertTrue(validate(draft(date = LocalDate(2026, 10, 22))).isEmpty())
        assertTrue(validate(draft(date = LocalDate(2015, 1, 1))).isEmpty())
    }

    @Test
    fun anEditSkipsTheDateCheck() {
        assertTrue(validate(draft(date = LocalDate(2026, 12, 1)), checkDate = false).isEmpty())
    }

    @Test
    fun supersetMembersMustAgreeOnTheNumberOfSets() {
        val issues = validate(
            draft(exercises = listOf(exercise(set(), set(), group = 1), exercise(set(), group = 1))),
        )

        assertEquals(listOf(WorkoutDraftIssue.Superset), issues)
    }
}
