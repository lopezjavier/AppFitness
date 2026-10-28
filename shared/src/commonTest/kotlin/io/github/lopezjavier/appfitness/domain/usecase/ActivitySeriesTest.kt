package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthActivityDay
import io.github.lopezjavier.appfitness.domain.model.HealthActivityHistory
import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.model.SleepStage
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val TODAY = LocalDate(2026, 10, 28)
private val MADRID = TimeZone.of("Europe/Madrid")
private val UTC = TimeZone.UTC

private fun session(id: String, start: String, end: String, minutes: Int, stages: List<SleepStage> = emptyList()) =
    SleepSession(id, Instant.parse(start), Instant.parse(end), minutes, stages)

private fun stage(key: String, start: String, end: String) = SleepStage(key, Instant.parse(start), Instant.parse(end))

private fun history(
    daily: List<DailyActivity> = emptyList(),
    sleep: List<SleepSession> = emptyList(),
    resting: List<RestingHeartRate> = emptyList(),
) = HealthActivityHistory(daily, sleep, resting)

class ActivitySeriesTest {
    private val window = WeightChartRange.Month.window(TODAY)

    @Test
    fun steps_giveOnePointPerDayAscendingSkippingNulls() {
        val h = history(
            daily = listOf(
                DailyActivity(LocalDate(2026, 10, 20), 1000, null, null),
                DailyActivity(LocalDate(2026, 10, 21), null, 5.0, null),
                DailyActivity(LocalDate(2026, 10, 22), 3000, null, null),
            ),
        )

        val series = ActivitySeries.of(h, ActivityMetric.Steps, window, UTC)

        assertEquals(
            listOf(
                ActivitySeriesPoint(LocalDate(2026, 10, 20), 1000.0),
                ActivitySeriesPoint(LocalDate(2026, 10, 22), 3000.0),
            ),
            series,
        )
    }

    @Test
    fun kcalAndRestingMetrics_readTheirOwnValues() {
        val d = LocalDate(2026, 10, 20)
        val h =
            history(daily = listOf(DailyActivity(d, 1, 200.5, 2400.0)), resting = listOf(RestingHeartRate(d, 55, null)))

        assertEquals(200.5, ActivitySeries.of(h, ActivityMetric.ActiveKcal, window, UTC).single().value)
        assertEquals(2400.0, ActivitySeries.of(h, ActivityMetric.TotalKcal, window, UTC).single().value)
        assertEquals(55.0, ActivitySeries.of(h, ActivityMetric.RestingHeartRate, window, UTC).single().value)
    }

    @Test
    fun sleep_isDatedByTheLocalDayItEnded_aroundMidnight() {
        // Ends 23:30 UTC on the 26th = 01:30 on the 27th in Madrid (UTC+2 in October).
        val h = history(sleep = listOf(session("a", "2026-10-26T20:00:00Z", "2026-10-26T23:30:00Z", 200)))

        assertEquals(LocalDate(2026, 10, 27), ActivitySeries.of(h, ActivityMetric.Sleep, window, MADRID).single().day)
        assertEquals(LocalDate(2026, 10, 26), ActivitySeries.of(h, ActivityMetric.Sleep, window, UTC).single().day)
    }

    @Test
    fun sleep_sessionsEndingTheSameDayAreSummed() {
        val h = history(
            sleep = listOf(
                session("a", "2026-10-26T22:00:00Z", "2026-10-27T05:00:00Z", 400),
                session("b", "2026-10-27T13:00:00Z", "2026-10-27T14:00:00Z", 50),
            ),
        )

        val point = ActivitySeries.of(h, ActivityMetric.Sleep, window, UTC).single()

        assertEquals(450.0, point.value)
    }

    @Test
    fun emptyMetric_givesEmptySeries() {
        val h = history(daily = listOf(DailyActivity(LocalDate(2026, 10, 20), 10, null, null)))

        assertEquals(emptyList(), ActivitySeries.of(h, ActivityMetric.RestingHeartRate, window, UTC))
    }

    @Test
    fun everyRange_includesVisibleStartAndExcludesTheDayBefore() {
        for (range in WeightChartRange.entries) {
            val w = range.window(TODAY)
            val before = w.visibleStart.minus(DatePeriod(days = 1))
            val h = history(
                daily = listOf(
                    DailyActivity(before, 1, null, null),
                    DailyActivity(w.visibleStart, 2, null, null),
                    DailyActivity(TODAY, 3, null, null),
                    DailyActivity(w.endExclusive, 4, null, null),
                ),
            )

            val days = ActivitySeries.of(h, ActivityMetric.Steps, w, UTC).map { it.day }

            assertEquals(listOf(w.visibleStart, TODAY), days, "range $range")
        }
    }

    @Test
    fun dayValues_collectTheFiveValuesAndMissingOnesAreNull() {
        val d = LocalDate(2026, 10, 27)
        val h = history(
            daily = listOf(DailyActivity(d, 900, 100.0, null)),
            sleep = listOf(session("a", "2026-10-26T22:00:00Z", "2026-10-27T05:00:00Z", 400)),
        )

        val day = ActivityDayValues.of(h, d, UTC)

        assertEquals(900, day.steps)
        assertEquals(100.0, day.activeKcal)
        assertNull(day.totalKcal)
        assertEquals(400, day.sleep?.minutes)
        assertNull(day.restingHeartRate)
    }

    @Test
    fun dayValues_ofADayWithNothing_isAllNull() {
        val day = ActivityDayValues.of(history(), TODAY, UTC)

        assertEquals(HealthActivityDay(TODAY, null, null, null, null, null), day)
    }

    @Test
    fun dayValues_mergeTwoSessionsOfTheSameDay() {
        val d = LocalDate(2026, 10, 27)
        val h = history(
            sleep = listOf(
                session("a", "2026-10-26T22:00:00Z", "2026-10-27T05:00:00Z", 400),
                session("b", "2026-10-27T13:00:00Z", "2026-10-27T14:00:00Z", 50),
            ),
        )

        val sleep = ActivityDayValues.of(h, d, UTC).sleep

        assertEquals(450, sleep?.minutes)
        assertEquals(Instant.parse("2026-10-26T22:00:00Z"), sleep?.startedAt)
        assertEquals(Instant.parse("2026-10-27T14:00:00Z"), sleep?.endedAt)
    }
}

class SleepStageSummaryTest {
    @Test
    fun stages_areSummedPerKeyInMinutes() {
        val s = session(
            "a",
            "2026-10-26T22:00:00Z",
            "2026-10-27T02:00:00Z",
            200,
            listOf(
                stage("deep", "2026-10-26T22:00:00Z", "2026-10-26T23:00:00Z"),
                stage("light", "2026-10-26T23:00:00Z", "2026-10-27T00:30:00Z"),
                stage("deep", "2026-10-27T00:30:00Z", "2026-10-27T01:00:00Z"),
            ),
        )

        assertEquals(mapOf("deep" to 90, "light" to 90), SleepStageSummary.of(s)?.minutesByStage)
    }

    @Test
    fun unknownKeys_areKeptAsTheyCome() {
        val s = session(
            "a",
            "2026-10-26T22:00:00Z",
            "2026-10-26T23:00:00Z",
            60,
            listOf(stage("unknown", "2026-10-26T22:00:00Z", "2026-10-26T22:20:00Z")),
        )

        assertEquals(mapOf("unknown" to 20), SleepStageSummary.of(s)?.minutesByStage)
    }

    @Test
    fun withoutStages_isNull() {
        assertNull(SleepStageSummary.of(session("a", "2026-10-26T22:00:00Z", "2026-10-27T05:00:00Z", 400)))
    }
}
