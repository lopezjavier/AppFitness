package io.github.lopezjavier.appfitness.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** The activity totals of one calendar day (device time zone). Each value is null when the watch gave none. */
data class DailyActivity(val day: LocalDate, val steps: Int?, val activeKcal: Double?, val totalKcal: Double?)

/** One phase of a sleep session; [stage] is a stable key such as `awake|light|deep|rem|sleeping|unknown`. */
data class SleepStage(val stage: String, val startedAt: Instant, val endedAt: Instant)

/** One night's sleep; [minutes] is the time actually asleep. [externalId] is the Health Connect record id. */
data class SleepSession(
    val externalId: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val minutes: Int,
    val stages: List<SleepStage>,
)

/** The resting heart rate of one day. */
data class RestingHeartRate(val day: LocalDate, val bpm: Int, val externalId: String?)

/** Everything one watch-data sync sends to the server. */
data class HealthActivityBatch(
    val daily: List<DailyActivity>,
    val sleep: List<SleepSession>,
    val restingHeartRate: List<RestingHeartRate>,
)

/** Rows written (inserted or replaced) per type after de-duplication. */
data class HealthActivityImportResult(val daily: Int, val sleep: Int, val restingHeartRate: Int)

/** What the activity screen shows: the latest sleep, the last seven days (newest first) and the latest resting rate. */
data class HealthActivitySummary(
    val lastSleep: SleepSession?,
    val recentDays: List<DailyActivity>,
    val restingHeartRate: RestingHeartRate?,
)
