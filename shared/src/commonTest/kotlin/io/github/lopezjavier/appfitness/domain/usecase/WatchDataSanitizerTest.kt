package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val DAY = LocalDate(2026, 10, 21)
private val STARTED = Instant.parse("2026-10-21T21:00:00Z")

class WatchDataSanitizerTest {
    private fun clean(
        daily: List<DailyActivity> = emptyList(),
        sleep: List<SleepSession> = emptyList(),
        resting: List<RestingHeartRate> = emptyList(),
    ) = WatchDataSanitizer.sanitize(HealthActivityBatch(daily, sleep, resting))

    private fun day(steps: Int? = null, active: Double? = null, total: Double? = null, day: LocalDate = DAY) =
        DailyActivity(day, steps, active, total)

    private fun sleep(minutes: Int = 435, id: String = "s1", end: Instant = STARTED + 480.minutes) =
        SleepSession(id, STARTED, end, minutes, emptyList())

    @Test
    fun steps_negativeIsDropped_overTheMaximumIsClamped_normalIsKept() {
        val result = clean(
            daily = listOf(
                day(steps = -1, total = 1.0, day = DAY),
                day(steps = 1_000_001, day = DAY.plus(1, kotlinx.datetime.DateTimeUnit.DAY)),
                day(steps = 8500, day = DAY.plus(2, kotlinx.datetime.DateTimeUnit.DAY)),
            ),
        ).daily

        assertEquals(listOf(null, 1_000_000, 8500), result.map { it.steps })
    }

    @Test
    fun kcal_negativeNanAndInfiniteAreDropped_overTheMaximumIsClamped_roundedToTwoDecimals() {
        val result = clean(
            daily = listOf(
                day(steps = 1, active = -5.0, total = 100_001.0),
                day(
                    steps = 1,
                    active = Double.NaN,
                    total = Double.POSITIVE_INFINITY,
                    day = DAY.plus(1, kotlinx.datetime.DateTimeUnit.DAY),
                ),
                day(active = 620.456, day = DAY.plus(2, kotlinx.datetime.DateTimeUnit.DAY)),
            ),
        ).daily

        assertEquals(
            listOf(null, null, 620.46),
            listOf(result[0].activeKcal, result[1].activeKcal, result[2].activeKcal),
        )
        assertEquals(listOf(100_000.0, null), listOf(result[0].totalKcal, result[1].totalKcal))
    }

    @Test
    fun dailyRowWithAllValuesMissing_isDropped() {
        assertTrue(clean(daily = listOf(day(steps = -3, active = -1.0))).daily.isEmpty())
    }

    @Test
    fun sleepMinutes_zeroDropped_overADayClamped_normalKept() {
        val result = clean(
            sleep = listOf(sleep(0, "a"), sleep(1441, "b"), sleep(435, "c")),
        ).sleep

        assertEquals(listOf("b" to 1440, "c" to 435), result.map { it.externalId to it.minutes }.sortedBy { it.first })
    }

    @Test
    fun sleepEndingWhenItStarts_isDropped() {
        assertTrue(clean(sleep = listOf(sleep(end = STARTED))).sleep.isEmpty())
    }

    @Test
    fun externalId_over200Characters_dropsTheRow_200IsKept() {
        val result = clean(
            sleep = listOf(sleep(id = "x".repeat(201)), sleep(id = "y".repeat(200))),
            resting = listOf(RestingHeartRate(DAY, 52, "z".repeat(201))),
        )

        assertEquals(listOf("y".repeat(200)), result.sleep.map { it.externalId })
        assertEquals(listOf(null), result.restingHeartRate.map { it.externalId })
    }

    @Test
    fun restingBpm_outsideTwentyToTwoFifty_isDroppedNeverClamped() {
        val result = clean(
            resting = listOf(19, 20, 250, 251).mapIndexed { index, bpm ->
                RestingHeartRate(DAY.plus(index, kotlinx.datetime.DateTimeUnit.DAY), bpm, null)
            },
        ).restingHeartRate

        assertEquals(listOf(20, 250), result.map { it.bpm })
    }

    @Test
    fun moreThan400Days_keepsTheNewest400() {
        val rows = (0 until 401).map { day(steps = it, day = DAY.plus(it, kotlinx.datetime.DateTimeUnit.DAY)) }

        val result = clean(daily = rows).daily

        assertEquals(400, result.size)
        assertEquals(DAY.plus(1, kotlinx.datetime.DateTimeUnit.DAY), result.minOf { it.day })
        assertEquals(DAY.plus(400, kotlinx.datetime.DateTimeUnit.DAY), result.maxOf { it.day })
    }

    @Test
    fun moreThan400SleepsAndRates_keepTheNewest400() {
        val sleeps = (0 until 401).map { i ->
            SleepSession("s$i", STARTED + (i * 1440).minutes, STARTED + (i * 1440 + 480).minutes, 400, emptyList())
        }
        val rates = (0 until 401).map { RestingHeartRate(DAY.plus(it, kotlinx.datetime.DateTimeUnit.DAY), 55, null) }

        val result = clean(sleep = sleeps, resting = rates)

        assertEquals(400, result.sleep.size)
        assertTrue(result.sleep.none { it.externalId == "s0" })
        assertEquals(400, result.restingHeartRate.size)
        assertEquals(DAY.plus(1, kotlinx.datetime.DateTimeUnit.DAY), result.restingHeartRate.minOf { it.day })
    }
}
