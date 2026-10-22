package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.HealthDataSource
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import kotlin.time.Instant

/**
 * Keeps Health Connect in step with the user's manual weights (planning/04 §3.7): a saved weight is written (an edit
 * only when a copy already exists), a deleted one removes its copy. It is best effort: a failed Health Connect call or
 * a failed marker store never reaches the caller, so a save or delete is never blocked.
 */
class HealthWriteBack(
    private val source: HealthDataSource,
    private val repository: BodyMeasurementRepository,
    private val clock: Clock,
) {
    /**
     * [previous] is the entry as it was before an edit, or null for a new save (a new save also covers the "same day
     * replaces the entry" case: writing again with the same client id is an idempotent update).
     */
    suspend fun onSaved(
        rowId: String,
        weightKg: Double,
        bodyFatPct: Double?,
        measuredAt: Instant,
        previous: BodyMeasurement?,
    ) {
        if (previous != null && previous.hcRecordId == null) return
        if (!source.access().canWrite) return
        val written = source.writeWeighIn(rowId, weightKg, bodyFatPct, measuredAt, clock.now().toEpochMilliseconds())
        if (written.isSuccess && previous?.hcRecordId != rowId) {
            repository.setHcRecordId(rowId, rowId)
        }
    }

    /** Preferencias saves the weight inside `save_preferences`: read today's manual entry back and write it. */
    suspend fun onTodaysWeightSaved(dayStart: Instant, dayEnd: Instant) {
        val row = repository.getMeasurements(dayStart, dayEnd).getOrNull()
            ?.filter { it.source == MeasurementSource.Manual }
            ?.maxByOrNull { it.measuredAt }
            ?: return
        onSaved(row.id, row.weightKg, row.bodyFatPct, row.measuredAt, previous = null)
    }

    suspend fun onDeleted(row: BodyMeasurement) {
        val copy = row.hcRecordId ?: return
        source.deleteCopy(copy)
    }
}
