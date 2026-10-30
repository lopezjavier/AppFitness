package io.github.lopezjavier.appfitness.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun exerciseJson(id: String, repMin: Int = 8, repMax: Int = 12) =
    """{"exercise_id":"$id","exercise_key":"k-$id","role":"main","muscle_group":"chest","sets":3,""" +
        """"rep_min":$repMin,"rep_max":$repMax,"rest_seconds":90,"reason":"Porque si","future_field":1}"""

private fun sessionJson(week: Int, index: Int, name: String, exercises: String, routine: String = "null") =
    """{"id":"s$week$index","plan_id":"p1","week":$week,"session_index":$index,"name":"$name",""" +
        """"focus":["chest"],"deload":false,"exercises":[$exercises],"routine_id":$routine}"""

private fun trainingJson(days: Int, chosen: Boolean) =
    """{"days_per_week":$days,"days_chosen":$chosen,"session_minutes":60,"rep_scheme":"hypertrophy",""" +
        """"block_weeks":4,"equipment":["dumbbell","bodyweight"],"weekly_sets":{"chest":10,"back":12},""" +
        """"sessions_per_muscle":{"chest":2,"back":2},"extra":true}"""

class CoachBlockDtoTest {
    private fun plan(training: String?, folder: String? = null) = coachPlanJson.decodeFromString<CoachBlockPlanDto>(
        """{"id":"p1","user_id":"u","status":"proposed","training":${training ?: "null"},""" +
            """"folder_id":${folder?.let { "\"$it\"" } ?: "null"}}""",
    )

    private fun sessions(vararg json: String) =
        coachPlanJson.decodeFromString<List<BlockSessionDto>>("[" + json.joinToString(",") + "]")

    @Test
    fun threeDayBlock_decodesTrainingAndSessionsIgnoringUnknownKeys() {
        val block = blockOf(
            plan(trainingJson(3, chosen = false)),
            sessions(
                sessionJson(1, 0, "Empuje", exerciseJson("e1")),
                sessionJson(1, 1, "Tirón", exerciseJson("e2")),
                sessionJson(1, 2, "Pierna", exerciseJson("e3")),
            ),
        )

        assertNotNull(block)
        assertEquals(3, block.training.daysPerWeek)
        assertFalse(block.training.daysChosen)
        assertEquals(60, block.training.sessionMinutes)
        assertEquals(listOf("dumbbell", "bodyweight"), block.training.equipment)
        assertEquals(mapOf("chest" to 10, "back" to 12), block.training.weeklySets)
        assertEquals(mapOf("chest" to 2, "back" to 2), block.training.sessionsPerMuscle)
        assertEquals(3, block.sessions.size)
        val exercise = block.sessions.first().exercises.single()
        assertEquals("e1", exercise.exerciseId)
        assertEquals("k-e1", exercise.exerciseKey)
        assertEquals(90, exercise.restSeconds)
        assertEquals("Porque si", exercise.reason)
        assertFalse(block.saved)
    }

    @Test
    fun oneDayFullBody_isASingleSessionPerWeek() {
        val block = blockOf(
            plan(trainingJson(1, chosen = false)),
            sessions(
                sessionJson(1, 0, "Cuerpo completo", exerciseJson("e1")),
                sessionJson(2, 0, "Cuerpo completo", ""),
            ),
        )

        assertNotNull(block)
        assertEquals(1, block.training.daysPerWeek)
        assertEquals(listOf(1, 1), block.weeks().map { it.sessions.size })
    }

    @Test
    fun daysChosenTrue_isKept() {
        val block = blockOf(plan(trainingJson(4, chosen = true)), sessions(sessionJson(1, 0, "A", "")))

        assertTrue(assertNotNull(block).training.daysChosen)
    }

    @Test
    fun strengthSessions_keepTheirOwnRepRanges() {
        val block = blockOf(
            plan(trainingJson(2, chosen = false)),
            sessions(
                sessionJson(1, 0, "Fuerza A", exerciseJson("e1", repMin = 3, repMax = 5)),
                sessionJson(1, 1, "Fuerza B", exerciseJson("e1", repMin = 6, repMax = 8)),
            ),
        )

        val ranges = assertNotNull(block).sessions.map { it.exercises.single().repMin to it.exercises.single().repMax }
        assertEquals(listOf(3 to 5, 6 to 8), ranges)
    }

    @Test
    fun aRoutineIdOrAFolder_marksTheBlockSaved() {
        val withRoutine = blockOf(plan(trainingJson(1, false)), sessions(sessionJson(1, 0, "A", "", "\"r1\"")))
        val withFolder = blockOf(plan(trainingJson(1, false), folder = "f1"), sessions(sessionJson(1, 0, "A", "")))

        assertTrue(assertNotNull(withRoutine).saved)
        assertTrue(assertNotNull(withFolder).saved)
    }

    @Test
    fun withoutSessionsOrTraining_thereIsNoBlock() {
        assertNull(blockOf(plan(trainingJson(1, false)), emptyList()))
        assertNull(blockOf(plan(null), sessions(sessionJson(1, 0, "A", ""))))
    }
}
