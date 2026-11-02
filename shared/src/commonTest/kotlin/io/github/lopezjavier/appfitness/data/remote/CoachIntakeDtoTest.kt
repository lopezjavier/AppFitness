package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoachIntakeDtoTest {
    private fun row(text: String) = coachPlanJson.decodeFromString<CoachIntakeDto>(text).toDomain()

    private fun body(update: IntakeUpdate) =
        coachPlanJson.encodeToString(JsonObject.serializer(), SaveIntakeParams.of(update))

    @Test
    fun fullRow_mapsTheThreeFields() {
        val intake = row(
            """{"user_id":"u1","training_years":4.5,"known_maintenance_kcal":2400,"free_text":"hola",""" +
                """"maintenance_handover_at":null,"saved_at":"2026-11-02T10:00:00+00:00"}""",
        )

        assertEquals(4.5, intake.trainingYears)
        assertEquals(2400, intake.knownMaintenanceKcal)
        assertEquals("hola", intake.freeText)
        assertEquals(true, intake.maintenanceHandoverPending)
    }

    @Test
    fun handoverAlreadyShown_isNotPending() {
        val intake = row(
            """{"known_maintenance_kcal":2400,"maintenance_handover_at":"2026-11-02T10:00:00+00:00"}""",
        )

        assertEquals(false, intake.maintenanceHandoverPending)
    }

    @Test
    fun withoutMaintenance_handoverIsNotPending() {
        assertEquals(false, row("""{"free_text":"x"}""").maintenanceHandoverPending)
    }

    @Test
    fun emptyRow_mapsToNulls() {
        assertEquals(CoachIntake(), row("{}"))
    }

    @Test
    fun oldRowWithDroppedColumns_isToleratedAndIgnored() {
        val intake = row(
            """{"training_years":3,"situation":"returning_injury","injured_area":"shoulder",""" +
                """"pre_break_bests":[{"exercise_id":"e1","weight_kg":90,"reps":5}],"known_cut_kcal":1900,""" +
                """"free_text":"texto","future":{"a":1}}""",
        )

        assertEquals(CoachIntake(trainingYears = 3.0, freeText = "texto"), intake)
    }

    @Test
    fun saveParams_trainingYears_sendOnlyThatKey() {
        assertEquals("""{"p":{"training_years":2.5}}""", body(IntakeUpdate.TrainingYears(2.5)))
    }

    @Test
    fun saveParams_maintenance_sendOnlyThatKey() {
        assertEquals("""{"p":{"known_maintenance_kcal":2500}}""", body(IntakeUpdate.KnownMaintenance(2500)))
    }

    @Test
    fun saveParams_freeText_sendOnlyThatKey() {
        assertEquals("""{"p":{"free_text":"texto"}}""", body(IntakeUpdate.FreeText("texto")))
    }

    @Test
    fun saveParams_nullValue_isAnExplicitNullThatClears() {
        assertEquals("""{"p":{"known_maintenance_kcal":null}}""", body(IntakeUpdate.KnownMaintenance(null)))
        assertEquals("""{"p":{"training_years":null}}""", body(IntakeUpdate.TrainingYears(null)))
        assertEquals("""{"p":{"free_text":null}}""", body(IntakeUpdate.FreeText(null)))
    }

    @Test
    fun freeTextLine_mapsKindStatusAndDate() {
        val line = coachPlanJson.decodeFromString<FreeTextLineDto>(
            """{"kind":"check_in","source_id":"2026-10-26","date":"2026-10-28","text":"duermo mal",""" +
                """"status":"over_limit"}""",
        ).toDomain()

        assertEquals(FreeTextKind.CheckIn, line.kind)
        assertEquals("2026-10-26", line.sourceId)
        assertEquals(LocalDate(2026, 10, 28), line.date)
        assertEquals(FreeTextStatus.OverLimit, line.status)
        assertTrue(line.text.isNotEmpty())
    }

    @Test
    fun freeTextLine_unknownKindAndStatus_doNotCrash() {
        val line = coachPlanJson.decodeFromString<FreeTextLineDto>(
            """{"kind":"podcast","source_id":"x","date":"2026-10-28","text":"t","status":"archived"}""",
        ).toDomain()

        assertEquals(FreeTextKind.Unknown, line.kind)
        assertFalse(line.status == FreeTextStatus.Sent)
    }

    @Test
    fun kindWire_roundTripsForTheExclusionParams() {
        assertEquals(
            """{"p_kind":"discomfort","p_id":"d1","p_excluded":true}""",
            coachPlanJson.encodeToString(ExcludeLineParams.of(FreeTextKind.Discomfort, "d1", true)),
        )
    }
}
