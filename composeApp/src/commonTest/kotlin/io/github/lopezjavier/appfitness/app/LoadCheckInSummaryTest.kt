package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.PersonalRecordType
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.model.WeekRecord
import io.github.lopezjavier.appfitness.domain.model.WeeklyWeightTrend
import io.github.lopezjavier.appfitness.domain.usecase.LoadCheckInSummary
import io.github.lopezjavier.appfitness.feature.analytics.stats.FakeTrainingStatsRepository
import io.github.lopezjavier.appfitness.feature.analytics.stats.FakeTrainingStatsRepository.Window
import io.github.lopezjavier.appfitness.feature.weight.FakeWeightRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

// Week under test: Monday 9 Nov to Sunday 15 Nov 2026; previous week 2 to 8 Nov.
private val MONDAY = LocalDate(2026, 11, 9)
private val SUNDAY = LocalDate(2026, 11, 15)
private val PREVIOUS_MONDAY = LocalDate(2026, 11, 2)
private val PREVIOUS_SUNDAY = LocalDate(2026, 11, 8)

private fun at(date: LocalDate, hour: Int = 8, minute: Int = 0, zone: TimeZone = TimeZone.UTC): Instant =
    date.atTime(hour, minute).toInstant(zone)

private fun weigh(date: LocalDate, kg: Double, source: MeasurementSource = MeasurementSource.Manual) =
    BodyMeasurement("w-$date-$kg-$source", at(date), kg, source)

class LoadCheckInSummaryTest {
    private val stats = FakeTrainingStatsRepository()
    private val weight = FakeWeightRepository()

    private fun load(zone: TimeZone = TimeZone.UTC) = LoadCheckInSummary(stats, weight, zone)

    @Test
    fun ranges_areTheCheckInWeekAndThePreviousOne_wheneverItIsOpened() = runTest {
        // Any day of the week resolves to the same Monday-Sunday ranges: the figures never depend on today.
        for (day in listOf(MONDAY, LocalDate(2026, 11, 13), SUNDAY)) {
            stats.summaryCalls.clear()
            stats.recordCalls.clear()

            load()(day).getOrThrow()

            assertEquals(listOf(Window(MONDAY, SUNDAY), Window(PREVIOUS_MONDAY, PREVIOUS_SUNDAY)), stats.summaryCalls)
            assertEquals(listOf(Window(MONDAY, SUNDAY)), stats.recordCalls)
        }
    }

    @Test
    fun training_carriesBothWeeksAndTheirDifference() = runTest {
        stats.summaryFor = { w ->
            if (w.from == MONDAY) TrainingSummary(4, 40, 12_400.0, 0) else TrainingSummary(3, 30, 11_600.0, 0)
        }

        val training = load()(SUNDAY).getOrThrow().training

        assertEquals(12_400.0, training.current.volume)
        assertEquals(11_600.0, training.previous.volume)
        assertEquals(800.0, training.current.volume - training.previous.volume)
    }

    @Test
    fun emptyWeek_isZeroNotAnError() = runTest {
        stats.summaryFor = { TrainingSummary(0, 0, 0.0, 0) }

        val summary = load()(SUNDAY).getOrThrow()

        assertEquals(0, summary.training.current.workouts)
        assertTrue(summary.records.isEmpty())
    }

    @Test
    fun records_passThrough() = runTest {
        val record = WeekRecord("ex-1", PersonalRecordType.Best1Rm, 100.0, LocalDate(2026, 11, 11))
        stats.recordsFor = { listOf(record) }

        assertEquals(listOf(record), load()(SUNDAY).getOrThrow().records)
    }

    @Test
    fun weightTrend_isThisWeekAverageMinusLastWeekAverage() = runTest {
        weight.rows += weigh(PREVIOUS_MONDAY, 80.0)
        weight.rows += weigh(PREVIOUS_SUNDAY, 80.4)
        weight.rows += weigh(MONDAY, 79.0)
        weight.rows += weigh(SUNDAY, 79.4)

        val trend = load()(SUNDAY).getOrThrow().weightTrend

        // Averages 80.2 and 79.2.
        assertEquals(-1.0, assertIs<WeeklyWeightTrend.Delta>(trend).kgDelta, absoluteTolerance = 1e-9)
    }

    @Test
    fun weightTrend_withoutWeighInThisWeek_isNotEnoughData() = runTest {
        weight.rows += weigh(PREVIOUS_MONDAY, 80.0)

        assertEquals(WeeklyWeightTrend.NotEnoughData, load()(SUNDAY).getOrThrow().weightTrend)
    }

    @Test
    fun weightTrend_withoutWeighInLastWeek_isNotEnoughData() = runTest {
        weight.rows += weigh(MONDAY, 80.0)

        assertEquals(WeeklyWeightTrend.NotEnoughData, load()(SUNDAY).getOrThrow().weightTrend)
    }

    @Test
    fun weightTrend_oneWeighInEachWeek_isTheirDifference() = runTest {
        weight.rows += weigh(PREVIOUS_SUNDAY, 81.0)
        weight.rows += weigh(MONDAY, 81.5)

        val trend = load()(MONDAY).getOrThrow().weightTrend

        assertEquals(0.5, assertIs<WeeklyWeightTrend.Delta>(trend).kgDelta, absoluteTolerance = 1e-9)
    }

    @Test
    fun weightTrend_healthConnectTakesPrecedenceOverManualOfTheSameDay() = runTest {
        weight.rows += weigh(PREVIOUS_MONDAY, 80.0)
        weight.rows += weigh(MONDAY, 70.0, MeasurementSource.Manual)
        weight.rows += weigh(MONDAY, 79.0, MeasurementSource.HealthConnect)

        val trend = load()(SUNDAY).getOrThrow().weightTrend

        assertEquals(-1.0, assertIs<WeeklyWeightTrend.Delta>(trend).kgDelta, absoluteTolerance = 1e-9)
    }

    @Test
    fun weightDays_followTheDeviceTimeZone() = runTest {
        val zone = TimeZone.of("+02:00")
        // 23:30 UTC on Sunday 8 Nov is 01:30 on Monday 9 Nov at +02:00: it belongs to the check-in week.
        weight.rows += BodyMeasurement("a", at(PREVIOUS_SUNDAY, 23, 30), 79.0, MeasurementSource.Manual)
        weight.rows += BodyMeasurement("b", at(PREVIOUS_MONDAY, 10, 0), 80.0, MeasurementSource.Manual)

        val trend = load(zone)(SUNDAY).getOrThrow().weightTrend

        assertEquals(-1.0, assertIs<WeeklyWeightTrend.Delta>(trend).kgDelta, absoluteTolerance = 1e-9)
    }

    @Test
    fun weighInOnSundayAt2359AndMondayAt0000_fallInDifferentWeeks() = runTest {
        weight.rows += BodyMeasurement("a", at(SUNDAY, 23, 59), 80.0, MeasurementSource.Manual)
        weight.rows += BodyMeasurement("b", at(LocalDate(2026, 11, 16), 0, 0), 99.0, MeasurementSource.Manual)
        weight.rows += weigh(PREVIOUS_MONDAY, 81.0)

        val trend = load()(SUNDAY).getOrThrow().weightTrend

        assertEquals(-1.0, assertIs<WeeklyWeightTrend.Delta>(trend).kgDelta, absoluteTolerance = 1e-9)
    }

    @Test
    fun failedRecords_failsTheSummaryWithoutThrowing() = runTest {
        stats.recordsFailure = AppError.Network

        assertTrue(load()(SUNDAY).isFailure)
    }

    @Test
    fun failedWeightRead_failsTheSummary() = runTest {
        weight.failReads = true

        assertTrue(load()(SUNDAY).isFailure)
    }
}
