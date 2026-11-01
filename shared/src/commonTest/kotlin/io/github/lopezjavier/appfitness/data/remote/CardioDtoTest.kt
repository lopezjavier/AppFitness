package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineExercise
import io.github.lopezjavier.appfitness.domain.model.SetType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun week(extra: String = "") = coachPlanJson.decodeFromString<PlanWeekDto>(
        """{"week":1,"start_date":"2026-11-02","end_date":"2026-11-08","phase":"current"$extra}""",
    ).toDomain()

    @Test
    fun planWeek_readsCardioAndStepFigures() {
        val week = week(""","cardio_min_done":100,"cardio_min_target":150,"steps_avg":7800,"steps_target":7000""")

        assertNotNull(week)
        assertEquals(100, week.cardioMinDone)
        assertEquals(150, week.cardioMinTarget)
        assertEquals(7800, week.stepsAvg)
        assertEquals(7000, week.stepsTarget)
    }

    @Test
    fun planWeek_olderPayloadDefaultsToNoCardioAndNoSteps() {
        val week = week()

        assertNotNull(week)
        assertEquals(0, week.cardioMinDone)
        assertNull(week.cardioMinTarget)
        assertNull(week.stepsAvg)
        assertNull(week.stepsTarget)
    }

    @Test
    fun planWeek_nullStepsAverageStaysNullWhenTargetExists() {
        val week = week(""","steps_avg":null,"steps_target":7000""")

        assertNotNull(week)
        assertNull(week.stepsAvg)
        assertEquals(7000, week.stepsTarget)
    }

    private val cardioTraining =
        """{"days_per_week":3,"session_minutes":60,"cardio":{"target_min_week":300,"planned_min_week":180,""" +
            """"on_your_own_min":120,"per_routine_min":[60,60,60],"pace":"suave"},""" +
            """"routine_total_min":[110,110,110]}"""

    @Test
    fun blockTraining_readsCardioPlanAndRoutineTotals() {
        val training = coachPlanJson.decodeFromString<BlockTrainingDto>(cardioTraining).toDomain()

        val cardio = assertNotNull(training.cardio)
        assertEquals(300, cardio.targetMinWeek)
        assertEquals(120, cardio.onYourOwnMin)
        assertEquals(listOf(60, 60, 60), cardio.perRoutineMin)
        assertEquals("suave", cardio.pace)
        assertEquals(listOf(110, 110, 110), training.routineTotalMin)
    }

    @Test
    fun blockTraining_150MinutesSplitsFiftyEachWithNothingOnYourOwn() {
        val training = coachPlanJson.decodeFromString<BlockTrainingDto>(
            """{"cardio":{"target_min_week":150,"planned_min_week":150,"on_your_own_min":0,""" +
                """"per_routine_min":[50,50,50],"pace":"suave"}}""",
        ).toDomain()

        assertEquals(listOf(50, 50, 50), training.cardio?.perRoutineMin)
        assertEquals(0, training.cardio?.onYourOwnMin)
    }

    @Test
    fun blockTraining_storedBeforeCardioHasNoCardioAndNoTotals() {
        val training = coachPlanJson.decodeFromString<BlockTrainingDto>("""{"days_per_week":3}""").toDomain()

        assertNull(training.cardio)
        assertEquals(emptyList(), training.routineTotalMin)
    }

    @Test
    fun blockExercise_cardioSlotCarriesMinutesAndPaceWithoutRepFields() {
        val exercise = coachPlanJson.decodeFromString<BlockExerciseDto>(
            """{"exercise_id":"c1","role":"cardio","sets":1,"duration_min":50,"pace":"suave","reason":"x"}""",
        ).toDomain()

        assertTrue(exercise.isCardio)
        assertEquals(50, exercise.durationMin)
        assertEquals("suave", exercise.pace)
        assertEquals(0, exercise.repMin)
        assertEquals(0, exercise.restSeconds)
    }

    @Test
    fun blockExercise_liftingIsNotCardio() {
        val exercise = coachPlanJson.decodeFromString<BlockExerciseDto>(
            """{"exercise_id":"e1","role":"main","sets":3,"rep_min":8,"rep_max":12,"rest_seconds":90}""",
        ).toDomain()

        assertFalse(exercise.isCardio)
        assertNull(exercise.durationMin)
    }

    @Test
    fun plannedSetRow_withDurationMapsToATimedSetWithoutReps() {
        val row = json.decodeFromString<PlannedSetRowDto>(
            """{"target_reps":null,"target_duration_s":3000,"set_type":"working","set_index":1}""",
        )

        assertEquals(PlannedSet(null, 0, SetType.Working, 3000), PlannedSet.ofMinutes(50))
        assertEquals(3000, row.durationS)
        assertNull(row.reps)
    }

    @Test
    fun saveRequest_sendsDurationAndOmitsRepsForATimedSet_andLiftingIsUnchanged() {
        val routine = Routine(
            id = null,
            name = "Dia 1",
            notes = null,
            archived = false,
            exercises = listOf(
                RoutineExercise("e1", 0, null, listOf(PlannedSet(40.0, 8)), restSeconds = 90),
                RoutineExercise("c1", 1, null, listOf(PlannedSet.ofMinutes(50)), restSeconds = 0),
            ),
        )

        val body = json.encodeToString(SaveRoutineRequest.serializer(), SaveRoutineRequest.from(routine))

        assertTrue("""{"weight_kg":40.0,"reps":8,"set_type":"working"}""" in body)
        assertTrue("""{"duration_s":3000,"set_type":"working"}""" in body)
    }
}
