package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepStage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val MADRID = TimeZone.of("Europe/Madrid")
private val START = Instant.parse("2026-10-21T21:00:00Z")

class WatchDataShapingTest {
    private fun stage(key: String, fromMinute: Int, toMinute: Int) = SleepStage(
        key,
        START.plus(kotlin.time.Duration.parse("${fromMinute}m")),
        START.plus(kotlin.time.Duration.parse("${toMinute}m")),
    )

    @Test
    fun sleepMinutes_sumsOnlyTheStagesAsleep() {
        val end = START.plus(kotlin.time.Duration.parse("480m"))
        val stages = listOf(
            stage("light", 0, 200),
            stage("awake", 200, 245),
            stage("deep", 245, 380),
            stage("rem", 380, 440),
            stage("sleeping", 440, 480),
            stage("unknown", 480, 480),
        )

        assertEquals(435, WatchDataShaping.sleepMinutes(stages, START, end))
    }

    @Test
    fun sleepMinutes_withoutStages_isTheWholeDuration() {
        val end = START.plus(kotlin.time.Duration.parse("420m"))

        assertEquals(420, WatchDataShaping.sleepMinutes(emptyList(), START, end))
    }

    @Test
    fun sleepDay_isTheLocalDayOfTheEnd_evenWhenTheNightCrossesMidnight() {
        // Slept 22:30 on the 21st until 00:30 on the 22nd, Madrid time (UTC+2).
        val ended = Instant.parse("2026-10-21T22:30:00Z")

        assertEquals(LocalDate(2026, 10, 22), WatchDataShaping.sleepDay(ended, MADRID))
    }

    @Test
    fun stageKey_mapsHealthConnectStageTypes() {
        val expected = mapOf(
            0 to "unknown",
            1 to "awake",
            2 to "sleeping",
            3 to "awake",
            4 to "light",
            5 to "deep",
            6 to "rem",
            7 to "awake",
            99 to "unknown",
        )

        expected.forEach { (type, key) -> assertEquals(key, WatchDataShaping.stageKey(type), "type $type") }
    }

    @Test
    fun restingPerDay_keepsTheLatestReadingOfEachLocalDay() {
        val readings = listOf(
            RestingReading("a", Instant.parse("2026-10-22T05:00:00Z"), 55),
            RestingReading("b", Instant.parse("2026-10-22T10:00:00Z"), 52),
            RestingReading("c", Instant.parse("2026-10-21T10:00:00Z"), 58),
        )

        val result = WatchDataShaping.restingPerDay(readings, MADRID)

        assertEquals(
            listOf(
                RestingHeartRate(LocalDate(2026, 10, 21), 58, "c"),
                RestingHeartRate(LocalDate(2026, 10, 22), 52, "b"),
            ),
            result,
        )
    }
}
