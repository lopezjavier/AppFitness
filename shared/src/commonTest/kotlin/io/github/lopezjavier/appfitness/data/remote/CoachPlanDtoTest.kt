package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.DietBreakWeek
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseOptions
import io.github.lopezjavier.appfitness.domain.model.PhaseReason
import io.github.lopezjavier.appfitness.domain.model.PlanActivity
import io.github.lopezjavier.appfitness.domain.model.PlanLimit
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.StepsBasis
import io.github.lopezjavier.appfitness.domain.model.phaseForIntention
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NUTRITION_FLOOR =
    """{"target_kcal":1200,"floor_kcal":1200,"limited_by":"floor","expenditure_kcal":1750.5,"rate_pct_week":0.5,""" +
        """"rate_kg_week":-0.375,"strategy":"deficit","protein_g":120,"carbs_g":110.4,"fat_g":40,"protein_pct":40,""" +
        """"carbs_pct":37,"fat_pct":23,"distribution":{"per_meal_g":30,"g_per_kg":0.4,"min_meals":4},""" +
        """"edited_by_user":false,"future_key":{"x":1}}"""

private const val NUTRITION_FREE =
    """{"target_kcal":2600,"floor_kcal":1500,"limited_by":null,"rate_pct_week":0.25,"rate_kg_week":0.2,""" +
        """"strategy":"surplus","protein_g":150,"carbs_g":330,"fat_g":70,"protein_pct":23,"carbs_pct":51,""" +
        """"fat_pct":26,"edited_by_user":true}"""

private fun plan(nutrition: String, outcomes: String, status: String = "proposed", acceptedAt: String = "null") =
    """{"id":"p1","block_start":"2026-12-09","block_weeks":4,"intention":"fat_loss","status":"$status",""" +
        """"nutrition":$nutrition,"outcomes":$outcomes,"accepted_at":$acceptedAt,"data_version":9}"""

class CoachPlanDtoTest {
    private fun decode(json: String) = coachPlanJson.decodeFromString<CoachPlanDto>(json).toDomain()

    @Test
    fun nutritionWithFloor_mapsEveryField() {
        val nutrition = decode(plan(NUTRITION_FLOOR, "[]")).nutrition

        assertEquals(1200, nutrition.targetKcal)
        assertEquals(1200, nutrition.floorKcal)
        assertEquals(PlanLimit.Floor, nutrition.limitedBy)
        assertEquals(-0.375, nutrition.rateKgWeek)
        assertEquals(GoalStrategy.Deficit, nutrition.strategy)
        assertEquals(110, nutrition.carbsG)
        assertEquals(30, nutrition.distribution?.perMealG)
        assertEquals(4, nutrition.distribution?.minMeals)
    }

    @Test
    fun nutritionWithoutFloorLimit_hasNoLimitAndKeepsTheEditedFlag() {
        val nutrition = decode(plan(NUTRITION_FREE, "[]")).nutrition

        assertNull(nutrition.limitedBy)
        assertNull(nutrition.distribution)
        assertEquals(GoalStrategy.Surplus, nutrition.strategy)
        assertTrue(nutrition.editedByUser)
    }

    private fun nutritionWithActivity(activity: String) =
        NUTRITION_FREE.removeSuffix("}") + ""","activity":$activity}"""

    @Test
    fun planWithoutActivity_hasNullActivity() {
        assertNull(decode(plan(NUTRITION_FREE, "[]")).nutrition.activity)
    }

    @Test
    fun activity_isMappedWithItsExtraKeysIgnored() {
        val activity = decode(
            plan(
                nutritionWithActivity(
                    """{"cardio_min_week":120,"cardio_edited":true,"cardio_band":[150,300],"steps_day":8500,""" +
                        """"steps_basis":"average","steps_avg":8760,"future":1}""",
                ),
                "[]",
            ),
        ).nutrition.activity

        assertEquals(PlanActivity(120, true, 8500, StepsBasis.Average), activity)
    }

    @Test
    fun unknownStepsBasis_fallsBackToTheFloor() {
        val activity = decode(
            plan(nutritionWithActivity("""{"cardio_min_week":150,"steps_day":7000,"steps_basis":"x"}"""), "[]"),
        ).nutrition.activity

        assertEquals(StepsBasis.Floor, activity?.stepsBasis)
        assertEquals(false, activity?.cardioEdited)
    }

    private fun nutritionWith(extra: String) = NUTRITION_FREE.removeSuffix("}") + ",$extra}"

    @Test
    fun phaseKeys_areMapped() {
        val nutrition = decode(
            plan(
                nutritionWith(
                    """"limited_by":"deficit_cap","phase":"minimal","phase_reason":"user_busy",""" +
                        """"phase_options":{"cut_rate_pct":0.8,"diet_break":true},""" +
                        """"diet_break":{"target_kcal":2600,"protein_g":150,"carbs_g":300,"fat_g":80,"week":4}""",
                ),
                "[]",
            ),
        ).nutrition

        assertEquals(Phase.Minimal, nutrition.phase)
        assertEquals(PhaseReason.UserBusy, nutrition.phaseReason)
        assertEquals(PhaseOptions(0.8, true), nutrition.phaseOptions)
        assertEquals(DietBreakWeek(2600, 150, 300, 80, 4), nutrition.dietBreak)
    }

    @Test
    fun legacyRegainPhase_readsAsMaintenanceWithIntentionReason() {
        val nutrition = decode(
            plan(nutritionWith(""""phase":"regain","phase_reason":"returning_break""""), "[]"),
        ).nutrition

        assertEquals(Phase.Maintenance, nutrition.phase)
        assertEquals(PhaseReason.Intention, nutrition.phaseReason)
    }

    @Test
    fun deficitCapLimit_isMapped() {
        val nutrition = decode(
            plan(NUTRITION_FREE.replace(""""limited_by":null""", """"limited_by":"deficit_cap""""), "[]"),
        )
            .nutrition

        assertEquals(PlanLimit.DeficitCap, nutrition.limitedBy)
    }

    @Test
    fun legacyJsonWithoutPhase_takesThePhaseFromTheIntention() {
        val decoded = decode(plan(NUTRITION_FREE, "[]"))

        assertEquals(Phase.Cut, decoded.phase)
        assertEquals(PhaseReason.Intention, decoded.nutrition.phaseReason)
        assertEquals(PhaseOptions(0.5, false), decoded.nutrition.phaseOptions)
        assertNull(decoded.nutrition.dietBreak)
    }

    @Test
    fun legacyJsonWithoutPhase_usesTheRowColumnsBeforeTheIntention() {
        val row = plan(NUTRITION_FREE, "[]").removeSuffix("}") +
            ""","phase":"minimal","phase_reason":"user_busy","phase_options":{"cut_rate_pct":0.5,"diet_break":false}}"""

        val decoded = decode(row)

        assertEquals(Phase.Minimal, decoded.phase)
        assertEquals(PhaseReason.UserBusy, decoded.nutrition.phaseReason)
    }

    @Test
    fun unknownPhaseAndReason_fallBackWithoutThrowing() {
        val decoded = decode(plan(nutritionWith(""""phase":"bulk","phase_reason":"x""""), "[]"))

        assertEquals(Phase.Maintenance, decoded.phase)
        assertEquals(PhaseReason.Intention, decoded.nutrition.phaseReason)
    }

    @Test
    fun outOfRangeCutRate_fallsBackToTheDefault() {
        val decoded = decode(
            plan(nutritionWith(""""phase_options":{"cut_rate_pct":1.7,"diet_break":true}"""), "[]"),
        )

        assertEquals(PhaseOptions(0.5, true), decoded.nutrition.phaseOptions)
    }

    @Test
    fun intentionTable_givesTheFirstPhase() {
        val expected = mapOf(
            "fat_loss" to Phase.Cut,
            "muscle" to Phase.Gain,
            "recomposition" to Phase.Recomposition,
            "maintenance" to Phase.Maintenance,
            "strength" to Phase.Maintenance,
        )
        expected.forEach { (intention, phase) -> assertEquals(phase, phaseForIntention(intention), intention) }
    }

    @Test
    fun emptyOutcomes_isAnEmptyList() {
        assertTrue(decode(plan(NUTRITION_FREE, "[]")).outcomes.isEmpty())
    }

    @Test
    fun weightAndE1rmOutcomes_areMapped() {
        val outcomes = """[{"kind":"weight","key":"weight","target_value":72.5,"unit":"kg","reason":"r1",""" +
            """"deadline":"2027-01-06","basis":{"a":1}},{"kind":"e1rm","key":"ex-1","target_value":100,""" +
            """"unit":"kg","reason":"r2","deadline":"2026-12-30","basis":{}}]"""

        val decoded = decode(plan(NUTRITION_FLOOR, outcomes))

        assertEquals(listOf(OutcomeKind.Weight, OutcomeKind.E1rm), decoded.outcomes.map { it.kind })
        assertEquals(72.5, decoded.weightOutcome?.targetValue)
        assertEquals(LocalDate(2026, 12, 30), decoded.outcomes[1].deadline)
        assertEquals("ex-1", decoded.outcomes[1].key)
    }

    @Test
    fun unknownOutcomeKind_isDropped() {
        val outcomes = """[{"kind":"body_fat","key":"k","target_value":15,"reason":"r","deadline":"2027-01-06"}]"""

        assertTrue(decode(plan(NUTRITION_FLOOR, outcomes)).outcomes.isEmpty())
    }

    @Test
    fun unknownKeys_areIgnored_andStatusAndAcceptedAtMapped() {
        val decoded = decode(plan(NUTRITION_FLOOR, "[]", "accepted", "\"2026-12-10T08:30:00.123+00:00\""))

        assertEquals(PlanStatus.Accepted, decoded.status)
        assertEquals(LocalDate(2026, 12, 9), decoded.blockStart)
        assertEquals("2026-12-10T08:30:00.123Z", decoded.acceptedAt.toString())
    }

    @Test
    fun functionEvent_exposesThePlanId() {
        val event = coachPlanJson.decodeFromString<PlanEventDto>(
            """{"plan_id":"p9","recommendation_id":"r9","nutrition":$NUTRITION_FLOOR,"outcomes":[]}""",
        )

        assertEquals("p9", event.planId)
        assertEquals("r9", event.recommendationId)
    }

    @Test
    fun recommendationRow_mapsToCalorieChange() {
        val row = coachPlanJson.decodeFromString<CoachRecommendationDto>(
            """{"id":"c1","plan_id":"p1","kind":"calorie_change","reason":"rate_outside_interval",""" +
                """"status":"pending","payload":{"from_kcal":2000,"to_kcal":2100,"change_kcal":100,""" +
                """"change_pct":5.0,""" +
                """"slope_kg_week":-0.1,"interval_low":-0.2,"interval_high":0,"rate_kg_week":-0.4}}""",
        )

        val change = row.toCalorieChange()

        assertEquals(2000, change.fromKcal)
        assertEquals(2100, change.toKcal)
        assertEquals(5.0, change.changePct)
        assertEquals("rate_outside_interval", change.reasonCode)
        assertEquals(-0.1, change.slopeKgWeek)
    }
}
