package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachIntakeDtoTest {
    private fun row(text: String) = coachPlanJson.decodeFromString<CoachIntakeDto>(text).toDomain()

    private fun body(update: IntakeUpdate) =
        coachPlanJson.encodeToString(JsonObject.serializer(), SaveIntakeParams.of(update))

    @Test
    fun fullRow_mapsTheFields() {
        val intake = row(
            """{"user_id":"u1","training_years":4.5,"known_maintenance_kcal":2400,""" +
                """"maintenance_handover_at":null,"saved_at":"2026-11-02T10:00:00+00:00"}""",
        )

        assertEquals(4.5, intake.trainingYears)
        assertEquals(2400, intake.knownMaintenanceKcal)
        assertEquals(true, intake.maintenanceHandoverPending)
    }

    @Test
    fun rowWithTheRemovedFreeTextField_isDecodedAndTheFieldIgnored() {
        val intake = row("""{"training_years":4.5,"free_text":"hola"}""")

        assertEquals(CoachIntake(trainingYears = 4.5), intake)
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
        assertEquals(false, row("""{"training_years":2}""").maintenanceHandoverPending)
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

        assertEquals(CoachIntake(trainingYears = 3.0), intake)
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
    fun saveParams_nullValue_isAnExplicitNullThatClears() {
        assertEquals("""{"p":{"known_maintenance_kcal":null}}""", body(IntakeUpdate.KnownMaintenance(null)))
        assertEquals("""{"p":{"training_years":null}}""", body(IntakeUpdate.TrainingYears(null)))
    }
}
