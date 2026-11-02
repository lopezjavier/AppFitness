package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.CoachIntake
import io.github.lopezjavier.appfitness.domain.model.CoachIntakeDraft
import io.github.lopezjavier.appfitness.domain.model.FreeTextKind
import io.github.lopezjavier.appfitness.domain.model.FreeTextStatus
import io.github.lopezjavier.appfitness.domain.model.InjuredArea
import io.github.lopezjavier.appfitness.domain.model.PreBreakBest
import io.github.lopezjavier.appfitness.domain.model.Situation
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachIntakeDtoTest {
    private fun row(text: String) = coachPlanJson.decodeFromString<CoachIntakeDto>(text).toDomain()

    @Test
    fun fullRow_mapsEveryField() {
        val intake = row(
            """{"user_id":"u1","training_years":4.5,"situation":"returning_injury","injured_area":"shoulder",""" +
                """"pre_break_bests":[{"exercise_id":"e1","weight_kg":90,"reps":5}],"known_maintenance_kcal":2400,""" +
                """"known_cut_kcal":1900,"free_text":"hola","maintenance_handover_at":null,""" +
                """"saved_at":"2026-11-02T10:00:00+00:00","updated_at":"2026-11-02T10:00:00+00:00"}""",
        )

        assertEquals(4.5, intake.trainingYears)
        assertEquals(Situation.ReturningInjury, intake.situation)
        assertEquals(InjuredArea.Shoulder, intake.injuredArea)
        assertEquals(listOf(PreBreakBest("e1", 90.0, 5)), intake.preBreakBests)
        assertEquals(2400, intake.knownMaintenanceKcal)
        assertEquals(1900, intake.knownCutKcal)
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
    fun emptyRow_mapsToNullsAndEmptyBests() {
        assertEquals(CoachIntake(maintenanceHandoverPending = false), row("{}"))
    }

    @Test
    fun unknownEnumValuesAndKeys_areToleratedNeverCrash() {
        val intake = row(
            """{"situation":"on_vacation","injured_area":"neck","future":{"a":1},""" +
                """"pre_break_bests":[{"exercise_id":"e1","weight_kg":60,"reps":8,"rpe":9}]}""",
        )

        assertNull(intake.situation)
        assertEquals(InjuredArea.Other, intake.injuredArea)
        assertEquals(1, intake.preBreakBests.size)
    }

    @Test
    fun saveParams_sendOnlyFilledFieldsWithWireNames() {
        val params = SaveIntakeParams.of(
            CoachIntake(
                trainingYears = 2.0,
                situation = Situation.ReturningBreak,
                injuredArea = InjuredArea.LowerBack,
                preBreakBests = listOf(PreBreakBest("e1", 100.0, 3)),
                knownMaintenanceKcal = 2500,
                freeText = "texto",
            ),
        )

        assertEquals(
            """{"p":{"training_years":2.0,"situation":"returning_break","injured_area":"lower_back",""" +
                """"pre_break_bests":[{"exercise_id":"e1","weight_kg":100.0,"reps":3}],""" +
                """"known_maintenance_kcal":2500,"free_text":"texto"}}""",
            coachPlanJson.encodeToString(params),
        )
    }

    @Test
    fun extractBody_carriesTheTextAndOnlyTheFilledFields() {
        val body = ExtractRequestBody.of(
            "Entrené 4 años",
            CoachIntakeDraft(trainingYears = 3.0, situation = Situation.Continuous),
            "haiku",
        )

        assertEquals(
            """{"kind":"intake_extract","input":{"free_text":"Entrené 4 años",""" +
                """"fields":{"training_years":3.0,"situation":"continuous"}},"model":"haiku"}""",
            coachPlanJson.encodeToString(body),
        )
    }

    @Test
    fun extractBody_withoutFields_omitsThem() {
        assertEquals(
            """{"kind":"intake_extract","input":{"free_text":"hola"}}""",
            coachPlanJson.encodeToString(ExtractRequestBody.of("hola", null, null)),
        )
    }

    @Test
    fun extractResult_usExample_mapsToTheDraft() {
        val draft = coachPlanJson.decodeFromString<ExtractResultDto>(
            """{"fields":{"training_years":4,"situation":"returning_injury","injured_area":"shoulder",""" +
                """"pre_break_bests":[{"exercise_id":"bench","weight_kg":90,"reps":5}],""" +
                """"known_maintenance_kcal":null,"known_cut_kcal":null}}""",
        ).toDomain()

        assertEquals(
            CoachIntakeDraft(
                trainingYears = 4.0,
                situation = Situation.ReturningInjury,
                injuredArea = InjuredArea.Shoulder,
                preBreakBests = listOf(PreBreakBest("bench", 90.0, 5)),
            ),
            draft,
        )
    }

    @Test
    fun extractResult_unknownValues_becomeNull() {
        val draft = coachPlanJson.decodeFromString<ExtractResultDto>(
            """{"fields":{"situation":"nope","extra":1}}""",
        ).toDomain()

        assertEquals(CoachIntakeDraft(), draft)
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
