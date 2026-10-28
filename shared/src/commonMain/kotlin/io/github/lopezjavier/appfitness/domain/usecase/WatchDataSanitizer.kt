package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthActivityBatch
import io.github.lopezjavier.appfitness.domain.model.RestingHeartRate
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import kotlin.math.round

/**
 * Brings a watch-data batch inside the database ranges before it is sent: one out-of-range row would fail the whole
 * import (check violation), and an array over 400 rows is rejected. Implausible values are dropped or clamped.
 */
object WatchDataSanitizer {
    private const val MAX_STEPS = 1_000_000
    private const val MAX_KCAL = 100_000.0
    private const val MAX_MINUTES = 1440
    private const val MIN_BPM = 20
    private const val MAX_BPM = 250
    private const val MAX_EXTERNAL_ID = 200
    private const val MAX_ROWS = 400
    private const val CENTS = 100.0

    fun sanitize(batch: HealthActivityBatch) = HealthActivityBatch(
        daily = batch.daily.mapNotNull(::cleanDaily).sortedByDescending { it.day }.take(MAX_ROWS).sortedBy { it.day },
        sleep = batch.sleep.mapNotNull(::cleanSleep).sortedByDescending { it.endedAt }.take(MAX_ROWS)
            .sortedBy { it.endedAt },
        restingHeartRate = batch.restingHeartRate.mapNotNull(::cleanResting).sortedByDescending { it.day }
            .take(MAX_ROWS).sortedBy { it.day },
    )

    private fun cleanDaily(row: DailyActivity): DailyActivity? {
        val cleaned = row.copy(
            steps = row.steps?.takeIf { it >= 0 }?.coerceAtMost(MAX_STEPS),
            activeKcal = cleanKcal(row.activeKcal),
            totalKcal = cleanKcal(row.totalKcal),
        )
        return cleaned.takeUnless { it.steps == null && it.activeKcal == null && it.totalKcal == null }
    }

    private fun cleanKcal(value: Double?): Double? {
        if (value == null || !value.isFinite() || value < 0) return null
        return round(value.coerceAtMost(MAX_KCAL) * CENTS) / CENTS
    }

    private fun cleanSleep(row: SleepSession): SleepSession? = when {
        row.endedAt <= row.startedAt -> null
        row.minutes < 1 -> null
        row.externalId.length > MAX_EXTERNAL_ID -> null
        else -> row.copy(minutes = row.minutes.coerceAtMost(MAX_MINUTES))
    }

    private fun cleanResting(row: RestingHeartRate): RestingHeartRate? {
        if (row.bpm !in MIN_BPM..MAX_BPM) return null
        return row.copy(externalId = row.externalId?.takeIf { it.length <= MAX_EXTERNAL_ID })
    }
}
