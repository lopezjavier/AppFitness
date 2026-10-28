package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepStage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** One resting heart rate reading as Health Connect holds it, before it is reduced to one per day. */
data class RestingReading(val id: String, val time: Instant, val bpm: Int)

/** Pure rules that turn raw Health Connect watch records into the shape stored on the server. */
object WatchDataShaping {
    private val ASLEEP_STAGES = setOf("light", "deep", "rem", "sleeping")

    /** Maps a Health Connect `SleepSessionRecord.STAGE_TYPE_*` value to the stable stage key. */
    fun stageKey(type: Int): String = when (type) {
        STAGE_AWAKE, STAGE_OUT_OF_BED, STAGE_AWAKE_IN_BED -> "awake"
        STAGE_SLEEPING -> "sleeping"
        STAGE_LIGHT -> "light"
        STAGE_DEEP -> "deep"
        STAGE_REM -> "rem"
        else -> "unknown"
    }

    /** Minutes asleep: the sum of the asleep stages, or the whole duration when the record has no stages. */
    fun sleepMinutes(stages: List<SleepStage>, startedAt: Instant, endedAt: Instant): Int {
        if (stages.isEmpty()) return (endedAt - startedAt).inWholeMinutes.toInt()
        return stages.filter { it.stage in ASLEEP_STAGES }.sumOf { (it.endedAt - it.startedAt).inWholeMinutes.toInt() }
    }

    /** A night belongs to the local day it ends on. */
    fun sleepDay(endedAt: Instant, zone: TimeZone): LocalDate = endedAt.toLocalDateTime(zone).date

    /** One resting heart rate per local day: the latest reading of the day wins. */
    fun restingPerDay(readings: List<RestingReading>, zone: TimeZone): List<RestingHeartRate> =
        readings.groupBy { it.time.toLocalDateTime(zone).date }
            .map { (day, ofDay) ->
                val latest = ofDay.maxBy { it.time }
                RestingHeartRate(day, latest.bpm, latest.id)
            }
            .sortedBy { it.day }

    // Values of SleepSessionRecord.STAGE_TYPE_*; they are part of the Health Connect contract.
    private const val STAGE_AWAKE = 1
    private const val STAGE_SLEEPING = 2
    private const val STAGE_OUT_OF_BED = 3
    private const val STAGE_LIGHT = 4
    private const val STAGE_DEEP = 5
    private const val STAGE_REM = 6
    private const val STAGE_AWAKE_IN_BED = 7
}
