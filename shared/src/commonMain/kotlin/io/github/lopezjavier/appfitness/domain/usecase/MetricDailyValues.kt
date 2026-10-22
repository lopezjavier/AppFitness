package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.BodyMetric
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The value of [BodyMetric] per local day, oldest first (the value travels in [DailyWeight.kg] whatever its unit).
 * Composition metrics average the day's rows that have the metric, Health Connect rows first (ADR-47); weight uses
 * [DailyWeights]; waist and hip take the newest row of the day.
 */
class MetricDailyValues(private val dailyWeights: DailyWeights = DailyWeights()) {
    operator fun invoke(
        measurements: List<BodyMeasurement>,
        circumferences: List<BodyCircumference>,
        metric: BodyMetric,
        timeZone: TimeZone,
    ): List<DailyWeight> = when (metric) {
        BodyMetric.Weight -> dailyWeights(measurements, timeZone)
        BodyMetric.BodyFat -> composition(measurements, timeZone) { it.bodyFatPct }
        BodyMetric.LeanMass -> composition(measurements, timeZone) { it.leanMassKg }
        BodyMetric.Water -> composition(measurements, timeZone) { it.waterKg }
        BodyMetric.Bone -> composition(measurements, timeZone) { it.boneKg }
        BodyMetric.Bmr -> composition(measurements, timeZone) { it.bmrKcal?.toDouble() }
        BodyMetric.Waist -> circumference(circumferences, CircumferenceSite.Waist, timeZone)
        BodyMetric.Hip -> circumference(circumferences, CircumferenceSite.Hip, timeZone)
    }

    private fun composition(
        rows: List<BodyMeasurement>,
        timeZone: TimeZone,
        select: (BodyMeasurement) -> Double?,
    ): List<DailyWeight> = rows
        .filter { select(it) != null }
        .groupBy { it.measuredAt.toLocalDateTime(timeZone).date }
        .map { (date, dayRows) ->
            val preferred = dayRows.filter { it.source == MeasurementSource.HealthConnect }.ifEmpty { dayRows }
            DailyWeight(date, preferred.mapNotNull(select).average())
        }
        .sortedBy { it.date }

    private fun circumference(
        rows: List<BodyCircumference>,
        site: CircumferenceSite,
        timeZone: TimeZone,
    ): List<DailyWeight> = rows
        .filter { it.site == site }
        .groupBy { it.measuredAt.toLocalDateTime(timeZone).date }
        .map { (date, dayRows) ->
            val newest = dayRows.maxWith(compareBy<BodyCircumference> { it.measuredAt }.thenBy { it.createdAt })
            DailyWeight(date, newest.valueCm)
        }
        .sortedBy { it.date }
}
