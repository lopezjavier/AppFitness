package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun m(
    at: String,
    source: MeasurementSource = MeasurementSource.Manual,
    fat: Double? = null,
    lean: Double? = null,
    water: Double? = null,
    bone: Double? = null,
    bmr: Int? = null,
) = BodyMeasurement(
    id = at + source,
    measuredAt = Instant.parse(at),
    weightKg = 70.0,
    source = source,
    bodyFatPct = fat,
    leanMassKg = lean,
    waterKg = water,
    boneKg = bone,
    bmrKcal = bmr,
)

private fun c(at: String, cm: Double, site: CircumferenceSite = CircumferenceSite.Waist, created: String = at) =
    BodyCircumference("$site$at$created", site, cm, Instant.parse(at), Instant.parse(created))

private fun d(day: Int) = LocalDate(2026, 10, day)

class MetricDailyValuesTest {
    private val values = MetricDailyValues()

    private fun composition(metric: BodyMetric, rows: List<BodyMeasurement>) =
        values(rows, emptyList(), metric, TimeZone.UTC)

    @Test
    fun eachCompositionMetric_readsItsOwnColumn() {
        val rows = listOf(m("2026-10-20T07:00:00Z", fat = 20.0, lean = 55.0, water = 40.0, bone = 3.0, bmr = 1700))

        assertEquals(20.0, composition(BodyMetric.BodyFat, rows).single().kg)
        assertEquals(55.0, composition(BodyMetric.LeanMass, rows).single().kg)
        assertEquals(40.0, composition(BodyMetric.Water, rows).single().kg)
        assertEquals(3.0, composition(BodyMetric.Bone, rows).single().kg)
        assertEquals(1700.0, composition(BodyMetric.Bmr, rows).single().kg)
    }

    @Test
    fun rowsWithoutTheMetric_areSkippedAndTheDayIsAbsent() {
        val rows = listOf(m("2026-10-20T07:00:00Z"), m("2026-10-21T07:00:00Z", fat = 18.0))

        assertEquals(listOf(DailyWeight(d(21), 18.0)), composition(BodyMetric.BodyFat, rows))
    }

    @Test
    fun healthConnectRowsWithTheMetric_winOverManual() {
        val rows = listOf(
            m("2026-10-20T07:00:00Z", MeasurementSource.HealthConnect, fat = 22.0),
            m("2026-10-20T08:00:00Z", MeasurementSource.Manual, fat = 30.0),
        )

        assertEquals(22.0, composition(BodyMetric.BodyFat, rows).single().kg)
    }

    @Test
    fun healthConnectRowWithoutTheMetric_doesNotHideManualOne() {
        val rows = listOf(
            m("2026-10-20T07:00:00Z", MeasurementSource.HealthConnect),
            m("2026-10-20T08:00:00Z", MeasurementSource.Manual, fat = 30.0),
        )

        assertEquals(30.0, composition(BodyMetric.BodyFat, rows).single().kg)
    }

    @Test
    fun sameDayRows_areAveraged() {
        val rows = listOf(m("2026-10-20T07:00:00Z", fat = 20.0), m("2026-10-20T19:00:00Z", fat = 22.0))

        assertEquals(21.0, composition(BodyMetric.BodyFat, rows).single().kg)
    }

    @Test
    fun weight_equalsDailyWeights() {
        val rows = listOf(m("2026-10-20T07:00:00Z"), m("2026-10-21T07:00:00Z"))

        assertEquals(DailyWeights()(rows, TimeZone.UTC), composition(BodyMetric.Weight, rows))
    }

    @Test
    fun waist_takesTheNewestRowOfTheDayAndIgnoresOtherSites() {
        val rows = listOf(
            c("2026-10-20T07:00:00Z", 90.0),
            c("2026-10-20T19:00:00Z", 88.0),
            c("2026-10-20T19:00:00Z", 87.0, created = "2026-10-20T19:05:00Z"),
            c("2026-10-20T20:00:00Z", 99.0, CircumferenceSite.Hip),
        )

        val waist = values(emptyList(), rows, BodyMetric.Waist, TimeZone.UTC)
        val hip = values(emptyList(), rows, BodyMetric.Hip, TimeZone.UTC)

        assertEquals(listOf(DailyWeight(d(20), 87.0)), waist)
        assertEquals(listOf(DailyWeight(d(20), 99.0)), hip)
    }

    @Test
    fun localDayBoundary_splitsRowsAroundMidnight() {
        val zone = TimeZone.of("Europe/Madrid") // UTC+2 in October
        val rows = listOf(c("2026-10-20T21:30:00Z", 90.0), c("2026-10-20T22:30:00Z", 89.0))

        assertEquals(listOf(d(20), d(21)), values(emptyList(), rows, BodyMetric.Waist, zone).map { it.date })
    }

    @Test
    fun emptyInput_givesNoDays() {
        BodyMetric.entries.forEach {
            assertTrue(values(emptyList(), emptyList(), it, TimeZone.UTC).isEmpty())
        }
    }
}
