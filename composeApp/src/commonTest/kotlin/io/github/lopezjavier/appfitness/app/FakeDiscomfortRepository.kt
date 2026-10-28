package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import kotlin.time.Instant

/** In-memory discomforts; [failing] makes every call fail; [clock] stamps the logged rows. */
internal class FakeDiscomfortRepository(
    var rows: List<Discomfort> = emptyList(),
    var failing: Boolean = false,
    private val now: () -> Instant = { Instant.parse("2026-10-19T10:00:00Z") },
) : DiscomfortRepository {
    var logCalls = 0
    var resolveCalls = 0

    override suspend fun log(exerciseId: String, note: String?, severe: Boolean): Result<Discomfort> {
        logCalls++
        if (failing) return Result.failure(AppException(AppError.Network))
        val row = Discomfort("d${rows.size + 1}", exerciseId, now(), note, severe)
        rows = listOf(row) + rows
        return Result.success(row)
    }

    override suspend fun resolveOpen(exerciseId: String, at: Instant): Result<Unit> {
        resolveCalls++
        if (failing) return Result.failure(AppException(AppError.Network))
        rows = rows.map { if (it.exerciseId == exerciseId && it.resolvedAt == null) it.copy(resolvedAt = at) else it }
        return Result.success(Unit)
    }

    override suspend fun unresolved(exerciseIds: Set<String>): Result<List<Discomfort>> {
        if (failing) return Result.failure(AppException(AppError.Network))
        return Result.success(
            rows.filter { it.exerciseId in exerciseIds && it.resolvedAt == null }.sortedByDescending { it.loggedAt },
        )
    }
}
