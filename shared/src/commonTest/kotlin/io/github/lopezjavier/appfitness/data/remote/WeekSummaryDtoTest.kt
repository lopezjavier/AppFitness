package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.LiftStatus
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.RecordType
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeekSummaryDtoTest {
    private fun parse(json: String) = weekSummaryOf(Json.parseToJsonElement(json))

    private val head = """"plan_id":"p1","week":3,"start_date":"2027-02-01","end_date":"2027-02-07","seen":false"""

    private fun v2(progress: String) = """{$head,"schema_version":2,"progress":$progress}"""

    private val full = """{
        "as_of":"2027-02-07","refreshed_at":"2027-02-08T10:00:00Z","final":false,"intention":"fat_loss","phase":"cut",
        "missing":["weigh_ins","diary","deleted_sessions","deleted_weigh_ins","future_code"],
        "context":{"sessions_done":3,"sessions_planned":4,"sets_done":18,"sets_planned":20},
        "strength":{"block_week":3,"groups":[
          {"muscle_group":"chest","exercises":[
            {"exercise_id":"bench","main_lift":true,"state":"up",
             "baseline":{"date":"2027-01-18","load_kg":80,"reps":[8,8,7],"rpe":8},
             "best":{"date":"2027-02-03","load_kg":82.5,"reps":[6,6],"rpe":null},"discomfort_id":null},
            {"exercise_id":"fly","main_lift":false,"state":"future_state","baseline":null,"best":null},
            {"exercise_id":"press","main_lift":false,"state":"paused","baseline":null,"best":null,"discomfort_id":"d1"}
          ]},
          {"muscle_group":"unknown_group","exercises":[]}]},
        "lifts":[{"exercise_id":"bench","weeks":[{"week":1,"e1rm_kg":100.5,"deload":false},
                                                 {"week":2,"e1rm_kg":null,"deload":true}],
                  "state":"up","change_kg":4.0,"change_pct":4.2},
                 {"exercise_id":"squat","weeks":[],"state":"weird"}],
        "records":[{"exercise_id":"bench","kind":"e1rm","value_kg":92.5,"previous_kg":90,"main_lift":true},
                   {"exercise_id":"row","kind":"other","value_kg":1,"previous_kg":1,"main_lift":false}],
        "weight":{"avg_kg":80.2,"days":4,"prev_avg_kg":80.5,"prev_days":5,"change_kg":-0.3,"start_avg_kg":82,
                  "phase_weeks":3,"change_since_start_kg":-1.8,
                  "series":[{"start_date":"2027-01-18","avg_kg":82.0,"days":4},{"start_date":"2027-01-25","avg_kg":80.5,"days":5}],
                  "outcome":{"kg":75,"deadline":"2027-05-01"},
                  "trend":{"slope":-0.6,"low":-1.209,"high":0.009,"weeks":4},"rate_kg_week":-0.4},
        "waist":{"cm":84.0,"date":"2027-02-05","ref_cm":86.5,"ref_date":"2027-01-01","change_cm":-2.5}
    }"""

    @Test
    fun v2Snapshot_parsesEveryBlock() {
        val summary = assertNotNull(parse(v2(full)))
        val progress = assertNotNull(summary.progress)

        assertEquals(2, summary.schemaVersion)
        assertEquals(TrainingIntention.FatLoss, progress.intention)
        assertEquals(Phase.Cut, progress.phase)
        assertEquals(false, progress.final)
        assertEquals(LocalDate(2027, 2, 7), progress.asOf)
        assertNotNull(progress.refreshedAt)
        assertEquals(3, progress.context.sessionsDone)
        assertEquals(20, progress.context.setsPlanned)
        assertEquals(3, progress.strength.blockWeek)
        assertEquals(1, progress.lifts.size)
        assertEquals(LiftStatus.Up, progress.lifts.single().state)
        assertEquals(listOf(100.5, null), progress.lifts.single().weeks.map { it.e1rmKg })
        assertTrue(progress.lifts.single().weeks[1].deload)
        assertEquals(RecordType.E1rm, progress.records.single().kind)
        assertEquals(LocalDate(2027, 5, 1), progress.weight.outcome?.deadline)
        assertEquals(-0.6, progress.weight.trend?.slope)
        assertEquals(4, progress.weight.trend?.weeks)
        assertEquals(-0.4, progress.weight.rateKgWeek)
        assertEquals(2, progress.weight.series.size)
        assertEquals(-2.5, progress.waist?.changeCm)
    }

    @Test
    fun strength_readsRepsAsIntListAndCarriedDate() {
        val chest = assertNotNull(parse(v2(full))?.progress).strength.groups.single()
        val bench = chest.exercises.first()

        assertEquals(MuscleGroup.Chest, chest.muscleGroup)
        assertEquals(listOf(8, 8, 7), bench.baseline?.reps)
        assertEquals(LocalDate(2027, 2, 3), bench.best?.date)
        assertEquals(82.5, bench.best?.loadKg)
        assertNull(bench.best?.rpe)
        assertEquals(8.0, bench.baseline?.rpe)
    }

    @Test
    fun unknownExerciseState_dropsThatExerciseOnly() {
        val chest = assertNotNull(parse(v2(full))?.progress).strength.groups.single()

        assertEquals(listOf("bench", "press"), chest.exercises.map { it.exerciseId })
        assertEquals(ProgressState.Paused, chest.exercises.last().state)
    }

    @Test
    fun unknownLiftStateAndRecordKind_areDropped() {
        val progress = assertNotNull(parse(v2(full))?.progress)

        assertEquals(listOf("bench"), progress.lifts.map { it.exerciseId })
        assertEquals(listOf("bench"), progress.records.map { it.exerciseId })
    }

    @Test
    fun missingCodes_mapKnownOnesAndDropUnknown() {
        val progress = assertNotNull(parse(v2(full))?.progress)

        assertEquals(
            listOf(MissingKind.WeighIns, MissingKind.Food, MissingKind.DeletedSessions, MissingKind.DeletedWeighIns),
            progress.missing,
        )
    }

    @Test
    fun v1Snapshot_hasNoProgressAndSchemaVersion1() {
        val summary = assertNotNull(parse("""{$head,"sessions_done":3}"""))

        assertNull(summary.progress)
        assertEquals(1, summary.schemaVersion)
        assertEquals(3, summary.sessionsDone)
    }

    @Test
    fun pausedWeek_hasNoProgress() {
        val summary = assertNotNull(
            parse(
                """{"plan_id":"p1","paused":true,"start_date":"2027-02-01","end_date":"2027-02-07",""" +
                    """"schema_version":2}""",
            ),
        )

        assertTrue(summary.paused)
        assertNull(summary.progress)
        assertEquals(2, summary.schemaVersion)
    }

    @Test
    fun missingOptionalBlocks_hideOnlyThatBlock() {
        val progress = assertNotNull(
            parse(
                v2(
                    """{"intention":"weird","phase":"cut","context":{"sessions_done":1,"sessions_planned":2,"sets_done":5,"sets_planned":null},
                    "strength":{"block_week":1,"groups":[]},"lifts":[],"records":[],
                    "weight":{"avg_kg":null,"days":0,"prev_days":0,"series":[],"outcome":null,
                    "trend":{"slope":null,"low":null,"high":null,"weeks":1},"rate_kg_week":null}}""",
                ),
            )?.progress,
        )

        assertNull(progress.waist)
        assertNull(progress.weight.outcome)
        assertNull(progress.weight.changeKg)
        assertNull(progress.context.setsPlanned)
        assertNull(progress.intention)
        assertNull(progress.weight.rateKgWeek)
        assertTrue(progress.missing.isEmpty())
    }

    @Test
    fun missingWeightBlock_givesAnEmptyWeight() {
        val progress = assertNotNull(parse(v2("""{"strength":{"block_week":2,"groups":[]}}"""))?.progress)

        assertNull(progress.weight.avgKg)
        assertEquals(0, progress.weight.days)
        assertNull(progress.weight.trend)
    }
}
