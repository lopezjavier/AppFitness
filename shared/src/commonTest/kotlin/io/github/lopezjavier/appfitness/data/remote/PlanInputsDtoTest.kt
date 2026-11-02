package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CurrentPlan
import io.github.lopezjavier.appfitness.domain.model.PlanFixTarget
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputSource
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanInputsDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(text: String): PlanInputs = json.decodeFromString<PlanInputsDto>(text).toDomain()

    private fun entry(
        key: String = "sex",
        value: String = "null",
        source: String = "profile",
        status: String = "ok",
        fix: String = "null",
        extra: String = "",
    ) = """{"key":"$key","value":$value,"source":"$source","status":"$status","blocking":false,""" +
        """"fix_target":$fix$extra}"""

    private fun list(vararg entries: String, current: String = "") =
        """{"kind":"plan","as_of":"2026-10-29"$current,"inputs":[${entries.joinToString(",")}]}"""

    @Test
    fun allStatuses_areMapped() {
        val statuses = listOf("ok", "missing", "unset_optional", "provisional", "stale")

        val result = decode(list(*statuses.map { entry(status = it) }.toTypedArray()))

        assertEquals(
            listOf(
                PlanInputStatus.Ok,
                PlanInputStatus.Missing,
                PlanInputStatus.UnsetOptional,
                PlanInputStatus.Provisional,
                PlanInputStatus.Stale,
            ),
            result.inputs.map { it.status },
        )
    }

    @Test
    fun allFixTargets_areMapped_andNullStaysNull() {
        val result = decode(
            list(
                entry(fix = "\"my_training\""),
                entry(fix = "\"personal_data\""),
                entry(fix = "\"weight\""),
                entry(fix = "\"activity_sheet\""),
                entry(fix = "null"),
            ),
        )

        assertEquals(
            listOf(
                PlanFixTarget.MyTraining,
                PlanFixTarget.PersonalData,
                PlanFixTarget.Weight,
                PlanFixTarget.ActivitySheet,
                null,
            ),
            result.inputs.map { it.fixTarget },
        )
    }

    @Test
    fun allSources_areMapped() {
        val result = decode(
            list(
                entry(source = "profile"),
                entry(source = "my_training"),
                entry(source = "weigh_in"),
                entry(source = "coach_estimate"),
            ),
        )

        assertEquals(
            listOf(
                PlanInputSource.Profile,
                PlanInputSource.MyTraining,
                PlanInputSource.WeighIn,
                PlanInputSource.CoachEstimate,
            ),
            result.inputs.map { it.source },
        )
    }

    @Test
    fun allKeys_areMapped() {
        val keys = listOf(
            "intention", "sex", "age", "height", "weight", "activity_level", "total_expenditure", "weight_trend",
            "diary", "place", "equipment", "cardio", "days_per_week", "session_minutes", "experience", "intake",
        )

        val result = decode(list(*keys.map { entry(key = it) }.toTypedArray()))

        assertEquals(PlanInputKey.entries - PlanInputKey.Unknown, result.inputs.map { it.key })
    }

    @Test
    fun weighIn_carriesItsDate() {
        val result =
            decode(list(entry(key = "weight", value = "82.5", source = "weigh_in", extra = ""","date":"2026-10-20"""")))

        assertEquals(LocalDate(2026, 10, 20), result.inputs.single().date)
        assertEquals(PlanInputValue.Number(82.5), result.inputs.single().value)
    }

    @Test
    fun currentPlan_isAbsentAcceptedOrProposed() {
        assertNull(decode(list()).currentPlan)
        assertEquals(CurrentPlan.Accepted, decode(list(current = ""","current_plan":"accepted"""")).currentPlan)
        assertEquals(CurrentPlan.Proposed, decode(list(current = ""","current_plan":"proposed"""")).currentPlan)
    }

    @Test
    fun kind_isMapped() {
        assertEquals(PlanInputKind.Plan, decode(list()).kind)
        assertEquals(PlanInputKind.Block, decode("""{"kind":"block","inputs":[]}""").kind)
    }

    @Test
    fun valueTypes_areText_Number_Flag_Items_Date_andAbsent() {
        val result = decode(
            list(
                entry(value = "\"male\""),
                entry(value = "31"),
                entry(value = "true"),
                entry(value = """["dumbbells","barbell"]"""),
                entry(value = "\"2026-10-20\""),
                entry(value = "null"),
            ),
        )

        assertEquals(
            listOf(
                PlanInputValue.Text("male"),
                PlanInputValue.Number(31.0),
                PlanInputValue.Flag(true),
                PlanInputValue.Items(listOf("dumbbells", "barbell")),
                PlanInputValue.Date(LocalDate(2026, 10, 20)),
                PlanInputValue.Absent,
            ),
            result.inputs.map { it.value },
        )
    }

    @Test
    fun missingValueField_isAbsent() {
        val result = decode("""{"kind":"plan","inputs":[{"key":"sex","status":"missing","blocking":true}]}""")

        assertEquals(PlanInputValue.Absent, result.inputs.single().value)
        assertEquals(true, result.inputs.single().blocking)
    }

    @Test
    fun unknownKeyStatusSourceAndFixTarget_becomeUnknownWithoutCrashing() {
        val result = decode(list(entry(key = "shoe_size", source = "oracle", status = "weird", fix = "\"somewhere\"")))

        val input = result.inputs.single()
        assertEquals(PlanInputKey.Unknown, input.key)
        assertEquals(PlanInputSource.Unknown, input.source)
        assertEquals(PlanInputStatus.Unknown, input.status)
        assertNull(input.fixTarget)
    }

    @Test
    fun intakeEntry_okMeansSaved_isNeverBlocking_andDoesNotBreakNeighbours() {
        val result = decode(
            list(
                entry(
                    key = "intake",
                    value = """{"saved":true,"situation":"continuous"}""",
                    source = "intake",
                    status = "ok",
                    fix = "\"intake\"",
                ),
                entry(key = "sex"),
            ),
        )

        val input = result.inputs.first()
        assertEquals(PlanInputKey.Intake, input.key)
        assertEquals(PlanInputSource.Intake, input.source)
        assertEquals(PlanFixTarget.Intake, input.fixTarget)
        assertEquals(false, input.blocking)
        assertEquals(true, result.intakeSaved)
        assertEquals(PlanInputKey.Sex, result.inputs.last().key)
    }

    @Test
    fun intakeEntry_unsetOptionalMeansNotSaved() {
        val result = decode(
            list(
                entry(
                    key = "intake",
                    value = """{"saved":false,"situation":"somethingNew"}""",
                    source = "intake",
                    status = "unset_optional",
                    fix = "\"intake\"",
                ),
            ),
        )

        assertEquals(false, result.intakeSaved)
    }

    @Test
    fun listWithoutIntakeEntry_isNotSaved() {
        assertEquals(false, decode(list(entry(key = "sex"))).intakeSaved)
    }

    @Test
    fun params_encodeKindAndToday() {
        val plan = PlanInputsParams.of(PlanInputKind.Plan, LocalDate(2026, 10, 29))
        val block = PlanInputsParams.of(PlanInputKind.Block, LocalDate(2026, 10, 29))

        assertEquals(
            """{"p_kind":"plan","p_today":"2026-10-29"}""",
            Json.encodeToString(PlanInputsParams.serializer(), plan),
        )
        assertEquals("block", block.kind)
    }
}
