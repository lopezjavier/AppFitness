package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachMissing
import io.github.lopezjavier.appfitness.domain.model.DerivedExperience
import io.github.lopezjavier.appfitness.domain.model.DerivedSource
import io.github.lopezjavier.appfitness.domain.model.ValueState
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(text: String) = json.decodeFromString<CoachDerivedDto>(text).toDomain()

    private fun payload(
        activity: String = ACTIVITY_DATA,
        trend: String = TREND,
        intake: String = INTAKE,
        extra: String = "",
    ) = """{"as_of":"2026-10-29",$extra"activity_level":$activity,"basal_kcal":{"value":1700,"missing":[]},""" +
        """"total_expenditure":$EXPENDITURE,"weight_trend":$trend,"intake":$intake,"experience":$EXPERIENCE}"""

    @Test
    fun fullPayload_mapsEveryField() {
        val values = parse(payload())

        assertEquals(LocalDate(2026, 10, 29), values.asOf)
        assertEquals(ActivityLevel.High, values.activityLevel.value)
        assertEquals(ActivityLevel.Moderate, values.activityLevel.estimate)
        assertEquals(DerivedSource.Data, values.activityLevel.source)
        assertEquals(9500.5, values.activityLevel.sources.stepsAvg)
        assertEquals(12, values.activityLevel.sources.stepsDays)
        assertEquals(2600.0, values.totalExpenditure.kcal)
        assertEquals(DerivedSource.Adaptive, values.totalExpenditure.source)
        assertEquals(-0.4, values.weightTrend.slopeKgWeek)
        assertEquals(-0.6, values.weightTrend.intervalLow)
        assertEquals(12, values.intake.loggedDays)
        assertEquals(28, values.intake.windowDays)
        assertEquals(DerivedExperience.Intermediate, values.experience.value)
    }

    @Test
    fun nullNumerics_mapToNull() {
        val values = parse(
            payload(
                trend = """{"slope_kg_week":null,"interval_low":null,"interval_high":null,"weeks":null,""" +
                    """"low_confidence":true,"provisional":true,"confirmed":false,"reask":false,""" +
                    """"uncertainty":null,"missing":["weigh_ins"]}""",
            ),
        )

        assertNull(values.weightTrend.slopeKgWeek)
        assertNull(values.weightTrend.intervalLow)
        assertTrue(values.weightTrend.lowConfidence)
        assertEquals(listOf(CoachMissing.WeighIns), values.weightTrend.missing)
    }

    @Test
    fun unknownMissingKey_becomesUnknownInsteadOfCrashing() {
        val values =
            parse(payload(activity = ACTIVITY_DATA.replace("\"missing\":[]", "\"missing\":[\"steps\",\"sleep\"]")))

        assertEquals(listOf(CoachMissing.Steps, CoachMissing.Unknown("sleep")), values.activityLevel.missing)
    }

    @Test
    fun unknownExtraKeys_areIgnored() {
        val values = parse(payload(extra = """"future_field":{"a":1},"""))

        assertEquals(ActivityLevel.High, values.activityLevel.value)
    }

    @Test
    fun profileSourceProvisional_isProvisionalAndNotAllConfirmed() {
        val values = parse(payload(activity = ACTIVITY_PROFILE))

        assertEquals(DerivedSource.Profile, values.activityLevel.source)
        assertEquals(ValueState.Provisional, values.activityLevel.state)
        assertFalse(values.allConfirmed)
    }

    @Test
    fun reask_isReaskStateAndAnyReask() {
        val values = parse(payload(activity = ACTIVITY_DATA.replace("\"reask\":false", "\"reask\":true")))

        assertEquals(ValueState.Reask, values.activityLevel.state)
        assertTrue(values.anyReask)
    }

    @Test
    fun edited_isEditedStateAndConfirmedValuesAreAllConfirmed() {
        val activity = ACTIVITY_DATA.replace("\"edited\":false", "\"edited\":true")
            .replace("\"provisional\":false", "\"provisional\":false")
        val values = parse(
            payload(activity = activity, trend = TREND.replace("\"provisional\":true", "\"provisional\":false")),
        )

        assertEquals(ValueState.Edited, values.activityLevel.state)
        assertTrue(values.allConfirmed)
        assertFalse(values.anyReask)
    }

    @Test
    fun confirmParams_omitNullLevelAndUseWireNameOtherwise() {
        val encoder = Json { encodeDefaults = false }
        val date = LocalDate(2026, 10, 29)

        assertEquals("""{"p_today":"2026-10-29"}""", encoder.encodeToString(ConfirmDerivedParams.of(date, null)))
        assertEquals(
            """{"p_today":"2026-10-29","p_activity_level":"very_high"}""",
            encoder.encodeToString(ConfirmDerivedParams.of(date, ActivityLevel.VeryHigh)),
        )
    }

    private companion object {
        const val ACTIVITY_DATA =
            """{"value":"high","estimate":"moderate","factor":1.7,"source":"data","provisional":false,""" +
                """"confirmed":true,"edited":false,"reask":false,"uncertainty":0.1,"sources":{"steps_avg":9500.5,""" +
                """"steps_days":12,"active_kcal_avg":400,"active_kcal_days":10,"sessions_per_week":3.5},"missing":[]}"""
        const val ACTIVITY_PROFILE =
            """{"value":"light","estimate":null,"factor":1.375,"source":"profile","provisional":true,""" +
                """"confirmed":false,"edited":false,"reask":false,"uncertainty":null,"sources":{"steps_avg":null,""" +
                """"steps_days":0,"active_kcal_avg":null,"active_kcal_days":0,"sessions_per_week":null},""" +
                """"missing":["steps","active_kcal"]}"""
        const val EXPENDITURE =
            """{"value":2600,"source":"adaptive","unreliable":false,"allow_calorie_changes":true,""" +
                """"provisional":false,"confirmed":true,"reask":false,"uncertainty":150,"missing":[]}"""
        const val TREND =
            """{"slope_kg_week":-0.4,"interval_low":-0.6,"interval_high":-0.2,"weeks":6,"low_confidence":false,""" +
                """"provisional":true,"confirmed":false,"reask":false,"uncertainty":0.2,"missing":[]}"""
        const val INTAKE = """{"logged_days":12,"window_days":28,"ratio":0.43,"reliable":false}"""
        const val EXPERIENCE =
            """{"value":"intermediate","sessions":40,"weeks":14,"provisional":false,"confirmed":true,""" +
                """"reask":false,"uncertainty":null,"missing":[]}"""
    }
}
