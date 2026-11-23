package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.RangeProgressState
import io.github.lopezjavier.appfitness.domain.model.RangeRecordKind
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingStatsRangeDtoTest {
    private fun exercise(id: String, state: String, last: String = """{"date":"2026-10-20","e1rm_kg":106}""") =
        """{"exercise_id":"$id","main_lift":true,"sessions":4,"first":{"date":"2026-10-01","e1rm_kg":100},""" +
            """"last":$last,"change_kg":6,"change_pct":6.0,"state":"$state"}"""

    private fun parse(exercises: String, records: String = """{"items":[],"total":0}""") =
        rangeProgressOf(Json.parseToJsonElement("""{"exercises":[$exercises],"records":$records}"""))

    @Test
    fun states_mapUpMarginDownAndUncompared() {
        val result = parse(
            listOf(
                exercise("a", "up"),
                exercise("b", "margin"),
                exercise("c", "down"),
                exercise("d", "uncompared", last = "null"),
            ).joinToString(","),
        )

        assertEquals(
            listOf(
                RangeProgressState.Up,
                RangeProgressState.Same,
                RangeProgressState.Down,
                RangeProgressState.Uncompared,
            ),
            result.progress.exercises.map { it.state },
        )
    }

    @Test
    fun uncomparedWithNullLast_hasNoLastAndNoChange() {
        val item = parse(
            """{"exercise_id":"d","main_lift":false,"sessions":1,"first":{"date":"2026-10-01","e1rm_kg":80},""" +
                """"last":null,"change_kg":null,"change_pct":null,"state":"uncompared"}""",
        ).progress.exercises.single()

        assertNull(item.lastKg)
        assertNull(item.changeKg)
        assertNull(item.changePct)
        assertEquals(80.0, item.firstKg)
    }

    @Test
    fun exercise_mapsEveryField() {
        val item = parse(exercise("bench", "up")).progress.exercises.single()

        assertEquals("bench", item.exerciseId)
        assertTrue(item.mainLift)
        assertEquals(4, item.sessions)
        assertEquals(100.0, item.firstKg)
        assertEquals(106.0, item.lastKg)
        assertEquals(6.0, item.changeKg)
        assertEquals(6.0, item.changePct)
    }

    @Test
    fun unknownState_dropsThatExerciseOnly() {
        val result = parse(exercise("a", "sideways") + "," + exercise("b", "up"))

        assertEquals(listOf("b"), result.progress.exercises.map { it.exerciseId })
    }

    @Test
    fun emptyExercises_giveAnEmptyProgress() {
        assertTrue(parse("").progress.exercises.isEmpty())
    }

    @Test
    fun records_mapKindsAndTotal() {
        val result = parse(
            "",
            """{"items":[{"exercise_id":"a","kind":"e1rm","value_kg":106,"previous_kg":100,"main_lift":true},""" +
                """{"exercise_id":"b","kind":"heaviest","value_kg":60.5,"previous_kg":57.5,"main_lift":false},""" +
                """{"exercise_id":"c","kind":"volume","value_kg":1,"previous_kg":0,"main_lift":false}],"total":5}""",
        )

        assertEquals(listOf(RangeRecordKind.E1rm, RangeRecordKind.Heaviest), result.records.items.map { it.kind })
        assertEquals(60.5, result.records.items.last().valueKg)
        assertEquals(57.5, result.records.items.last().previousKg)
        assertEquals(5, result.records.total)
    }

    @Test
    fun unreadableBody_isAnEmptyResultAndNeverThrows() {
        val result = rangeProgressOf(Json.parseToJsonElement("[]"))

        assertTrue(result.progress.exercises.isEmpty())
        assertEquals(0, result.records.total)
    }

    @Test
    fun weeklySets_parseEmptyWeeksAndDropUnknownGroups() {
        val weeks = weeklyMuscleSetsOf(
            Json.parseToJsonElement(
                """[{"week_start":"2026-10-12","groups":[{"muscle_group":"chest","sets":4.5},""" +
                    """{"muscle_group":"wings","sets":2}]},{"week_start":"2026-10-19","groups":[]}]""",
            ),
        )

        assertEquals(LocalDate(2026, 10, 12), weeks.first().weekStart)
        assertEquals(listOf(MuscleGroupSets(MuscleGroup.Chest, 4.5)), weeks.first().groups)
        assertTrue(weeks.last().groups.isEmpty())
    }
}
