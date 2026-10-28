package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.HealthActivityDay
import io.github.lopezjavier.appfitness.domain.model.HealthActivityHistory
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** What the activity chart can plot. */
enum class ActivityMetric {
    Steps,
    Sleep,
    ActiveKcal,
    TotalKcal,
    RestingHeartRate,
    ;

    companion object {
        val Default = Steps
    }
}

/** One chart point: steps, kcal, sleep minutes or bpm of [day]. */
data class ActivitySeriesPoint(val day: LocalDate, val value: Double)

/** The chart series of one metric over a [ChartWindow]. */
object ActivitySeries {
    /**
     * One point per local day with data, ascending; days without data have no point. Only `visibleStart` (inclusive)
     * and `endExclusive` are used. Sleep is dated by the day (in [zone]) its session ended; sessions of a day add up.
     */
    fun of(
        history: HealthActivityHistory,
        metric: ActivityMetric,
        window: ChartWindow,
        zone: TimeZone,
    ): List<ActivitySeriesPoint> {
        val points = when (metric) {
            ActivityMetric.Steps -> history.daily.mapNotNull { row -> row.steps?.let { row.day to it.toDouble() } }
            ActivityMetric.ActiveKcal -> history.daily.mapNotNull { row -> row.activeKcal?.let { row.day to it } }
            ActivityMetric.TotalKcal -> history.daily.mapNotNull { row -> row.totalKcal?.let { row.day to it } }
            ActivityMetric.RestingHeartRate -> history.restingHeartRate.map { it.day to it.bpm.toDouble() }
            ActivityMetric.Sleep -> history.sleep.map { it.endedAt.toLocalDateTime(zone).date to it.minutes.toDouble() }
        }
        return points
            .filter { (day, _) -> day >= window.visibleStart && day < window.endExclusive }
            .groupBy({ it.first }, { it.second })
            .map { (day, values) -> ActivitySeriesPoint(day, values.sum()) }
            .sortedBy { it.day }
    }
}

/** The five values of one day taken from a loaded history. */
object ActivityDayValues {
    /** Sleep sessions that ended on [day] (in [zone]) are merged into one: minutes add up, stages are joined. */
    fun of(history: HealthActivityHistory, day: LocalDate, zone: TimeZone): HealthActivityDay {
        val daily = history.daily.firstOrNull { it.day == day }
        val sessions = history.sleep.filter { it.endedAt.toLocalDateTime(zone).date == day }
        return HealthActivityDay(
            day = day,
            steps = daily?.steps,
            activeKcal = daily?.activeKcal,
            totalKcal = daily?.totalKcal,
            sleep = merge(sessions),
            restingHeartRate = history.restingHeartRate.firstOrNull { it.day == day }?.bpm,
        )
    }

    private fun merge(sessions: List<SleepSession>): SleepSession? = when (sessions.size) {
        0 -> null

        1 -> sessions.single()

        else -> SleepSession(
            externalId = sessions.first().externalId,
            startedAt = sessions.minOf { it.startedAt },
            endedAt = sessions.maxOf { it.endedAt },
            minutes = sessions.sumOf { it.minutes },
            stages = sessions.flatMap { it.stages },
        )
    }
}

/** Minutes per stage key of one sleep session. */
data class SleepStageSummary(val minutesByStage: Map<String, Int>) {
    companion object {
        /** Null when the session carries no stages (only its total duration is known). */
        fun of(session: SleepSession): SleepStageSummary? {
            if (session.stages.isEmpty()) return null
            val minutes = session.stages
                .groupBy({ it.stage }, { (it.endedAt - it.startedAt).inWholeSeconds })
                .mapValues { (_, seconds) -> (seconds.sum() / SECONDS_PER_MINUTE).toInt() }
            return SleepStageSummary(minutes)
        }

        private const val SECONDS_PER_MINUTE = 60L
    }
}
