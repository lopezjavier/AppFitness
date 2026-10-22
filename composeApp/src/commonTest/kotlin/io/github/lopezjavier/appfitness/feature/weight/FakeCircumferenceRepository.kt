package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceEntry
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import kotlin.time.Instant

/** In-memory [CircumferenceRepository]; every row gets a growing `createdAt` so later saves order last. */
internal class FakeCircumferenceRepository(vararg initial: BodyCircumference) : CircumferenceRepository {
    val rows = initial.toMutableList()
    val deletes = mutableListOf<String>()

    /** One list of entries per successful `addAll` call. */
    val addCalls = mutableListOf<List<CircumferenceEntry>>()
    var failReads = false
    var writeFailure: Throwable? = null
    private var nextId = 1

    override suspend fun getAll(): Result<List<BodyCircumference>> = if (failReads) {
        Result.failure(AppException(AppError.Network))
    } else {
        Result.success(rows.sortedByDescending { it.measuredAt })
    }

    override suspend fun addAll(entries: List<CircumferenceEntry>, measuredAt: Instant): Result<Unit> {
        writeFailure?.let { return Result.failure(it) }
        addCalls += entries
        entries.forEach {
            val n = nextId++
            rows += BodyCircumference("new-$n", it.site, it.valueCm, measuredAt, Instant.fromEpochSeconds(n.toLong()))
        }
        return Result.success(Unit)
    }

    override suspend fun update(id: String, valueCm: Double, measuredAt: Instant): Result<Unit> {
        writeFailure?.let { return Result.failure(it) }
        val index = rows.indexOfFirst { it.id == id }
        if (index >= 0) rows[index] = rows[index].copy(valueCm = valueCm, measuredAt = measuredAt)
        return if (index >= 0) Result.success(Unit) else Result.failure(AppException(AppError.NotFound))
    }

    override suspend fun delete(id: String): Result<Unit> {
        writeFailure?.let { return Result.failure(it) }
        deletes += id
        rows.removeAll { it.id == id }
        return Result.success(Unit)
    }
}
