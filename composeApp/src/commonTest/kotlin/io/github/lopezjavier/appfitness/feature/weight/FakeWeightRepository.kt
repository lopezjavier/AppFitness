package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import kotlin.time.Instant

/** In-memory [BodyMeasurementRepository] that applies saves and deletes like the RPC does. */
internal class FakeWeightRepository(vararg initial: BodyMeasurement) : BodyMeasurementRepository {
    val rows = initial.toMutableList()
    val saves = mutableListOf<ManualWeightSave>()
    val deletes = mutableListOf<String>()
    val hcRecordIds = mutableListOf<Pair<String, String?>>()
    var failReads = false
    var getMeasurementsCalls = 0
    var failComposition = false
    var onboarding: BodyMeasurement? = null
    var saveFailure: Throwable? = null
    var deleteFailure: Throwable? = null
    private var nextId = 1

    override suspend fun getOnboardingWeight(): Result<BodyMeasurement?> = Result.success(onboarding)

    override suspend fun saveOnboardingWeight(weightKg: Double, measuredAt: Instant): Result<Unit> =
        Result.success(Unit)

    override suspend fun getLatestWeight(): Result<BodyMeasurement?> = if (failReads) {
        Result.failure(AppException(AppError.Network))
    } else {
        Result.success(rows.maxByOrNull { it.measuredAt })
    }

    override suspend fun getMeasurements(from: Instant, toExclusive: Instant): Result<List<BodyMeasurement>> =
        if (failReads.also { getMeasurementsCalls++ }) {
            Result.failure(AppException(AppError.Network))
        } else {
            Result.success(rows.filter { it.measuredAt >= from && it.measuredAt < toExclusive })
        }

    override suspend fun getLatestComposition(): Result<LatestComposition> = if (failComposition) {
        Result.failure(AppException(AppError.Network))
    } else {
        Result.success(LatestComposition.from(rows))
    }

    override suspend fun saveManualWeight(save: ManualWeightSave): Result<String> {
        saveFailure?.let { return Result.failure(it) }
        saves += save
        val saved = BodyMeasurement(
            id = save.id ?: "new-${nextId++}",
            measuredAt = save.measuredAt,
            weightKg = save.weightKg,
            source = MeasurementSource.Manual,
            bodyFatPct = save.bodyFatPct,
        )
        val sameDayManual = rows.indexOfFirst {
            it.source == MeasurementSource.Manual &&
                it.measuredAt >= save.dayStart &&
                it.measuredAt < save.dayEnd &&
                (save.id == null || it.id == save.id)
        }
        if (sameDayManual >= 0) rows[sameDayManual] = saved.copy(id = rows[sameDayManual].id) else rows += saved
        return Result.success(if (sameDayManual >= 0) rows[sameDayManual].id else saved.id)
    }

    override suspend fun deleteMeasurement(id: String): Result<Unit> {
        deleteFailure?.let { return Result.failure(it) }
        deletes += id
        rows.removeAll { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun setHcRecordId(id: String, hcRecordId: String?): Result<Unit> {
        hcRecordIds += id to hcRecordId
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = rows[index].copy(hcRecordId = hcRecordId)
        return Result.success(Unit)
    }
}
